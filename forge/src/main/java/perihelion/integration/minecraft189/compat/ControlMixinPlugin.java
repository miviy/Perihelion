package perihelion.integration.minecraft189.compat;

import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Detect a replaced walking method without fighting another client's transformer. */
public final class ControlMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> ours, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith(".PlayerControlsMixin")) return;
        boolean[] hooks = rotationHooks(node);
        MotionHookStatus.rotationHooks(hooks[0], hooks[1]);
        if (!MotionHookStatus.supportsServerRotation()) {
            LogManager.getLogger("Perihelion/Compatibility").warn(
                    "Another transformer replaced walking rotation reads. Perihelion server-only rotation is unavailable; other controls and modules remain active.");
        }
    }
    static boolean[] rotationHooks(ClassNode node) {
        boolean yaw = false, pitch = false;
        for (MethodNode method : node.methods) {
            if (!(method.name.equals("onUpdateWalkingPlayer") || method.name.equals("func_175161_p")) || !method.desc.equals("()V")) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!call.owner.equals(node.name)) continue;
                yaw |= call.name.contains("perihelion$motionYaw");
                pitch |= call.name.contains("perihelion$motionPitch");
            }
        }
        return new boolean[] {yaw, pitch};
    }
}
