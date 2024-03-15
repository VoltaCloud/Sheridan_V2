package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.Coalition;

public class WikiFindingTargetsPage extends BotWikiGen {
    public WikiFindingTargetsPage(CommandManager2 manager) {
        super(manager, "target_finding");
    }

    @Override
    public String generateMarkdown() {
        return build(
            "# Prerequisites",
            "Set the `" + Coalition.ENEMIES.name() + "` coalition. See: " + linkPage("coalitions"),
            "# Target commands",
// /war find target commands
//                Raid targets
//                war find raid
//                loot
//                unprotected
            "# Target alerts",
//                WAR_ALERT_OPT_OUT
//                BOUNTY_ALERT
                // TODO bank alerts
            "# Target embeds",
                commandMarkdownSpoiler(CM.embed.template.guerilla.cmd),
                commandMarkdownSpoiler(CM.embed.template.war_contested_range.cmd),
                commandMarkdownSpoiler(CM.embed.template.war_winning.cmd),
                commandMarkdownSpoiler(CM.embed.template.raid.cmd),
                //        /embed template spy_enemy
                commandMarkdownSpoiler(CM.embed.template.ally_enemy_sheets.cmd),
                linkPage("embeds"),
            "# See also",
                linkPage("espionage"),
                linkPage("war_alerts"),
                linkPage("blitzes")
        );
       /*
       Enemies coalition



       TODO /war find target commands
       TODO spy target commands

       TODO embed templates that find targets

       See beige alert system



        */
    }
}
