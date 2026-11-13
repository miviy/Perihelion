package perihelion.bootstrap.forge;

import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import perihelion.core.lifecycle.ClientRuntime;

/** Forge owns this root command; parsing and behavior remain in core. */
final class ForgeRootCommand extends CommandBase {
    private ClientRuntime runtime;
    ForgeRootCommand(ClientRuntime runtime) { this.runtime = runtime; }
    void detach() { runtime = null; }
    @Override public String getCommandName() { return "perihelion"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/perihelion help"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
    @Override public void processCommand(ICommandSender sender, String[] args) {
        ClientRuntime current = runtime;
        if (current == null) { sender.addChatMessage(new ChatComponentText("Perihelion is stopped.")); return; }
        current.commands().execute(String.join(" ", args), message -> sender.addChatMessage(new ChatComponentText("[Perihelion] " + message)));
    }
    @Override public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos position) {
        return runtime == null ? Collections.emptyList() : runtime.commands().complete(String.join(" ", args));
    }
}
