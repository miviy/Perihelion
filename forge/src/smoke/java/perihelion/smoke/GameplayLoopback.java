package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import io.netty.bootstrap.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.*;
import io.netty.channel.socket.nio.*;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** A controlled PLAY peer receiving real serialized packets, without game authentication. */
final class GameplayLoopback implements AutoCloseable {
  final List<Object> received = new CopyOnWriteArrayList<>();
  final NioEventLoopGroup group = new NioEventLoopGroup(2);
  final AtomicReference<Throwable> failure = new AtomicReference<>();
  final AtomicReference<Channel> accepted = new AtomicReference<>();
  final AtomicReference<Object> manager = new AtomicReference<>();
  final AtomicReference<Object> serverManager = new AtomicReference<>();
  final List<Object> delivered = new CopyOnWriteArrayList<>();
  final Object gameHandler;
  final Field managerField;
  final Object originalManager;
  Channel server, client;
  private Channel originalChannel;
  private String keepAliveHandler;

  GameplayLoopback(Object game) throws Exception {
    gameHandler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
    managerField = field(gameHandler.getClass(), "netManager", "field_147302_e");
    originalManager = managerField.get(gameHandler);
    CountDownLatch ready = new CountDownLatch(2);
    try {
      server =
          new ServerBootstrap()
              .group(group)
              .channel(NioServerSocketChannel.class)
              .childHandler(
                  new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {
                      accepted.set(channel);
                      Object peer =
                          NetworkHookProbe.socketManager(
                              "SERVERBOUND",
                              "INetHandlerPlayServer",
                              (proxy, method, arguments) -> {
                                if (arguments != null
                                    && arguments.length == 1
                                    && Class.forName("net.minecraft.network.Packet")
                                        .isInstance(arguments[0])) received.add(arguments[0]);
                                return null;
                              });
                      serverManager.set(peer);
                      NetworkHookProbe.installCodecs(
                          channel, peer, "SERVERBOUND", "CLIENTBOUND", ready, failure);
                    }
                  })
              .bind(new InetSocketAddress("127.0.0.1", 0))
              .sync()
              .channel();
      client =
          new Bootstrap()
              .group(group)
              .channel(NioSocketChannel.class)
              .handler(
                  new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {
                      Object peer =
                          NetworkHookProbe.socketManager(
                              "CLIENTBOUND",
                              "INetHandlerPlayClient",
                              (proxy, method, arguments) -> {
                                if (arguments != null
                                    && arguments.length == 1
                                    && Class.forName("net.minecraft.network.Packet")
                                        .isInstance(arguments[0])) delivered.add(arguments[0]);
                                return null;
                              });
                      manager.set(peer);
                      NetworkHookProbe.installCodecs(
                          channel, peer, "CLIENTBOUND", "SERVERBOUND", ready, failure);
                    }
                  })
              .connect(server.localAddress())
              .sync()
              .channel();
      if (!ready.await(5, TimeUnit.SECONDS))
        throw new AssertionError("gameplay loopback PLAY timeout");
      healthy();
      routeOriginalKeepAlives();
      managerField.set(gameHandler, manager.get());
    } catch (Throwable problem) {
      close();
      throw problem;
    }
  }

  void await(int count) throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (received.size() < count && System.nanoTime() < until) {
      healthy();
      Thread.sleep(2);
    }
    healthy();
    if (received.size() < count)
      throw new AssertionError("loopback received " + received.size() + " of " + count);
  }

  void sendServer(Object packet) throws Exception {
    method(
            serverManager.get().getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(serverManager.get(), packet);
    accepted.get().eventLoop().submit(() -> {}).sync();
  }

  private void healthy() {
    if (failure.get() != null)
      throw new AssertionError("gameplay loopback codec failure", failure.get());
  }

  // NetHandlerPlayClient replies through its mutable manager field. While that field
  // points at this fixture, a real integrated-server keepalive would otherwise reply
  // into the test TCP peer and contaminate its exact packet sequence.
  private void routeOriginalKeepAlives() throws Exception {
    if (!(Boolean)
        method(originalManager.getClass(), "isLocalChannel", "func_150731_c")
            .invoke(originalManager)) return;
    originalChannel =
        (Channel)
            field(originalManager.getClass(), "channel", "field_150746_k").get(originalManager);
    Class<?> keepAlive = Class.forName("net.minecraft.network.play.server.S00PacketKeepAlive");
    Constructor<?> reply =
        Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
            .getConstructor(int.class);
    Method id = method(keepAlive, "func_149134_c");
    Method send =
        method(
            originalManager.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a");
    keepAliveHandler = "perihelion-loopback-keepalive-" + System.identityHashCode(this);
    originalChannel
        .pipeline()
        .addBefore(
            "packet_handler",
            keepAliveHandler,
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext context, Object message)
                  throws Exception {
                if (keepAlive.isInstance(message)) {
                  send.invoke(
                      originalManager, reply.newInstance(((Number) id.invoke(message)).intValue()));
                } else context.fireChannelRead(message);
              }
            });
    originalChannel.eventLoop().submit(() -> {}).sync();
  }

  boolean verifyOriginalKeepAlive() throws Exception {
    if (originalChannel == null) return false;
    final int probeId = 0x5a137;
    java.util.concurrent.atomic.AtomicInteger replies =
        new java.util.concurrent.atomic.AtomicInteger();
    Class<?> reply = Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive");
    Method key = method(reply, "getKey", "func_149460_c");
    String recorder = keepAliveHandler + "-probe";
    originalChannel
        .pipeline()
        .addLast(
            recorder,
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(
                  ChannelHandlerContext context, Object message, ChannelPromise promise)
                  throws Exception {
                if (reply.isInstance(message)
                    && ((Number) key.invoke(message)).intValue() == probeId)
                  replies.incrementAndGet();
                context.write(message, promise);
              }
            });
    try {
      Object inbound =
          Class.forName("net.minecraft.network.play.server.S00PacketKeepAlive")
              .getConstructor(int.class)
              .newInstance(probeId);
      originalChannel
          .eventLoop()
          .submit(() -> originalChannel.pipeline().fireChannelRead(inbound))
          .sync();
      originalChannel.eventLoop().submit(() -> {}).sync();
      healthy();
      return replies.get() == 1;
    } finally {
      originalChannel.pipeline().remove(recorder);
    }
  }

  @Override
  public void close() throws Exception {
    managerField.set(gameHandler, originalManager);
    if (originalChannel != null
        && keepAliveHandler != null
        && originalChannel.pipeline().context(keepAliveHandler) != null)
      originalChannel.pipeline().remove(keepAliveHandler);
    if (client != null) client.close().sync();
    if (accepted.get() != null) accepted.get().close().sync();
    if (server != null) server.close().sync();
    group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
  }
}
