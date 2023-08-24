package com.locutus.wiki;

import com.locutus.wiki.pages.WikiAlliancePlaceholdersPage;
import com.locutus.wiki.pages.WikiArgumentsPage;
import com.locutus.wiki.pages.WikiAuditingPage;
import com.locutus.wiki.pages.WikiAutoMaskingPage;
import com.locutus.wiki.pages.WikiBankPage;
import com.locutus.wiki.pages.WikiBeigeCyclingPage;
import com.locutus.wiki.pages.WikiBlitzPage;
import com.locutus.wiki.pages.WikiCoalitionsPage;
import com.locutus.wiki.pages.WikiCommandsPage;
import com.locutus.wiki.pages.WikiCounteringPage;
import com.locutus.wiki.pages.WikiDNRPage;
import com.locutus.wiki.pages.WikiDepositsPage;
import com.locutus.wiki.pages.WikiEmbassyPage;
import com.locutus.wiki.pages.WikiEscrowPage;
import com.locutus.wiki.pages.WikiFindingTargetsPage;
import com.locutus.wiki.pages.WikiHelpPage;
import com.locutus.wiki.pages.WikiInterviewPage;
import com.locutus.wiki.pages.WikiNationPlaceholdersPage;
import com.locutus.wiki.pages.WikiRecruitmentPage;
import com.locutus.wiki.pages.WikiSendTargetsPage;
import com.locutus.wiki.pages.WikiSetupPage;
import com.locutus.wiki.pages.WikiSpyWarPage;
import com.locutus.wiki.pages.WikiTaxBracketPage;
import com.locutus.wiki.pages.WikiWarAlertsPage;
import com.locutus.wiki.pages.WikiWarRoomPage;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class WikiGenHandler {

    private final CommandManager2 manager;
    private final PWGPTHandler gpt;
    private final String pathRelative;

    public WikiGenHandler(String pathRelative, CommandManager2 manager) {
        this.manager = manager;
        this.gpt = manager.getPwgptHandler();
        this.pathRelative = pathRelative;
    }

    public void writeDefaults() throws IOException {
        List<WikiGen> pages = new ArrayList<>();

        // commands
        pages.add(new WikiCommandsPage(manager));
        // arguments
        pages.add(new WikiArgumentsPage(manager));
        // nation_placeholders
        pages.add(new WikiNationPlaceholdersPage(manager));
        // alliance_placeholders
        pages.add(new WikiAlliancePlaceholdersPage(manager));
        // register defaults
        pages.add(new WikiSetupPage(manager));
        //War Alerts
        pages.add(new WikiWarAlertsPage(manager)); // <---
//        //Auto masking
        pages.add(new WikiAutoMaskingPage(manager)); // <---
        // Embassies
        pages.add(new WikiEmbassyPage(manager)); // <---
        //Deposits System
        pages.add(new WikiDepositsPage(manager)); // <---
//        //Setup an offshore or bank
        pages.add(new WikiBankPage(manager));  // <---
//        //Interview System
        pages.add(new WikiInterviewPage(manager)); // <---
//        //Auditing System / Alliance MMR requirements
        pages.add(new WikiAuditingPage(manager)); // <---
//        //DNR system
        pages.add(new WikiDNRPage(manager)); // <---
//        //Coalitions system
        pages.add(new WikiCoalitionsPage(manager)); // <---
//        //War Room System
        pages.add(new WikiWarRoomPage(manager)); // <---
//        //Recruitment System
        pages.add(new WikiRecruitmentPage(manager)); // <---
//        //Countering
        pages.add(new WikiCounteringPage(manager)); // <---
//        //Finding Targets / War panels
        pages.add(new WikiFindingTargetsPage(manager)); // <---
//        //Tax Bracket Automation
        pages.add(new WikiTaxBracketPage(manager)); // <---
//        //Escrow System
        pages.add(new WikiEscrowPage(manager)); // <---
//        //Plan a blitz
        pages.add(new WikiBlitzPage(manager)); // <---
//        //Send out targets
        pages.add(new WikiSendTargetsPage(manager)); // <---
//        //Spy war
        pages.add(new WikiSpyWarPage(manager)); // <---
//        //Blockade System
        pages.add(new WikiBlockadePage(manager)); // <---   ####
//        //Beige cycling tutorial
        pages.add(new WikiBeigeCyclingPage(manager)); // <---
//        //Loan System
        pages.add(new WikiLoanPage(manager));// <---   ####
//        //Report System
        pages.add(new WikiReportPage(manager)); // <---   ####
//        //Anti Leak System
        pages.add(new WikiAntiLeakPage(manager)); // <---   ####
//        //Making custom spreadsheets
        pages.add(new WikiCustomSpreadsheetsPage(manager)); // <---   ####
//        //Statistics System
        pages.add(new WikiStatisticsPage(manager)); // <---   ####
//        //Trade System (use trading guide)
//        //- Link DocScripts trade tool (cause useful)
        pages.add(new WikiTradePage(manager)); // <---   ####

        WikiHelpPage help = new WikiHelpPage(manager, pages);
        pages.add(help);

        for (WikiGen page : pages) {
            writePage(page);
        }
    }

    private void writePage(WikiGen page) throws IOException {
        File file = new File(pathRelative + File.separator + page.getPageName().toLowerCase().replace(" ", "_") + ".md");
        Files.write(file.toPath(), page.generateMarkdown().getBytes());
    }
}
