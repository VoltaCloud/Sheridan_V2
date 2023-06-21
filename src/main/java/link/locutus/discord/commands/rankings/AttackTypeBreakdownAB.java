package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.db.entities.AttackTypeBreakdown;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.WarAttackParser;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class AttackTypeBreakdownAB extends Command {
    public AttackTypeBreakdownAB() {
        super("AttackTypeBreakdown", "AttackTypes", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return "`" + super.help() + " <alliance|coalition> <alliance|coalition> <days>` OR `" + super.help() + " <war-url>`";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 3 || (args.size() == 3 && args.get(0).equalsIgnoreCase(args.get(1)))) {
            return usage(args.size(), unkown, channel);
        }

        WarAttackParser parser = new WarAttackParser(Locutus.imp().getGuildDB(guild), args, flags);

        AttackTypeBreakdown breakdown = new AttackTypeBreakdown(parser.getNameA(), parser.getNameB());
        breakdown.addAttacks(parser.getAttacks(), parser.getIsPrimary(), parser.getIsSecondary());

        channel.create().writeTable("Attack Breakdown", breakdown.toTableList(), true, null).send();

        return null;
    }
}
