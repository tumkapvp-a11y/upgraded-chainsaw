package wgextender.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;

public final class CommandUtils {

    private CommandUtils() {
    }

    public static Map<String, Command> getCommands() {
        return Bukkit.getCommandMap().getKnownCommands();
    }

    public static Command getCommand(String label) {
        return getCommands().get(label);
    }

    /**
     * Points every label (name, aliases, namespaced variants) of {@code oldCommand} to {@code newCommand}.
     *
     * @return number of replaced labels
     */
    public static int replaceCommand(Command oldCommand, Command newCommand) {
        Map<String, Command> commands = getCommands();
        List<String> labels = new ArrayList<>();
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            if (entry.getValue() == oldCommand) {
                labels.add(entry.getKey());
            }
        }
        for (String label : labels) {
            commands.put(label, newCommand);
        }
        return labels.size();
    }
}
