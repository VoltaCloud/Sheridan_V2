package com.locutus.wiki;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class WikiGen {
    private final String pageName;
    private final ValueStore store;

    public WikiGen(ValueStore store, String pageName) {
        this.pageName = pageName;
        this.store = store;
    }

    public String getDescription() {
        String markdown = generateMarkdown();
        String[] lines = markdown.split("\n");
        // Get all the top level `#` headings
        List<String> headings = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("# ")) {
                headings.add(line.substring(2));
            }
        }
        return StringMan.join(headings, "\n");
    }

    public abstract String generateMarkdown();

    public String build(String... content) {
        return StringMan.join(content, "\n");
    }

    public String commandMarkdownSpoiler(CommandRef ref) {
        String title = ref.toSlashCommand(true);
        String body = commandMarkdown(ref);
        return MarkupUtil.spoiler(title, body);
    }

    public String commandMarkdown(CommandRef ref) {
        return ref.getCallable(true).toBasicMarkdown(store, null, "/", false, true);
    }

}