package link.locutus.discord.commands.info;

import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class CityCost extends Command {
    public CityCost() {
        super("citycost", "citycosts", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.city.cost.cmd);
    }
    @Override
    public String help() {
        return super.help() + " <current-city> <max-city> [manifest-destiny=false] [city-planning=false] [advanced-city-planning=false] [metropolitan-planning=false] [government-support-agency=false] [domestic-affairs=false]";
    }

    @Override
    public String desc() {
        return "Calculate the costs of purchasing cities (from current to max) e.g.\n" +
                "`" + Settings.commandPrefix(true) + "CityCost 5 10 true false false false";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(args.size(), 2, channel);

        int current = Integer.parseInt(args.get(0));
        int max = Integer.parseInt(args.get(1));
        if (max > 1000) throw new IllegalArgumentException("Max cities 1000");

        boolean manifest = false;
        boolean cp = false;
        boolean acp = false;
        boolean mp = false;
        boolean gsa = false;
        boolean bda = false;

        if (args.size() >= 3) manifest = Boolean.parseBoolean(args.get(2));
        if (args.size() >= 4) cp = Boolean.parseBoolean(args.get(3));
        if (args.size() >= 5) acp = Boolean.parseBoolean(args.get(4));
        if (args.size() >= 6) mp = Boolean.parseBoolean(args.get(5));
        if (args.size() >= 7) gsa = Boolean.parseBoolean(args.get(6));
        if (args.size() >= 8) bda = Boolean.parseBoolean(args.get(7));

        double total = 0;

        for (int i = Math.max(1, current); i < max; i++) {
            total += PW.City.nextCityCost(i, manifest, cp && i >= Projects.URBAN_PLANNING.requiredCities(),
                    acp && i >= Projects.ADVANCED_URBAN_PLANNING.requiredCities(),
                    mp && i >= Projects.METROPOLITAN_PLANNING.requiredCities(),
                    gsa, bda);
        }

        return "$" + MathMan.format(total);
    }
}
