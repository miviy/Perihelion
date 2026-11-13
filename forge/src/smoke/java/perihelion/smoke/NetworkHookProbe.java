package perihelion.smoke;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs against the actual Mixin-transformed class in an isolated game process. */
public final class NetworkHookProbe {
    private static Class<?> managerType;
    private static Class<?> packetType;
    private static Class<?> targetType;
    private static Class<?> bridgeType;
    private static Method sendSimple;
    private static Method sendListeners;
    private static Method read;
    private static Field channelField;
    private static Field handlerField;
    private static int checks;

    private NetworkHookProbe() { }

    public static void run() throws Exception {
        managerType = type("net.minecraft.network.NetworkManager");
        packetType = type("net.minecraft.network.Packet");
        targetType = type("perihelion.integration.minecraft189.HookBridge$Target");
        bridgeType = type("perihelion.integration.minecraft189.HookBridge");
        sendSimple = method(managerType, 1, "sendPacket", "func_179290_a");
        sendListeners = method(managerType, 3, "sendPacket", "func_179288_a");
        read = method(managerType, 2, "channelRead0");
        // The compiler also emits an Object bridge method; use the Packet overload.
        for (Method candidate : managerType.getDeclaredMethods()) {
            if (candidate.getName().equals("channelRead0") && candidate.getParameterTypes()[1] == packetType) {
                read = candidate;
                read.setAccessible(true);
            }
        }
        channelField = field(managerType, "channel", "field_150746_k");
        handlerField = field(managerType, "packetListener", "field_150744_m");
        Field targetField = field(bridgeType, "target");
        Object previous = targetField.get(null);
        if (previous != null) bridgeType.getMethod("clear", targetType).invoke(null, previous);
        State state = new State();
        Object target = Proxy.newProxyInstance(targetType.getClassLoader(), new Class<?>[]{targetType},
                (proxy, called, arguments) -> state.call(called.getName(), arguments));
        List<Fixture> fixtures = new ArrayList<Fixture>();
        try {
            Fixture noTarget = fixture(fixtures, "CLIENTBOUND", true);
            Object initial = outbound();
            send(noTarget, initial);
            check(noTarget.channel.readOutbound() == initial, "uninstalled bridge is transparent");
            bridgeType.getMethod("install", targetType).invoke(null, target);
            overloadsAndQueue(state, fixtures);
            cancellationAndReentry(state, fixtures);
            filters(state, fixtures);
            inboundAndBuffers(state, fixtures);
            threads(state, fixtures);
            socketRoundTrip(state);
            System.out.println("[PERIHELION_SMOKE] PASS networkHooks checks=" + checks
                    + " transformedClass=" + managerType.getName());
        } finally {
            bridgeType.getMethod("clear", targetType).invoke(null, target);
            if (previous != null) bridgeType.getMethod("install", targetType).invoke(null, previous);
            for (Fixture fixture : fixtures) {
                if (fixture.channel == null) continue;
                channelField.set(fixture.manager, fixture.channel);
                fixture.channel.finish();
                while (fixture.channel.readOutbound() != null) { }
            }
        }
    }

