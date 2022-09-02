package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.StringMan;

import java.util.*;
import java.util.function.Predicate;

public interface CommandCallable {
    CommandCallable clone(CommandCallable parent, List<String> aliases);
    CommandCallable getParent();

    Object call(ArgumentStack stack);

    String help(ValueStore store);

    String desc(ValueStore store);

    String simpleDesc();

    String simpleHelp();

    List<String> aliases();

    default void validatePermissions(ValueStore store, PermissionHandler permisser) throws IllegalArgumentException {

    }

    default boolean hasPermission(ValueStore store, PermissionHandler permisser) {
        try {
            validatePermissions(store, permisser);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    default String toHtml(ValueStore store, PermissionHandler permisser) {
        return toHtml(store, permisser, null);
    }

    String toHtml(ValueStore store, PermissionHandler permisser, String endpoint);

    default CommandCallable getCallable(List<String> args) {
        CommandCallable root = this;

        ArrayDeque<String> stack = new ArrayDeque<>(args);
        while (!stack.isEmpty()) {
            String arg = stack.poll();
            if (root instanceof CommandGroup) {
                root = ((CommandGroup) root).get(arg);
            } else {
                throw new IllegalArgumentException("Command: " + root.getPrimaryCommandId() + " of type " + root.getClass().getSimpleName() + " has no subcommand: " + arg);
            }
        }
        return root;
    }

    default String getFullPath() {
        return getFullPath(" ");
    }

    default String getFullPath(String delim) {
        List<String> pathList = new ArrayList<>();
        CommandCallable root = this;
        while (root != null) {
            if (root.getPrimaryCommandId() != null && !root.getPrimaryCommandId().isEmpty()) {
                pathList.add(root.getPrimaryCommandId());
            }
            root = root.getParent();
        }
        Collections.reverse(pathList);
        return StringMan.join(pathList, delim);
    }

    default String getPrimaryCommandId() {
        if (aliases().isEmpty()) return "";
        return aliases().get(0);
    }

    default Set<ParametricCallable> getParametricCallables(Predicate<ParametricCallable> returnIf) {
        return Collections.emptySet();
    }
}
