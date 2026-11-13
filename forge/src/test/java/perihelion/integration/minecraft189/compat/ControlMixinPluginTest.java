package perihelion.integration.minecraft189.compat;

import org.junit.Test;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import static org.junit.Assert.*;

public class ControlMixinPluginTest {
    @Test public void transformedWalkingMethodRequiresBothHooks() {
        for (String methodName : new String[] {"onUpdateWalkingPlayer", "func_175161_p"}) {
            ClassNode node = new ClassNode(); node.name = "net/minecraft/client/entity/EntityPlayerSP";
            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, methodName, "()V", null, null);
            node.methods.add(method);
            assertArrayEquals(new boolean[] {false, false}, ControlMixinPlugin.rotationHooks(node));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, "redirect$perihelion$motionYaw$zzf000", "()F", false));
            assertArrayEquals(new boolean[] {true, false}, ControlMixinPlugin.rotationHooks(node));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, "redirect$perihelion$motionPitch$zzf000", "()F", false));
            assertArrayEquals(new boolean[] {true, true}, ControlMixinPlugin.rotationHooks(node));
        }
        MotionHookStatus.rotationHooks(true, false); assertFalse(MotionHookStatus.supportsServerRotation());
        MotionHookStatus.rotationHooks(true, true); assertTrue(MotionHookStatus.supportsServerRotation());
        MotionHookStatus.rotationHooks(false, false); assertFalse(MotionHookStatus.supportsServerRotation());
    }
}