    private static void overloadsAndQueue(State state, List<Fixture> fixtures) throws Exception {
        state.reset();
        Fixture fixture = fixture(fixtures, "CLIENTBOUND", true);
        Object first = outbound();
        Object second = outbound();
        List<Integer> order = new ArrayList<Integer>();
        send(fixture, first);
        send(fixture, second, future -> { check(future.isSuccess(), "allowed send completion succeeds"); order.add(1); },
                future -> order.add(2));
        check(state.sends == 2, "both send overloads dispatch exactly once");
        check(fixture.channel.readOutbound() == first && fixture.channel.readOutbound() == second,
                "allowed packet ordering preserved");
        check(order.equals(Arrays.asList(1, 2)), "allowed listeners preserve order");

        state.reset();
        Fixture queued = fixture(fixtures, "CLIENTBOUND", false);
        Object beforeOpen = outbound();
        send(queued, beforeOpen);
        check(state.sends == 0, "queued packets are observed at dispatch, not enqueue");
        queued.attach();
        Object afterOpen = outbound();
        send(queued, afterOpen);
        check(state.sends == 2, "queued packet and following packet dispatch once each");
        check(queued.channel.readOutbound() == beforeOpen && queued.channel.readOutbound() == afterOpen,
                "queued ordering preserved");

        state.reset();
        state.cancelSend = true;
        Fixture queuedCancellation = fixture(fixtures, "CLIENTBOUND", false);
        List<Integer> cancellationOrder = new ArrayList<Integer>();
        send(queuedCancellation, outbound(), future -> {
            if (future.isCancelled()) cancellationOrder.add(1);
        }, future -> { if (future.isCancelled()) cancellationOrder.add(2); });
        queuedCancellation.attach();
        send(queuedCancellation, outbound());
        check(state.sends == 2 && cancellationOrder.equals(Arrays.asList(1, 2)),
                "queued cancellation resolves original listeners exactly once in order");
        check(queuedCancellation.channel.readOutbound() == null, "queued cancelled packets are not delivered");
    }

    private static void cancellationAndReentry(State state, List<Fixture> fixtures) throws Exception {
        state.reset();
        Fixture fixture = fixture(fixtures, "CLIENTBOUND", true);
        state.cancelSend = true;
        List<Integer> order = new ArrayList<Integer>();
        AtomicReference<Throwable> listenerFailure = new AtomicReference<Throwable>();
        Object completionSend = outbound();
        send(fixture, outbound(), future -> {
            try {
                check(future.isCancelled() && !future.isSuccess(), "cancelled send resolves future as cancelled");
                order.add(1);
                state.cancelSend = false;
                send(fixture, completionSend);
            } catch (Throwable failure) { listenerFailure.set(failure); }
        }, future -> order.add(2));
        if (listenerFailure.get() != null) throw new AssertionError("completion callback failed", listenerFailure.get());
        check(order.equals(Arrays.asList(1, 2)), "cancelled listeners preserve order");
        check(state.sends == 2, "completion callbacks run after raw callback guard exits");
        check(fixture.channel.readOutbound() == completionSend && fixture.channel.readOutbound() == null,
                "cancelled original packet never enters outbound queue");
        check(fixture.channel.isOpen(), "cancellation does not close channel");

        state.reset();
        state.beforeSend = () -> send(fixture, outbound());
        Object outer = outbound();
        send(fixture, outer);
        check(state.sends == 1 && state.failures == 1, "recursive raw send faults its outer callback");
        check(fixture.channel.readOutbound() == outer && fixture.channel.readOutbound() == null,
                "recursive packet is rejected and failed outer callback fails open");

        state.reset();
        state.beforeSend = () -> { throw new IllegalArgumentException("intentional probe failure"); };
        Object ordinary = outbound();
        send(fixture, ordinary);
        check(state.failures == 1 && fixture.channel.readOutbound() == ordinary,
                "ordinary hook failure preserves packet delivery");
        state.reset();
        send(fixture, outbound());
        check(state.sends == 1, "callback guard is cleared after failure");
        fixture.channel.readOutbound();
        state.reset();
        state.beforeSend = () -> { throw new ThreadDeath(); };
        boolean fatalEscaped = false;
        try { send(fixture, outbound()); } catch (ThreadDeath expected) { fatalEscaped = true; }
        check(fatalEscaped && state.failures == 0, "VM-fatal callback errors escape isolation");
        state.reset();
    }

