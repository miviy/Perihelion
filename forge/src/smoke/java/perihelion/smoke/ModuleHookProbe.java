package perihelion.smoke;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import perihelion.core.context.ConnectionContext;
import perihelion.core.control.InputKey;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleEntry;
import perihelion.core.module.ModuleState;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.ItemUseDelayAccess;
import perihelion.integration.minecraft189.control.MinecraftControls;
import perihelion.integration.minecraft189.event.PlayerControlEvent;
import perihelion.integration.minecraft189.event.VisualEffectEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exercises the shipped module definitions against transformed Forge/OptiFine classes. */
public final class ModuleHookProbe {
    private static int checks;

    private ModuleHookProbe() {}

    public static void run(ClientRuntime runtime) throws Exception {
        Class<?> gameType = Class.forName("net.minecraft.client.Minecraft");
        Object game = method(gameType, "getMinecraft", "func_71410_x").invoke(null);
        Object player = field(gameType, "thePlayer", "field_71439_g").get(game);
        Object world = field(gameType, "theWorld", "field_71441_e").get(game);
        Object settings = field(gameType, "gameSettings", "field_71474_y").get(game);
        Object renderer = field(gameType, "entityRenderer", "field_71460_t").get(game);
        Object controller = field(gameType, "playerController", "field_71442_b").get(game);
        Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
        Field currentSlot = field(inventory.getClass(), "currentItem", "field_70461_c");
        Object[] items =
                (Object[])
                        field(inventory.getClass(), "mainInventory", "field_70462_a")
                                .get(inventory);
        int slot = currentSlot.getInt(inventory);
        Object originalItem = items[slot];
        Object gameTimer = field(gameType, "timer", "field_71428_T").get(game);
        Field speed = field(gameTimer.getClass(), "timerSpeed", "field_74278_d");
        Field gamma = field(settings.getClass(), "gammaSetting", "field_74333_Y");
        Field focus = field(gameType, "inGameHasFocus", "field_71415_G");
        Field screen = field(gameType, "currentScreen", "field_71462_r");
        float oldGamma = gamma.getFloat(settings), oldSpeed = speed.getFloat(gameTimer);
        boolean oldFocus = focus.getBoolean(game);
        Object oldScreen = screen.get(game);
        ItemUseDelayAccess delay = (ItemUseDelayAccess) game;
        int oldDelay = delay.perihelion$itemUseDelay();
        Method setSprint =
                method(
                        player.getClass(),
                        new Class<?>[] {boolean.class},
                        "setSprinting",
                        "func_70031_b");
        Method isSprint = method(player.getClass(), "isSprinting", "func_70051_ag");
        boolean oldSprint = (Boolean) isSprint.invoke(player);
        HookBridge.Target target = (HookBridge.Target) field(HookBridge.class, "target").get(null);
        MinecraftControls controls = target.controls();
        Map<Field, Object> playerFields = new LinkedHashMap<>();
        int subscriptions = runtime.clientEvents().subscriptionCount();
        boolean addedBlindness = false;
        try {
            screen.set(game, null);
            focus.setBoolean(game, true);
            // Actual getFovModifier transformed entry point.
            setSprint.invoke(player, true);
            Method fov = method(player.getClass(), "getFovModifier", "func_175156_o");
            float vanillaFov = ((Number) fov.invoke(player)).floatValue();
            enable(runtime, "nofov");
            check(
                    ((Number) fov.invoke(player)).floatValue() == 1,
                    "NoFOV suppresses sprint modifier");
            disable(runtime, "nofov");
            check(
                    ((Number) fov.invoke(player)).floatValue() == vanillaFov,
                    "NoFOV disable restores vanilla FOV");

            // Real lightmap: compare the module's result against vanilla with donor gamma 100.
            Method lightmap =
                    method(
                            renderer.getClass(),
                            new Class<?>[] {float.class},
                            "updateLightmap",
                            "func_78472_g");
            Field lightmapDirty =
                    field(renderer.getClass(), "lightmapUpdateNeeded", "field_78536_aa");
            Field colors = field(renderer.getClass(), "lightmapColors", "field_78504_Q");
            gamma.setFloat(settings, 100);
            lightmapDirty.setBoolean(renderer, true);
            lightmap.invoke(renderer, 0.5f);
            int[] donorLightmap = ((int[]) colors.get(renderer)).clone();
            gamma.setFloat(settings, 0.5f);
            enable(runtime, "fullbright");
            lightmapDirty.setBoolean(renderer, true);
            lightmap.invoke(renderer, 0.5f);
            check(
                    Arrays.equals(donorLightmap, (int[]) colors.get(renderer)),
                    "Fullbright lightmap equals donor gamma 100");
            check(
                    gamma.getFloat(settings) == 0.5f,
                    "Fullbright never changes persisted brightness");
            disable(runtime, "fullbright");
            lightmapDirty.setBoolean(renderer, true);
            lightmap.invoke(renderer, 0.5f);
            check(
                    !Arrays.equals(donorLightmap, (int[]) colors.get(renderer)),
                    "Fullbright disable restores normal lightmap");

            saveSet(player, playerFields, 6, "hurtTime", "field_70737_aN");
            saveSet(player, playerFields, 10, "maxHurtTime", "field_70738_aO");
            Method hurt =
                    method(
                            renderer.getClass(),
                            new Class<?>[] {float.class},
                            "hurtCameraEffect",
                            "func_78482_e");
            check(!identityAfter(hurt, renderer), "vanilla hurt camera changes modelview");
            enable(runtime, "nohurtcam");
            check(identityAfter(hurt, renderer), "NoHurtCam suppresses actual camera transform");
            disable(runtime, "nohurtcam");
            check(!identityAfter(hurt, renderer), "hurt camera returns on disable");
            saveSet(player, playerFields, 0.4f, "cameraYaw", "field_71109_bG");
            saveSet(player, playerFields, 0.4f, "prevCameraYaw", "field_71107_bF");
            Method bob =
                    method(
                            renderer.getClass(),
                            new Class<?>[] {float.class},
                            "setupViewBobbing",
                            "func_78475_f");
            check(!identityAfter(bob, renderer), "vanilla bob changes modelview");
            enable(runtime, "nobob");
            check(identityAfter(bob, renderer), "NoBob suppresses actual bob transform");
            disable(runtime, "nobob");
            check(!identityAfter(bob, renderer), "view bob returns on disable");

            Class<?> potionEffect = Class.forName("net.minecraft.potion.PotionEffect");
            Method addPotion =
                    method(
                            player.getClass(),
                            new Class<?>[] {potionEffect},
                            "addPotionEffect",
                            "func_70690_d");
            Method hasPotion =
                    method(
                            player.getClass(),
                            new Class<?>[] {int.class},
                            "isPotionActive",
                            "func_82165_m");
            check(!(Boolean) hasPotion.invoke(player, 15), "fixture begins without blindness");
            Method fog =
                    method(
                            renderer.getClass(),
                            new Class<?>[] {int.class, float.class},
                            "setupFog",
                            "func_78468_a");
            Method fogColor =
                    method(
                            renderer.getClass(),
                            new Class<?>[] {float.class},
                            "updateFogColor",
                            "func_78466_h");
            fog.invoke(renderer, 0, 0.5f);
            float normalFog = GL11.glGetFloat(GL11.GL_FOG_END);
            fogColor.invoke(renderer, 0.5f);
            float[] normalColor = fogColor(renderer);
            addPotion.invoke(
                    player, potionEffect.getConstructor(int.class, int.class).newInstance(15, 200));
            addedBlindness = true;
            fog.invoke(renderer, 0, 0.5f);
            float blindFog = GL11.glGetFloat(GL11.GL_FOG_END);
            check(blindFog < normalFog, "fixture produces blindness fog");
            enable(runtime, "antiblind");
            fog.invoke(renderer, 0, 0.5f);
            fogColor.invoke(renderer, 0.5f);
            check(
                    GL11.glGetFloat(GL11.GL_FOG_END) == normalFog,
                    "AntiBlind restores normal fog distance");
            check(Arrays.equals(normalColor, fogColor(renderer)), "AntiBlind restores fog color");
            check(
                    (Boolean) hasPotion.invoke(player, 15),
                    "AntiBlind preserves actual potion effect");
            disable(runtime, "antiblind");
            fog.invoke(renderer, 0, 0.5f);
            check(GL11.glGetFloat(GL11.GL_FOG_END) == blindFog, "blindness fog returns on disable");
            method(
                            player.getClass(),
                            new Class<?>[] {int.class},
                            "removePotionEffect",
                            "func_82170_o")
                    .invoke(player, 15);
            addedBlindness = false;

            enable(runtime, "timer");
            set(runtime, "timer", "time-amount", "1.5");
            runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
            controls.apply();
            check(speed.getFloat(gameTimer) == 1.5f, "Timer sets actual shared speed");
            disable(runtime, "timer");
            controls.apply();
            check(speed.getFloat(gameTimer) == oldSpeed, "Timer restores baseline speed");

            enable(runtime, "fastplace");
            for (String item : new String[] {"stone", "egg", "snowball"}) {
                items[slot] = stack(item);
                delay.perihelion$itemUseDelay(4);
                runtime.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
                check(delay.perihelion$itemUseDelay() == 1, "FastPlace default delay for " + item);
            }
            set(runtime, "fastplace", "allow-projectiles", "false");
            for (String item : new String[] {"egg", "snowball", "diamond_sword"}) {
                items[slot] = stack(item);
                delay.perihelion$itemUseDelay(4);
                runtime.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
                check(
                        delay.perihelion$itemUseDelay() == 4,
                        "FastPlace respects item filter for " + item);
            }
            items[slot] = null;
            delay.perihelion$itemUseDelay(4);
            runtime.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
            check(delay.perihelion$itemUseDelay() == 4, "FastPlace handles empty hand");
            items[slot] = stack("stone");
            set(runtime, "fastplace", "delay", "0");
            runtime.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
            check(delay.perihelion$itemUseDelay() == 0, "FastPlace supports zero cooldown");
            disable(runtime, "fastplace");
            delay.perihelion$itemUseDelay(4);
            runtime.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
            check(delay.perihelion$itemUseDelay() == 4, "disabled FastPlace leaves cooldown alone");

            enable(runtime, "sprint");
            runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
            check(
                    runtime.controls().key(InputKey.SPRINT).orElse(false),
                    "Sprint submits held sprint request");
            Method invisible =
                    method(
                            player.getClass(),
                            new Class<?>[] {boolean.class},
                            "setInvisible",
                            "func_82142_c");
            boolean oldInvisible =
                    (Boolean)
                            method(player.getClass(), "isInvisible", "func_82150_aj")
                                    .invoke(player);
            try {
                set(runtime, "sprint", "cancel-invis", "true");
                invisible.invoke(player, true);
                runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
                check(
                        !runtime.controls().key(InputKey.SPRINT).isPresent(),
                        "Sprint yields immediately when invisibility is excluded");
            } finally {
                invisible.invoke(player, oldInvisible);
                set(runtime, "sprint", "cancel-invis", "false");
            }
            runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
            controls.apply();
            enable(runtime, "wtap");
            Class<?> worldType = Class.forName("net.minecraft.world.World");
            Object pig =
                    Class.forName("net.minecraft.entity.passive.EntityPig")
                            .getConstructor(worldType)
                            .newInstance(world);
            field(pig.getClass(), "hurtTime", "field_70737_aN").setInt(pig, 6);
            setSprint.invoke(player, true);
            Method attack =
                    method(
                            controller.getClass(),
                            new Class<?>[] {
                                Class.forName("net.minecraft.entity.player.EntityPlayer"),
                                Class.forName("net.minecraft.entity.Entity")
                            },
                            "attackEntity",
                            "func_78764_a");
            attack.invoke(controller, player, pig); // Exercises the actual controller attack hook.
            runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
            controls.apply();
            check(
                    runtime.controls().key(InputKey.SPRINT).equals(java.util.Optional.of(false)),
                    "WTap suppresses Sprint's lower-priority held key");
            check(!(Boolean) isSprint.invoke(player), "WTap suppresses actual sprint state");
            runtime.controls().beginTick();
            runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
            controls.apply();
            check(
                    runtime.controls().key(InputKey.SPRINT).orElse(false),
                    "Sprint resumes after one reset tick");
            check(
                    !runtime.controls().sprinting().isPresent(),
                    "WTap releases actual sprint claim after one tick");
            disable(runtime, "wtap");
            disable(runtime, "sprint");
            controls.apply();

            set(runtime, "aura", "auto-disable", "false");
            // All production activations retire on world loss and receive fresh contexts on rejoin.
            for (ModuleEntry entry : gameplay(runtime)) enable(runtime, entry.info().id());
            runtime.updateWorld(false, ConnectionContext.DISCONNECTED, true);
            controls.apply();
            check(
                    runtime.clientEvents().subscriptionCount() == subscriptions,
                    "world loss removes every production listener");
            check(
                    !runtime.controls().timerSpeed().isPresent()
                            && !runtime.controls().sprinting().isPresent(),
                    "world loss removes shared claims");
            runtime.updateWorld(
                    true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
            for (ModuleEntry entry : gameplay(runtime))
                check(
                        entry.state() == ModuleState.ENABLED,
                        "rejoin reactivates " + entry.info().id());
            for (ModuleEntry entry : gameplay(runtime)) {
                ModuleContext context =
                        (ModuleContext) field(ModuleEntry.class, "context").get(entry);
                context.listen(
                        String.class,
                        event -> {
                            throw new IllegalStateException("intentional module probe failure");
                        });
            }
            runtime.clientEvents().post("fault");
            runtime.modules().processFaults();
            controls.apply();
            for (ModuleEntry entry : gameplay(runtime))
                check(
                        entry.state() == ModuleState.FAULTED,
                        "fault quarantines " + entry.info().id());
            check(
                    runtime.clientEvents().subscriptionCount() == subscriptions,
                    "fault cleanup removes all production listeners");
            check(
                    !HookBridge.suppressEffect(VisualEffectEvent.Kind.GAMMA),
                    "faulted visual modules fall through to vanilla");
            check(speed.getFloat(gameTimer) == oldSpeed, "fault cleanup restores timer");
            for (ModuleEntry entry : gameplay(runtime)) {
                enable(runtime, entry.info().id());
                disable(runtime, entry.info().id());
            }
            System.out.println("PERIHELION_SMOKE MODULES checks=" + checks + " PASS");
        } finally {
            for (ModuleEntry entry : gameplay(runtime)) {
                runtime.modules().setEnabled(entry.info().id(), false);
                for (perihelion.core.setting.Setting<?> setting :
                        entry.module().settings().values())
                    reset(runtime, entry.info().id(), setting);
            }
            controls.apply();
            for (Map.Entry<Field, Object> saved : playerFields.entrySet())
                saved.getKey().set(player, saved.getValue());
            if (addedBlindness)
                method(
                                player.getClass(),
                                new Class<?>[] {int.class},
                                "removePotionEffect",
                                "func_82170_o")
                        .invoke(player, 15);
            items[slot] = originalItem;
            gamma.setFloat(settings, oldGamma);
            speed.setFloat(gameTimer, oldSpeed);
            setSprint.invoke(player, oldSprint);
            delay.perihelion$itemUseDelay(oldDelay);
            screen.set(game, oldScreen);
            focus.setBoolean(game, oldFocus);
        }
    }

    private static java.util.List<ModuleEntry> gameplay(ClientRuntime runtime) {
        java.util.List<ModuleEntry> entries = new java.util.ArrayList<>();
        for (ModuleEntry entry : runtime.modules().entries())
            if (entry.info().requiresWorld()) entries.add(entry);
        return entries;
    }

    private static <T> void reset(
            ClientRuntime runtime, String id, perihelion.core.setting.Setting<T> setting) {
        runtime.modules().setSetting(id, setting, setting.defaultValue());
    }

    private static float[] fogColor(Object renderer) throws Exception {
        return new float[] {
            field(renderer.getClass(), "fogColorRed", "field_175080_Q").getFloat(renderer),
            field(renderer.getClass(), "fogColorGreen", "field_175082_R").getFloat(renderer),
            field(renderer.getClass(), "fogColorBlue", "field_175081_S").getFloat(renderer)
        };
    }

    private static Object stack(String name) throws Exception {
        Class<?> item = Class.forName("net.minecraft.item.Item");
        Object value =
                method(item, new Class<?>[] {String.class}, "getByNameOrId", "func_111206_d")
                        .invoke(null, name);
        return Class.forName("net.minecraft.item.ItemStack")
                .getConstructor(item, int.class)
                .newInstance(value, 64);
    }

    private static void saveSet(
            Object target, Map<Field, Object> saved, Object value, String... names)
            throws Exception {
        Field field = field(target.getClass(), names);
        saved.put(field, field.get(target));
        field.set(target, value);
    }

    private static boolean identityAfter(Method method, Object receiver) throws Exception {
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glLoadIdentity();
            method.invoke(receiver, 0.5f);
            FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
            for (int i = 0; i < 16; i++)
                if (Math.abs(matrix.get(i) - (i % 5 == 0 ? 1 : 0)) > 0.00001) return false;
            return true;
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(mode);
        }
    }

    private static void enable(ClientRuntime runtime, String id) {
        runtime.modules().setEnabled(id, true);
        check(
                runtime.modules().require(id).state() == ModuleState.ENABLED,
                id + " enables without faults");
    }

    private static void disable(ClientRuntime runtime, String id) {
        runtime.modules().setEnabled(id, false);
    }

    private static void set(ClientRuntime runtime, String id, String setting, String value) {
        runtime.commands().execute("set " + id + " " + setting + " " + value, output -> {});
    }

    private static Field field(Class<?> type, String... names) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (String name : names) {
                try {
                    Field field = c.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
        throw new NoSuchFieldException(type + " " + Arrays.toString(names));
    }

    private static Method method(Class<?> type, String... names) throws NoSuchMethodException {
        return method(type, new Class<?>[0], names);
    }

    private static Method method(Class<?> type, Class<?>[] parameters, String... names)
            throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (String name : names) {
                try {
                    Method method = c.getDeclaredMethod(name, parameters);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
        throw new NoSuchMethodException(type + " " + Arrays.toString(names));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
