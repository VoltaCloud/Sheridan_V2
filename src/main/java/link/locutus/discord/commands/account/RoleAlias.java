package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class RoleAlias extends Command {
    public RoleAlias() {
        super("aliasrole", CommandCategory.GUILD_MANAGEMENT);
    }

    @Override
    public String help() {
        return CM.role.setAlias.cmd.toSlashMention();
    }

    @Override
    public String usage() {
        return super.usage() + " <locutus-role> <discord-role> [alliance]";
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.role.setAlias.cmd);
    }

    @Override
    public String desc() {
        return "Map a Locutus role to a discord role. Valid roles are: " + Roles.getValidRolesStringList();
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() > 3) return usage();
        User user = author;
        Guild server = guild;
        GuildDB db = Locutus.imp().getGuildDB(guild);
        Roles locutusRole = args.size() > 0 ? Roles.parse(args.get(0)) : null;
        Role role = args.size() > 1 ? DiscordUtil.getRole(server, args.get(1)) : null;
        boolean removeRole = false;
        if (args.size() > 1 && role == null) {
            if (args.get(1).equalsIgnoreCase("null")) {
                removeRole = true;
            } else {
                return "Invalid role `" + args.get(1) + "`";
            }
        }
        DBAlliance alliance = args.size() > 2 ? DBAlliance.parse(args.get(2), true) : null;
        return AdminCommands.aliasRole(db, locutusRole, role, alliance, removeRole);
    }
}
