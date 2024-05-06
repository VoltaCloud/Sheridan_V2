package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.conflict.CoalitionSide;
import link.locutus.discord.db.conflict.CtownedFetcher;
import link.locutus.discord.gpt.pw.WikiPagePW;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.wiki.game.PWWikiPage;
import link.locutus.wiki.game.PWWikiUtil;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTopic;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.web.jooby.AwsManager;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.text.Normalizer;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ConflictCommands {
    @Command(desc = "View a conflict's configured information")
    public String info(ConflictManager manager, Conflict conflict, boolean showParticipants) {
        StringBuilder response = new StringBuilder();

        response.append("**__" + conflict.getName() + " - `#" + conflict.getId() + "` - " + conflict.getCategory().name() + "__**\n");
        response.append("wiki: `" + conflict.getWiki() + "`\n");
        response.append("Start: " + DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(conflict.getStartTurn()), null) + "\n");
        response.append("End: " + (conflict.getEndTurn() == Long.MAX_VALUE ? "Ongoing" : DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(conflict.getEndTurn()), null)) + "\n");
        response.append("Casus Belli: `" + conflict.getCasusBelli() + "`\n");
        response.append("Status: `" + conflict.getStatusDesc() + "`\n");

        CoalitionSide col1 = conflict.getSide(true);
        CoalitionSide col2 = conflict.getSide(false);
        List<CoalitionSide> sides = Arrays.asList(col1, col2);
        if (showParticipants) {
            for (CoalitionSide side : sides) {
                response.append("\n**" + col1.getName() + "**\n");
                for (int aaId : side.getAllianceIdsSorted()) {
                    long start = conflict.getStartTurn(aaId);
                    long end = conflict.getEndTurn(aaId);
                    String aaName = manager.getAllianceName(aaId);
                    response.append("- " + MarkupUtil.markdownUrl(aaName, PW.getAllianceUrl(aaId)) + " | ");
                    if (start == conflict.getStartTurn()) {
                        response.append("Initial");
                    } else {
                        response.append(DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(start), null));
                    }
                    response.append(" -> ");
                    if (end >= conflict.getEndTurn()) {
                        response.append("Present");
                    } else {
                        response.append(DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(end), null));
                    }
                    response.append("\n");
                }

            }

        } else {
            for (CoalitionSide side : sides) {
                response.append(side.getName() + ": `" + StringMan.join(side.getAllianceIdsSorted(), ",") + "`\n");
            }
            response.append("Use `showParticipants: True` to list participants");
        }

        return response.toString() + "\n\n<http://wars.locutus.link/conflict?id=" + conflict.getId() + ">";
    }

    @Command(desc = "Sets the wiki page for a conflict")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setWiki(ConflictManager manager, Conflict conflict, String url) throws IOException {
        if (url.startsWith("http")) {
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            url = url.substring(url.lastIndexOf("/") + 1);
        }
        conflict.setWiki(url);
        return "Set wiki to: `" + url + "`. Attempting to load additional wiki information...\n\n" +
                importWikiPage(manager, conflict.getName(), url, false);
    }

    @Command(desc = "Sets the wiki page for a conflict")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setStatus(ConflictManager manager, Conflict conflict, String status) throws IOException {
        conflict.setStatus(status);
        return "Done! Set the status of `" + conflict.getName() + "` to `" + status + "`";
    }

    @Command(desc = "Sets the wiki page for a conflict")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setCB(ConflictManager manager, Conflict conflict, String casus_belli) throws IOException {
        conflict.setCasusBelli(casus_belli);
        return "Done! Set the casus belli of `" + conflict.getName() + "` to `" + casus_belli + "`";
    }

    @Command(desc = "Sets the wiki page for a conflict")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setCategory(ConflictManager manager, Conflict conflict, ConflictCategory category) throws IOException {
        conflict.setCategory(category);
        return "Done! Set the category of `" + conflict.getName() + "` to `" + category + "`";
    }

    @Command(desc = "Pushes conflict data to the AWS bucket for the website")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncConflictData(ConflictManager manager, @Default Set<Conflict> conflicts, @Switch("g") boolean includeGraphs, @Switch("w") boolean reloadWars) {
        WebRoot webRoot = WebRoot.getInstance();
        AwsManager aws = webRoot.getAws();
        if (aws == null) {
            throw new IllegalArgumentException("AWS is not configured in `config.yaml`");
        }
        if (reloadWars) {
            manager.loadConflictWars(conflicts, true);
        }
        if (conflicts != null) {
            List<String> urls = new ArrayList<>();
            for (Conflict conflict : conflicts) {
                String key = "conflicts/" + conflict.getId() + ".gzip";
                byte[] value = conflict.getPsonGzip(manager);
                aws.putObject(key, value);
                urls.add(aws.getLink(key));

                if (includeGraphs) {
                    String graphKey = "conflicts/graphs/" + conflict.getId() + ".gzip";
                    byte[] graphValue = conflict.getGraphPsonGzip(manager);
                    aws.putObject(graphKey, graphValue);
                    urls.add(aws.getLink(graphKey));
                }
            }
            return "Done! See:\n- <" + StringMan.join(urls, ">\n- <") + ">";
        }
        String key = "conflicts/index.gzip";
        byte[] value = manager.getPsonGzip();
        aws.putObject(key, value);
        return "Done! See: <" + aws.getLink(key) + ">";
    }

    @Command(desc = "Delete a conflict from the database\n" +
            "Does not push changes to the website")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String deleteConflict(ConflictManager manager, @Me IMessageIO io, @Me JSONObject command, Conflict conflict, @Switch("f") boolean confirm) {
        if (!confirm) {
            String title = "Delete conflict " + conflict.getName();
            StringBuilder body = new StringBuilder();
            body.append("ID: `" + conflict.getId() + "`\n");
            body.append("Start: `" + TimeUtil.turnsToTime(conflict.getStartTurn()) + "`\n");
            body.append("End: `" + (conflict.getEndTurn() == Long.MAX_VALUE ? "Ongoing..." : TimeUtil.turnsToTime(conflict.getEndTurn())) + "`\n");
            body.append("Col1: `" + conflict.getCoalition2Obj().stream().map(DBAlliance::getName).collect(Collectors.joining(",")) + "`\n");
            body.append("Col2: `" + conflict.getCoalition2Obj().stream().map(DBAlliance::getName).collect(Collectors.joining(",")) + "`\n");
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        manager.deleteConflict(conflict);
        return "Deleted conflict: #" + conflict.getId() + " - `" + conflict.getName() + "`" +
                "\nNote: this does not push the data to the site";
    }

    @Command(desc = "Get a list of the conflicts in the database")
    public String listConflicts(@Me IMessageIO io, @Me JSONObject command, ConflictManager manager, @Switch("i") boolean includeInactive) {
        Map<Integer, Conflict> conflicts = ArrayUtil.sortMap(manager.getConflictMap(), (o1, o2) -> {
            if (o1.getEndTurn() == Long.MAX_VALUE || o2.getEndTurn() == Long.MAX_VALUE) {
                return Long.compare(o2.getEndTurn(), o1.getEndTurn());
            }
            return Long.compare(o2.getStartTurn(), o1.getStartTurn());
        });
        if (!includeInactive) {
            conflicts.entrySet().removeIf(entry -> entry.getValue().getEndTurn() != Long.MAX_VALUE);
        }
        if (conflicts.isEmpty()) {
            if (includeInactive) {
                return "No conflicts";
            }
            io.create().confirmation("No active conflicts",
                    "Press `list inactive` to show inactive conflicts",
                    command,
                    "includeInactive", "list inactive").send();
            return null;
        }
        StringBuilder response = new StringBuilder();
        // Name - Start date - End date (bold underline
        // - Coalition1:
        // - Coalition2:
        for (Map.Entry<Integer, Conflict> entry : conflicts.entrySet()) {
            Conflict conflict = entry.getValue();
            response.append("**# " + conflict.getId() + ": ").append(conflict.getName()).append("** - ")
                    .append(TimeUtil.DD_MM_YYYY.format(TimeUtil.getTimeFromTurn(conflict.getStartTurn()))).append(" - ");
            if (conflict.getEndTurn() == Long.MAX_VALUE) {
                response.append("Present");
            } else {
                response.append(TimeUtil.DD_MM_YYYY.format(TimeUtil.getTimeFromTurn(conflict.getEndTurn())));
            }
            response.append("\n");
            response.append("- Coalition 1: ").append(conflict.getCoalition1().stream().map(f -> PW.getName(f, true)).collect(Collectors.joining(","))).append("\n");
            response.append("- Coalition 2: ").append(conflict.getCoalition2().stream().map(f -> PW.getName(f, true)).collect(Collectors.joining(","))).append("\n");
            response.append("---\n");
        }
        return response.toString();
    }

    @Command(desc = "Set the end date for a conflict\n" +
            "Use a value of `-1` to specify no end date\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setConflictEnd(ConflictManager manager, @Me JSONObject command, Conflict conflict, @Timestamp long time, @Arg("Only set the end date for a single alliance") @Switch("a") DBAlliance alliance) {
        String timeStr = command.getString("time");
        if (MathMan.isInteger(timeStr) && Long.parseLong(timeStr) < 0) {
            time = Long.MAX_VALUE;
        }
        if (alliance != null) {
            Boolean side = conflict.isSide(alliance.getId());
            if (side == null) {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is not in the conflict");
            }
            conflict.addParticipant(alliance.getAlliance_id(), side, null, time);
            return "Set `" + conflict.getName() + "` end to " + TimeUtil.DD_MM_YYYY.format(time) + " for " + alliance.getMarkdownUrl();
        }
        conflict.setEnd(time);
        return "Set `" + conflict.getName() + "` end to " + TimeUtil.DD_MM_YYYY.format(time) +
                "\nNote: this does not push the data to the site";
    }

    @Command(desc = "Set the start date for a conflict\n" +
            "Use a value of `-1` to specify no start date (if prividing an alliance)\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setConflictStart(ConflictManager manager, @Me JSONObject command, Conflict conflict, @Timestamp long time, @Switch("a") DBAlliance alliance) {
        String timeStr = command.getString("time");
        if (MathMan.isInteger(timeStr) && Long.parseLong(timeStr) < 0) {
            if (alliance == null) {
                throw new IllegalArgumentException("Cannot set start date to NULL without specifying an alliance");
            }
            time = Long.MAX_VALUE;
        }
        if (alliance != null) {
            Boolean side = conflict.isSide(alliance.getId());
            if (side == null) {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is not in the conflict");
            }
            conflict.addParticipant(alliance.getAlliance_id(), side, time, null);
            return "Set `" + conflict.getName() + "` start to " + TimeUtil.DD_MM_YYYY.format(time) + " for " + alliance.getMarkdownUrl();
        }
        conflict.setStart(time);
        return "Set `" + conflict.getName() + "` start to " + TimeUtil.DD_MM_YYYY.format(time) +
                "\nNote: this does not push the data to the site";
    }

    @Command(desc = "Set the name of a conflict")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setConflictName(ConflictManager manager, Conflict conflict, String name, @Switch("col1") boolean isCoalition1, @Switch("col2") boolean isCoalition2) {
        if (isCoalition1 && isCoalition2) {
            throw new IllegalArgumentException("Cannot specify both `isCoalition1` and `isCoalition2`");
        }
        if (!name.matches("[a-zA-Z0-9_. ]+")) {
            throw new IllegalArgumentException("Conflict name must be alphanumeric (`" + name + "`)");
        }
        if (MathMan.isInteger(name)) {
            throw new IllegalArgumentException("Conflict name cannot be a number (`" + name + "`)");
        }
        String previousName = isCoalition1 ? conflict.getSide(true).getName() : isCoalition2 ? conflict.getSide(false).getName() : conflict.getName();
        String sideName;
        if (isCoalition1) {
            sideName = "coalition 1";
            conflict.setName(name, true);
        } else if (isCoalition2) {
            sideName = "coalition 2";
            conflict.setName(name, false);
        } else {
            sideName = "conflict";
            conflict.setName(name);
        }
        return "Changed " + sideName + " name `" + previousName  + "` => `" + name + "`" +
                "\nNote: this does not push the data to the site";
    }

    private int getFighting(DBAlliance alliance, Set<DBAlliance> enemyCoalition) {
        return (int) alliance.getActiveWars().stream().filter(war -> {
            int defAllianceId = war.getAttacker_aa() == alliance.getId() ? war.getDefender_aa() : war.getAttacker_aa();
            if (!enemyCoalition.contains(DBAlliance.getOrCreate(defAllianceId))) return false;
            DBNation attacker = war.getNation(true);
            DBNation defender = war.getNation(false);
            if (attacker == null || defender == null) return false;
            if (defender.active_m() > 2880 || defender.getPositionEnum() == Rank.APPLICANT)
                return false;
            return true;
        }).count();
    }

    @Command(desc = "Manually create an ongoing conflict between two coalitions")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String addConflict(ConflictManager manager, @Me User user, ConflictCategory category, Set<DBAlliance> coalition1, Set<DBAlliance> coalition2, @Switch("n") String conflictName, @Switch("d")@Timestamp Long start) {
        if (conflictName != null) {
            if (MathMan.isInteger(conflictName)) {
                throw new IllegalArgumentException("Conflict name cannot be a number (`" + conflictName + "`)");
            }
            if (!conflictName.matches("[a-zA-Z0-9_. ]+")) {
                throw new IllegalArgumentException("Conflict name must be alphanumeric (`" + conflictName + "`)");
            }
        }
        boolean isAdmin = Roles.ADMIN.hasOnRoot(user);
        if (!isAdmin) {
            if (conflictName != null) {
                throw new IllegalArgumentException("Cannot specify `conflictName` " + Roles.ADMIN.toDiscordRoleNameElseInstructions(Locutus.imp().getServer()));
            }
            if (start != null && (start > System.currentTimeMillis() || start < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2) - 1000)) {
                throw new IllegalArgumentException("Date for `start` must be within 2 days");
            }
            // coalition1 and coalition 2 cannot have overlap
            if (coalition1.stream().filter(coalition2::contains).count() > 0) {
                throw new IllegalArgumentException("Cannot have overlap between `coalition1` and `coalition2`");
            }
            // each alliance must have at least 1 war with an enemy alliance
            int totalWars = 0;
            for (DBAlliance aa : coalition1) {
                int wars = getFighting(aa, coalition2);
                if (wars == 0) {
                    throw new IllegalArgumentException("Alliance " + aa.getMarkdownUrl() + " does not have any active wars with an alliance in `coalition2`");
                }
                totalWars += wars;
            }
            for (DBAlliance aa : coalition2) {
                if (getFighting(aa, coalition1) == 0) {
                    throw new IllegalArgumentException("Alliance " + aa.getMarkdownUrl() + " does not have any active wars with an alliance in `coalition1`");
                }
            }
            // ensure
            if (totalWars <= 15) {
                throw new IllegalArgumentException("Total wars between `coalition1` and `coalition2` must be greater than 15");
            }
        }
        if (conflictName == null) {
            DBAlliance largest1 = coalition1.stream().max(Comparator.comparingDouble(DBAlliance::getScore)).orElse(null);
            DBAlliance largest2 = coalition2.stream().max(Comparator.comparingDouble(DBAlliance::getScore)).orElse(null);
            conflictName = largest1.getName() + " sphere VS " + largest2.getName() + " sphere ";
            if (manager.getConflict(conflictName) != null) {
                conflictName = conflictName + "(" + Calendar.getInstance().get(Calendar.YEAR) + ")";
            }
        }
        if (manager.getConflict(conflictName) != null) {
            throw new IllegalArgumentException("Conflict with name `" + conflictName + "` already exists");
        }
        Conflict conflict = manager.addConflict(conflictName, category, "Coalition 1", "Coalition 2", "", "", "", TimeUtil.getTurn(start), Long.MAX_VALUE);
        StringBuilder response = new StringBuilder();
        response.append("Created conflict `" + conflictName + "`\n");
        // add coalitions
        for (DBAlliance alliance : coalition1) conflict.addParticipant(alliance.getId(), true, null, null);
        for (DBAlliance alliance : coalition2) conflict.addParticipant(alliance.getId(), false, null, null);
        response.append("- Coalition1: `").append(coalition1.stream().map(f -> f.getName()).collect(Collectors.joining(","))).append("`\n");
        response.append("- Coalition2: `").append(coalition2.stream().map(f -> f.getName()).collect(Collectors.joining(",")));
        return response.toString() +
                "\nTo set the end date, use:" +
                CM.conflict.edit.end.cmd.toSlashMention() +
                "\nNote: this does not push the data to the site";
    }

    @Command(desc = "Remove a set of alliances from a conflict\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String removeCoalition(Conflict conflict, Set<DBAlliance> alliances) {
        Set<DBAlliance> notRemoved = new LinkedHashSet<>();
        for (DBAlliance alliance : alliances) {
            if (conflict.getCoalition1().contains(alliance.getId()) || conflict.getCoalition2().contains(alliance.getId())) {
                conflict.removeParticipant(alliance.getId());
            } else {
                notRemoved.add(alliance);
            }
        }
        List<String> errors = new ArrayList<>();
        if (notRemoved.size() > 0) {
            if (notRemoved.size() < 10) {
                errors.add("The alliances you specified " + StringMan.join(notRemoved.stream().map(DBAlliance::getName).collect(Collectors.toList()), ", ") + " are not in the conflict");
            } else {
                errors.add(notRemoved.size() + " alliances you specified are not in the conflict");
            }
            if (notRemoved.size() == alliances.size()) {
                throw new IllegalArgumentException(StringMan.join(errors, "\n"));
            }
        }
        String msg = "Removed " + (alliances.size() - notRemoved.size()) + " alliances from the conflict";
        if (!errors.isEmpty()) {
            msg += "\n- " + StringMan.join(errors, "\n- ");
        }
        return msg + "\nNote: this does not push the data to the site";
    }

    @Command(desc = "Add a set of alliances to a conflict\n" +
            "This does not push the data to the site")
    public String addCoalition(@Me User user, Conflict conflict, Set<DBAlliance> alliances, @Switch("col1") boolean isCoalition1, @Switch("col2") boolean isCoalition2) {
        boolean hasAdmin = Roles.ADMIN.hasOnRoot(user);
        if (isCoalition1 && isCoalition2) {
            throw new IllegalArgumentException("Cannot specify both `isCoalition1` and `isCoalition2`");
        }
        Set<DBAlliance> addCol1 = new HashSet<>();
        Set<DBAlliance> addCol2 = new HashSet<>();

        for (DBAlliance alliance : alliances) {
            if (conflict.getCoalition1().contains(alliance.getId())) {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is already in coalition 1");
            }
            if (conflict.getCoalition2().contains(alliance.getId())) {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is already in coalition 2");
            }
            if (hasAdmin) {
                if (isCoalition1) {
                    addCol1.add(alliance);
                    continue;
                } else if (isCoalition2) {
                    addCol2.add(alliance);
                    continue;
                }
            }

            Map<Integer, Treaty> treaties = alliance.getTreaties(TreatyType::isMandatoryDefensive, false);
            boolean hasTreatyCol1 = false;
            boolean hasTreatyCol2 = false;
            for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
                if (conflict.getCoalition1().contains(entry.getKey())) {
                    hasTreatyCol1 = true;
                } else if (conflict.getCoalition2().contains(entry.getKey())) {
                    hasTreatyCol2 = true;
                }
            }
            int fightingCol1 = getFighting(alliance, conflict.getCoalition1Obj());
            int fightingCol2 = getFighting(alliance, conflict.getCoalition2Obj());

            if (hasTreatyCol1 && !hasTreatyCol2 && fightingCol1 == 0) {
                if (isCoalition2) throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " has a treaty with a member of coalition 1");
                addCol1.add(alliance);
            } else if (hasTreatyCol2 && !hasTreatyCol1 && fightingCol2 == 0) {
                if (isCoalition1) throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " has a treaty with a member of coalition 2");
                addCol2.add(alliance);
            } else if (fightingCol1 > 0) {
                if (fightingCol2 > 0) {
                    throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is fighting both coalitions");
                }
                if (hasTreatyCol1 && !hasTreatyCol2) {
                    throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " has a treaty with a member of coalition 1");
                }
                if (isCoalition1) throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is fighting coalition 1");
                addCol2.add(alliance);
            } else if (fightingCol2 > 0) {
                if (isCoalition2) throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is fighting coalition 2");
                if (hasTreatyCol2 && !hasTreatyCol1) {
                    throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " has a treaty with a member of coalition 2");
                }
                addCol1.add(alliance);
            } else if (hasAdmin) {
                throw new IllegalArgumentException("Please specify either `isCoalition1` or `isCoalition2`");
            } else {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " does not have active wars with the conflict participants. Please contact an administrator");
            }
        }
        for (DBAlliance aa : addCol1) {
            conflict.addParticipant(aa.getId(), true, null, null);
        }
        for (DBAlliance aa : addCol2) {
            conflict.addParticipant(aa.getId(), false, null, null);
        }
        return "Added " + addCol1.size() + " alliances to coalition 1 and " + addCol2.size() + " alliances to coalition 2\n" +
                "Note: this does not push the data to the site";
    }

    @Command(desc = "Import ctowned conflicts into the database\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importCtowned(ConflictManager manager,
                                @Default("true") @Arg("If the cached version of the site is used")
                                boolean useCache) throws SQLException, IOException, ParseException, ClassNotFoundException {
        CtownedFetcher fetcher = new CtownedFetcher(manager);
        fetcher.loadCtownedConflicts(useCache, ConflictCategory.NON_MICRO, "conflicts", "conflicts");
        fetcher.loadCtownedConflicts(useCache, ConflictCategory.MICRO, "conflicts/micros", "conflicts-micros");
        return "Done!\nNote: this does not push the data to the site";
    }

    @Command(desc = "Import alliance names (to match with the ids of deleted alliances)\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importAllianceNames(ConflictManager manager) throws IOException, ParseException {
        manager.saveDataCsvAllianceNames();
        for (Map.Entry<String, Integer> entry : PWWikiUtil.getWikiAllianceIds().entrySet()) {
            if (manager.getAllianceName(entry.getValue()) != null) continue;
            if (manager.getAllianceId(entry.getKey(), Long.MAX_VALUE) != null) continue;
            manager.addLegacyName(entry.getValue(), entry.getKey(), 0);
        }
        return "Done!\nNote: this does not push the data to the site";
    }

    @Command(desc = "Import a wiki page as a conflict\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importWikiPage(ConflictManager manager, String name, @Default String url, @Default("true") boolean useCache) throws IOException {
        if (name.contains("http")) return "Please specify the name of the wiki page, not the URL for `name`";
        if (url == null) {
            url = Normalizer.normalize(name.replace(" ", "_"), Normalizer.Form.NFKC);
            url = URLEncoder.encode(url, StandardCharsets.UTF_8);
        } else {
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            url = url.substring(url.lastIndexOf("/") + 1);
        }
        Map<String, String> errors = new HashMap<>();
        Conflict conflict = PWWikiUtil.loadWikiConflict(name, url, errors, useCache);
        StringBuilder response = new StringBuilder();
        if (conflict == null) {
            response.append("Failed to load conflict `" + name + "` with url `https://politicsandwar.com/wiki/" + url + "`\n");
        } else {
            if (conflict.getStartTurn() < TimeUtil.getTurn(1577836800000L)) {
                response.append("Conflict `" + name + "` before the war cutoff date of " + DiscordUtil.timestamp(1577836800000L, null) + "\n");
            } else {
                loadWikiConflicts(manager, List.of(conflict));
                response.append("Loaded conflict `" + name + "` with url `https://politicsandwar.com/wiki/" + url + "`\n");
                response.append("See: " +
                        CM.conflict.info.cmd.toSlashMention() +
                        "\n\n");
            }
        }
        if (!errors.isEmpty()) {
            response.append("Errors: " + StringMan.join(errors.values(), "\n"));
        }
        return response.toString() + "\n\nNote: this does not push the data to the site";
    }

    private String loadWikiConflicts(ConflictManager manager, List<Conflict> conflicts) {
        Map<String, Set<Conflict>> conflictsByWiki = manager.getConflictMap().values().stream().collect(Collectors.groupingBy(Conflict::getWiki, Collectors.toSet()));
        // Cutoff date
        conflicts.removeIf(f -> f.getStartTurn() < TimeUtil.getTurn(1577836800000L));
        // print all ongoing conflicts
        for (Conflict conflict : conflicts) {
            Conflict original = conflict;
            Set<Conflict> existingSet = conflictsByWiki.get(conflict.getWiki());
            if (existingSet == null) {
                conflict = manager.addConflict(conflict.getName(), conflict.getCategory(), conflict.getSide(true).getName(), conflict.getSide(false).getName(), conflict.getWiki(), conflict.getCasusBelli(), conflict.getStatusDesc(), conflict.getStartTurn(), conflict.getEndTurn());
                existingSet = Set.of(conflict);
            }
            for (Conflict existing : existingSet) {
                if (existing.getStatusDesc().isEmpty()) {
                    existing.setStatus(conflict.getStatusDesc());
                }
                if (existing.getCasusBelli().isEmpty()) {
                    existing.setCasusBelli(conflict.getCasusBelli());
                }
                if (existingSet.size() == 1 && !conflict.getName().equalsIgnoreCase(existing.getName())) {
                    existing.setName(conflict.getName());
                }
                if (existing.getAnnouncement().isEmpty() && !original.getAnnouncement().isEmpty()) {
                    for (Map.Entry<String, DBTopic> entry : original.getAnnouncement().entrySet()) {
                        existing.addAnnouncement(entry.getKey(), entry.getValue(), true);
                    }
                }
                if (existing.getCategory() != conflict.getCategory()) {
                    existing.setCategory(conflict.getCategory());
                }
                if (existing.getAllianceIds().isEmpty()) {
                    for (int aaId : original.getCoalition1()) existing.addParticipant(aaId, true, null, null);
                    for (int aaId : original.getCoalition2()) existing.addParticipant(aaId, false, null, null);
                }
            }
        }
        return "Done!\nNote: this does not push the data to the site";
    }

    @Command(desc = "Import all wiki pages as conflicts\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importWikiAll(ConflictManager manager, @Default("true") boolean useCache) throws IOException {
        Map<String, String> errors = new LinkedHashMap<>();
        List<Conflict> conflicts = PWWikiUtil.loadWikiConflicts(errors, useCache);

        return loadWikiConflicts(manager, conflicts);
    }


    @Command(desc = "Recalculate the graph data for a set of conflicts\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String recalculateGraphData(ConflictManager manager, Set<Conflict> conflicts) {
        manager.loadConflictWars(conflicts, true);
        return "Done!\nNote: this does not push the data to the site";
    }

    @Command(desc = "Bulk import conflict data from multiple sources\n" +
            "Including ctowned, wiki, graph data, alliance names or ALL\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importConflictData(ConflictManager manager, @Switch("c") boolean ctowned, @Switch("g") Set<Conflict> graphData, @Switch("a") boolean allianceNames, @Switch("w") boolean wiki, @Switch("s") boolean all) throws IOException, SQLException, ClassNotFoundException, ParseException {
        boolean loadGraphData = false;
        if (all) {
            Locutus.imp().getWarDb().loadWarCityCountsLegacy();
            allianceNames = true;
            ctowned = true;
            wiki = true;
            loadGraphData = true;
        }
        if (allianceNames) {
            importAllianceNames(manager);
        }
        if (ctowned) {
            importCtowned(manager, true);
        }
        if (wiki) {
            importWikiAll(manager, true);
        }
        if (loadGraphData && graphData == null) {
            graphData = new LinkedHashSet<>(manager.getConflictMap().values());
            recalculateGraphData(manager, graphData);
        }
        if (graphData != null) {
            for (Conflict conflict : graphData) {
                System.out.println("Updating graphs " + conflict.getName() + " | " + conflict.getId());
                conflict.updateGraphsLegacy(manager);
            }
        }
        return "Done!\nNote: this does not push the data to the site";
    }
}