    private static void filters(State state, List<Fixture> fixtures) throws Exception {
        state.reset();
        Fixture fixture = fixture(fixtures, "CLIENTBOUND", true);
        state.running = false;
        Object stopped = outbound();
        send(fixture, stopped);
        check(state.sends == 0 && fixture.channel.readOutbound() == stopped, "stopped runtime is transparent");
        state.running = true;
        send(fixture, type("net.minecraft.network.login.client.C00PacketLoginStart").newInstance());
        check(state.sends == 0, "login packets excluded");
        fixture.channel.readOutbound();
        fixture.play();
        handlerField.set(fixture.manager, null);
        send(fixture, outbound());
        check(state.sends == 0, "missing play handler excluded");
        fixture.channel.readOutbound();
        Fixture server = fixture(fixtures, "SERVERBOUND", true);
        send(server, outbound());
        check(state.sends == 0, "integrated-server direction excluded");
        server.channel.readOutbound();

        Fixture local = fixture(fixtures, "CLIENTBOUND", true);
        LocalChannel localChannel = new LocalChannel();
        channelField.set(local.manager, localChannel);
        invoke(read, local.manager, null, inbound());
        check(state.receives == 0 && local.delivered == 1, "local-channel receive bypasses hooks");
        channelField.set(local.manager, local.channel);
        local.channel.close();
        invoke(read, local.manager, null, inbound());
        check(state.receives == 0 && local.delivered == 1, "closed channel does not dispatch");

        // Forge's special proxy packet must not be treated as ordinary PLAY traffic.
        ByteBuf proxyBuffer = Unpooled.buffer();
        try {
            Class<?> bufferType = type("net.minecraft.network.PacketBuffer");
            Object packetBuffer = bufferType.getConstructor(ByteBuf.class).newInstance(proxyBuffer);
            Object proxyPacket = type("net.minecraftforge.fml.common.network.internal.FMLProxyPacket")
                    .getConstructor(bufferType, String.class).newInstance(packetBuffer, "PERIHELION_TEST");
            Fixture proxyFixture = fixture(fixtures, "CLIENTBOUND", true);
            send(proxyFixture, proxyPacket);
            check(state.sends == 0 && proxyFixture.channel.readOutbound() == proxyPacket,
                    "Forge proxy packet excluded without Forge imports in production hook");
        } finally { proxyBuffer.release(); }
    }

    private static void inboundAndBuffers(State state, List<Fixture> fixtures) throws Exception {
        state.reset();
        Fixture fixture = fixture(fixtures, "CLIENTBOUND", true);
        fixture.channel.writeInbound(inbound());
        check(state.receives == 1 && fixture.delivered == 1, "inbound delivery observed once");
        state.cancelReceive = true;
        fixture.channel.writeInbound(inbound());
        check(state.receives == 2 && fixture.delivered == 1, "cancelled inbound never reaches play handler");
        ByteBuf inboundData = Unpooled.buffer();
        Object inboundPayload = payload("server.S3FPacketCustomPayload", inboundData);
        inboundData.retain();
        fixture.channel.writeInbound(inboundPayload);
        check(inboundData.refCnt() == 1, "cancelled inbound payload releases one owned reference");
        inboundData.release();

        ByteBuf outboundData = Unpooled.buffer();
        try {
            state.cancelSend = true;
            send(fixture, payload("client.C17PacketCustomPayload", outboundData));
            check(outboundData.refCnt() == 1, "cancelled outbound payload remains caller-owned");
        } finally { outboundData.release(); }
        state.reset();
        state.beforeReceive = () -> { throw new IllegalStateException("intentional inbound failure"); };
        fixture.channel.writeInbound(inbound());
        check(state.failures == 1 && fixture.delivered == 2, "inbound failure preserves vanilla delivery");
    }

