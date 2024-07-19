package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.bank.SyncBanks;
import link.locutus.discord.commands.external.guild.SyncBounties;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.sync.*;
import link.locutus.discord.db.*;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.util.task.mail.AlertMailTask;
import link.locutus.discord.util.task.multi.GetUid;
import link.locutus.discord.web.jooby.handler.CommandResult;
import link.locutus.wiki.WikiGenHandler;
import link.locutus.discord.Locutus;
import link.locutus.discord.RequestTracker;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.config.Messages;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.db.handlers.GuildCustomMessageHandler;
import link.locutus.discord.event.Event;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.io.PageRequestQueue;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.EditAllianceTask;
import link.locutus.discord.util.task.roles.AutoRoleInfo;
import link.locutus.discord.util.update.AllianceListener;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.Rank;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import link.locutus.discord.util.update.WarUpdateProcessor;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdminCommands {

    @Command(desc = "Sync and debug war rooms")
    @RolePermission(Roles.ADMIN)
    public String syncWarrooms(@Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, @Switch("f") boolean force) throws IOException {
        long start = System.currentTimeMillis();

        StringBuilder response = new StringBuilder();
        List<String> errors = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        WarCategory cat = db.getWarChannel(true);
        Guild guild = cat.getGuild();

        Set<Integer> aaIds = cat.getTrackedAllianceIds();
        if (aaIds.isEmpty()) {
            errors.add("No alliances being tracked. Set the `" + Coalition.ALLIES.name() + "` coalition: " + CM.coalition.add.cmd.toSlashMention());
        } else {
            response.append("**Alliances:**: `");
            response.append(aaIds.stream().map(f -> DBAlliance.getOrCreate(f).getMarkdownUrl()).collect(Collectors.joining(",")));
            response.append("`\n");
        }

        if (guild.getChannels().size() >= 500) {
            errors.add("Server at max channels (500)");
        }
        if (guild.getCategories().size() >= 50) {
            errors.add("Server at max categories (50)");
        }

        response.append("**Server:** `" + guild.getName() + "` | `" + guild.getId() + "`");
        if (guild.getIdLong() != db.getIdLong()) response.append(" (WAR_SERVER is set)");
        response.append("\n");

        // list the categories
        Member self = guild.getSelfMember();
        Set<Category> categories = cat.getCategories();

        Map<Category, Set<Permission>> permissionsMissing = new LinkedHashMap<>();
        Map<Category, CityRanges> ranges = new LinkedHashMap<>();

        Permission[] catPerms = cat.getCategoryPermissions();
        for (Category category : categories) {
            EnumSet<Permission> selfPerms = self.getPermissions(category);
            for (Permission perm : catPerms) {
                if (!selfPerms.contains(perm)) {
                    permissionsMissing.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(perm);
                }
            }
            CityRanges range = cat.getRangeFromCategory(category);
            if (range != null) {
                ranges.put(category, range);
            }
        }

        if (categories.isEmpty()) {
            errors.add("No categories found. " +
                    "Create one starting with `warcat`\n" +
                    "Grant the bot the perms: `" + Arrays.stream(catPerms).map(f -> f.getName()).collect(Collectors.joining(", ")) + "`\n");
        } else {
            response.append("**" + categories.size() + " categories:**\n");
            for (Category category : categories) {
                response.append("- " + category.getName());
                CityRanges cityRange = ranges.get(category);
                if (cityRange != null) {
                    response.append(" | city:" + cityRange);
                }
                Set<Permission> lacking = permissionsMissing.getOrDefault(category, Collections.emptySet());
                if (!lacking.isEmpty()) {
                    response.append(" | missing: `" + lacking.stream().map(f -> f.getName()).collect(Collectors.joining(",")) + "`");
                }
                response.append("\n");
            }
        }

        // Say War rooms can be sorted by cities by naming the category e.g. `warcat-c1-10`
        if (ranges.isEmpty()) {
            notes.add("No city ranges found. Create a category starting with `warcat` and ending with a city range e.g. `warcat-c1-10`");
            notes.add("If a sorted category is full, the next free category will be used, even if a room does not match the filter");
            notes.add("You may create multiple categories with the same or overlapping filters");
        }

        Map<DBWar, WarCategory.WarCatReason> warsLog = new LinkedHashMap<>();
        Map<DBNation, WarCategory.WarCatReason> inactiveRoomLog = new LinkedHashMap<>();
        Map<DBNation, WarCategory.WarCatReason> activeRoomLog = new LinkedHashMap<>();
        Set<DBNation> toCreate = new LinkedHashSet<>();
        Map<Integer, WarCategory.WarCatReason> toDelete = new LinkedHashMap<>();
        Map<DBNation, TextChannel> toReassign = new LinkedHashMap<>();
        Map<Integer, Set<TextChannel>> duplicates = new LinkedHashMap<>();

        cat.sync(warsLog, inactiveRoomLog, activeRoomLog, toCreate, toDelete, toReassign, duplicates, force);
        if (!warsLog.isEmpty()) {
            response.append("\n**" + warsLog.size() + " wars:**\n");
            for (Map.Entry<DBWar, WarCategory.WarCatReason> entry : warsLog.entrySet()) {
                DBWar war = entry.getKey();
                WarCategory.WarCatReason reason = entry.getValue();
                response.append("- " + war.warId + ": " + reason.name() + " - " + reason.getReason() + "\n");
            }
        }
        if (!inactiveRoomLog.isEmpty()) {
            response.append("\n**" + inactiveRoomLog.size() + " inactive rooms:**\n");
            for (Map.Entry<DBNation, WarCategory.WarCatReason> entry : inactiveRoomLog.entrySet()) {
                DBNation nation = entry.getKey();
                WarCategory.WarCatReason reason = entry.getValue();
                response.append("- " + nation.getNation() + ": " + reason.name() + " - " + reason.getReason() + "\n");
            }
        }
        if (!activeRoomLog.isEmpty()) {
            response.append("\n**" + activeRoomLog.size() + " active rooms:**\n");
            for (Map.Entry<DBNation, WarCategory.WarCatReason> entry : activeRoomLog.entrySet()) {
                DBNation nation = entry.getKey();
                WarCategory.WarCatReason reason = entry.getValue();
                response.append("- " + nation.getNation() + ": " + reason.name() + " - " + reason.getReason() + "\n");
            }
        }
        if (!toCreate.isEmpty()) {
            response.append("\n**" + toCreate.size() + " rooms to create:**\n");
            for (DBNation nation : toCreate) {
                response.append("- " + nation.getMarkdownUrl() + "\n");
            }
        }
        if (!toDelete.isEmpty()) {
            response.append("\n**" + toDelete.size() + " rooms to delete:**\n");
            for (Map.Entry<Integer, WarCategory.WarCatReason> entry : toDelete.entrySet()) {
                int id = entry.getKey();
                WarCategory.WarCatReason reason = entry.getValue();
                response.append("- " + PW.getMarkdownUrl(id, false) + ": " + reason.name() + " - " + reason.getReason() + "\n");
            }
        }
        if (!toReassign.isEmpty()) {
            response.append("\n**" + toReassign.size() + " rooms to reassign:**\n");
            for (Map.Entry<DBNation, TextChannel> entry : toReassign.entrySet()) {
                DBNation nation = entry.getKey();
                TextChannel channel = entry.getValue();
                response.append("- " + nation.getMarkdownUrl() + " -> " + channel.getAsMention() + "\n");
            }
        }
        if (!duplicates.isEmpty()) {
            response.append("\n**" + duplicates.size() + " duplicate channels:**\n");
            for (Map.Entry<Integer, Set<TextChannel>> entry : duplicates.entrySet()) {
                int id = entry.getKey();
                Set<TextChannel> channels = entry.getValue();
                response.append("- " + PW.getMarkdownUrl(id, false) + ": " + channels.stream().map(Channel::getAsMention).collect(Collectors.joining(", ")) + "\n");
            }
        }

        StringBuilder full = new StringBuilder();
        if (!errors.isEmpty()) {
            full.append("\n**" + errors.size() + " errors:**\n");
            for (String error : errors) {
                full.append("- " + error + "\n");
            }
        }
        if (!notes.isEmpty()) {
            full.append("\n**" + notes.size() + " notes:**\n");
            for (String note : notes) {
                full.append("- " + note + "\n");
            }
        }
        full.append(response);

        if (!force) {
            String title = "Confirm sync war rooms";
            String body = "See the attached log file for details on room creation, deletion";
            io.create().confirmation(title, body, command).file("warcat.txt", full.toString()).send();
            return null;
        }
        long diff = System.currentTimeMillis() - start;
        io.create().append("Sync war rooms complete. Took: " + diff + "ms\n" +
                "See the attached log file for task output").file("warcat.txt", full.toString()).send();

        return null;
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String savePojos() throws IOException {
        CommandManager2 manager = Locutus.cmd().getV2();
        manager.getCommands().savePojo(null, CM.class, "CM");
        manager.getNationPlaceholders().getCommands().savePojo(null, CM.class, "NationCommands");
        manager.getAlliancePlaceholders().getCommands().savePojo(null, CM.class, "AllianceCommands");
        return "Done!";
    }
    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String runMilitarizationAlerts() {
        AllianceListener.runMilitarizationAlerts();
        return "Done! (see console)";
    }

//    @Command
//    @RolePermission(value = Roles.ADMIN, root = true)
//    public String unset() {
//        // TODO list servers
//
//        // unset from specified servers (coalition, or NOT coalitions)
//        // use Set<GuildDB> and have filters ig for in root coalition (or not)
//
//        // option 2, set to follow a channel
//    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String checkActiveConflicts() {
        WarUpdateProcessor.checkActiveConflicts();
        return "Done! (see console)";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBans(@Default("false") boolean discordBans) throws SQLException {
        Locutus.imp().getNationDB().updateBans(Event::post);
        if (discordBans) {
            int size = syncDiscordBans();
            return "Done! Synced " + size + " bans";
        }
        return "Done! (see console)";
    }

    private int syncDiscordBans() {
        List<Guild> checkGuilds = new ArrayList<>();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            if (db.isAlliance()) {
                checkGuilds.add(db.getGuild());
            }
        }
        List<DiscordBan> toAdd = new ArrayList<>();
        for (Guild guild : checkGuilds) {
            Role botRole = guild.getBotRole();
            if (botRole == null) continue;
            if (!botRole.hasPermission(Permission.BAN_MEMBERS)) continue;
            try {
                List<Guild.Ban> bans = RateLimitUtil.complete(guild.retrieveBanList());
                for (Guild.Ban ban : bans) {
                    User user = ban.getUser();
                    String reason = ban.getReason();
                    if (reason == null) reason = "";

                    // long user, long server, long date, String reason
                    DiscordBan discordBan = new DiscordBan(user.getIdLong(), guild.getIdLong(), System.currentTimeMillis(), reason);
                    toAdd.add(discordBan);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        Locutus.imp().getDiscordDB().addBans(toAdd);
        return toAdd.size();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String showFileQueue(@Me IMessageIO io, @Default @Timestamp Long timestamp, @Switch("r") Integer numResults) throws URISyntaxException {
        PageRequestQueue handler = FileUtil.getPageRequestQueue();
        List<PageRequestQueue.PageRequestTask<?>> jQueue = handler.getQueue();

        Map<PagePriority, Integer> pagePriorities = new HashMap<>();
        int unknown = 0;
        int size = 0;
        synchronized (jQueue) {
            ArrayList<PageRequestQueue.PageRequestTask<?>> copy = new ArrayList<>(jQueue);
            size = copy.size();
            for (PageRequestQueue.PageRequestTask<?> task : copy) {
                long priority = task.getPriority();
                int ordinal = (int) (priority / Integer.MAX_VALUE);
                if (ordinal >= PagePriority.values.length) unknown++;
                else {
                    PagePriority pagePriority = PagePriority.values[ordinal];
                    pagePriorities.put(pagePriority, pagePriorities.getOrDefault(pagePriority, 0) + 1);
                }
            }
        }
        List<Map.Entry<PagePriority, Integer>> entries = new ArrayList<>(pagePriorities.entrySet());
        // sort
        entries.sort((o1, o2) -> o2.getValue() - o1.getValue());
        if (numResults == null) numResults = 25;

        StringBuilder sb = new StringBuilder();
        sb.append("**File Queue:** " + size + "\n");
        for (Map.Entry<PagePriority, Integer> entry : entries) {
            sb.append(entry.getKey().name()).append(": ").append(entry.getValue()).append("\n");
        }
        if (unknown > 0) {
            sb.append("Unknown: ").append(unknown).append("\n");
        }

        if (timestamp != null) {
            RequestTracker tracker = handler.getTracker();
            Map<String, Integer> byDomain = tracker.getCountByDomain(timestamp);
            Map<String, Integer> byUrl = tracker.getCountByUrl(timestamp);

            sb.append("\n**By Domain:**\n");
            int domainI = 1;
            for (Map.Entry<String, Integer> entry : byDomain.entrySet()) {
                sb.append("- " + entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                if (domainI++ >= numResults) break;
            }

            sb.append("\n**By URL:**\n");
            int urlI = 1;
            for (Map.Entry<String, Integer> entry : byUrl.entrySet()) {
                sb.append("- " + entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                if (urlI++ >= numResults) break;
            }

            RequestTracker v3Tracker = PoliticsAndWarV3.getRequestTracker();
            Map<String, Integer> v3Request = v3Tracker.getCountByDomain(timestamp);
            if (!v3Request.isEmpty()) {
                sb.append("\n**V3 By Domain:**\n");
                int v3I = 1;
                for (Map.Entry<String, Integer> entry : v3Request.entrySet()) {
                    sb.append("- " + entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    if (v3I++ >= numResults) break;
                }
            }

        }


        if (numResults > 25) {
            io.create().file("queue.txt", sb.toString()).send();
            return null;
        } else {
            return sb.toString();
        }
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String dumpWiki(@Default String pathRelative) throws IOException, InvocationTargetException, IllegalAccessException {
        if (pathRelative == null) pathRelative = "../locutus.wiki";
        CommandManager2 manager = Locutus.imp().getCommandManager().getV2();
        WikiGenHandler generator = new WikiGenHandler(pathRelative, manager);
        generator.writeDefaults();

        return "Done!";
    }


    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTreasures() {
        Locutus.imp().getNationDB().updateTreasures(Event::post);
        return "Done!";
    }

    @Command(desc = "Generate a sheet of recorded login times for a set of nations within a time range")
    public String loginTimes(@Me GuildDB db, @Me IMessageIO io, Set<DBNation> nations, @Timestamp long cutoff, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.NATION_SHEET);
        }
        // time cutoff < 30d
        if (System.currentTimeMillis() - cutoff > TimeUnit.DAYS.toMillis(30)) {
            return "Cutoff must be within the last 30 days";
        }
        if (nations.size() > 30) {
            return "Too many nations (max: 30)";
        }
        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "time"
        ));
        sheet.setHeader(header);
        for (DBNation nation : nations) {
            List<DBSpyUpdate> activity = Locutus.imp().getNationDB().getSpyActivityByNation(nation.getNation_id(), cutoff);
            for (DBSpyUpdate update : activity) {
                header.set(0, String.valueOf(nation.getNation_id()));
                header.set(1, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(update.timestamp)));
                sheet.addRow(header);
            }
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(io.create(), "login_times").send();
        return null;
    }

    @Command(desc = "Pull registered nations from locutus\n" +
            "(or a provided url)")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncDiscordWithLocutus(@Default String url) throws IOException {
        if (url == null) {
            url = WebRoot.REDIRECT + "/discordids";
        }
        int count = 0;
        // read string from url
        String csvTabSeparated = FileUtil.readStringFromURL(PagePriority.DISCORD_IDS_ENDPOINT, url);
        // split into lines
        String[] lines = csvTabSeparated.split("\n");
        // iterate each line
        for (String line : lines) {
            String[] columns = line.split("\t");
            int nationId = Integer.parseInt(columns[0]);
            long discordId = Long.parseLong(columns[1]);
            PNWUser existing = Locutus.imp().getDiscordDB().getUserFromDiscordId(discordId);
            if (existing != null && existing.getNationId() == nationId && existing.getDiscordId() == discordId) {
                continue;
            }

            String username = null;
            if (columns.length > 2) {
                username = columns[2];
                if (username.isEmpty()) {
                    username = null;
                }
            }
            if (username == null) username = discordId + "";

            // register the user
            count++;
            Locutus.imp().getDiscordDB().addUser(new PNWUser(nationId, discordId, username));
        }
        return "Done! Imported " + count + "/" + lines.length + " users from " + url;
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncWars(@Switch("c") boolean updateCityCounts) throws IOException, ParseException {
        if (updateCityCounts) {
            Locutus.imp().getWarDb().loadWarCityCountsLegacy();
            return "Done (city counts)";
        }
        Locutus.imp().getWarDb().updateAllWars(Event::post);
        return "Done!";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String deleteAllInaccessibleChannels(@Switch("f") boolean force) {
        Map<GuildDB, List<GuildSetting>> toUnset = new LinkedHashMap<>();

        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            if (force) {
                List<GuildSetting> keys = db.listInaccessibleChannelKeys();
                if (!keys.isEmpty()) {
                    toUnset.put(db, keys);
                }
            } else {
                db.unsetInaccessibleChannels();
            }
        }

        if (toUnset.isEmpty()) {
            return "No keys to unset";
        }
        StringBuilder response = new StringBuilder();
        for (Map.Entry<GuildDB, List<GuildSetting>> entry : toUnset.entrySet()) {
            response.append(entry.getKey().getGuild().toString() + ":\n");
            List<String> keys = entry.getValue().stream().map(f -> f.name()).collect(Collectors.toList());
            response.append("- " + StringMan.join(keys, "\n- "));
            response.append("\n");
        }
        String footer = "Rerun the command with `-f` to confirm";
        return response + footer;
    }


    @Command(desc = "Reset city names")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String resetCityNames(@Me DBNation me, @Me Auth auth, String name) throws IOException {
        for (int id : me.getCityMap(false).keySet()) {
            auth.setCityName(id, name);
        }
        return "Done!";
    }


    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public void stop(boolean save) {
        Locutus.imp().stop();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncReferrals(@Me GuildDB db) {
        if (!db.isValidAlliance()) return "Not in an alliance";
        Collection<DBNation> nations = db.getAllianceList().getNations(true, 10000, true);
        for (DBNation nation : nations) {
            db.getHandler().onRefer(nation);
        }
        return "Done!";
    }

    @Command(desc = "Set the archive status of the bot's announcement")
    @RolePermission(any = true, value = {Roles.INTERNAL_AFFAIRS, Roles.MILCOM, Roles.ADMIN, Roles.FOREIGN_AFFAIRS, Roles.ECON})
    public String archiveAnnouncement(@Me GuildDB db, int announcementId, @Default Boolean archive) {
        if (archive == null) archive = true;
        db.setAnnouncementActive(announcementId, !archive);
        return (archive ? "Archived" : "Unarchived") + " announcement with id: #" + announcementId;
    }

    @Command(desc = "Find the announcement for the closest matching invite")
    @RolePermission(Roles.ADMIN)
    @NoFormat
    public String find_invite(@Me GuildDB db, String invite) throws IOException {
        List<Announcement.PlayerAnnouncement> matches = db.getPlayerAnnouncementsContaining(invite);
        if (matches.isEmpty()) {
            return "No announcements found with content: `" + invite + "`";
        } else {
            return "Found " + matches.size() + " matches:\n- " +
                    matches.stream().map(f -> "{ID:" + f.ann_id + ", receiver:" + f.receiverNation + "}").collect(Collectors.joining("\n- "));
        }
    }

    @Command(desc = "Find the announcement closest matching a message")
    @RolePermission(Roles.ADMIN)
    @NoFormat
    public String find_announcement(@Me GuildDB db, int announcementId, String message) throws IOException {
        List<Announcement.PlayerAnnouncement> announcements = db.getPlayerAnnouncementsByAnnId(announcementId);
        if (announcements.isEmpty()) {
            return "No announcements found with id: #" + announcementId;
        }
        long diffMin = Long.MAX_VALUE;
        List<Announcement.PlayerAnnouncement> matches = new ArrayList<>();
        for (Announcement.PlayerAnnouncement announcement : announcements) {
            String content = announcement.getContent();
            if (message.equalsIgnoreCase(content)) {
                return "Announcement sent to nation id: " + announcement.receiverNation;
            }
            byte[] diff = StringMan.getDiffBytes(message, content);
            if (diff.length < diffMin) {
                diffMin = diff.length;
                matches.clear();
                matches.add(announcement);
            } else if (diff.length == diffMin) {
                matches.add(announcement);
            }
        }

        if (matches.isEmpty()) {
            return "No announcements found with id: #" + announcementId;
        } else if (matches.size() == 1) {
            Announcement.PlayerAnnouncement match = matches.get(0);
            return "Closest match: " + match.receiverNation + " with " + diffMin + " differences:\n```\n" + match.getContent() + "\n```";
        } else {
            StringBuilder response = new StringBuilder();
            response.append(matches.size() + " matches with " + diffMin + " differences:\n");
            for (Announcement.PlayerAnnouncement match : matches) {
                response.append("- " + match.receiverNation + "\n");
                // content in ```
                response.append("```\n" + match.getContent() + "\n```\n");
            }
            return response.toString();
        }
    }

    @Command(desc = "Send an announcement to multiple nations, with random variations for each receiver\n")
    @RolePermission(Roles.ADMIN)
    @HasApi
    @NoFormat
    public String announce(@Me GuildDB db, @Me Guild guild, @Me JSONObject command, @Me IMessageIO currentChannel,
                           @Me User author,
                           NationList sendTo,
                           @Arg("The subject used for sending an in-game mail if a discord direct message fails") String subject,
                           @Arg("The message you want to send") @TextArea String announcement,
                           @Arg("Lines of replacement words or phrases, separated by `|` for each variation\n" +
                                   "Add multiple lines for each replacement you want") @TextArea String replacements,
                           @Arg("The channel to post the announcement to (must be same server)") @Switch("c") MessageChannel channel,
                           @Arg("The text to post in the channel below the hidden announcement (e.g. mentions)") @Switch("b") String bottomText,
                           @Arg("The required number of differences between each message") @Switch("v") @Default("0") Integer requiredVariation,
                           @Arg("The required depth of changes from the original message") @Switch("r") @Default("0") Integer requiredDepth,
                           @Arg("Variation seed. The same seed will produce the same variations, otherwise results are random") @Switch("s") Long seed,
                           @Arg("If messages are sent in-game") @Switch("m") boolean sendMail,
                           @Arg("If messages are sent via discord direct message") @Switch("d") boolean sendDM,
                           @Switch("f") boolean force) throws IOException {
        // ensure channel is in same server or null
        if (channel != null && ((GuildMessageChannel) channel).getGuild().getIdLong() != guild.getIdLong()) {
            throw new IllegalArgumentException("Channel must be in the same server: " + ((GuildMessageChannel) channel).getGuild() + " != " + guild);
        }
        if (bottomText != null && channel == null) {
            throw new IllegalArgumentException("Bottom text requires a channel");
        }

        ApiKeyPool keys = (sendMail || sendDM) ? db.getMailKey() : null;
        if ((sendMail || sendDM) && keys == null) throw new IllegalArgumentException("No API_KEY set, please use " + GuildKey.API_KEY.getCommandMention() + "");
        Set<Integer> aaIds = db.getAllianceIds();
        if (sendMail || sendDM) {
            GPTUtil.checkThrowModeration(announcement + "\n" + replacements);
        }

        List<String> errors = new ArrayList<>();
        Collection<DBNation> nations = sendTo.getNations();
        for (DBNation nation : nations) {
            User user = nation.getUser();
            if (user == null) {
                errors.add("Cannot find user for `" + nation.getNation() + "`");
            } else if (guild.getMember(user) == null) {
                errors.add("Cannot find member in guild for `" + nation.getNation() + "` | `" + user.getName() + "`");
            } else {
                continue;
            }
            if (!aaIds.isEmpty() && !aaIds.contains(nation.getAlliance_id())) {
                throw new IllegalArgumentException("Cannot send to nation not in alliance: " + nation.getNation() + " | " + user);
            }
            if (!force) {
                if (nation.active_m() > 20000)
                    return "The " + nations.size() + " receivers includes inactive for >2 weeks. Use `" + sendTo.getFilter() + ",#active_m<20000` or set `force` to confirm";
                if (nation.getVm_turns() > 0)
                    return "The " + nations.size() + " receivers includes vacation mode nations. Use `" + sendTo.getFilter() + ",#vm_turns=0` or set `force` to confirm";
                if (nation.getPosition() < 1) {
                    return "The " + nations.size() + " receivers includes applicants. Use `" + sendTo.getFilter() + ",#position>1` or set `force` to confirm";
                }
            }
        }

        List<String> replacementLines = Announcement.getReplacements(replacements);
        Random random = seed == null ? new Random() : new Random(seed);
        Set<String> results = StringMan.enumerateReplacements(announcement, replacementLines, nations.size() + 1000, requiredVariation, requiredDepth);

        if (results.size() < nations.size()) return "Not enough entropy. Please provide more replacements";

        if (!force) {
            StringBuilder confirmBody = new StringBuilder();
            if (!sendDM && !sendMail) confirmBody.append("**Warning: No ingame or direct message option has been specified**\n");
            confirmBody.append("Send DM (`-d`): " + sendDM).append("\n");
            confirmBody.append("Send Ingame (`-m`): " + sendMail).append("\n");
            if (!errors.isEmpty()) {
                confirmBody.append("\n**Errors**:\n- " + StringMan.join(errors, "\n- ")).append("\n");
            }
//            DiscordUtil.createEmbedCommand(currentChannel, "Send to " + nations.size() + " nations", confirmBody + "\nPress to confirm", );
            DiscordUtil.pending(currentChannel, command, "Send to " + nations.size() + " nations", confirmBody + "\nPress to confirm");
            return null;
        }

        currentChannel.send("Please wait...");

        List<String> resultsArray = new ArrayList<>(results);
        Collections.shuffle(resultsArray, random);

        resultsArray = resultsArray.subList(0, nations.size());

        List<Integer> failedToDM = new ArrayList<>();
        List<Integer> failedToMail = new ArrayList<>();

        StringBuilder output = new StringBuilder();

        Map<DBNation, String> sentMessages = new HashMap<>();

        int i = 0;
        for (DBNation nation : nations) {
            String replaced = resultsArray.get(i++);
            String personal = replaced + "\n\n- " + author.getAsMention() + " " + guild.getName();

            boolean result = sendDM && nation.sendDM(personal);
            if (!result && sendDM) {
                failedToDM.add(nation.getNation_id());
            }
            if ((!result && sendDM) || sendMail) {
                try {
                    nation.sendMail(keys, subject, personal, false);
                } catch (IllegalArgumentException e) {
                    failedToMail.add(nation.getNation_id());
                }
            }

            sentMessages.put(nation, replaced);

            output.append("\n\n```" + replaced + "```" + "^ " + nation.getNation());
        }

        output.append("\n\n------\n");
        if (errors.size() > 0) {
            output.append("Errors:\n- " + StringMan.join(errors, "\n- "));
        }
        if (failedToDM.size() > 0) {
            output.append("\nFailed DM (sent ingame): " + StringMan.getString(failedToDM));
        }
        if (failedToMail.size() > 0) {
            output.append("\nFailed Mail: " + StringMan.getString(failedToMail));
        }

        int annId = db.addAnnouncement(author, subject, announcement, replacements, sendTo.getFilter(), false);
        output.append("\n\nAnnouncement ID: " + annId);
        for (Map.Entry<DBNation, String> entry : sentMessages.entrySet()) {
            byte[] diff = StringMan.getDiffBytes(announcement, entry.getValue());
            db.addPlayerAnnouncement(entry.getKey(), annId, diff);
        }

        if (channel != null) {
            IMessageBuilder msg = new DiscordChannelIO(channel).create();
            StringBuilder body = new StringBuilder();
            body.append("From: " + author.getAsMention() + "\n");
            body.append("To: `" + sendTo.getFilter() + "`\n");

            if (sendMail) {
                body.append("- A copy of this announcement has been sent ingame\n");
            }
            if (sendDM) {
                body.append("- A copy of this announcement has been sent as a direct message\n");
            }

            body.append("\n\nPress `view` to view the announcement");

            msg = msg.embed("[#" + annId + "] " + subject, body.toString());
            if (bottomText != null && !bottomText.isEmpty()) {
                msg = msg.append(bottomText);
            }

            CM.announcement.view cmd = CM.announcement.view.cmd.ann_id(annId + "");
            msg.commandButton(CommandBehavior.EPHEMERAL, cmd, "view").send();
        }

        return output.toString().trim();
    }



//    @Command
//    @RolePermission(value = Roles.ADMIN, root = true)
//    public String whitelistAuto(double requiredBank, double requiredDeposit, long timeFrame) {
//        GuildDB rootDb = Locutus.imp().getRootDb();
//        OffshoreInstance offshore = rootDb.getOffshore();
//
//        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
//
//        Set<Integer> currentWhitelist = rootDb.getCoalition(Coalition.WHITELISTED_AUTO);
//        Set<Integer> offshoring = rootDb.getCoalition(Coalition.OFFSHORING);
//
//        Map<Integer, Double> totalDepositByAA = new HashMap<>();
//        Map<Integer, Double> totalTransferByAA = new HashMap<>();
//
//        for (int allianceId : offshoring) {
//            GuildDB other = Locutus.imp().getGuildDBByAA(allianceId);
//            if (other == null || other.getOffshoreDB() != rootDb) continue;
//
//            double[] deposits = offshore.getDeposits(other, false);
//            double total = PW.convertedTotal(deposits);
//
//            List<Transaction2> tx = offshore.getTransactionsAA(allianceId, false);
//            tx.removeIf(f -> f.tx_datetime < cutoff);
//            tx.removeIf(f -> f.sender_id != allianceId);
//            double txWeek = tx.stream().mapToDouble(f -> f.convertedTotal()).sum();
//
//
//
//        }
//    }

    @Command(desc = "Add or remove a role from a set of members on discord based on a spreadsheet\n" +
            "By default only roles will be added, specify `removeRoles` to remove roles from users not assigned the role in the sheet\n" +
            "Specify `listMissing` to list nations that are not assigned a role in the sheet\n" +
            "Columns:\n" +
            "- `nation`, `leader`, `user`, `member` (at least one)\n" +
            "- `role`, `role1`, `roleN` (multiple, or use comma separated values in one cell)")
    @RolePermission(value = Roles.ADMIN)
    public String maskSheet(@Me IMessageIO io, @Me GuildDB db, @Me Guild guild, @Me JSONObject command,
                            SpreadSheet sheet,
                            @Arg("Remove these roles from users not assigned the role in the sheet")
                            @Switch("u") Set<Role> removeRoles,
                            @Arg("Remove all roles mentioned in the sheet")
                            @Switch("ra") boolean removeAll,
                            @Arg("List nations that are not assigned a role in the sheet")
                            @Switch("ln") Set<DBNation> listMissing,
                            @Switch("f") boolean force) {
        sheet.loadValues(null, true);
        List<Object> nations = sheet.findColumn("nation");
        List<Object> leaders = sheet.findColumn("leader");
        List<Object> users = sheet.findColumn(-1, f -> {
            String lower = f.toLowerCase(Locale.ROOT);
            return lower.startsWith("user") || lower.startsWith("member");
        });
        if (nations == null && leaders == null && users == null) {
            throw new IllegalArgumentException("Expecting column `nation` or `leader` or `user` or `member`");
        }
        Map<String, List<Object>> roles = sheet.findColumn(-1, f -> f.startsWith("role"), true);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Expecting at least one column starting with `role`");
        }
        if (removeAll) {
            Set<String> parsed = new LinkedHashSet<>();
            for (Map.Entry<String, List<Object>> entry : roles.entrySet()) {
                String columnName = entry.getKey();
                List<Object> roleValues = entry.getValue();
                if (roleValues == null || roleValues.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < roleValues.size(); i++) {
                    Object roleCell = roleValues.get(i);
                    if (roleCell == null) {
                        continue;
                    }
                    String roleNameList = roleCell.toString();
                    for (String roleName : roleNameList.split(",")) {
                        roleName = roleName.trim();
                        if (parsed.contains(roleName)) continue;
                        parsed.add(roleName);
                        try {
                            Role role = DiscordBindings.role(guild, roleName);
                            if (removeRoles == null) removeRoles = new LinkedHashSet<>();
                            removeRoles.add(role);
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                    }
                }
            }
        }

        List<String> errors = new ArrayList<>();
        Map<Member, Set<Role>> existingRoles = new LinkedHashMap<>();
        Map<Member, Set<Role>> rolesAllowed = new LinkedHashMap<>();
        Map<Member, Set<Role>> rolesAdded = new LinkedHashMap<>();
        Map<Member, Set<Role>> rolesRemoved = new LinkedHashMap<>();

        Set<DBNation> nationsInSheet = new LinkedHashSet<>();

        int max = Math.max(Math.max(nations == null ? 0 : nations.size(), leaders == null ? 0 : leaders.size()), users == null ? 0 : users.size());
        for (int i = 0; i < max; i++) {
            Object nationObj = nations == null || nations.size() < i ? null : nations.get(i);
            String nationStr = nationObj == null ? null : nationObj.toString();

            Object leaderObj = leaders == null || leaders.size() < i ? null : leaders.get(i);
            String leaderStr = leaderObj == null ? null : leaderObj.toString();

            Object userObj = users == null || users.size() < i ? null : users.get(i);
            String userStr = userObj == null ? null : userObj.toString();

            String input = nationStr == null ? leaderStr == null ? userStr : leaderStr : nationStr;

            User user = null;
            try {
                if (userStr != null) {
                    user = DiscordBindings.user(null, userStr);
                } else if (nationStr != null) {
                    DBNation nation = PWBindings.nation(null, nationStr);
                    if (nation != null) {
                        user = nation.getUser();
                        if (user == null) {
                            errors.add("[Row:" + (i + 2) + "] Nation has no user: " + nation.getMarkdownUrl());
                        }
                    } else {
                        errors.add("[Row:" + (i + 2) + "] Nation not found: `" + nationStr + "`");
                    }
                } else if (leaderStr != null) {
                    DBNation nation = Locutus.imp().getNationDB().getNationByLeader(leaderStr);
                    if (nation != null) {
                        user = nation.getUser();
                        if (user == null) {
                            errors.add("[Row:" + (i + 2) + "] Nation has no user: " + nation.getMarkdownUrl());
                        }
                    } else {
                        errors.add("[Row:" + (i + 2) + "] Nation Leader not found: `" + leaderStr + "`");
                    }
                }
            } catch (IllegalArgumentException e) {
                errors.add("[Row:" + (i + 2) + "] " + e.getMessage());
            }
            if (user == null) continue;
            if (listMissing != null) {
                DBNation nation = DBNation.getByUser(user);
                if (nation != null) {
                    nationsInSheet.add(nation);
                }
            }
            Member member = guild.getMember(user);
            if (member == null) {
                errors.add("[Row:" + (i + 2) + "] User `" + user.getName() + " not found ` in " + guild.toString());
                continue;
            }

            for (Map.Entry<String, List<Object>> entry : roles.entrySet()) {
                String columnName = entry.getKey();
                List<Object> roleValues = entry.getValue();
                if (roleValues == null || roleValues.isEmpty() || roleValues.size() < i) {
                    continue;
                }
                Object roleCell = roleValues.get(i);
                if (roleCell == null) {
                    continue;
                }
                String roleNameList = roleCell.toString();
                for (String roleName : roleNameList.split(",")) {
                    roleName = roleName.trim();
                    try {
                        Role role = DiscordBindings.role(guild, roleName);
                        rolesAllowed.computeIfAbsent(member, f -> new LinkedHashSet<>()).add(role);
                        if (existingRoles.computeIfAbsent(member, f -> new HashSet<>(f.getRoles())).contains(role)) {
                            continue;
                        }
                        rolesAdded.computeIfAbsent(member, f -> new LinkedHashSet<>()).add(role);
                    } catch (IllegalArgumentException e) {
                        errors.add("[Row:" + (i + 2) + ",Column:" + columnName + "] `" + input + "` -> `" + roleName + "`: " + e.getMessage());
                        continue;
                    }
                }
            }
        }

        if (removeRoles != null && !removeRoles.isEmpty()) {
            for (Member member : guild.getMembers()) {
                if (member.getUser().isBot()) continue;
                Set<Role> granted = rolesAllowed.getOrDefault(member, Collections.emptySet());
                for (Role role : removeRoles) {
                    if (!granted.contains(role)) {
                        if (existingRoles.computeIfAbsent(member, f -> new HashSet<>(f.getRoles())).contains(role)) {
                            rolesRemoved.computeIfAbsent(member, f -> new LinkedHashSet<>()).add(role);
                        }
                    }
                }
            }
        }

        StringBuilder body = new StringBuilder();
        body.append("**Sheet**: <" + sheet.getURL() + ">\n");
        IMessageBuilder msg = io.create();

        if (listMissing != null) {
            StringBuilder listMissingMessage = new StringBuilder();
            Set<DBNation> missingNations = listMissing.stream().filter(f -> !nationsInSheet.contains(f)).collect(Collectors.toSet());
            if (!missingNations.isEmpty()) {
                listMissingMessage.append("nation,leader,url,username,user_id\n");
                for (DBNation nation : missingNations) {
                    String name = nation.getName();
                    String leader = nation.getLeader();
                    String url = nation.getUrl();
                    String user = nation.getUserDiscriminator();
                    Long userId = nation.getUserId();
                    listMissingMessage.append(name).append(",")
                            .append(leader).append(",")
                            .append(url).append(",")
                            .append(user == null ? "" : user).append(",")
                            .append(userId == null ? "" : userId).append("\n");
                }
                body.append("**listMissing**: `").append(missingNations.size() + "`\n");
                msg = msg.file("missing_nations.csv", listMissingMessage.toString());
            } else {
                body.append("**listMissing**: No missing nations\n");
            }
        }

        if (removeRoles != null) {
            body.append("**removeRoles**: `").append(removeRoles.stream().map(Role::getName).collect(Collectors.joining(","))).append("`\n");
        }

        if (rolesRemoved.isEmpty() && rolesAdded.isEmpty()) {
            msg.append("\n**Result**: No roles to add or remove").send();
            return null;
        }

        AutoRoleInfo info = new AutoRoleInfo(db, body.toString());
        for (Map.Entry<Member, Set<Role>> entry : rolesAdded.entrySet()) {
            Member member = entry.getKey();
            for (Role role : entry.getValue()) {
                info.addRoleToMember(member, role);
            }
        }
        for (Map.Entry<Member, Set<Role>> entry : rolesRemoved.entrySet()) {
            Member member = entry.getKey();
            for (Role role : entry.getValue()) {
                info.removeRoleFromMember(member, role);
            }
        }
        if (!force) {
            String changeStr = info.toString();
            if (body.length() + changeStr.length() >= 2000) {
                msg = msg.file("role_changes.txt", changeStr);
            } else {
                body.append("\n\n------------\n\n" + changeStr);
            }
            msg.confirmation("Confirm bulk role change", body.toString(), command).send();
            return null;
        }
        io.send("Please wait...");
        info.execute();
        return info.getChangesAndErrorMessage();
    }

    @Command(desc = "Add or remove a role from a set of members on discord")
    @RolePermission(Roles.ADMIN)
    public String mask(@Me Member me, @Me GuildDB db, Set<Member> members, Role role, boolean value, @Arg("If the role should be added or removed from all other members\n" +
            "If `value` is true, the role will be removed, else added") @Switch("r") boolean toggleMaskFromOthers) {
        List<Role> myRoles = me.getRoles();
        List<String> response = new ArrayList<>();
        for (Member member : members) {
            User user = member.getUser();
            List<Role> roles = member.getRoles();
            if (value && roles.contains(role)) {
                response.add(user.getName() + " already has the role: `" + role + "`");
                continue;
            } else if (!value && !roles.contains(role)) {
                response.add(user.getName() + ": does not have the role: `" + role + "`");
                continue;
            }
            if (value) {
                RateLimitUtil.queue(db.getGuild().addRoleToMember(member, role));
                response.add(user.getName() + ": Added role to member");
            } else {
                RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                response.add(user.getName() + ": Removed role from member");
            }
        }
        if (toggleMaskFromOthers) {
            for (Member member : db.getGuild().getMembers()) {
                if (members.contains(member)) continue;
                List<Role> memberRoles = member.getRoles();
                if (value) {
                    if (memberRoles.contains(role)) {
                        RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                        response.add(member.getUser().getName() + ": Removed role from member");
                    }
                } else {
                    if (!memberRoles.contains(role)) {
                        RateLimitUtil.queue(db.getGuild().addRoleToMember(member, role));
                        response.add(member.getUser().getName() + ": Added role to member");
                    }
                }
            }
        }
        return StringMan.join(response, "\n").trim();
    }

    @Command
    @RolePermission(value = Roles.MAIL, root = true)
    public String dm(@Me User author, @Me Guild guild, @Me IMessageIO io, @Me JSONObject command, Set<DBNation> nations, String message, @Switch("f") boolean force) {
        if (nations.size() > 500) {
            throw new IllegalArgumentException("Too many nations: " + nations.size() + " (max 500)");
        }
        if (!force) {
            String title = "Send " + nations.size() + " messages";
            Set<Integer> alliances = new LinkedHashSet<>();
            for (DBNation nation : nations) alliances.add(nation.getAlliance_id());
            String embedTitle = title + " to nations.";
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances.";
            String dmMsg = "content: ```" + message + "```";
            io.create().embed(embedTitle, dmMsg).confirmation(command).send();
            return null;
        }
        boolean hasAdmin = Roles.ADMIN.hasOnRoot(author);
        List<String> errors = new ArrayList<>();
        List<User> users = new ArrayList<>();
        for (DBNation nation : nations) {
            User user = nation.getUser();
            if (user == null) {
                errors.add("No user found for " + nation.getNation());
            } else {
                if (!hasAdmin) {
                    Member member = guild.getMember(user);
                    if (member == null) {
                        errors.add("No member found for " + nation.getNation() + " in guild " + guild);
                        continue;
                    }
                }

                users.add(user);
            }
        }
        if (users.isEmpty()) {
            return "No users found. Are they registered? " + CM.register.cmd.toSlashMention();
        }
        GPTUtil.checkThrowModeration(message);
        CompletableFuture<IMessageBuilder> msgFuture = io.sendMessage("Sending " + users.size() + " with " + errors.size() + " errors\n" + StringMan.join(errors, "\n"));
        for (User mention : users) {
            mention.openPrivateChannel().queue(f -> RateLimitUtil.queue(f.sendMessage(author.getAsMention() + " said: " + message + "\n\n(no reply)")));
        }
        io.sendMessage("Done! Sent " + users.size() + " messages");
        return null;
    }

    @Command(desc = "Edit an attribute of your in-game alliance\n" +
            "Attributes match the in-game fields and are case sensitive\n" +
            "Run the command without arguments to get a list of attributes"
    )
    @RolePermission(Roles.ADMIN)
    public String editAlliance(@Me GuildDB db, @Me User author, DBAlliance alliance, @Default String attribute, @Default @TextArea String value) throws Exception {
        if (!db.isAllianceId(alliance.getAlliance_id())) {
            return "Alliance: " + alliance.getAlliance_id() + " not registered to guild " + db.getGuild() + ". See: " + CM.settings.info.cmd.toSlashMention() + " with key: " + GuildKey.ALLIANCE_ID.name();
        }

        Rank rank = attribute != null && attribute.toLowerCase().contains("bank") ? Rank.HEIR : Rank.OFFICER;
        Auth auth = alliance.getAuth(AlliancePermission.EDIT_ALLIANCE_INFO);
        if (auth == null) return "No authorization set";

        StringBuilder response = new StringBuilder();

        EditAllianceTask task = new EditAllianceTask(auth.getNation(), new Consumer<Map<String, String>>() {
            @Override
            public void accept(Map<String, String> post) {
                if (attribute == null || value == null) {
                    throw new IllegalArgumentException("Currently set: " + StringMan.getString(post));
                }
                if (post.containsKey(attribute.toLowerCase()) || attribute.equals("acceptmem")) {
                    post.put(attribute.toLowerCase(), value);
                    response.append("Attribute has been set.");
                } else {
                    response.append("Invalid key: " + attribute + ". Options: " + StringMan.getString(post));
                }
            }
        });
        task.call();
        return response.toString();
    }

    @Command(desc = "Remove a discord role the bot uses for command permissions")
    @RolePermission(Roles.ADMIN)
    public String unregisterRole(@Me User user, @Me Guild guild, @Me GuildDB db, Roles locutusRole, @Arg("Only remove a role mapping for this alliance") @Default DBAlliance alliance) {
        return aliasRole(user, guild, db, locutusRole, null, alliance, true);
    }

    private static String mappingToString(Map<Long, Role> mapping) {
        List<String> response = new ArrayList<>();
        for (Map.Entry<Long, Role> entry : mapping.entrySet()) {
            Role role = entry.getValue();
            long aaId = entry.getKey();
            if (aaId == 0) {
                response.add("*:" + role.getName());
            } else {
                response.add(aaId + ": " + role.getName());
            }
        }
        if (response.isEmpty()) return "";
        return "- " + StringMan.join(response, "\n- ");
    }

    @Command(desc = "Set the discord roles the bot uses for command permissions")
    @RolePermission(Roles.ADMIN)
    public static String aliasRole(@Me User author, @Me Guild guild, @Me GuildDB db, @Default Roles locutusRole, @Default() Role discordRole, @Arg("If the role mapping is only for a specific alliance (WIP)") @Default() DBAlliance alliance, @Arg("Remove the existing mapping instead of setting it") @Switch("r") boolean removeRole) {
        if (alliance != null && !db.isAllianceId(alliance.getAlliance_id())) {
            return "Alliance: " + alliance.getAlliance_id() + " not registered to guild " + db.getGuild() + ". See: " + CM.settings.info.cmd.toSlashMention() + " with key: " + GuildKey.ALLIANCE_ID.name();
        }
        StringBuilder response = new StringBuilder();
        boolean showGlobalMappingInfo = false;

        if (locutusRole == null) {
            if (discordRole != null) {
                List<String> rolesListStr = new ArrayList<>();
                Map<Roles, Map<Long, Long>> allMapping = db.getMappingRaw();
                if (removeRole) {
                    // remove all roles registered to it
                    for (Map.Entry<Roles, Map<Long, Long>> locEntry : allMapping.entrySet()) {
                        for (Map.Entry<Long, Long> discEntry : locEntry.getValue().entrySet()) {
                            long aaId =discEntry.getKey();
                            if (alliance != null && aaId != alliance.getAlliance_id()) continue;
                            if (discEntry.getValue() == discordRole.getIdLong()) {
                                String aaStr =  aaId == 0 ? "*" : PW.getName(aaId, true);
                                rolesListStr.add("Removed " + locEntry.getKey().name() + " from " + discordRole.getName() + " (AA:" + aaId + ")");
                                db.deleteRole(locEntry.getKey(), aaId);
                            }
                        }
                    }
                    if (rolesListStr.isEmpty()) {
                        return "No aliases found for " + discordRole.getName();
                    }
                    response.append("Removed aliases for " + discordRole.getName() + ":\n- ");
                    response.append(StringMan.join(rolesListStr, "\n- "));
                    response.append("\n\nUse " + CM.role.setAlias.cmd.toSlashMention() + " to view current role aliases");
                    return response.toString();
                }

                for (Map.Entry<Roles, Map<Long, Long>> locEntry : allMapping.entrySet()) {
                    Map<Long, Long> aaToRoleMap = locEntry.getValue();
                    showGlobalMappingInfo |= aaToRoleMap.size() > 1 && aaToRoleMap.containsKey(0L);
                    for (Map.Entry<Long, Long> discEntry : aaToRoleMap.entrySet()) {
                        if (discEntry.getValue() == discordRole.getIdLong()) {
                            Roles role = locEntry.getKey();
                            long aaId = discEntry.getKey();
                            if (aaId == 0) {
                                rolesListStr.add("*:" + role.name());
                            } else {
                                rolesListStr.add(DBAlliance.getOrCreate((int) aaId).getName() + "/" + aaId + ":" + role.name());
                            }
                        }
                    }
                }
                if (rolesListStr.isEmpty()) {
                    return "No aliases found for " + discordRole.getName();
                }
                response.append("Aliases for " + discordRole.getName() + ":\n- ");
                response.append(StringMan.join(rolesListStr, "\n- "));
                if (showGlobalMappingInfo) response.append("\n`note: " + Messages.GLOBAL_ROLE_MAPPING_INFO + "`");
                return response.toString();
            }

            List<String> registeredRoles = new ArrayList<>();
            List<String> unregisteredRoles = new ArrayList<>();
            for (Roles role : Roles.values) {
                Map<Long, Role> mapping = db.getAccountMapping(role);
                if (mapping != null && !mapping.isEmpty()) {
                    registeredRoles.add(role + ":\n" + mappingToString(mapping));
                    continue;
                }
                if (role.getKey() != null && db.getOrNull(role.getKey()) == null) continue;
                unregisteredRoles.add(role + ":\n" + mappingToString(mapping));
            }

            if (!registeredRoles.isEmpty()) {
                response.append("**Registered Roles**:\n" + StringMan.join(registeredRoles, "\n") + "\n");
            }
            if (!unregisteredRoles.isEmpty()) {
                response.append("**Unregistered Roles**:\n" + StringMan.join(unregisteredRoles, "\n") + "\n");
            }
            response.append("Provide a value for `locutusRole` for specific role information.\n" +
                    "Provide a value for `discordRole` to register a role.\n");

            return response.toString();
        }

        if (discordRole == null) {
            if (removeRole) {
                Role alias = db.getRole(locutusRole, alliance != null ? (long) alliance.getAlliance_id() : null);
                if (alias == null) {
                    String allianceStr = alliance != null ? alliance.getName() + "/" + alliance.getAlliance_id() : "*";
                    return "No role alias found for " + allianceStr + ":" + locutusRole.name();
                }
                if (alliance != null) {
                    db.deleteRole(locutusRole, alliance.getAlliance_id());
                } else {
                    db.deleteRole(locutusRole);
                }
                response.append("Removed role alias for " + locutusRole.name() + ":\n");
            }
            Map<Long, Role> mapping = db.getAccountMapping(locutusRole);
            response.append("**" + locutusRole.name() + "**:\n");
            response.append("`" + locutusRole.getDesc() + "`\n");
            if (mapping.isEmpty()) {
                response.append("No value set.");
            } else {
                response.append("```\n" + mappingToString(mapping) + "```\n");
            }
            response.append("Provide a value for `discordRole` to register a role.\n");
            if (mapping.size() > 1 && mapping.containsKey(0L)) {
                response.append("`note: " + Messages.GLOBAL_ROLE_MAPPING_INFO + "`");
            }
            return response.toString().trim();
        }

        if (removeRole) {
            throw new IllegalArgumentException("Cannot remove role alias with this command. Use " + CM.role.unregister.cmd.locutusRole(locutusRole.name()).toSlashCommand() + "");
        }


        int aaId = alliance == null ? 0 : alliance.getAlliance_id();
        String allianceStr = alliance == null ? "*" : alliance.getName() + "/" + aaId;
        db.addRole(locutusRole, discordRole, aaId);
        return "Added role alias: " + locutusRole.name().toLowerCase() + " to " + discordRole.getName() + " for alliance " + allianceStr + "\n" +
                "To unregister, use " + CM.role.unregister.cmd.locutusRole(locutusRole.name()).toSlashCommand() + "";
    }

    public String printApiStats(ApiKeyPool keys) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ApiKeyPool.ApiKey key : keys.getKeys()) {
            PoliticsAndWarV3 v3 = new PoliticsAndWarV3(ApiKeyPool.create(key));
            try {
                ApiKeyDetails stats = v3.getApiKeyStats();
                map.put(key.getKey(), StringMan.formatJsonLikeText(stats.toString()));
            } catch (Throwable e) {
                map.put(key.getKey(), e.getMessage());
            }
        }
        StringBuilder response = new StringBuilder();

        // Convert map to simple message (newline for each / header)
        for (Map.Entry<String, String> entry : map.entrySet()) {
            response.append("**Key `" + entry.getKey() + "`:\n```json\n" + entry.getValue() + "\n```\n\n");
        }
        return response.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String apiUsageStats(@Me DBAlliance alliance, boolean cached) {
        ApiKeyPool keys = alliance.getApiKeys();
        System.out.println(printApiStats(keys));
        return "Done! (see console)";
    }

    @Command(desc = "Import api keys from the guild API_KEY setting, so they can be validated")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importGuildKeys() {
        StringBuilder response = new StringBuilder();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            List<String> keys = db.getOrNull(GuildKey.API_KEY);
            if (keys == null) return "No keys found for guild " + db.getGuild().getName() + " (" + db.getGuild().getId() + ")";
            for (String key : keys) {
                try {
                    ApiKeyDetails stats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                    Locutus.imp().getDiscordDB().addApiKey(stats.getNation().getId(), key);

                    response.append(key + ": success" + "\n");
                } catch (Throwable e) {
                    response.append(key + ": " + e.getMessage() + "\n");
                }
            }
        }
        return "Done!";
    }

//    @Command(desc = "Check if current api keys are valid")
//    @RolePermission(value = Roles.ADMIN, root = true)
//    public String validateAPIKeys() {
//        // Validate v3 keys used in the guild db?
//        return "TODO";
//        Set<String> keys = Locutus.imp().getPnwApiV2().getApiKeyUsageStats().keySet();
//        Map<String, String> failed = new LinkedHashMap<>();
//        Map<String, ApiKeyDetails> success = new LinkedHashMap<>();
//        for (String key : keys) {
//            try {
//                ApiKeyDetails stats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
//                if (stats != null && stats.getNation() != null && stats.getNation().getId() != null) {
//                    success.put(key, stats);
//                } else {
//                    failed.put(key, "Error: null (1)");
//                }
//            } catch (Throwable e) {
//                failed.put(key, e.getMessage());
//            }
//        }
//        StringBuilder response = new StringBuilder();
//        for (Map.Entry<String, String> e : failed.entrySet()) {
//            response.append(e.getKey() + ": " + e.getValue() + "\n");
//        }
//        for (Map.Entry<String, ApiKeyDetails> e : success.entrySet()) {
//            String key = e.getKey();
//            ApiKeyDetails record = e.getValue();
//            int natId = record.getNation().getId();
//            DBNation nation = DBNation.getById(natId);
//            if (nation != null) {
//                response.append(key + ": " + record.toString() + " | " + nation.getNation() + " | " + nation.getAllianceName() + " | " + nation.getPosition() + "\n");
//            } else {
//                response.append(e.getKey() + ": " + e.getValue() + "\n");
//            }
//        }
//        System.out.println(response); // keep
//        return "Done (see console)";
//    }

    @Command(desc = "Test your alliance recruitment message by sending it to the bot creator's nation")
    @RolePermission(value = Roles.ADMIN)
    public String testRecruitMessage(@Me GuildDB db) throws IOException {
        JsonObject response = db.sendRecruitMessage(Locutus.imp().getNationDB().getNation(Settings.INSTANCE.NATION_ID));
        return response.toString();
    }

    @Command(desc = "Purge a category's channels older than the time specified")
    @RolePermission(value = Roles.ADMIN)
    public String debugPurgeChannels(Category category, @Range(min=60) @Timestamp long cutoff) {
        long now = System.currentTimeMillis();
        int deleted = 0;
        for (GuildMessageChannel GuildMessageChannel : category.getTextChannels()) {
            if (GuildMessageChannel.getLatestMessageIdLong() > 0) {
                long message = GuildMessageChannel.getLatestMessageIdLong();
                try {
                    long created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(message).toEpochSecond() * 1000L;
                    if (created > cutoff) {
                        continue;
                    }
                } catch (Throwable ignore) {}
            }
            RateLimitUtil.queue(GuildMessageChannel.delete());
            deleted++;
            continue;
        }
        return "Deleted " + deleted + " channels";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listExpiredGuilds(boolean checkMessages) {
        StringBuilder response = new StringBuilder();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            Guild guild = db.getGuild();
            Member owner = db.getGuild().getOwner();
            DBNation nation = DiscordUtil.getNation(owner.getUser());

            Set<Integer> aaIds = db.getAllianceIds();

            if (nation != null && nation.active_m() > 30000) {
                response.append(guild + "/" + StringMan.getString(aaIds) + ": owner (nation:" + nation.getNation_id() + ") is inactive " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m()) + "\n");
                continue;
            }
            // In an alliance with inactive leadership (1 month)
            if (!aaIds.isEmpty() && !db.isValidAlliance()) {
                response.append(guild + "/" + StringMan.getString(aaIds) + ": alliance is invalid (nation:" + (nation == null ? "" : nation.getNation_id() + ")\n"));
                continue;
            }

            if (aaIds.isEmpty() && nation == null && checkMessages) {
                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                boolean error = false;
                long last = 0;

                outer:
                for (GuildMessageChannel channel : guild.getTextChannels()) {
                    if (channel.getLatestMessageIdLong() == 0) continue;
                    try {
                        long latestSnowflake = channel.getLatestMessageIdLong();
                        long latestMs = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(latestSnowflake).toEpochSecond() * 1000L;
                        if (latestMs > cutoff) {
                            List<Message> messages = RateLimitUtil.complete(channel.getHistory().retrievePast(5));
                            for (Message message : messages) {
                                if (message.getAuthor().isSystem() || message.getAuthor().isBot() || guild.getMember(message.getAuthor()) == null) {
                                    continue;
                                }
                                last = Math.max(last, message.getTimeCreated().toEpochSecond() * 1000L);
                                if (last > cutoff) {
                                    break outer;
                                }
                            }
                        }
                    } catch (Throwable e) {
                        error = true;
                    }
                }
                if (last < cutoff) {
                    response.append(guild + ": has no recent messages\n");
                    continue;
                }
            }
        }
        return response.toString();
    }

    @Command(desc = "Remove deleted alliances or guilds from a coalition\n" +
            "Note: Do not remove deleted offshores or banks if you want to use their previous transactions in deposit calculations")
    @RolePermission(value = Roles.ADMIN)
    public String removeInvalidOffshoring(@Me GuildDB db, Coalition coalition) {
        Set<Long> toRemove = new HashSet<>();
        for (long id : db.getCoalitionRaw(coalition)) {
            GuildDB otherDb;
            if (id > Integer.MAX_VALUE) {
                otherDb = Locutus.imp().getGuildDB(id);
            } else {
                otherDb = Locutus.imp().getGuildDBByAA((int) id);
            }
            if (otherDb == null) {
                toRemove.add(id);
            }
        }
        System.out.println(StringMan.getString(toRemove));
        for (long id : toRemove) {
            db.removeCoalition(id,coalition);
        }
        return "Removed `" + StringMan.join(toRemove, ",") + "` from " + Coalition.OFFSHORING;

    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String leaveServer(long guildId) {
        GuildDB db = Locutus.imp().getGuildDB(guildId);
        if (db == null) return "Server not found " + guildId;
        Guild guild = db.getGuild();
        RateLimitUtil.queue(guild.leave());
        return "Leaving " + guild.getName();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listExpiredOffshores() {
        OffshoreInstance offshore = Locutus.imp().getRootBank();
        GuildDB db = offshore.getGuildDB();
        Set<Long> coalitions = db.getCoalitionRaw(Coalition.OFFSHORING);

        Map<Long, List<String>> notices = new HashMap<>();
        Set<Long> printDeposits = new HashSet<>();
        Set<Long> hasError = new HashSet<>();

        for (Long id : coalitions) {
            List<String> notice = notices.computeIfAbsent(id, f -> new ArrayList<>());
            GuildDB otherDb = (id > Integer.MAX_VALUE) ? Locutus.imp().getGuildDB(id) : Locutus.imp().getGuildDBByAA(id.intValue());
            if (otherDb == null) {
                notice.add("- No database found");
                hasError.add(id);
                continue;
            }

            if (id > Integer.MAX_VALUE) {
                notice.add(" **CORPORATION**");
            } else {
                DBAlliance alliance = DBAlliance.get(id.intValue());
                if (alliance == null || !alliance.exists()) {
                    notice.add("\n- AA does not exist: " + id);
                    printDeposits.add(id);
                } else {
                   notice.add(" **ALLIANCE**");
                }
            }

            notice.add("\n- Guild: `" + otherDb.getGuild().toString() + "`");
            Set<Integer> aaIds = otherDb.getAllianceIds();
            if (!aaIds.isEmpty()) {
                List<String> aaNames = new ArrayList<>();
                for (int aaId : aaIds) {
                    DBAlliance aa = DBAlliance.get(aaId);
                    if (aa == null) {
                        aaNames.add(aaId + "");
                    } else {
                        aaNames.add(aa.getName() + "/" + aaId);
                    }
                }
                notice.add("\n- Alliance: `" + StringMan.getString(aaNames) + "`");
            }

            List<Transaction2> transactions;
            if (!aaIds.isEmpty()) {
                transactions = offshore.getTransactionsAA(aaIds, false);
            } else {
                transactions = offshore.getTransactionsGuild(id, false);
            }
            transactions.removeIf(f -> f.tx_datetime > System.currentTimeMillis());
            transactions.removeIf(f -> f.receiver_id == f.banker_nation && f.tx_id > 0);
            long latestTx = transactions.isEmpty() ? 0 : transactions.stream().mapToLong(f -> f.tx_datetime).max().getAsLong();
            if (latestTx < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)) {
                String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - latestTx);
                notice.add("\n- Latest Transfer: `" + timeStr + "`");
                hasError.add(id);
                printDeposits.add(id);
            }

            Member owner = otherDb.getGuild().getOwner();

            if (!aaIds.isEmpty()) {
                AllianceList alliance = new AllianceList(aaIds);
                Set<DBNation> nations = new HashSet<>(alliance.getNations());
                nations.removeIf(f -> f.getPosition() < Rank.LEADER.id);
                long minActiveM = Long.MAX_VALUE;
                DBNation latestNation = null;
                for (DBNation nation : nations) {
                    if (nation.active_m() < minActiveM) {
                        minActiveM = nation.active_m();
                        latestNation = nation;
                    }
                }
                if (minActiveM > 10000) {
                    notice.add("\n- Inactive Leadership: `" + (latestNation != null ? "<" + latestNation.getUrl() + ">" : null) + " | " + TimeUtil.secToTime(TimeUnit.MINUTES, minActiveM) + "`");
                    printDeposits.add(id);
                }
            }

            DBNation nation = owner != null ? DiscordUtil.getNation(owner.getIdLong()) : null;
            if (nation == null) {
                notice.add("\n- Owner is Unregistered");
                printDeposits.add(id);
            } else if (nation.active_m() > 10000) {
                notice.add("\n- Owner is inactive: <@" + owner.getIdLong() + "> | <" + nation.getUrl() + "> | `" + TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m()) + "`");
                printDeposits.add(id);
            }
        }

        StringBuilder response = new StringBuilder();
        for (long id : coalitions) {
            if (!hasError.contains(id)) continue;
            List<String> notes = notices.get(id);
            response.append("\n\n**").append(id).append("**");
            for (String note : notes) {
                response.append(note);
            }
            if (printDeposits.contains(id)) {
                Map<ResourceType, Double> depo;
                if (id > Integer.MAX_VALUE) {
                    depo = offshore.getDeposits(id, false);
                } else {
                    depo = offshore.getDeposits((int) id, false);
                }
                response.append("\n- Deposits: `" + ResourceType.resourcesToString(depo) + "` worth: `$" + MathMan.format(ResourceType.convertedTotal(depo)) + "`");
            }
            response.append("\n\n");
        }

        return response.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listGuildOwners() {
        ArrayList<GuildDB> guilds = new ArrayList<>(Locutus.imp().getGuildDatabases().values());
        guilds.sort(new Comparator<GuildDB>() {
            @Override
            public int compare(GuildDB o1, GuildDB o2) {
                return Long.compare(o1.getGuild().getIdLong(), o2.getGuild().getIdLong());
            }
        });
        StringBuilder result = new StringBuilder();
        for (GuildDB value : guilds) {
            Guild guild = value.getGuild();
            User owner = Locutus.imp().getDiscordApi().getUserById(guild.getOwnerIdLong());
            result.append(guild.getIdLong() + " | " + guild.getName() + " | " + owner.getName()).append("\n");
        }
        return result.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncInfraLand() throws IOException, ParseException {
        List<Event> events = new ArrayList<>();
        Locutus.imp().getNationDB().updateCitiesV2(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        return "Updated city infra land. " + events.size() + " changes detected";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncMetrics(@Default("80") int topX) throws IOException, ParseException {
        AllianceMetric.update(topX);
        return "Updated metrics for top " + topX + " alliances";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCities(NationDB db) throws IOException, ParseException {
        StringBuilder result = new StringBuilder();
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");

        List<Event> events = new ArrayList<>();
        db.updateAllCities(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        result.append("events: " + events.size() + "\n");
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");
        result.append("Updated all cities. " + events.size() + " changes detected");
        return result.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCitiesTest(NationDB db) throws IOException, ParseException {
        StringBuilder result = new StringBuilder();
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");

        List<Event> events = new ArrayList<>();
        db.updateCitiesV2(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        result.append("events: " + events.size() + "\n");
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");
        result.append("Updated all cities. " + events.size() + " changes detected");
        return result.toString();
    }

//    @Command()
//    @RolePermission(value = Roles.ADMIN, root = true)
//    public String syncCitiesTest2(NationDB db, @Me DBNation me) throws IOException, ParseException {
//        Map<Integer, JavaCity> cities = me.getCityMap(true);
//        StringBuilder result = new StringBuilder();
//        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");
//
//        List<Event> events = new ArrayList<>();
//        db.updateCitiesV2(events::add);
//        if (events.size() > 0) {
//            Locutus.imp().getExecutor().submit(() -> {
//                for (Event event : events) event.post();;
//            });
//        }
//        result.append("events: " + events.size() + "\n");
//        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");
//        result.append("Updated all cities. " + events.size() + " changes detected");
//        return result.toString();
//    }


    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncNations(NationDB db, @Default Set<DBNation> nations, @Switch("d") boolean dirtyNations) throws IOException, ParseException {
        if (dirtyNations) {
            db.updateDirtyNations(Event::post);
        }
        List<Event> events = new ArrayList<>();
        Set<Integer> updatedIds;
        if (nations != null && !nations.isEmpty()) {
            updatedIds = db.updateNations(nations.stream().map(DBNation::getId).toList(), events::add);
        } else {
            updatedIds = db.updateAllNations(events::add);
        }
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        return "Updated " + updatedIds.size() + " nations. " + events.size() + " changes detected";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBanks(@Me GuildDB db, @Me IMessageIO channel, @Default DBAlliance alliance, @Default @Timestamp Long timestamp) throws IOException, ParseException {
        if (alliance != null) {
            db = alliance.getGuildDB();
            if (db == null) throw new IllegalArgumentException("No guild found for AA:" + alliance);

            channel.send("Syncing banks for " + db.getGuild() + "...");
            OffshoreInstance bank = alliance.getBank();
            bank.sync(timestamp, false);
        }

        Locutus.imp().getBankDB().updateBankRecs(false, Event::post);
        return "Done!";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBlockades(@Me IMessageIO channel) throws IOException, ParseException {
        Locutus.imp().getWarDb().syncBlockades();
        return "Done!";
    }

    @Command(aliases = {"syncforum", "syncforums"})
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncForum(@Me IMessageIO channel, @Default Integer sectionId, @Default String sectionName) throws IOException, ParseException, SQLException {
        ForumDB forumDB = Locutus.imp().getForumDb();
        if (sectionId != null) {
            if (sectionName == null) sectionName = forumDB.getSectionName(sectionId);
            if (sectionName == null) {
                throw new IllegalArgumentException("No section found for id: " + sectionId);
            }
            forumDB.scrapeTopic(sectionId, sectionName);
        } else {
            forumDB.update();
        }
        return "Done!";
    }

    @Command(desc = "List users in the guild that have provided login credentials to locutus")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listAuthenticated(@Me GuildDB db) {
        List<Member> members = db.getGuild().getMembers();

        Map<DBNation, Rank> registered = new LinkedHashMap<>();
        Map<DBNation, String> errors = new HashMap<>();

        Set<Integer> alliances = db.getAllianceIds(false);
        for (Member member : members) {
            DBNation nation = DiscordUtil.getNation(member.getUser());
            if (nation != null && (alliances.isEmpty() || alliances.contains(nation.getAlliance_id()))) {
                try {
                    Auth auth = nation.getAuth(true);
                    registered.put(nation, Rank.byId(nation.getPosition()));
                    try {
                        ApiKeyPool.ApiKey key = auth.fetchApiKey();
                    } catch (Throwable e) {
                        errors.put(nation, e.getMessage());
                    }
                } catch (IllegalArgumentException ignore) {}
            }
        }

        if (registered.isEmpty()) {
            return "No registered users";
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<DBNation, Rank> entry : registered.entrySet()) {
            result.append(entry.getKey().getNation() + "- " + entry.getValue());
            String error = errors.get(entry.getValue());
            if (error != null) {
                result.append(": Could not validate: " + error);
            }
            result.append("\n");
        }
        return result.toString().trim();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public synchronized String importLinkedBans() throws IOException {
        Locutus.imp().getNationDB().importMultiBans();
        return "Done";
    }

    @Command
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public synchronized String hasSameNetworkAsBan(@Me IMessageIO io, @Me User author, Set<DBNation> nations, @Switch("e") boolean listExpired, @Switch("f") boolean forceUpdate) throws IOException {
        if (forceUpdate && nations.size() > 300 && !Roles.ADMIN.hasOnRoot(author)) {
            throw new IllegalArgumentException("Too many nations to update");
        }
        Map<Integer, BigInteger> latestUids = Locutus.imp().getDiscordDB().getLatestUidByNation();
        Map<BigInteger, Set<Integer>> uidsByNation = new HashMap<>();
        Map<BigInteger, Set<DBNation>> uidsByNationExisting = new HashMap<>();
        for (Map.Entry<Integer, BigInteger> entry : latestUids.entrySet()) {
            BigInteger uid = entry.getValue();
            int nationId = entry.getKey();
            uidsByNation.computeIfAbsent(uid, k -> new HashSet<>()).add(nationId);
            DBNation nation = DBNation.getById(nationId);
            if (nation != null) {
                uidsByNationExisting.computeIfAbsent(uid, k -> new HashSet<>()).add(nation);
            }
        }

        // remove uidsBynationExisting when values size <= 1
        uidsByNationExisting.entrySet().removeIf(entry -> entry.getValue().size() <= 1);
        uidsByNationExisting.entrySet().removeIf(entry -> {
            boolean contains = false;
            for (DBNation nation : entry.getValue()) {
                if (nations.contains(nation)) {
                    contains = true;
                    break;
                }
            }
            return !contains;
        });

        if (forceUpdate && uidsByNationExisting.size() > 0) {
            Set<DBNation> nationsToUpdate = new HashSet<>();

            CompletableFuture<IMessageBuilder> msgFuture = io.sendMessage("Updating...");
            IMessageBuilder msg = null;

            long start = System.currentTimeMillis();
            for (Set<DBNation> nationSet : uidsByNationExisting.values()) {
                nationsToUpdate.addAll(nationSet);
            }
            int i = 1;
            for (DBNation nation : nationsToUpdate) {
                if (System.currentTimeMillis() - start > 10000) {
                    msg = io.updateOptionally(msgFuture, "Updating " + nation.getNation() + "(" + i + "/" + nationsToUpdate.size() + ")");
                    start = System.currentTimeMillis();
                }
                nation.fetchUid(true);
                i++;
            }
            if (msg != null && msg.getId() > 0) {
                io.delete(msg.getId());
            }
            return hasSameNetworkAsBan(io, author, nations, listExpired, false);
        }

        // get the bans
        Map<Integer, DBBan> bans = Locutus.imp().getNationDB().getBansByNation();

        Map<DBNation, Set<DBBan>> sameNetworkBans = new HashMap<>();

        for (DBNation nation : nations) {
            BigInteger uid = latestUids.get(nation.getId());
            if (uid == null) continue;
            Set<Integer> nationIds = uidsByNation.get(uid);

            List<DBBan> natBans = nation.getBans();
            if (!listExpired) natBans.removeIf(DBBan::isExpired);

            if (!natBans.isEmpty()) {
                sameNetworkBans.put(nation, new HashSet<>(natBans));
            }

            for (int id : nationIds) {
                if (id == nation.getId()) continue;
                DBBan ban = bans.get(id);
                if (ban != null && (listExpired || !ban.isExpired())) {
                    sameNetworkBans.computeIfAbsent(nation, k -> new HashSet<>()).add(ban);
                }
            }
        }

        StringBuilder response = new StringBuilder();

        if (!uidsByNationExisting.isEmpty()) {
            response.append("## Active nations sharing the same network:\n");
            for (Map.Entry<BigInteger, Set<DBNation>> entry : uidsByNationExisting.entrySet()) {
                response.append(entry.getKey().toString(16)).append(":\n");
                for (DBNation nation : entry.getValue()) {
                    response.append("- ").append(nation.getUrl()).append("\n");
                }
            }
            response.append("\n");
        }
        if (!sameNetworkBans.isEmpty()) {
            response.append("## Bans on the same network:\n");
            for (Map.Entry<DBNation, Set<DBBan>> entry : sameNetworkBans.entrySet()) {
                DBNation nation = entry.getKey();
                if (!nations.contains(nation)) continue;
                // Key then dot points, with nation url
                response.append(entry.getKey().getUrl()).append(":\n");
                for (DBBan ban : entry.getValue()) {
                    StringBuilder banStr = new StringBuilder("nation:" + ban.nation_id);
                    if (ban.discord_id > 0) {
                        banStr.append(" discord:").append(ban.discord_id);
                    }
                    if (ban.isExpired()) {
                        banStr.append(" (expired)");
                    } else {
                        banStr.append(" (expires ").append(TimeUtil.secToTime(TimeUnit.MILLISECONDS, ban.getTimeRemaining())).append(")");
                    }
                    banStr.append(": `" + ban.reason.replace("\n", " ") + "`");
                    response.append("- ").append(banStr).append("\n");
                }
            }
        }

        response.append("\nDone!");

        return response.toString();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncLootFromAttacks() {
        int found = 0;
        int added = 0;
        List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(0, AttackType.A_LOOT);
        for (AbstractCursor attack : attacks) {
            if (attack.getAllianceIdLooted() > 0) {
                LootEntry existing = Locutus.imp().getNationDB().getAllianceLoot(attack.getAllianceIdLooted());
                if (existing != null && existing.getDate() < attack.getDate()) {
                    Double pct = attack.getLootPercent();
                    if (pct == 0) pct = 0.01;
                    double factor = 1/pct;
                    double[] loot = attack.getLoot();

                    double[] lootCopy = loot == null ? ResourceType.getBuffer() : loot.clone();
                    for (int i = 0; i < lootCopy.length; i++) {
                        lootCopy[i] = (lootCopy[i] * factor) - lootCopy[i];
                    }

                    Locutus.imp().getNationDB().saveAllianceLoot(attack.getAllianceIdLooted(), attack.getDate(), lootCopy, NationLootType.WAR_LOSS);
                }
            }
        }
        return "Done!";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String conditionalMessageSettings(boolean setMeta, boolean sendMessages, boolean run) {
        GuildCustomMessageHandler messageHandler = Locutus.imp().getMessageHandler();
        messageHandler.setMeta(setMeta);
        messageHandler.setSendMessages(sendMessages);
        if (run) {
            messageHandler.run();
        }
        return "Done!";
    }


    @Command(desc = "Returns a list of forum profiles and their respective nation id / discord tag\n" +
            "Deprecated because it is an unauthenticated list, anyone can set their discord or nation on the forums\n" +
            "Information purposes only")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncForumProfiles(@Me GuildDB guildDB, @Me IMessageIO io, @Default SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(guildDB, SheetKey.FORUM_PROFILES);
        }
        String urlRaw = "https://forum.politicsandwar.com/index.php?/profile/%s-ignore/";

        List<String> header = new ArrayList<>(Arrays.asList(
                "profile",
                "discord",
                "discord_id",
                "nation_id"
        ));
        sheet.setHeader(header);

        DiscordDB discordDB = Locutus.imp().getDiscordDB();

        for (int i = 0; i < 15000; i++) {
            String url = String.format(urlRaw, i);

            try {
                String html = FileUtil.readStringFromURL(PagePriority.FORUM_PAGE, url);
                Document dom = Jsoup.parse(html);
                int nationId = Integer.parseInt(dom.select("strong:matches(Nation ID)").first().parent().nextElementSibling().text());
                String discordId = dom.select("strong:matches(Discord Name)").first().parent().nextElementSibling().text();

//                if (Locutus.imp().getDiscordDB().getUserFromNationId(nationId) != null) continue;

                if (nationId != 0) {
                    String[] split = discordId.split("#");
                    User user = null;
                    if (split.length == 2) {
                        user = Locutus.imp().getDiscordApi().getUserByTag(split[0], split[1]);
                    }
                    if (user == null && !discordId.contains("#")) {
                        List<User> users = Locutus.imp().getDiscordApi().getUsersByName(discordId, true);
                        if (users.size() == 1) {
                            user = users.get(0);
                        }
                    }

                    header.set(0, i + "");
                    header.set(1, discordId);
                    header.set(2, user == null ? "" : user.getId());
                    header.set(3, Integer.toString(nationId));

                    sheet.addRow(header);
                }
            } catch (Throwable ignore) {
            }
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(io.create(), "login_times").send();
        return null;
    }

    //    SyncBounties
    @Command(desc = "Force a fetch and update of bounties from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBounties() throws IOException {
        Locutus.imp().getWarDb().updateBountiesV3();
        return "Done!";
    }


//    SyncWarRooms
    @Command(desc = "Force a fetch and update of war rooms for each guild")
    @RolePermission(value = Roles.MILCOM)
    public String purgeWarRooms( // war room delete_all
            @Me GuildDB db,
            @Me IMessageIO io,
            @Me User user,
            @Arg("Only delete a single channel") @Switch("c") MessageChannel channel) throws IOException {

        WarCategory warCat = db.getWarChannel(true);
        if (channel == null) {
            channel = io instanceof DiscordChannelIO ? ((DiscordChannelIO) io).getChannel() : null;
        }
        if (channel != null) {
            Guild chanGuild = ((GuildMessageChannel) channel).getGuild();
            if (!Roles.MILCOM.has(user, chanGuild)) {
                throw new IllegalArgumentException("Missing " + Roles.MILCOM.toDiscordRoleNameElseInstructions(chanGuild));
            }
        }
        WarCategory.WarRoom room = channel instanceof GuildMessageChannel mC ? WarCategory.getGlobalWarRoom(mC) : null;
        if (channel != null && room == null) {
            throw new IllegalArgumentException("Channel is not a war room");
        }
        if (room != null) {
            room.delete("Deleted by " + DiscordUtil.getFullUsername(user));
            return "Deleted " + channel.getName();
        } else {
            Set<Category> categories = new HashSet<>();
            Iterator<Map.Entry<Integer, WarCategory.WarRoom>> iter = warCat.getWarRoomMap().entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Integer, WarCategory.WarRoom> entry = iter.next();
                TextChannel guildChan = entry.getValue().getChannel(false);
                if (guildChan != null) {
                    Category category = guildChan.getParentCategory();
                    if (category != null) categories.add(category);
                    RateLimitUtil.queue(guildChan.delete());
                }
                iter.remove();
            }
            for (Category category : categories) {
                if (category.getName().startsWith("warcat-")) {
                    RateLimitUtil.queue(category.delete());
                }
            }
            return "Deleted war rooms! See also: " + CM.admin.sync.warrooms.cmd.toSlashMention();
        }
    }
//    SyncTreaties
    @Command(desc = "Force a fetch and update of treaties from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTreaties() throws IOException {
        Locutus.imp().getNationDB().updateTreaties(Event::post);
        return "Updated treaties!";
    }
//    SyncAttacks
    @Command(desc = "Force a fetch and update of attacks from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncAttacks(boolean runAlerts) throws IOException {
        WarUpdateProcessor.checkActiveConflicts();
        Locutus.imp().getWarDb().updateAttacks(runAlerts, Event::post, Settings.USE_V2);
        return "Done!";
    }
//    SyncTrade
    @Command(desc = "Force a fetch and update of trades from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTrade() throws IOException {
        Locutus.imp().getTradeManager().updateTradeList(Event::post);
        return "Done!";
    }
//    SyncUid [all]
    @Command(desc = "Force a fetch and update of uids from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncUid(boolean all) throws IOException {
        if (all) {
            Collection<DBNation> nations = Locutus.imp().getNationDB().getNations().values();
            for (DBNation nation : nations) {
                if (!Locutus.imp().getDiscordDB().getUuids(nation.getNation_id()).isEmpty()) continue;
                BigInteger uid = new GetUid(nation, false).call();
            }
        } else {
            Map<BigInteger, Set<Integer>> map = Locutus.imp().getDiscordDB().getUuidMap();
            for (Map.Entry<BigInteger, Set<Integer>> entry : map.entrySet()) {
                if (entry.getValue().size() <= 1) continue;

                for (int nationId : entry.getValue()) {
                    DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
                    if (nation != null) {
                        new GetUid(nation, false).call();
                    }
                }
            }
        }
        return "Done! See also " + CM.admin.list.multis.cmd.toSlashMention();
    }
//    SyncTaxes
    @Command(desc = "Force a fetch and update of taxes from the api",
    groups = {
            "Alliance to update",
            "Update via sheet tax records",
            "Update via login"
    })
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTaxes(
            @Me GuildDB db, @Me IMessageIO io,
            @Arg(value = "Specify other alliances, instead of the ones registered to this guild", group = 0)
            @Switch("a") DBAlliance alliance,
            @Arg(value = "The timeframe to update")
            @Switch("t") @Timestamp Long timestamp,
            @Arg(value = "Update using values in a spreadsheet\n" +
                    "Deprecated, use the api instead (i.e. no arguments)", group = 1)
            @Switch("s") SpreadSheet sheet_deprecated,
            @Arg(value = "Use the legacy deprecated method to update via login (not recommended)", group = 2)
            @Switch("l") boolean legacy_deprecated
            ) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
        if (legacy_deprecated && sheet_deprecated != null) {
            throw new IllegalArgumentException("Cannot use both `sheet_deprecated` and `legacy_deprecated`");
        }

        Set<Integer> aaIds = alliance != null ? Set.of(alliance.getId()): db.getAllianceIds();
        if (aaIds.size() > 1) {
            throw new IllegalArgumentException("Too many alliances to update (max 1). Please specify an alliance");
        }
        if (aaIds.isEmpty()) {
            throw new IllegalArgumentException("No alliances to update. Please specify an alliance");
        }
        int aaId = aaIds.iterator().next();

        if (sheet_deprecated != null) {
            if (timestamp != null) {
                throw new IllegalArgumentException("The `timestamp` argument is not supported with `sheet_deprecated`");
            }
            return SyncTaxes.updateTaxesLegacy(db, null, aaId);
        }
        if (legacy_deprecated) {
            DBAlliance aa = DBAlliance.get(aaId);
            if (aa == null) {
                throw new IllegalArgumentException("Alliance AA:" + aaId + " is not registered to guild: " + aaId);
            }
            CompletableFuture<IMessageBuilder> msgFuture = (io.sendMessage("Syncing taxes for " + aaId + ". Please wait..."));

            int taxesCount = aa.updateTaxesLegacy(timestamp);

            IMessageBuilder msg = msgFuture.get();
            if (msg != null && msg.getId() > 0) {
                io.delete(msg.getId());
            }
            return "Updated " + taxesCount + " records.\n"
                    + "<" + SyncTaxes.updateTurnGraph(db, aaId) + ">";
        }
        AllianceList aaList = db.getAllianceList();
        if (aaList == null) {
            return "No alliance registered to this guild. See " + GuildKey.ALLIANCE_ID.getCommandMention();
        }
        List<BankDB.TaxDeposit> taxes = aaList.updateTaxes(timestamp);
        return "Updated " + taxes.size() + " records.";
    }

//    SyncMail /mail check
    @Command(desc = "Force a fetch and update of mail for a nation")
    @RolePermission(value = Roles.MAIL)
    public String syncMail(@Me User user, @Me IMessageIO io, @Me DBNation nation, @Default DBNation account) throws IOException {
        if (account != null && account.getId() != nation.getId()) {
            GuildDB db = account.getGuildDB();
            if (db != null) {
                if (!Roles.MAIL.has(user, db.getGuild())) {
                    throw new IllegalArgumentException("Missing " + Roles.MAIL.toDiscordRoleNameElseInstructions(db.getGuild()));
                }
            } else {
                if (!Roles.ADMIN.hasOnRoot(user)) {
                    throw new IllegalArgumentException("Missing " + Roles.ADMIN.toDiscordRoleNameElseInstructions(Locutus.imp().getServer()));
                }
            }
        }
        if (account == null) account = nation;
        new AlertMailTask(account.getAuth(true), io.getIdLong()).run();
        return "Done!";
    }

//    SyncBanks
    @Command(desc = "Force a fetch and update of banks from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncOffshore(DBAlliance alliance) throws IOException {
        OffshoreInstance bank = alliance.getBank();
        if (bank == null) throw new IllegalArgumentException("No bank found for " + alliance + ". Set one with " + CM.offshore.add.cmd.toSlashMention());
        bank.sync(0L, false);
        return "Done!";
    }

    @Command(desc = "View info about trades with a given id")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String tradeId(Set<Integer> ids) {
        List<DBTrade> offers = new ArrayList<>();
        for (int id : ids) {
            DBTrade trade = Locutus.imp().getTradeManager().getTradeDb().getTradeById(id);
            if (trade != null) offers.add(trade);
        }
        return "- " + StringMan.join(offers, "\n- ");
    }

    @Command(desc = "View info about a guild with a given id")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String guildInfo(Guild guild) {
        return guild.getName() + "/" + guild.getIdLong() + "\n" +
                "Owner: " + guild.getOwner() + "\n" +
                "Members: " + StringMan.getString(guild.getMembers());
    }

    @Command(desc = "View meta information about a nation in the bot's database")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String nationMeta(DBNation nation, NationMeta meta) {
        ByteBuffer buf = nation.getMeta(meta);
        if (buf == null) return "No value set.";

        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        buf = ByteBuffer.wrap(arr);

        switch (arr.length) {
            case 0 -> {
                return "" + (buf.get() & 0xFF);
            }
            case 4 -> {
                return "" + (buf.getInt());
            }
            case 8 -> {
                ByteBuffer buf2 = ByteBuffer.wrap(arr);
                return buf.getLong() + "/" + MathMan.format(buf2.getDouble());
            }
            default -> {
                return new String(arr, StandardCharsets.ISO_8859_1);
            }
        }
    }

    @NoFormat
    @Command(desc = "Run a command as another user")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String sudo(@Me Guild guild, @Me IMessageIO io,
                       @Switch("u") User user,
                       @Switch("n") DBNation nation,
                       String command) {
        if (user == null && nation == null) {
            throw new IllegalArgumentException("Specify a user or nation");
        }
        if (user != null && nation != null) {
            throw new IllegalArgumentException("Specify only a user or nation");
        }
        CommandManager2 v2 = Locutus.cmd().getV2();
        if (user != null) {
            v2.run(guild, io, user, command, false, true);
        } else {
            MessageChannel channel = io instanceof DiscordChannelIO dio ? dio.getChannel() : null;
            Message message = io instanceof DiscordChannelIO dio ? dio.getUserMessage() : null;
            LocalValueStore locals = v2.createLocals(null, guild, channel, null, message, io, null);
            locals.addProvider(Key.of(DBNation.class, Me.class), nation);
            v2.run(locals, io, command, false, true);
        }
        return "Done!";
    }

    @NoFormat
    @Command(desc = "Run multiple commands")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String runMultiple(@Me Guild guild, @Me IMessageIO io, @Me User user, @TextArea String commands) {
        commands = commands.replace("\\n", "\n");
        String[] split = commands.split("\\r?\\n" + "[" + Settings.commandPrefix(false) + "|" + "/]");

        for (String cmd : split) {
            Locutus.cmd().getV2().run(guild, io, user, cmd, false, true);
        }
        return "Done!";
    }

    @NoFormat
    @Command(desc = "Run a command as multiple nations")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String sudoNations(@Me GuildDB db, @Me IMessageIO io, NationPlaceholders placeholders, ValueStore store,
                              Set<DBNation> nations, String command) {
        PlaceholderCache<DBNation> cache = new PlaceholderCache<>(nations);
        Function<DBNation, String> formatFunc = placeholders.getFormatFunction(store, command, cache, true);
        StringBuilder response = new StringBuilder();

        long start = System.currentTimeMillis();
        for (DBNation nation : nations) {

            String formattedCmd = formatFunc.apply(nation);
            User nationUser = nation.getUser();
            try {
                Map.Entry<CommandResult, String> result = nation.runCommandInternally(db.getGuild(), nationUser, command);
                response.append(nation.getMarkdownUrl() + ": " + result.getKey() + "\n" + result.getValue() + "\n---\n");
            } catch (Throwable e) {
                response.append(nation.getMarkdownUrl() + ": Error: " + e.getMessage());
            }
            if (-start + (start = System.currentTimeMillis()) > 5000) {
                io.sendMessage(response.toString());
                response.setLength(0);
            }

        }
        if (response.length() > 0) {
            io.sendMessage(response.toString());
        }
        return "Done!";
    }


}