    private static void threads(State state, List<Fixture> fixtures) throws Exception {
        state.reset();
        Fixture fixture = fixture(fixtures, "CLIENTBOUND", true);
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        EventLoop eventLoop = group.next();
        try {
            Channel wrapped = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[]{Channel.class},
                    (proxy, called, args) -> {
                        if (called.getName().equals("eventLoop")) return eventLoop;
                        if (called.getName().equals("newPromise")) return new DefaultChannelPromise((Channel) proxy, eventLoop);
                        return invoke(called, fixture.channel, args);
                    });
            channelField.set(fixture.manager, wrapped);
            state.cancelSend = true;
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Thread> completionThread = new AtomicReference<Thread>();
            send(fixture, outbound(), future -> { completionThread.set(Thread.currentThread()); completed.countDown(); });
            check(completed.await(3, TimeUnit.SECONDS), "off-loop cancellation completion arrives");
            check(state.sendThread == Thread.currentThread(), "outbound raw callback stays on caller thread");
            check(completionThread.get() != Thread.currentThread() && eventLoop.inEventLoop(completionThread.get()),
                    "cancelled completion uses channel event loop");
            eventLoop.submit(() -> {
                try { invoke(read, fixture.manager, (ChannelHandlerContext) null, inbound()); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            }).sync();
            check(state.receiveThread != Thread.currentThread() && eventLoop.inEventLoop(state.receiveThread),
                    "inbound callback stays on receiving Netty thread");
        } finally {
            channelField.set(fixture.manager, fixture.channel);
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    /** Real TCP framing/serialization through Minecraft codecs, without authentication or a world. */
    private static void socketRoundTrip(State state) throws Exception {
        state.reset();
        NioEventLoopGroup group = new NioEventLoopGroup(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch roundTrip = new CountDownLatch(1);
        AtomicReference<Throwable> asynchronousFailure = new AtomicReference<Throwable>();
        AtomicReference<Object> serverManager = new AtomicReference<Object>();
        AtomicReference<Object> clientManager = new AtomicReference<Object>();
        AtomicReference<Channel> accepted = new AtomicReference<Channel>();
        AtomicInteger serverDelivered = new AtomicInteger();
        AtomicInteger clientDelivered = new AtomicInteger();
        Channel server = null;
        Channel client = null;
        try {
            server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel channel) throws Exception {
                            accepted.set(channel);
                            Object manager = socketManager("SERVERBOUND", "INetHandlerPlayServer", (proxy, called, args) -> {
                                if (args != null && args.length == 1 && packetType.isInstance(args[0])) {
                                    serverDelivered.incrementAndGet();
                                    try { invoke(sendSimple, serverManager.get(), inbound()); }
                                    catch (Throwable failure) { asynchronousFailure.set(failure); roundTrip.countDown(); }
                                }
                                return null;
                            });
                            serverManager.set(manager);
                            installCodecs(channel, manager, "SERVERBOUND", "CLIENTBOUND", ready, asynchronousFailure);
                        }
                    }).bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
            client = new Bootstrap().group(group).channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel channel) throws Exception {
                            Object manager = socketManager("CLIENTBOUND", "INetHandlerPlayClient", (proxy, called, args) -> {
                                if (args != null && args.length == 1 && packetType.isInstance(args[0])) {
                                    clientDelivered.incrementAndGet();
                                    roundTrip.countDown();
                                }
                                return null;
                            });
                            clientManager.set(manager);
                            installCodecs(channel, manager, "CLIENTBOUND", "SERVERBOUND", ready, asynchronousFailure);
                        }
                    }).connect(server.localAddress()).sync().channel();
            check(ready.await(5, TimeUnit.SECONDS), "loopback peers enter PLAY");
            if (asynchronousFailure.get() != null) throw new AssertionError("loopback setup failed", asynchronousFailure.get());
            invoke(sendSimple, clientManager.get(), outbound());
            check(roundTrip.await(5, TimeUnit.SECONDS), "serialized keepalive roundtrip completes");
            if (asynchronousFailure.get() != null) throw new AssertionError("loopback roundtrip failed", asynchronousFailure.get());
            check(serverDelivered.get() == 1 && clientDelivered.get() == 1,
                    "actual framed packets reach each play handler once");
            check(state.sends == 1 && state.receives == 1,
                    "real socket hooks only observe the client side once per direction");
            check(client.eventLoop().inEventLoop(state.receiveThread), "socket receive hook runs on actual channel event loop");

            state.cancelSend = true;
            CountDownLatch cancelled = new CountDownLatch(1);
            AtomicReference<Thread> completionThread = new AtomicReference<Thread>();
            GenericFutureListener<Future<? super Void>> listener = future -> {
                if (!future.isCancelled()) asynchronousFailure.set(new AssertionError("socket cancellation future not cancelled"));
                completionThread.set(Thread.currentThread());
                cancelled.countDown();
            };
            invoke(sendListeners, clientManager.get(), outbound(), listener, new GenericFutureListener<?>[0]);
            check(cancelled.await(5, TimeUnit.SECONDS), "actual socket cancelled send completion resolves");
            if (asynchronousFailure.get() != null) throw new AssertionError("loopback completion failed", asynchronousFailure.get());
            check(client.eventLoop().inEventLoop(completionThread.get()), "actual socket completion respects channel thread");
            check(serverDelivered.get() == 1 && client.isOpen(), "cancelled socket packet is not serialized and channel stays open");

            state.cancelReceive = true;
            CountDownLatch rejectedInbound = new CountDownLatch(1);
            state.beforeReceive = rejectedInbound::countDown;
            invoke(sendSimple, serverManager.get(), inbound());
            check(rejectedInbound.await(5, TimeUnit.SECONDS), "second serialized server packet reaches inbound hook");
            client.eventLoop().submit(() -> { }).sync();
            check(clientDelivered.get() == 1 && state.receives == 2, "cancelled socket inbound packet stops before play handler");
            System.out.println("[PERIHELION_SMOKE] PASS loopbackPacketCodec tcp=true authenticated=false world=false");
        } finally {
            state.reset();
            if (client != null) client.close().sync();
            if (accepted.get() != null) accepted.get().close().sync();
            if (server != null) server.close().sync();
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    static Object socketManager(String direction, String handler,
            java.lang.reflect.InvocationHandler callbacks) throws Exception {
        Class<?> managerType = type("net.minecraft.network.NetworkManager");
        Class<?> directionType = type("net.minecraft.network.EnumPacketDirection");
        Object manager = managerType.getConstructor(directionType).newInstance(enumValue(directionType, direction));
        Class<?> handlerType = type("net.minecraft.network.play." + handler);
        field(managerType, "packetListener", "field_150744_m").set(manager, Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType}, callbacks));
        return manager;
    }

    static void installCodecs(SocketChannel channel, Object manager, String readDirection, String writeDirection,
            CountDownLatch ready, AtomicReference<Throwable> failure) throws Exception {
        Class<?> managerType = manager.getClass();
        Class<?> directionType = type("net.minecraft.network.EnumPacketDirection");
        channel.pipeline().addLast("splitter", (ChannelHandler) type("net.minecraft.util.MessageDeserializer2").newInstance());
        channel.pipeline().addLast("decoder", (ChannelHandler) type("net.minecraft.util.MessageDeserializer")
                .getConstructor(directionType).newInstance(enumValue(directionType, readDirection)));
        channel.pipeline().addLast("prepender", (ChannelHandler) type("net.minecraft.util.MessageSerializer2").newInstance());
        channel.pipeline().addLast("encoder", (ChannelHandler) type("net.minecraft.util.MessageSerializer")
                .getConstructor(directionType).newInstance(enumValue(directionType, writeDirection)));
        channel.pipeline().addLast("packet_handler", (ChannelHandler) manager);
        channel.pipeline().addLast("probe_ready", new ChannelInboundHandlerAdapter() {
            @Override public void channelActive(ChannelHandlerContext context) throws Exception {
                context.fireChannelActive();
                context.executor().execute(() -> {
                    try {
                        method(managerType, 1, "setConnectionState", "func_150723_a").invoke(manager,
                                enumValue(type("net.minecraft.network.EnumConnectionState"), "PLAY"));
                    } catch (Throwable problem) { failure.set(problem); }
                    finally { ready.countDown(); }
                });
            }
            @Override public void exceptionCaught(ChannelHandlerContext context, Throwable problem) {
                failure.set(problem);
                context.close();
            }
        });
    }

    private static Fixture fixture(List<Fixture> fixtures, String direction, boolean attach) throws Exception {
        Fixture result = new Fixture(direction, attach);
        fixtures.add(result);
        return result;
    }

    private static final class Fixture {
        final Object manager;
        EmbeddedChannel channel;
        int delivered;
        Fixture(String direction, boolean attach) throws Exception {
            Class<?> directionType = type("net.minecraft.network.EnumPacketDirection");
            manager = managerType.getConstructor(directionType).newInstance(enumValue(directionType, direction));
            if (attach) attach();
            else {
                Class<?> playHandler = type("net.minecraft.network.play.INetHandlerPlayClient");
                handlerField.set(manager, Proxy.newProxyInstance(playHandler.getClassLoader(), new Class<?>[]{playHandler},
                        (proxy, called, args) -> { if (called.getParameterTypes().length == 1) delivered++; return null; }));
            }
        }
        void attach() throws Exception {
            channel = new EmbeddedChannel((ChannelHandler) manager);
            Class<?> playHandler = type("net.minecraft.network.play.INetHandlerPlayClient");
            handlerField.set(manager, Proxy.newProxyInstance(playHandler.getClassLoader(), new Class<?>[]{playHandler},
                    (proxy, called, args) -> { if (called.getParameterTypes().length == 1) delivered++; return null; }));
            play();
        }
        void play() throws Exception {
            method(managerType, 1, "setConnectionState", "func_150723_a").invoke(manager,
                    enumValue(type("net.minecraft.network.EnumConnectionState"), "PLAY"));
        }
    }

    private interface Action { void run() throws Exception; }
    private static final class State {
        volatile boolean running = true;
        volatile boolean cancelSend;
        volatile boolean cancelReceive;
        volatile int sends;
        volatile int receives;
        volatile int failures;
        volatile Thread sendThread;
        volatile Thread receiveThread;
        volatile Action beforeSend;
        volatile Action beforeReceive;
        void reset() {
            running = true; cancelSend = false; cancelReceive = false; sends = receives = failures = 0;
            beforeSend = beforeReceive = null; sendThread = receiveThread = null;
        }
        Object call(String method, Object[] args) throws Exception {
            if (method.equals("isRunning")) return running;
            if (method.equals("beforeSend")) {
                sends++; sendThread = Thread.currentThread(); if (beforeSend != null) beforeSend.run(); return cancelSend;
            }
            if (method.equals("beforeReceive")) {
                receives++; receiveThread = Thread.currentThread(); if (beforeReceive != null) beforeReceive.run(); return cancelReceive;
            }
            if (method.equals("reportHookFailure")) { failures++; return null; }
            if (method.equals("stop")) { running = false; return null; }
            if (method.equals("toString")) return "NetworkHookProbeTarget";
            return null;
        }
    }

    private static void send(Fixture fixture, Object packet) throws Exception { invoke(sendSimple, fixture.manager, packet); }
    @SafeVarargs
    private static void send(Fixture fixture, Object packet, GenericFutureListener<Future<? super Void>> first,
            GenericFutureListener<Future<? super Void>>... rest) throws Exception {
        invoke(sendListeners, fixture.manager, packet, first, rest);
    }
    private static Object outbound() throws Exception { return type("net.minecraft.network.play.client.C00PacketKeepAlive").newInstance(); }
    private static Object inbound() throws Exception { return type("net.minecraft.network.play.server.S00PacketKeepAlive").newInstance(); }
    private static Object payload(String suffix, ByteBuf buffer) throws Exception {
        Class<?> bufferType = type("net.minecraft.network.PacketBuffer");
        Object data = bufferType.getConstructor(ByteBuf.class).newInstance(buffer);
        return type("net.minecraft.network.play." + suffix).getConstructor(String.class, bufferType).newInstance("PERIHELION_TEST", data);
    }
    private static Object enumValue(Class<?> type, String name) {
        for (Object value : type.getEnumConstants()) if (((Enum<?>) value).name().equals(name)) return value;
        throw new IllegalArgumentException(name);
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    private static Method method(Class<?> type, int parameters, String... names) throws Exception {
        for (String name : names) for (Method candidate : type.getDeclaredMethods()) {
            if (candidate.getName().equals(name) && candidate.getParameterTypes().length == parameters) {
                candidate.setAccessible(true); return candidate;
            }
        }
        throw new NoSuchMethodException(type.getName() + Arrays.toString(names));
    }
    private static Field field(Class<?> type, String... names) throws Exception {
        for (String name : names) try { Field found = type.getDeclaredField(name); found.setAccessible(true); return found; }
            catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + Arrays.toString(names));
    }
    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        }
    }
    private static void check(boolean passed, String label) {
        if (!passed) throw new AssertionError(label);
        checks++;
    }
}
