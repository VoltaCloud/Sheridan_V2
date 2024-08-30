package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.file.DataFile;
import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.AlliancePlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.rankings.builder.GroupedRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.*;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.battle.sim.AttackTypeNode;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.offshore.test.IAChannel;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.nation.MultiReport;
import link.locutus.discord.util.task.roles.AutoRoleInfo;
import link.locutus.discord.util.task.roles.IAutoRoleTask;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static link.locutus.discord.apiv1.enums.NationColor.COLOR_REVENUE_CAP;
import static link.locutus.discord.util.math.ArrayUtil.memorize;
import static org.example.jooq.bank.Tables.TRANSACTIONS_2;

public class UtilityCommands {
    @Command(desc = "List the color blocs and their revenue\n" +
            "Optionally switch nations or alliances to a color to view potential revenue changes")
    public String calculateColorRevenue(@Default Set<DBNation> set_aqua,
                                        @Default Set<DBNation> set_black,
                                        @Default Set<DBNation> set_blue,
                                        @Default Set<DBNation> set_brown,
                                        @Default Set<DBNation> set_green,
                                        @Default Set<DBNation> set_lime,
                                        @Default Set<DBNation> set_maroon,
                                        @Default Set<DBNation> set_olive,
                                        @Default Set<DBNation> set_orange,
                                        @Default Set<DBNation> set_pink,
                                        @Default Set<DBNation> set_purple,
                                        @Default Set<DBNation> set_red,
                                        @Default Set<DBNation> set_white,
                                        @Default Set<DBNation> set_yellow,
                                        @Default Set<DBNation> set_gray_or_beige
    ) {
        Map<NationColor, Set<DBNation>> changeColors = new HashMap<>();
        changeColors.put(NationColor.AQUA, set_aqua);
        changeColors.put(NationColor.BLACK, set_black);
        changeColors.put(NationColor.BLUE, set_blue);
        changeColors.put(NationColor.BROWN, set_brown);
        changeColors.put(NationColor.GREEN, set_green);
        changeColors.put(NationColor.LIME, set_lime);
        changeColors.put(NationColor.MAROON, set_maroon);
        changeColors.put(NationColor.OLIVE, set_olive);
        changeColors.put(NationColor.ORANGE, set_orange);
        changeColors.put(NationColor.PINK, set_pink);
        changeColors.put(NationColor.PURPLE, set_purple);
        changeColors.put(NationColor.RED, set_red);
        changeColors.put(NationColor.WHITE, set_white);
        changeColors.put(NationColor.YELLOW, set_yellow);
        changeColors.put(NationColor.GRAY, set_gray_or_beige);

        Map<DBNation, NationColor> newColors = new HashMap<>();

        for (Map.Entry<NationColor, Set<DBNation>> entry : changeColors.entrySet()) {
            if (entry.getValue() == null) continue;
            for (DBNation nation : entry.getValue()) {
                if (nation.getColor() == entry.getKey() || ((nation.isGray() || nation.isBeige()) && entry.getKey() == NationColor.GRAY)) continue;

                newColors.put(nation, entry.getKey());
            }
        }

        Map<NationColor, Integer> oldRevenueMap = new LinkedHashMap<>();
        Map<NationColor, Double> revenueTotalMap = new LinkedHashMap<>();
        Map<NationColor, Integer> numNationsMap = new LinkedHashMap<>();
        Map<NationColor, Integer> oldNumNationsMap = new LinkedHashMap<>();

        Set<NationColor> requiresScaling = new HashSet<>();
        List<Map.Entry<Double, Integer>> scalingCounts = new ArrayList<>();

        for (NationColor color : NationColor.values()) {
            Set<DBNation> oldNations = Locutus.imp().getNationDB().getNationsMatching(f -> f.getVm_turns() == 0 && f.getColor() == color);
            boolean isChanged = false;
            for (Map.Entry<DBNation, NationColor> entry : newColors.entrySet()) {
                if (entry.getKey().getColor() == color || entry.getValue() == color) {
                    isChanged = true;
                    break;
                }
            }

            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(f -> f.getVm_turns() == 0 && newColors.getOrDefault(f, f.getColor()) == color);

            oldNumNationsMap.put(color, oldNations.size());
            if (isChanged) numNationsMap.put(color, nations.size());
            else numNationsMap.put(color, oldNations.size());

            int oldColorRev = color.getTurnBonus();

            oldRevenueMap.put(color, oldColorRev);

            if (color == NationColor.BEIGE || color == NationColor.GRAY) {
                continue;
            }
            if (!newColors.isEmpty()) {
                Supplier<Double> oldRevenueTotal = memorize(() -> ResourceType.convertedTotal(new SimpleNationList(oldNations).getRevenue()));
                Supplier<Double> newRevenueTotal = memorize(() -> ResourceType.convertedTotal(new SimpleNationList(nations).getRevenue()));

                double scalingFactor = -1;
                if (oldColorRev > 0 && oldColorRev < COLOR_REVENUE_CAP) {
                    double oldRevTotalEstimate = oldRevenueTotal.get();
                    double oldRevTotalExact = ((double) oldColorRev) * oldNations.size() * oldNations.size();

                    scalingFactor = (1d / oldRevTotalEstimate) * oldRevTotalExact;
                    scalingCounts.add(new AbstractMap.SimpleEntry<>(scalingFactor, nations.size()));
                }
                if (isChanged) {
                    double newColorRev = newRevenueTotal.get();
                    if (scalingFactor != -1) {
                        newColorRev = newColorRev * scalingFactor;
                    } else {
                        requiresScaling.add(color);
                    }
                    revenueTotalMap.put(color, newColorRev);
                }
            }
        }

        // calculate scaling factor
        double scalingFactor = 1;
        if (!scalingCounts.isEmpty() && !requiresScaling.isEmpty()) {
            double totalValue = 0;
            long num = 0L;
            for (Map.Entry<Double, Integer> entry : scalingCounts) {
                totalValue += entry.getKey() * entry.getValue();
                num += entry.getValue();
            }
            scalingFactor = totalValue / num;
        }

        StringBuilder lines = new StringBuilder();
        for (NationColor color : NationColor.values) {
            lines.append(color + " | ");

            int numNations = numNationsMap.get(color);
            int oldNumNations = oldNumNationsMap.get(color);
            lines.append("nations: " + MathMan.format(numNations));
            if (oldNumNations != numNations) {
                String signSym = (numNations > oldNumNations) ? "+" : "-";
                lines.append(" (").append(signSym).append(MathMan.format(Math.abs(numNations - oldNumNations)) + ")");
            }

            int oldTurnIncome = oldRevenueMap.get(color);
            int newTurnIncome = oldTurnIncome;

            Double revenueTotal = revenueTotalMap.get(color);
            if (revenueTotal != null) {
                if (requiresScaling.contains(color)) {
                    revenueTotal *= scalingFactor;
                }
                newTurnIncome = (int) Math.round(revenueTotal / (numNations * numNations));
            }
            newTurnIncome = Math.max(0, Math.min(COLOR_REVENUE_CAP, newTurnIncome));
            lines.append(" | bonus: " + newTurnIncome);
            if (oldTurnIncome != newTurnIncome) {
                String signSym = (newTurnIncome > oldTurnIncome) ? "+" : "-";
                lines.append(" (").append(signSym).append(MathMan.format(Math.abs(newTurnIncome - oldTurnIncome)) + ")");
            }
            lines.append("\n");
        }
        return lines.toString();
    }

    @Command(desc = "list channels")
    @RolePermission(Roles.ADMIN)
    public String channelCount(@Me IMessageIO channel, @Me Guild guild) {
        StringBuilder channelList = new StringBuilder();

        List<Category> categories = guild.getCategories();
        for (Category category : categories) {
            channelList.append(category.getName() + "\n");

            for (GuildChannel catChannel : category.getChannels()) {
                String prefix = "+ ";
                if (catChannel instanceof VoiceChannel) {
                    prefix = "\uD83D\uDD0A ";
                }
                channelList.append(prefix + catChannel.getName() + "\n");
            }

            channelList.append("\n");
        }

        channel.create().file(guild.getChannels().size() + "/500 channels", channelList.toString()).send();
        return null;
    }

    @Command(desc = "Find potential offshores used by an alliance")
    @RolePermission(Roles.ECON)
    public String findOffshore(@Me IMessageIO channel, @Me JSONObject command, DBAlliance alliance, @Default @Timestamp Long cutoffMs) {
        if (cutoffMs == null) cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(200);

        List<Transaction2> transactions = Locutus.imp().getBankDB().getToNationTransactions(cutoffMs);
        long now = System.currentTimeMillis();
        transactions.removeIf(t -> {
            DBNation nation = DBNation.getById((int) t.getReceiver());
            return nation == null || t.getDate() > now || t.getSender() == alliance.getAlliance_id() || nation.getAlliance_id() != alliance.getAlliance_id() || (t.note != null && t.note.contains("defeated"));
        });
        Map<Integer, Integer> numTransactions = new HashMap<>();
        Map<Integer, Double> valueTransferred = new HashMap<>();

        for (Transaction2 t : transactions) {
            numTransactions.put((int) t.getSender(), numTransactions.getOrDefault((int) t.getSender(), 0) + 1);

            double value = ResourceType.convertedTotal(t.resources);

            valueTransferred.put((int) t.getSender(), valueTransferred.getOrDefault((int) t.getSender(), 0d) + value);
        }



        new SummedMapRankBuilder<>(numTransactions).sort().name(new Function<Map.Entry<Integer, Integer>, String>() {
            @Override
            public String apply(Map.Entry<Integer, Integer> e) {
                return PW.getName(e.getKey(), true) + ": " + e.getValue() + " | $" + MathMan.format(valueTransferred.get(e.getKey()));
            }
        }).build(channel, command, "Potential offshores", true);

        return null;
    }

//    @Command(desc = "See who was online at the time of a spy op (UTC)")
//    @RolePermission(Roles.MEMBER)
//    @WhitelistPermission
//    public String findSpyOp(@Me DBNation me, String times, int defenderSpies, @Default DBNation defender) {
//        if (defender == null) defender = me;
//
//        Set<Integer> ids = new HashSet<>();
//        Map<DBSpyUpdate, Long> updatesTmp = new HashMap<>();
//        long interval = TimeUnit.MINUTES.toMillis(3);
//
//        for (String timeStr : times.split(",")) {
//            long timestamp = TimeUtil.parseDate(TimeUtil.MMDD_HH_MM_A, timeStr, true);
//            List<DBSpyUpdate> updates = Locutus.imp().getNationDB().getSpyActivity(timestamp, interval);
//            for (DBSpyUpdate update : updates) {
////                nations.put(update.nation_id, nations.getOrDefault(update.nation_id, 0) + 1);
//                DBNation nation = DBNation.getById(update.nation_id);
//                if (nation == null) continue;
//                if (!defender.isInSpyRange(nation)) continue;
//
//                if (ids.contains(update.nation_id)) continue;
//                ids.add(update.nation_id);
//                updatesTmp.put(update, Math.abs(timestamp - update.timestamp));
//
//            }
//        }
//
//        if (updatesTmp.isEmpty()) return "No results (0)";
//
//        Map<DBNation, Map.Entry<Double, String>> allOdds = new HashMap<>();
//
//        for (Map.Entry<DBSpyUpdate, Long> entry : updatesTmp.entrySet()) {
//            DBSpyUpdate update = entry.getKey();
//            long diff = entry.getValue();
////            if (update.spies <= 0) continue;
////            if (update.change == 0) continue;
//
//            DBNation attacker = Locutus.imp().getNationDB().getNation(update.nation_id);
//            if (attacker == null || (defender != null && !attacker.isInSpyRange(defender)) || attacker.getNation_id() == defender.getNation_id()) continue;
//
//            int spiesUsed = update.spies;
//
//
//            int safety = 3;
//            int uncertainty = -1;
//            boolean foundOp = false;
//            boolean spySatellite = Projects.SPY_SATELLITE.hasBit(update.projects);
//            boolean intelligence = Projects.INTELLIGENCE_AGENCY.hasBit(update.projects);
//
//            if (spiesUsed == -1) spiesUsed = attacker.getSpies();
//
//            double odds = SpyCount.getOdds(spiesUsed, defenderSpies, safety, SpyCount.Operation.SPIES, defender);
//            if (spySatellite) odds = Math.min(100, odds * 1.2);
////            if (odds < 10) continue;
//
//            double ratio = odds;
//
//            int numOps = (int) Math.ceil((double) spiesUsed / attacker.getSpies());
//
//            if (!foundOp) {
//                ratio -= ratio * 0.1 * Math.abs((double) diff / interval);
//
//                ratio *= 0.1;
//
//                if (attacker.getPosition() <= 1) ratio *= 0.1;
//            } else {
//                ratio -= 0.1 * ratio * uncertainty;
//
//                if (attacker.getPosition() <= 1) ratio *= 0.5;
//            }
//
//            StringBuilder message = new StringBuilder();
//
//            if (foundOp) message.append("**");
//            message.append(MathMan.format(odds) + "%");
//            if (spySatellite) message.append(" | SAT");
//            if (intelligence) message.append(" | IA");
//            message.append(" | " + spiesUsed + "? spies (" + safety + ")");
//            long diff_m = Math.abs(diff / TimeUnit.MINUTES.toMillis(1));
//            message.append(" | " + diff_m + "m");
//            if (foundOp) message.append("**");
//
//            allOdds.put(attacker, new AbstractMap.SimpleEntry<>(ratio, message.toString()));
//        }
//
//        List<Map.Entry<DBNation, Map.Entry<Double, String>>> sorted = new ArrayList<>(allOdds.entrySet());
//        sorted.sort((o1, o2) -> Double.compare(o2.getValue().getKey(), o1.getValue().getKey()));
//
//        if (sorted.isEmpty()) {
//            return "No results";
//        }
//
//        StringBuilder response = new StringBuilder();
//        for (Map.Entry<DBNation, Map.Entry<Double, String>> entry : sorted) {
//            DBNation att = entry.getKey();
//
//            response.append(att.getNation() + " | " + att.getAllianceName() + "\n");
//            response.append(entry.getValue().getValue()).append("\n\n");
//        }
//
//        return response.toString();
//    }

    @Command
    @RolePermission(value = {Roles.ADMIN}, root = true)
    public String listOffshores() {
        StringBuilder response = new StringBuilder();

        Map<Integer, List<DBNation>> map = Locutus.imp().getNationDB().getNationsByAlliance(false, false, true, true, true);
        outer:
        for (Map.Entry<Integer, List<DBNation>> entry : map.entrySet()) {
            DBAlliance aa = DBAlliance.getOrCreate(entry.getKey());
            int aaid = aa.getAlliance_id();
            List<DBNation> members = entry.getValue();

            if (members.size() > 3) {
                continue;
            }
            if (members.size() == 0) {
                continue;
            }
            int maxCities = 0;
            int maxAge = 0;
            int activeMembers = 0;
            int numVM = 0;

            for (DBNation member : members) {
                if (member.getVm_turns() > 0) numVM++;
                if (member.getVm_turns() == 0 && member.active_m() > 10000) {
                    continue outer;
                }
                if (member.getVm_turns() == 0) {
                    activeMembers++;
                    maxCities = Math.max(maxCities, member.getCities());
                    maxAge = Math.max(maxAge, member.getAgeDays());
                }
            }
            if (numVM >= 3) {
                continue;
            }
            if (activeMembers == 0) {
                continue;
            }


            for (DBWar war : Locutus.imp().getWarDb().getWarsByAlliance(aa.getAlliance_id())) {

                int lostAA = war.getStatus() == WarStatus.ATTACKER_VICTORY ? war.getDefender_aa() : war.getStatus() == WarStatus.DEFENDER_VICTORY ? war.getAttacker_aa() : 0;
                boolean isLooted = lostAA != 0 && lostAA == aaid;
                if (!isLooted) continue;
                int otherAA = war.getStatus() == WarStatus.ATTACKER_VICTORY ? war.getAttacker_aa() : war.getStatus() == WarStatus.DEFENDER_VICTORY ? war.getDefender_aa() : 0;
                if (otherAA == aaid) continue;
                boolean lowMil = false;
                for (DBNation member : members) {
                    if (member.getVm_turns() != 0) continue;
                    if (member.active_m() > 7200) {
                        continue outer;
                    }
                    if (member.isGray()) {
                        continue outer;
                    }
                    if (member.getCities() > 1 && member.getAircraftPct() < 0.8) {
                        lowMil = true;
                    }
                }
                if (lowMil) {
                    for (DBNation member : members) {
                        if (member.daysSinceLastOffensive() < 30) lowMil = false;
                    }
                    if (lowMil) {
                        continue outer;
                    }
                }
            }

            response.append(aa.getName() + ": " + aa.getUrl()).append("\n");

            List<String> treaties = aa.getTreaties().keySet().stream().map(f -> PW.getName(f, true)).collect(Collectors.toList());
            if (!treaties.isEmpty()) {
                response.append("- Treaties: " + StringMan.join(treaties, ", ")).append("\n");
            } else{
                Map<Integer, Rank> previousIds = new Int2ObjectOpenHashMap<>();
                for (DBNation member : members) {
                    if (member.getVm_turns() != 0) continue;
                    List<AllianceChange> history = member.getAllianceHistory(null);
                    for (AllianceChange record : history) {
                        if (record.getFromRank().id >= Rank.OFFICER.id) {
                            Rank previous = previousIds.get(record.getFromId());
                            if (previous == null || previous.id < record.getFromRank().id) {
                                previousIds.put(record.getFromId(), record.getFromRank());
                            }
                        }
                        if (record.getToRank().id >= Rank.OFFICER.id) {
                            Rank previous = previousIds.get(record.getToId());
                            if (previous == null || previous.id < record.getToRank().id) {
                                previousIds.put(record.getToId(), record.getToRank());
                            }
                        }
                    }
                }
                previousIds.entrySet().removeIf(f -> {
                    DBAlliance alliance = DBAlliance.get(f.getKey());
                    return alliance == null || alliance.getScore() < 50000;
                });
                if (!previousIds.isEmpty()) {
                    List<String> previousOfficer = new ArrayList<>();
                    previousIds.forEach((id, rank) -> previousOfficer.add(PW.getName(id, true) + " (" + rank + ")"));
                    response.append("- Previous officer in: " + StringMan.join(previousOfficer, ", ")).append("\n");
                }
            }
            response.append("- Age: " + maxAge).append("\n");
            response.append("- Cities: " + maxCities).append("\n");
        }
        return response.toString();
    }

    @Command(desc = "Mark an alliance as the offshore of another\n" +
            "This is solely for informational purposes such as when displaying an alliance's info or militarization")
    public String markAsOffshore(@Me User author, @Me DBNation me, DBAlliance offshore, DBAlliance parent) {
        if (!Roles.ADMIN.hasOnRoot(author)) {
            DBAlliance expectedParent = offshore.findParentOfThisOffshore();
            if (expectedParent != parent) {
                if (me.getAlliance_id() != offshore.getAlliance_id()) {
                    return "You are not in " + offshore.getName();
                }
                if (me.getPositionEnum().id < Rank.OFFICER.id) return "You are not leader in " + offshore.getName();

                GuildDB db = parent.getGuildDB();
                if (db != null) {
                    Guild guild = db.getGuild();
                    Member member = guild.getMember(author);
                    if (member == null) return "You are not member in " + guild;
                    if (member.hasPermission(Permission.ADMINISTRATOR)) return "You are not admin in: " + guild;
                }
            }
        }
        offshore.setMeta(AllianceMeta.OFFSHORE_PARENT, parent.getAlliance_id());
        return "Set " + offshore.getName() + " as an offshore for " + parent.getName();
    }

    @Command(desc = "Return potential offshores for a list of enemy alliances\n" +
            "If allies are specified, only offshores that are not allied with any of the allies will be returned")
    @RolePermission(value = {Roles.ECON, Roles.MILCOM}, any = true)
    public String findOffshores(@Timestamp long cutoff, Set<DBAlliance> enemiesList, @Default() Set<DBAlliance> alliesList) {
        if (alliesList == null) alliesList = Collections.emptySet();
        Set<Integer> enemies = enemiesList.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());
        Set<Integer> allies = alliesList.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());

        Map<Integer, List<AllianceChange>> removes = Locutus.imp().getNationDB().getRemovesByAlliances(enemies, cutoff);
        Map<Integer, Set<AllianceChange>> removesByNation = removes.entrySet().stream().flatMap(f -> f.getValue().stream()).collect(Collectors.groupingBy(AllianceChange::getNationId, Collectors.toSet()));

        Map<Integer, Integer> offshoresWar = new HashMap<>();
        Map<Integer, Integer> offshoresTreaty = new HashMap<>();
        Map<Integer, Integer> offshoresOfficer = new HashMap<>();
        Map<Integer, Integer> offshoresMember = new HashMap<>();
        Map<Integer, Map.Entry<Integer, Map.Entry<Integer, Double>>> offshoresTransfers = new HashMap<>(); // Ofshore AA id -> (parent AA id, num transfers, value transferred)

        Map<Integer, List<DBNation>> alliances = Locutus.imp().getNationDB().getNationsByAlliance(false, false, true, true, true);
        outer:
        for (Map.Entry<Integer, List<DBNation>> entry : alliances.entrySet()) {
            int aaId = entry.getKey();
            if (allies.contains(aaId) || enemies.contains(aaId)) continue;
            List<DBNation> nations = new ArrayList<>(entry.getValue());
            nations.removeIf(f -> f.getVm_turns() > 0);

            if (nations.size() > 2) continue;

            for (DBNation nation : nations) {
                if (nation.active_m() > 7200) continue outer;
            }

            Set<DBAlliance> treaties = DBAlliance.getOrCreate(aaId).getTreatiedAllies();
            for (DBAlliance treaty : treaties) {
                if (allies.contains(treaty.getAlliance_id())) continue outer;
            }


            for (DBAlliance treatiedAlly : treaties) {
                if (enemies.contains(treatiedAlly.getAlliance_id())) {
                    offshoresTreaty.put(aaId, treatiedAlly.getAlliance_id());
                }
            }

            long start = System.currentTimeMillis();
            List<Transaction2> transfers = Locutus.imp().getBankDB().getTransactions(TRANSACTIONS_2.SENDER_ID.eq((long) aaId).and(TRANSACTIONS_2.SENDER_TYPE.eq(2)).and(TRANSACTIONS_2.TX_DATETIME.gt(cutoff)));

            transfers.removeIf(f -> f.sender_id != aaId || f.tx_datetime < cutoff);
            transfers.removeIf(f -> f.note != null && f.note.contains("of the alliance bank inventory"));
            if (!transfers.isEmpty()) {

                Map<Integer, Integer> numTransfers = new HashMap<>();
                Map<Integer, Double> valueTransfers = new HashMap<>();

                for (Transaction2 tx : transfers) {
                    if (tx.banker_nation == tx.receiver_id) continue;
                    DBNation receiver = DBNation.getById((int) tx.getReceiver());
                    if (receiver != null && enemies.contains(receiver.getAlliance_id())) {
                        int existingNum = numTransfers.getOrDefault(receiver.getAlliance_id(), 0);
                        double existingVal = valueTransfers.getOrDefault(receiver.getAlliance_id(), 0d);

                        numTransfers.put(receiver.getAlliance_id(), existingNum + 1);
                        valueTransfers.put(receiver.getAlliance_id(), existingVal + tx.convertedTotal());
                    }
                }

                if (!numTransfers.isEmpty()) {
                    for (Map.Entry<Integer, Integer> transferEntry : numTransfers.entrySet()) {
                        int enemyAAId = transferEntry.getKey();
                        int num = transferEntry.getValue();
                        double val = valueTransfers.get(enemyAAId);

                        Map.Entry<Integer, Double> numValPair = new AbstractMap.SimpleEntry<>(num, val);
                        offshoresTransfers.put(aaId, new AbstractMap.SimpleEntry<>(enemyAAId, numValPair));
                    }

                }
            }

            for (DBNation nation : nations) {
                for (DBWar war : nation.getActiveWars()) {
                    DBNation other = war.getNation(!war.isAttacker(nation));
                    if (other == null) continue;
                    if (allies.contains(other.getAlliance_id())) {
                        offshoresWar.put(aaId, other.getAlliance_id());
                    }
                }

                Set<AllianceChange> nationRemoves = removesByNation.getOrDefault(nation.getNation_id(), Collections.emptySet());
                for (AllianceChange change : nationRemoves) {
                    int previousAA = change.getFromId();
                    if (!enemies.contains(previousAA)) continue;
                    if (change.getDate() < cutoff) continue;

                    Rank rank = change.getFromRank();
                    if (rank.id >= Rank.OFFICER.id) {
                        offshoresOfficer.put(aaId, previousAA);
                    } else {
                        offshoresMember.put(aaId, previousAA);
                    }
                }
            }
        }

        {
            StringBuilder response = new StringBuilder();
            if (!offshoresWar.isEmpty()) {
                response.append("Attacking us:\n");
                for (Map.Entry<Integer, Integer> entry : offshoresWar.entrySet()) {
                    response.append(PW.getName(entry.getKey(), true) + " <" + PW.getAllianceUrl(entry.getKey()) + "> attacking " + PW.getName(entry.getValue(), true) + "\n");
                }
                response.append("\n");
            }

            if (!offshoresTreaty.isEmpty()) {
                response.append("Has treaty:\n");
                for (Map.Entry<Integer, Integer> entry : offshoresTreaty.entrySet()) {
                    response.append(PW.getName(entry.getKey(), true) + " <" + PW.getAllianceUrl(entry.getKey()) + "> treatied " + PW.getName(entry.getValue(), true) + "\n");
                }
                response.append("\n");
            }

            if (!offshoresOfficer.isEmpty()) {
                response.append("Former Officer:\n");
                for (Map.Entry<Integer, Integer> entry : offshoresOfficer.entrySet()) {
                    response.append(PW.getName(entry.getKey(), true) + " <" + PW.getAllianceUrl(entry.getKey()) + "> formerly officer in " + PW.getName(entry.getValue(), true) + "\n");
                }
                response.append("\n");
            }

            if (!offshoresMember.isEmpty()) {
                response.append("Former Member:\n");
                for (Map.Entry<Integer, Integer> entry : offshoresMember.entrySet()) {
                    response.append(PW.getName(entry.getKey(), true) + " <" + PW.getAllianceUrl(entry.getKey()) + "> formerly member in " + PW.getName(entry.getValue(), true) + "\n");
                }
                response.append("\n");
            }

            if (!offshoresTransfers.isEmpty()) {
                response.append("Bank transfers:\n");
                for (Map.Entry<Integer, Map.Entry<Integer, Map.Entry<Integer, Double>>> entry : offshoresTransfers.entrySet()) {
                    Map.Entry<Integer, Map.Entry<Integer, Double>> transferEntry = entry.getValue();
                    int otherAAId = transferEntry.getKey();
                    int num = transferEntry.getValue().getKey();
                    double value = transferEntry.getValue().getValue();

                    response.append(PW.getName(entry.getKey(), true) + " <" + PW.getAllianceUrl(entry.getKey()) + "> has " + num + " transfers with " + PW.getName(otherAAId, true) + " worth ~$" + MathMan.format(value) + "\n");
                }
            }
            if (response.length() == 0) return "No results founds in the specified timeframe";
            return "**__possible offshores__**\n" + response.toString();
        }
    }

    @Command(aliases = {"nap", "napdown"})

    public String nap(@Default("false") boolean listExpired) {
        Map<Long, String> naps = new LinkedHashMap<>();
        naps.put(238332L * 7200 * 1000, "<https://politicsandwar.fandom.com/wiki/Blue_Balled>\n<https://forum.politicsandwar.com/index.php?/topic/36719-peace-all-in-a-day/>");
        naps.put(239436L * 7200 * 1000, "<https://forum.politicsandwar.com/index.php?/topic/39524-treaty-why-nap-when-you-can-sleep/>");
        naps.put(1726938000000L, "(Mid-turn, 5:00pm UTC) <https://forum.politicsandwar.com/index.php?/topic/41182-peace-casino-royale/>");

        long turn = TimeUtil.getTurn();
        int skippedExpired = 0;

        StringBuilder response = new StringBuilder();
        for (Map.Entry<Long, String> entry : naps.entrySet()) {
            long timeEnd = entry.getKey();
            long turnEnd = TimeUtil.getTurn(timeEnd);
            if (turnEnd < turn && !listExpired) {
                skippedExpired++;
                continue;
            }
            response.append("## " + DiscordUtil.timestamp(timeEnd, null) + ":\n");
            response.append("- " + StringMan.join(entry.getValue().split("\n"), "\n- ") + "\n\n");
        }

        if (response.length() == 0) {
            if (skippedExpired > 0) {
                response.append("Skipped " + skippedExpired + " expired NAPs, set `listExpired: True` to include\n");
            }
            response.append("No active NAPs");
        }
        return response.toString();
//            String[] images = new String[] {
//                    "3556290",
//                    "13099758",
//                    "5876175",
//                    "15996537",
//                    "13578642",
//                    "11331727",
//                    "13776910",
//                    "3381639",
//                    "8476393",
//                    "3581186",
//                    "13354647",
//                    "5652813",
//                    "7645437",
//                    "9507023",
//                    "8832122",
//                    "4735568",
//                    "7391113",
//                    "5196956",
//                    "11955188",
//                    "5483839",
//                    "12321108",
//                    "17686107",
//                    "12262416",
//                    "13093956",
//                    "4909014",
//                    "17318955",
//                    "4655431",
//                    "14853646",
//                    "14464332",
//                    "14583973",
//                    "18127160",
//                    "4897716",
//                    "15353915",
//                    "8503723"
//            };
//            String baseUrl = "https://tenor.com/view/";
//            String id = images[ThreadLocalRandom.current().nextInt(images.length)];
//            return baseUrl + id;
//        }
    }
    @Command(desc = "Rank the number of wars between two coalitions by nation or alliance\n" +
            "Defaults to alliance ranking")
    public String warRanking(@Me JSONObject command, @Me IMessageIO channel, @Timestamp long time, Set<NationOrAlliance> attackers, Set<NationOrAlliance> defenders,
                             @Arg("Only include offensive wars in the ranking")
                             @Switch("o") boolean onlyOffensives,
                             @Arg("Only include defensive wars in the ranking")
                             @Switch("d") boolean onlyDefensives,
                             @Arg("Rank the average wars per alliance member")
                             @Switch("n") boolean normalizePerMember,
                             @Arg("Ignore inactive nations when determining alliance member counts")
                             @Switch("i") boolean ignore2dInactives,
                             @Arg("Rank by nation instead of alliance")
                             @Switch("a") boolean rankByNation,
                             @Arg("Only rank these war types")
                             @Switch("t") WarType warType,
                             @Arg("Only rank wars with these statuses")
                             @Switch("s") Set<WarStatus> statuses) {
        WarParser parser = WarParser.of(attackers, defenders, time, Long.MAX_VALUE);
        Map<Integer, DBWar> wars = parser.getWars();

        SummedMapRankBuilder<Integer, Double> ranksUnsorted = new RankBuilder<>(wars.values()).group(new BiConsumer<DBWar, GroupedRankBuilder<Integer, DBWar>>() {
            @Override
            public void accept(DBWar dbWar, GroupedRankBuilder<Integer, DBWar> builder) {
                if (warType != null && dbWar.getWarType() != warType) return;
                if (statuses != null && !statuses.contains(dbWar.getStatus())) return;
                if (!rankByNation) {
                    if (dbWar.getAttacker_aa() != 0 && !onlyDefensives) builder.put(dbWar.getAttacker_aa(), dbWar);
                    if (dbWar.getDefender_aa() != 0 && !onlyOffensives) builder.put(dbWar.getDefender_aa(), dbWar);
                } else {
                    if (!onlyDefensives) builder.put(dbWar.getAttacker_id(), dbWar);
                    if (!onlyOffensives) builder.put(dbWar.getDefender_id(), dbWar);
                }
            }
        }).sumValues(f -> 1d);
        if (normalizePerMember && !rankByNation) {
            ranksUnsorted = ranksUnsorted.adapt((aaId, numWars) -> {
                int num = DBAlliance.getOrCreate(aaId).getNations(true, ignore2dInactives ? 2440 : Integer.MAX_VALUE, true).size();
                if (num == 0) return 0d;
                return numWars / (double) num;
            });
        }

        RankBuilder<String> ranks = ranksUnsorted.sort().nameKeys(i -> PW.getName(i, !rankByNation));
        String offOrDef ="";
        if (onlyOffensives != onlyDefensives) {
            if (!onlyDefensives) offOrDef = "offensive ";
            else offOrDef = "defensive ";
        }

        String title = "Most " + offOrDef + "wars (" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - time) +")";
        if (normalizePerMember) title += "(per " + (ignore2dInactives ? "active " : "") + "nation)";

        ranks.build(channel, command, title, true);

        return null;
    }

    @Command(desc = "Calculate the costs of purchasing cities (from current to max)", aliases = {"citycost", "citycosts"})
    public String CityCost(@Range(min=1, max=100) int currentCity, @Range(min=1, max=100) int maxCity, @Default("false") boolean manifestDestiny, @Default("false") boolean urbanPlanning, @Default("false") boolean advancedUrbanPlanning, @Default("false") boolean metropolitanPlanning, @Default("false") boolean governmentSupportAgency) {
        if (maxCity > 1000) throw new IllegalArgumentException("Max cities 1000");

        double total = 0;

        for (int i = Math.max(1, currentCity); i < maxCity; i++) {
            total += PW.City.nextCityCost(i, manifestDestiny,
                    urbanPlanning && i >= Projects.URBAN_PLANNING.requiredCities(),
                    advancedUrbanPlanning && i >= Projects.ADVANCED_URBAN_PLANNING.requiredCities(),
                    metropolitanPlanning && i >= Projects.METROPOLITAN_PLANNING.requiredCities(),
                    governmentSupportAgency);
        }

        return "$" + MathMan.format(total);
    }

    @Command(desc = "Calculate the costs of purchasing infra (from current to max)", aliases = {"InfraCost", "infrastructurecost", "infra", "infrastructure", "infracosts"})
    public String InfraCost(@Range(min=0, max=40000) int currentInfra, @Range(min=0, max=40000) int maxInfra,
                            @Default("false") boolean urbanization,
                            @Default("false") boolean center_for_civil_engineering,
                            @Default("false") boolean advanced_engineering_corps,
                            @Default("false") boolean government_support_agency,
                            @Switch("c") @Default("1") int cities) {
        if (maxInfra > 40000) throw new IllegalArgumentException("Max infra 40000");
        double total = PW.City.Infra.calculateInfra(currentInfra, maxInfra);

        double discountFactor = 1;
        if (urbanization) {
            discountFactor -= 0.05;
            if (government_support_agency) {
                discountFactor -= 0.025;
            }
        }
        if (center_for_civil_engineering) discountFactor -= 0.05;
        if (advanced_engineering_corps) discountFactor -= 0.05;

        total = total * discountFactor * cities;

        return "$" + MathMan.format(total);
    }

    @Command(desc = "Calculate the costs of purchasing land (from current to max)", aliases = {"LandCost", "land", "landcosts"})
    public String LandCost(@Range(min=0, max=40000) int currentLand,
                           @Range(min=0, max=40000) int maxLand,
                           @Default("false") boolean rapidExpansion,
                           @Default("false") boolean arable_land_agency,
                           @Default("false") boolean advanced_engineering_corps,
                           @Default("false") boolean government_support_agency,
                           @Switch("c") @Default("1") int cities) {
        if (maxLand > 40000) throw new IllegalArgumentException("Max land 40000");

        double total = 0;

        total = PW.City.Land.calculateLand(currentLand, maxLand);

        double discountFactor = 1;
        if (rapidExpansion) {
            discountFactor -= 0.05;
            if (government_support_agency) discountFactor -= 0.025;
        }
        if (arable_land_agency) discountFactor -= 0.05;
        if (advanced_engineering_corps) discountFactor -= 0.05;

        total = total * discountFactor * cities;

        return "$" + MathMan.format(total);
    }

    @Command(desc = "Calculate the score of various things. Each argument is option, and can go in any order")
    public String score(@Default DBNation nation,
                        @Switch("c") Integer cities,
                        @Switch("b") Integer soldiers,
                        @Switch("f") Integer tanks,
                        @Switch("h") Integer aircraft,
                        @Switch("d") Integer ships,
                        @Switch("m") Integer missiles,
                        @Switch("n") Integer nukes,
                        @Switch("p") Integer projects,
                        @Switch("a") Integer avg_infra,
                        @Switch("i") Integer infraTotal,
                        @Switch("mmr") MMRDouble builtMMR
    ) {
        if (nation == null) {
            nation = new DBNation();
            nation.setMissiles(0);
            nation.setNukes(0);
            nation.setSoldiers(0);
            nation.setTanks(0);
            nation.setAircraft(0);
            nation.setShips(0);
        } else {
            nation = new DBNation(nation);
        }

        if (cities != null) nation.setCities(cities);
        if (soldiers != null) nation.setSoldiers(soldiers);
        if (tanks != null) nation.setTanks(tanks);

        if (aircraft != null) nation.setAircraft(aircraft);
        if (ships != null) nation.setShips(ships);
        if (missiles != null) nation.setMissiles(missiles);
        if (nukes != null) nation.setNukes(nukes);
        if (projects != null) {
            if (projects >= Projects.values.length) return "Too many projects: " + projects;
            nation.setProjectsRaw(0);
            for (int i = 0; i < projects; i++) {
                nation.setProject(Projects.values[i]);
            }
        }
        double infra = -1;
        if (avg_infra != null) {
            infra = avg_infra * nation.getCities();
        }
        if (infraTotal != null) {
            infra = infraTotal;
        }
        if (builtMMR != null) {
            nation.setMMR((builtMMR.getBarracks()), (builtMMR.getFactory()), (builtMMR.getHangar()), (builtMMR.getDrydock()));
        }
        double score = nation.estimateScore(infra == -1 ? nation.getInfra() : infra);

        if (score == 0) throw new IllegalArgumentException("No arguments provided");

        return "Score: " + MathMan.format(score) + "\n" +
                "WarRange: " + MathMan.format(score * 0.75) + "- " + MathMan.format(score * PW.WAR_RANGE_MAX_MODIFIER) + "\n" +
                "Can be Attacked By: " + MathMan.format(score / PW.WAR_RANGE_MAX_MODIFIER) + "- " + MathMan.format(score / 0.75) + "\n" +
                "Spy range: " + MathMan.format(score * 0.4) + "- " + MathMan.format(score * 1.5);


    }

    @Command(desc = "Check how many turns are left in the city/project timer", aliases = {"TurnTimer", "Timer", "CityTimer", "ProjectTimer"})
    public String TurnTimer(DBNation nation) throws IOException {
        StringBuilder response = new StringBuilder();
        response.append("City: " + nation.getCityTurns() + " turns (" + nation.getCities() + " cities)\n");
        response.append("Project: " + nation.getProjectTurns() + " turns | " +
                "(" + nation.getProjects().size() + "/" + nation.projectSlots() + " slots)\n");
        response.append("Color: " + nation.getColorTurns() + " turns \n");
        response.append("Domestic Policy: " + nation.getDomesticPolicyTurns() + " turns \n");
        response.append("War Policy: " + nation.getWarPolicyTurns() + " turns \n");
        response.append("Beige Turns: " + nation.getBeigeTurns() + " turns \n");
        response.append("Vacation: " + nation.getVm_turns());

        return response.toString();
    }

    @Command(desc = "Check how many projects slots a nation has", aliases = {"ProjectSlots", "ProjectSlot", "projects"})
    public static String ProjectSlots(DBNation nation) {
        Set<Project> projects = nation.getProjects();
        Map<ResourceType, Double> value = new HashMap<>();
        for (Project project : projects) {
            value = ResourceType.add(value, project.cost());
        }



        StringBuilder result = new StringBuilder();
        result.append(nation.getNation() + " has " + projects.size() + "/" + nation.projectSlots());
        for (Project project : projects) {
            result.append("\n- " + project.name());
        }
        result.append("\nworth: ~$" + MathMan.format(ResourceType.convertedTotal(value)));
        result.append("\n`" + ResourceType.resourcesToString(value) + "`");
        return result.toString();
    }

    @Command(desc = "Generate csv file of project costs")
    public String projectCostCsv() {
        StringBuilder response = new StringBuilder();
        response.append("PROJECT");
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            response.append("\t" + type);
        }
        response.append("marketValue");
        for (Project project : Projects.values) {
            response.append("\n");
            response.append(project.name());
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                Double rssValue = project.cost().getOrDefault(type, 0d);
                response.append("\t").append(MathMan.format(rssValue));
            }
            response.append("\t" + MathMan.format(ResourceType.convertedTotal(project.cost())));
        }
        return response.toString();
    }

    @Command(desc = "Get nation or bank loot history\n" +
            "Shows how much you will receive if you defeat a nation")
    public static String loot(@Me IMessageIO output, @Me DBNation me, NationOrAlliance nationOrAlliance,
                              @Arg("Score of the defeated nation\n" +
                                      "i.e. For determining bank loot percent")
                              @Default Double nationScore,
                              @Arg("Loot with pirate war policy\n" +
                                      "Else: Uses your war policy")
                              @Switch("p") boolean pirate) {
        double[] totalStored = null;
        double[] nationLoot = null;
        double[] allianceLoot = null;
        double[] revenue = null;
        int revenueTurns = 0;
        double revenueFactor = 0;
        double[] total = ResourceType.getBuffer();

        if (nationScore == null) nationScore = nationOrAlliance.isNation() ? nationOrAlliance.asNation().getScore() : me.getScore();
        DBAlliance alliance = nationOrAlliance.isAlliance() ? nationOrAlliance.asAlliance() : nationOrAlliance.asNation().getAlliance(false);

        List<String> extraInfo = new ArrayList<>();
        if (alliance != null) {
            LootEntry aaLootEntry = alliance.getLoot();
            if (aaLootEntry != null) {
                totalStored = aaLootEntry.getTotal_rss();
                Long date = aaLootEntry.getDate();
                extraInfo.add("Alliance Last looted: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - date));

                double aaScore = alliance.getScore();
                double ratio = ((nationScore * 10000) / aaScore) / 2d;
                double percent = Math.min(Math.min(ratio, 10000) / 30000, 0.33);
                allianceLoot = PW.multiply(totalStored.clone(), percent);
            } else {
                extraInfo.add("Alliance has not been looted yet");
            }
        }
        if (nationOrAlliance.isNation()) {
            revenueFactor = me.getWarPolicy() == WarPolicy.PIRATE || pirate ? 0.14 : 0.1;
            DBNation nation = nationOrAlliance.asNation();
            revenueFactor *= nation.lootModifier();

            LootEntry lootInfo = Locutus.imp().getNationDB().getLoot(nationOrAlliance.getId());

            revenueTurns = nation.getTurnsInactive(lootInfo);
            if (revenueTurns > 0) {
                revenue = nation.getRevenue(revenueTurns + 24, true, true, false, true, false, false, 0d, false);
                if (lootInfo != null) {
                    revenue = PW.capManuFromRaws(revenue, lootInfo.getTotal_rss());
                }
                revenue = PW.multiply(revenue, revenueFactor);

                // cap revenue at loot total
                if (lootInfo != null) {
                    for (ResourceType type : ResourceType.values) {
                        double revenueAmt = revenue[type.ordinal()];
                        double stockAmt = lootInfo.getTotal_rss()[type.ordinal()];
                        if (stockAmt + revenueAmt < 0) {
                            revenue[type.ordinal()] = -stockAmt;
                        }
                    }
                }
            }

            if (lootInfo != null) {
                totalStored = lootInfo.getTotal_rss();
                nationLoot = PW.multiply(totalStored.clone(), revenueFactor);

                double originalValue = lootInfo.convertedTotal();
                double originalLootable = originalValue * revenueFactor;
                String type = lootInfo.getType().name();
                StringBuilder info = new StringBuilder();
                info.append("Nation Loot from " + type);
                info.append("(" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - lootInfo.getDate()) + " ago)");
                info.append(", worth: $" + MathMan.format(originalValue) + "($" + MathMan.format(originalLootable) + " lootable)");
                if (nationOrAlliance.asNation().active_m() > 1440) info.append("- inactive for " + TimeUtil.secToTime(TimeUnit.MINUTES, nationOrAlliance.asNation().active_m()));
                extraInfo.add(info.toString());
            } else {
                extraInfo.add("No spy or beige loot found");
            }

            if (nation.active_m() > 1440 && nation.active_m() < 10080) {
                Activity activity = nation.getActivity(14 * 12);
                double loginChance = activity.loginChance(12, true);
                double loginPct = (loginChance * 100);
                extraInfo.add("Prior Week Login History (next 12 turns): " + MathMan.format(loginPct) + "%");
            }
        }

        me.setMeta(NationMeta.INTERVIEW_LOOT, (byte) 1);

        if (nationLoot == null && allianceLoot == null) {
            return "No loot history";
        }

        StringBuilder response = new StringBuilder();

        response.append("Total Stored: ```" + ResourceType.resourcesToString(totalStored) + "``` ");
        if (nationLoot != null) {
            response.append("Nation Loot (worth: $" + MathMan.format(ResourceType.convertedTotal(nationLoot)) + "): ```" + ResourceType.resourcesToString(nationLoot) + "``` ");
            ResourceType.add(total, nationLoot);
        }
        if (allianceLoot != null) {
            response.append("Alliance Loot (worth: $" + MathMan.format(ResourceType.convertedTotal(allianceLoot)) + "): ```" + ResourceType.resourcesToString(allianceLoot) + "``` ");
            ResourceType.add(total, allianceLoot);
        }
        if (revenue != null) {
            response.append("Revenue (" + revenueTurns + " turns @" + MathMan.format(revenueFactor) + "x, worth: $" + MathMan.format(ResourceType.convertedTotal(revenue)) + ") ```" + ResourceType.resourcesToString(revenue) + "``` ");
            ResourceType.add(total, revenue);
        }
        response.append("Total Loot (worth: $" + MathMan.format(ResourceType.convertedTotal(total)) + "): ```" + ResourceType.resourcesToString(total) + "``` ");
        if (!extraInfo.isEmpty()) response.append("\n`notes:`\n`- " + StringMan.join(extraInfo, "`\n`- ") +"`");

        CompletableFuture<IMessageBuilder> msgFuture = output.send(response.toString());

        if (nationOrAlliance.isNation() && nationOrAlliance.asNation().active_m() > 1440 && nationOrAlliance.asNation().active_m() < 20160) {
            DBNation nation = nationOrAlliance.asNation();
            Locutus.imp().getExecutor().submit(() -> {
                LoginFactorResult factors = DBNation.getLoginFactorPercents(nation);
                List<String> append = new ArrayList<>();
                for (Map.Entry<LoginFactor, Double> entry : factors.getResult().entrySet()) {
                    LoginFactor factor = entry.getKey();
                    double percent = entry.getValue();
                    append.add("quit chance (" + factor.name + "=" + factor.toString(factor.get(nation)) + "): " + MathMan.format(100 - percent) + "%");
                }
                try {
                    msgFuture.get().append("\n`- " + StringMan.join(append, "`\n`- ") + "`").send();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        return null;
    }

    @Command(desc = "Shows the cost of a project")
    public String ProjectCost(@Me GuildDB db, @Me IMessageIO channel,
                              Set<Project> projects,
                              @Default("false") boolean technologicalAdvancement,
                              @Default("false") boolean governmentSupportAgency,
                              @Switch("n") Set<DBNation> nations,
                              @Switch("s") SpreadSheet sheet,
                              @Switch("p") boolean ignoreProjectSlots,
                              @Switch("r") boolean ignoreRequirements,
                              @Switch("c") boolean ignoreProjectCity) throws GeneralSecurityException, IOException {
        if (sheet != null && nations == null) throw new IllegalArgumentException("You must specify `nations` for `sheet` option to be used");
        if ((ignoreProjectSlots || ignoreRequirements || ignoreProjectCity) && nations == null) throw new IllegalArgumentException("You must specify `nations` for `ignore` options to be used");
        List<Project> projectsList = new ArrayList<>(projects);
        double[] costs =  ResourceType.getBuffer();
        StringBuilder response = new StringBuilder();
        if (nations == null) {
            for (Project project : projectsList) {
                double[] cost = ResourceType.resourcesToArray(project.cost());
                if (technologicalAdvancement) {
                    double factor = 0.05;
                    if (governmentSupportAgency) {
                        factor *= 1.5;
                    }
                    cost = PW.multiply(cost, 1 - factor);
                }
                costs = ResourceType.add(costs, cost);
                response.append(project.name() + ":\n```" + ResourceType.resourcesToString(cost) + "```\nworth: ~$" + MathMan.format(ResourceType.convertedTotal(cost)) + "\n");
            }
            if (projectsList.size() > 1) {
                response.append("Total:\n```" + ResourceType.resourcesToString(costs) + "```\nworth: ~$" + MathMan.format(ResourceType.convertedTotal(costs)) + "\n");
            }
            return response.toString();
        } else {
            if (sheet == null) {
                sheet = SpreadSheet.create(db, SheetKey.PROJECT_SHEET);
            }
            List<String> header = new ArrayList<>(Arrays.asList(
                    "nation",
                    "alliance",
                    "cities",
                    "technological_advancement",
                    "government_support_agency",
                    "cost",
                    "cost_raw",
                    "errors"
            ));
            int indexOffset = header.size();
            for (Project project : projectsList) {
                header.add(project.name());
            }
            sheet.setHeader(header);

            Map<Project, Integer> counts = new LinkedHashMap<>();
            Map<Project, double[]> costByProject = new LinkedHashMap<>();
            for (DBNation nation : nations) {
                DBNation nationCopy = new DBNation(nation);
                double[] nationCost = ResourceType.getBuffer();
                List<String> errors = new ArrayList<>();
                List<Integer> buy = new ArrayList<>();

                if (technologicalAdvancement)
                    nationCopy.setDomesticPolicy(DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT);
                if (governmentSupportAgency) nationCopy.setProject(Projects.GOVERNMENT_SUPPORT_AGENCY);

                for (Project project : projectsList) {
                    boolean canBuy = false;
                    if (nation.hasProject(project)) {
                        errors.add("already has:" + project.name());
                    } else if (!ignoreProjectCity && nation.getCities() < project.requiredCities()) {
                        errors.add("cities:" + project.requiredCities() + " < " + nation.getCities() + " for " + project.name());
                    } else if (!ignoreProjectCity && nation.getCities() > project.maxCities()) {
                        errors.add("cities:" + project.maxCities() + " < " + nation.getCities() + " for " + project.name());
                    } else if (!ignoreProjectSlots && nation.getFreeProjectSlots() <= 0) {
                        errors.add("no free project slots");
                    } else if (!ignoreRequirements && !nation.hasProjects(project.requiredProjects(), false)) {
                        errors.add("missing required projects for " + project.name());
                    } else {
                        canBuy = true;
                        counts.merge(project, 1, Integer::sum);
                        double[] cost = nation.projectCost(project);
                        nationCost = ResourceType.add(nationCost, cost);
                        ResourceType.add(costByProject.computeIfAbsent(project, p -> ResourceType.getBuffer()), cost);
                    }
                    buy.add(canBuy ? 1 : 0);
                }
                costs = ResourceType.add(costs, nationCost);

                header.set(0, MarkupUtil.sheetUrl(nation.getName(), nation.getUrl()));
                header.set(1, MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));
                header.set(2, String.valueOf(nation.getCities()));
                header.set(3, nationCopy.getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT ? "true" : "false");
                header.set(4, nationCopy.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY) ? "true" : "false");
                header.set(5, ResourceType.resourcesToString(nationCost));
                header.set(6, MathMan.format(ResourceType.convertedTotal(nationCost)));
                header.set(7, StringMan.join(errors, ","));
                for (int i = 0; i < projectsList.size(); i++) {
                    header.set(i + indexOffset, String.valueOf(buy.get(i)));
                }
                sheet.addRow(header);
            }

            sheet.updateClearCurrentTab();
            sheet.updateWrite();
            IMessageBuilder msg = channel.create();
            sheet.attach(msg, "projects");

            counts = ArrayUtil.sortMap(counts, false);
            response.append("\nTotal:\n```" + ResourceType.resourcesToString(costs) + "```\nworth: ~$" + MathMan.format(ResourceType.convertedTotal(costs)) + "\n");
            // append counts
            response.append("# Bought: `" + StringMan.getString(counts) + "`\n");
            // Add total cost
            if (projects.size() > 1) {
                Map<Project, Double> costByProjectValue = new LinkedHashMap<>();
                for (Map.Entry<Project, double[]> entry : costByProject.entrySet()) {
                    costByProjectValue.put(entry.getKey(), ResourceType.convertedTotal(entry.getValue()));
                }
                ArrayUtil.sortMap(costByProjectValue, false);
                for (Map.Entry<Project, Double> entry : costByProjectValue.entrySet()) {
                    if (entry.getValue() == 0) continue;
                    response.append(entry.getKey().name() + ": ~$" + MathMan.format(entry.getValue()) + "\n- `" + ResourceType.resourcesToString(costByProject.get(entry.getKey())) + "`\n");
                }
            }
            response.append("\nSee " + CM.transfer.bulk.cmd.toSlashMention());
            msg.append(response.toString()).send();
            return null;
        }
    }

    @Command(desc = "Add or remove the configured auto roles to all users in this discord guild")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public static String autoroleall(@Me GuildDB db, @Me IMessageIO channel, @Me JSONObject command, @Switch("f") boolean force) {
        IAutoRoleTask task = db.getAutoRoleTask();
        task.syncDB();

        AutoRoleInfo result = task.autoRoleAll();
        if (force) {
            channel.send("Please wait...");
            result.execute();
            return result.getChangesAndErrorMessage();
        }

        String body = "`note: Results may differ if settings or users change`\n" +
                result.getSyncDbResult();
        String resultStr = result.toString();
        if (body.length() + resultStr.length() < 2000) {
            body += "\n\n------------\n\n" + resultStr;
        }
        IMessageBuilder msg = channel.create().confirmation("Auto role all", body, command);
        if (body.length() + resultStr.length() >= 2000) {
            msg = msg.file("role_changes.txt", result.toString());
        }

        if (db.hasAlliance()) {
            StringBuilder response = new StringBuilder();
            for (Map.Entry<Member, GuildDB.UnmaskedReason> entry : db.getMaskedNonMembers().entrySet()) {
                User user = entry.getKey().getUser();
                response.append("User: `" + DiscordUtil.getFullUsername(user).replace("_", "\\_") + "`" + " `<@" + user.getIdLong() + ">` ");
                DBNation nation = DiscordUtil.getNation(user);
                if (nation != null) {
                    String active = TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m());
                    if (nation.active_m() > 10000) active = "**" + active + "**";
                    response.append(nation.getName() + " | <" + nation.getUrl() + "> | " + active + " | " + Rank.byId(nation.getPosition()) + " in AA:" + nation.getAllianceName());
                }
                response.append("- ").append(entry.getValue());
                response.append("\n");
            }
            if (response.length() > 0) {
                msg.append(response.toString());
            }
        }

        msg.send();
        return null;
    }

    @Command(desc = "Give the configured bot auto roles to a user on discord")
    public static String autorole(@Me GuildDB db, @Me IMessageIO channel, @Me JSONObject command, Member member, @Switch("f") boolean force) {
        IAutoRoleTask task = db.getAutoRoleTask();
        task.syncDB();

        DBNation nation = DiscordUtil.getNation(member.getUser());
        if (nation == null) return "That nation isn't registered: " + CM.register.cmd.toSlashMention();
        AutoRoleInfo result = task.autoRole(member, nation);
        if (force) {
            result.execute();
            return result.getChangesAndErrorMessage();
        }

        String body = "`note: Results may differ if settings or users change`\n" +
                result.getSyncDbResult() + "\n------\n" + result.toString();
        channel.create().confirmation("Auto role " + nation.getNation(), body, command).send();
        return null;
    }

    @RolePermission(value = {Roles.MILCOM, Roles.INTERNAL_AFFAIRS,Roles.ECON,Roles.FOREIGN_AFFAIRS}, any=true)
    @Command(desc = "Create a sheet of alliances with customized columns\n" +
            "See <https://github.com/xdnw/locutus/wiki/nation_placeholders> for a list of placeholders")
    @NoFormat
    public static String AllianceSheet(AlliancePlaceholders aaPlaceholders, @Me Guild guild, @Me IMessageIO channel, @Me DBNation me, @Me User author, @Me GuildDB db,
                                @Arg("The nations to include in each alliance")
                                Set<DBNation> nations,
                                @Arg("The columns to use in the sheet")
                                @TextArea List<String> columns,
                                @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException, IllegalAccessException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.ALLIANCES_SHEET);
        }
        List<String> header = new ArrayList<>(columns);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        Map<Integer, List<DBNation>> nationMap = new RankBuilder<>(nations).group(n -> n.getAlliance_id()).get();

        Map<DBAlliance, DBNation> totals = new HashMap<>();
        for (Map.Entry<Integer, List<DBNation>> entry : nationMap.entrySet()) {
            Integer id = entry.getKey();
            DBAlliance alliance = DBAlliance.get(id);
            if (alliance == null) continue;
            DBNation total = DBNation.createFromList(PW.getName(id, true), entry.getValue(), false);
            totals.put(alliance, total);
        }

        sheet.setHeader(header);

        PlaceholderCache<DBAlliance> aaCache = new PlaceholderCache<>(totals.keySet());
        List<Function<DBAlliance, String>> formatByColumn = new ArrayList<>();
        for (String column : columns) {
            formatByColumn.add(aaPlaceholders.getFormatFunction(guild, me, author, column, aaCache, true));
        }
//        Placeholders.PlaceholderCache<DBNation> natCache = new Placeholders.PlaceholderCache<>(totals.values());

        for (Map.Entry<DBAlliance, DBNation> entry : totals.entrySet()) {
            DBAlliance dbAlliance = entry.getKey();
            DBNation nation = entry.getValue();
            for (int i = 0; i < columns.size(); i++) {
                Function<DBAlliance, String> formatter = formatByColumn.get(i);
                String formatted = formatter.apply(dbAlliance);
                header.set(i, formatted);
            }

            sheet.addRow(new ArrayList<>(header));
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(channel.create(), "alliances").send();
        return null;
    }

    @RolePermission(value = {Roles.MILCOM, Roles.ECON, Roles.INTERNAL_AFFAIRS}, any=true)
    @Command(desc = "A sheet of nations stats with customizable columns\n" +
            "See <https://github.com/xdnw/locutus/wiki/nation_placeholders> for a list of placeholders")
    @NoFormat
    public static void NationSheet(NationPlaceholders placeholders, @Me IMessageIO channel, @Me DBNation me, @Me User author, @Me GuildDB db,
                                   NationList nations,
                                   @Arg("A space separated list of columns to use in the sheet\n" +
                                           "Can include NationAttribute as placeholders in columns\n" +
                                           "All NationAttribute placeholders must be surrounded by {} e.g. {nation}")
                                   @TextArea List<String> columns,
                                   @Switch("t") @Timestamp Long snapshotTime,
                                   @Switch("e") boolean updateSpies, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        Set<DBNation> nationSet = PW.getNationsSnapshot(nations.getNations(), nations.getFilter(), snapshotTime, db.getGuild(), true);
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.NATION_SHEET);
        }
        List<String> header = new ArrayList<>(columns);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            if (arg.startsWith("=")) arg = "'" + arg;
            header.set(i, arg);
        }

        sheet.setHeader(header);

        PlaceholderCache<DBNation> cache = new PlaceholderCache<>(nationSet);
        List<Function<DBNation, String>> formatFunction = new ArrayList<>();
        for (String arg : columns) {
            formatFunction.add(placeholders.getFormatFunction(db.getGuild(), me, author, arg, cache, true));
        }
        for (DBNation nation : nationSet) {
            if (updateSpies) {
                nation.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK);
            }
            for (int i = 0; i < columns.size(); i++) {
                Function<DBNation, String> formatter = formatFunction.get(i);
                String formatted = formatter.apply(nation);

                header.set(i, formatted);
            }

            sheet.addRow(new ArrayList<>(header));
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(channel.create(), "nations").send();
    }

    @Command(desc = "Check if a nation shares networks with others\n" +
            "Notes:\n" +
            "- Sharing networks does not mean they are the same person (mobile networks, schools, public wifi, vpns, dynamic ips\n" +
            "- A network not shared 'concurrently' or within a short timeframe may be a false positive\n" +
            "- Having many networks, but only a few shared may be a sign of a VPN being used (there are legitimate reasons for using a VPN)\n" +
            "- It is against game rules to use evidence to threaten or coerce others\n" +
            "See: https://politicsandwar.com/rules/")
    public String multi(@Me IMessageIO channel, DBNation nation) {
        MultiReport report = new MultiReport(nation.getNation_id());
        String result = report.toString();

        String title = PW.getName(nation.getNation_id(), false) + " multi report";
        if (result.length() + title.length() >= 2000) {
            String condensed = report.toString(true);
            if (condensed.length() + title.length() < 2000) {
                channel.create().embed(PW.getName(nation.getNation_id(), false), condensed).send();
            }
        }
        channel.create().embed(title, result).send();
        String disclaimer = "```Disclaimer:\n" +
                "- Sharing networks does not mean they are the same person (mobile networks, schools, public wifi, vpns, dynamic ips)\n" +
                "- A network not shared 'concurrently' or within a short timeframe may be a false positive\n" +
                "- Having many networks, but only a few shared may be a sign of a VPN being used (there are legitimate reasons for using a VPN)\n" +
                "- It is against game rules to use evidence to threaten or coerce others\n" +
                "See: https://politicsandwar.com/rules/" +
                "```";
        return disclaimer;
    }

    @Command(desc = "Return number of turns a nation has left of beige color bloc")
    public String beigeTurns(DBNation nation) {
        return nation.getBeigeTurns() + " turns";
    }

    @Command(desc = "Return quickest attacks to beige an enemy at a resistance level", aliases = {"fastBeige", "quickestBeige", "quickBeige", "fastestBeige"})
    public String quickestBeige(@Range(min=1, max=100) int resistance,
                                @Arg("Don't allow ground attacks")
                                @Switch("g") boolean noGround,
                                @Arg("Don't allow naval attacks")
                                @Switch("s") boolean noShip,
                                @Arg("Don't allow aircraft attacks")
                                @Switch("a") boolean noAir,
                                @Arg("Don't allow missile attacks")
                                @Switch("m") boolean noMissile,
                                @Arg("Don't allow nuclear attacks")
                                @Switch("n") boolean noNuke) {
        if (resistance > 1000 || resistance < 1) throw new IllegalArgumentException("Resistance must be between 1 and 100");
        List<AttackType> allowed = new ArrayList<>(List.of(AttackType.values));
        if (noGround) allowed.removeIf(f -> f == AttackType.GROUND);
        if (noShip) allowed.removeIf(f -> f.getUnits().length > 0 && f.getUnits()[0] == MilitaryUnit.SHIP);
        if (noAir) allowed.removeIf(f -> f.getUnits().length > 0 && f.getUnits()[0] == MilitaryUnit.AIRCRAFT);
        if (noMissile) allowed.removeIf(f -> f.getUnits().length > 0 && f.getUnits()[0] == MilitaryUnit.MISSILE);
        if (noNuke) allowed.removeIf(f -> f.getUnits().length > 0 && f.getUnits()[0] == MilitaryUnit.NUKE);
        AttackTypeNode best = AttackTypeNode.findQuickest(allowed, resistance);

        return "Result: " + best.toString() + " MAP: " + best.map + " resistance:" + best.resistance;
    }

    @Command(desc = "Set a nation's tax bracket from a sheet\n" +
            "Expects columns, `nation` (or `leader` or `user`) and `bracket` or `internal` (for internal taxrates)")
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String setBracketBulk(@Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, SpreadSheet sheet, @Switch("f") boolean force) {
        sheet.loadValues(null, true);
        List<Object> nations = sheet.findColumn("nation");
        List<Object> leaders = sheet.findColumn("leader");
        List<Object> brackets = sheet.findColumn("bracket");
        List<Object> internals = sheet.findColumn("internal");
        if (nations == null && leaders == null) {
            throw new IllegalArgumentException("Expecting column `nation` or `leader` or `user` or `member`");
        }

        Map<String, TaxBracket> bracketsByStr = new LinkedHashMap<>();

        Map<DBNation, TaxBracket> setBracket = new LinkedHashMap<>();
        Map<DBNation, TaxRate> setInternal = new LinkedHashMap<>();
        Map<Integer, TaxRate> internalRates = db.getInternalTaxRates();

        int max = Math.max(Math.max(Math.max(nations == null ? 0 : nations.size(), leaders == null ? 0 : leaders.size()), brackets == null ? 0 : brackets.size()), internals == null ? 0 : internals.size());

        List<String> errors = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            Object nationObj = nations == null || nations.size() < i ? null : nations.get(i);
            String nationStr = nationObj == null ? null : nationObj.toString();

            Object leaderObj = leaders == null || leaders.size() < i ? null : leaders.get(i);
            String leaderStr = leaderObj == null ? null : leaderObj.toString();

            String input = nationStr == null ? leaderStr : nationStr;
            if (input == null || input.isEmpty()) continue;

            DBNation nation;
            if (nationStr != null) {
                try {
                    nation = PWBindings.nation(null, nationStr);
                } catch (IllegalArgumentException e) {
                    errors.add("[Row:" + (i + 2) + "] Nation not found: `" + nationStr + "`");
                    continue;
                }
            } else {
                nation = Locutus.imp().getNationDB().getNationByLeader(leaderStr);
                if (nation == null) {
                    errors.add("[Row:" + (i + 2) + "] Nation Leader not found: `" + leaderStr + "`");
                    continue;
                }
            }
            if (!db.isAllianceId(nation.getAlliance_id())) {
                errors.add("[Row:" + (i + 2) + "] Nation not in alliance: `" + nation.getNation() + "`");
                continue;
            }

            Object bracketObj = brackets == null || brackets.size() < i ? null : brackets.get(i);
            String bracketStr = bracketObj == null ? null : bracketObj.toString();

            Object internalObj = internals == null || internals.size() < i ? null : internals.get(i);
            String internalStr = internalObj == null ? null : internalObj.toString();

            if (bracketStr != null && !bracketStr.isEmpty()) {
                TaxBracket bracket = bracketsByStr.computeIfAbsent(bracketStr, s -> PWBindings.bracket(db, s));
                if (bracket.getId() == nation.getTax_id()) {
                    continue;
                }
                setBracket.put(nation, bracket);
            }
            if (internalStr != null && !internalStr.isEmpty()) {
                TaxRate internal = new TaxRate(internalStr);
                TaxRate existing = internalRates.get(nation.getId());
                if (existing != null && existing.equals(internal)) {
                    continue;
                }
                setInternal.put(nation, internal);
            }
        }

        if (setBracket.isEmpty() && setInternal.isEmpty()) {
            return "No tax bracket or internal changes found. Please set a value for one or more of the cells in the columns `bracket` or `internal`";
        }

        List<String> changes = new ArrayList<>();
        for (Map.Entry<DBNation, TaxBracket> entry : setBracket.entrySet()) {
            DBNation nation = entry.getKey();
            TaxBracket bracket = entry.getValue();
            changes.add("Set " + nation.getName() + " from tx_id=" + nation.getTax_id() + " to tx_id=" + bracket);
        }
        for (Map.Entry<DBNation, TaxRate> entry : setInternal.entrySet()) {
            DBNation nation = entry.getKey();
            TaxRate internal = entry.getValue();
            TaxRate from = internalRates.get(nation.getId());
            String fromStr = from == null ? "" : "from " + from + " ";
            changes.add("Set " + nation.getName() + " internal tax rate " + fromStr + "to " + internal);
        }


        if (!force) {
            List<String> titleComponents = new ArrayList<>(Arrays.asList("Set"));
            if (!setBracket.isEmpty()) {
                titleComponents.add("bracket for " + setBracket.size());
            }
            if (!setInternal.isEmpty()) {
                if (!setBracket.isEmpty()) titleComponents.add("and");
                titleComponents.add("internal for " + setInternal.size());
            }
            String title = StringMan.join(titleComponents, " ") + " nations";
            IMessageBuilder msg = io.create();
            StringBuilder body = new StringBuilder();

            // include errors
            if (!errors.isEmpty()) {
                body.append("**__" + errors.size() + " errors (see attached)__**\n");
                if (errors.size() < 4) {
                    // attach inline, dot points, markdown
                    body.append("- " + StringMan.join(errors, "\n- ") + "\n");
                } else {
                    msg.file("errors.txt", StringMan.join(errors, "\n"));
                }
                body.append("\n");
            }
            Set<Integer> fromBracketIds = setBracket.keySet().stream().map(DBNation::getTax_id).collect(Collectors.toSet());
            Set<Integer> toBracketIds = setBracket.values().stream().map(TaxBracket::getId).collect(Collectors.toSet());
            Set<TaxRate> fromInternalRates = setInternal.keySet().stream().map(n -> internalRates.get(n.getId())).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<TaxRate> toInternalRates = new HashSet<>(setInternal.values());
            if (!setBracket.isEmpty()) {
                body.append("Change " + setBracket.size() + " nations from " + fromBracketIds.size() + " unique tax bracket(s) to " + toBracketIds.size() + " bracket(s)\n");
            }
            if (!setInternal.isEmpty()) {
                body.append("Change " + setInternal.size() + " nations from " + fromInternalRates.size() + " unique internal tax rate(s) to " + toInternalRates.size() + " rate(s)\n");
            }
            body.append("\n\nSee `changes.txt` for list of changes");
            msg.file("changes.txt", StringMan.join(changes, "\n"));

            msg.confirmation(title, body.toString(), command).send();
            return null;
        }

        CompletableFuture<IMessageBuilder> msg = io.send((StringMan.join(errors, "\n") + "\n\nPlease wait...").trim());
        List<String> results = new ArrayList<>();

        AllianceList aaList = db.getAllianceList().subList(setBracket.keySet());
        for (Map.Entry<DBNation, TaxBracket> entry : setBracket.entrySet()) {
            DBNation nation = entry.getKey();
            TaxBracket bracket = entry.getValue();

            String resultStr;
            try {
                boolean result = aaList.setTaxBracket(bracket, nation);
                if (result) {
                    nation.setTax_id(bracket.getId());
                }
                resultStr = result + "";
            } catch (Exception e) {
                resultStr = StringMan.stripApiKey(e.getMessage());
            }
            results.add("Set " + nation.getMarkdownUrl() + " to tax_id=" + bracket.getId() + ": " + resultStr);
        }

        for (Map.Entry<DBNation, TaxRate> entry : setInternal.entrySet()) {
            DBNation nation = entry.getKey();
            TaxRate internal = entry.getValue();
            db.setInternalTaxRate(nation, internal);
            results.add("Set " + nation.getMarkdownUrl() + " internal tax rate to " + internal);
        }
        return StringMan.join(results, "\n");
    }

    @Command(desc = "Get info about your own nation")
    public String me(@Me JSONObject command, @Me Guild guild, @Me IMessageIO channel, @Me DBNation me, @Me User author, @Me GuildDB db, @Switch("s") @Timestamp Long snapshotDate) throws IOException {
        return who(command, guild, channel, author, db, me, Collections.singleton(me), null, false, false, false, false, false, false, snapshotDate, null);
    }

    @Command(aliases = {"who", "pnw-who", "who", "pw-who", "pw-info", "how", "where", "when", "why", "whois"},
            desc = "Get detailed information about a nation\n" +
                    "Nation argument can be nation name, id, link, or discord tag\n" +
                    "e.g. `{prefix}who @borg`")
    public static String who(@Me JSONObject command, @Me Guild guild, @Me IMessageIO channel, @Me User author, @Me GuildDB db, @Me @Default DBNation me,
                      @Arg("The nations to get info about")
                      Set<NationOrAlliance> nationOrAlliances,
                      @Arg("Sort any listed nations by this attribute")
                      @Default() NationAttributeDouble sortBy,
                      @Arg("List the nations instead of just providing a summary")
                      @Switch("l") boolean list,
                      @Arg("List the alliances of the provided nation")
                      @Switch("a") boolean listAlliances,
                      @Arg("List the discord user ids of each nation")
                      @Switch("r") boolean listRawUserIds,
                      @Arg("List the discord user mentions of each nation")
                      @Switch("m") boolean listMentions,
                      @Arg("List paginated info of each nation")
                      @Switch("i") boolean listInfo,
                      @Arg("List all interview channels of each nation")
                      @Switch("c") boolean listChannels,
                      @Switch("s") @Timestamp Long snapshotDate,
                      @Switch("p") Integer page) throws IOException {
        DBNation myNation = DiscordUtil.getNation(author.getIdLong());
        int perpage = 15;
        StringBuilder response = new StringBuilder();
        String filter = command.has("nationoralliances") ? command.getString("nationoralliances") : null;
        if (filter == null && me != null) filter = me.getQualifiedId();
        final Set<DBNation> nations = PW.getNationsSnapshot(SimpleNationList.from(nationOrAlliances).getNations(), filter, snapshotDate, db.getGuild(), true);

        String arg0;
        String title;
        if (nationOrAlliances.size() == 1) {
            NationOrAlliance nationOrAA = nationOrAlliances.iterator().next();
            if (nationOrAA.isNation()) {
                DBNation nation = nationOrAA.asNation();
                title = nation.getNation();
                StringBuilder markdown = new StringBuilder(nation.toFullMarkdown());
                IMessageBuilder msg = channel.create().embed(title, markdown.toString());

                //Run audit (if ia/econ, or self)
                if ((myNation != null && myNation.getId() == nation.getId()) || Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild)) {
                    CM.audit.run audit = CM.audit.run.cmd.nationList(nation.getQualifiedId());
                    msg = msg.commandButton(CommandBehavior.EPHEMERAL, audit, "Audit");
                }
                //Bans
                CM.nation.list.bans bans = CM.nation.list.bans.cmd.nationId(nation.getId() + "");
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, bans, "Bans");
                //Reports
                CM.report.search reports = CM.report.search.cmd.nationIdReported(nation.getNation_id() + "");
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, reports, "Reports");
                //Projects
                CM.project.slots projects = CM.project.slots.cmd.nation(nation.getUrl());
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, projects, "Projects");
                //Departures
                CM.nation.departures departures = CM.nation.departures.cmd.nationOrAlliance(nation.getUrl()).time("9999d");
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, departures, "Departures");
                //Multis
                CM.nation.list.multi multis = CM.nation.list.multi.cmd.nation(nation.getUrl());
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, multis, "Multis");
                //Reroll
                CM.nation.reroll reroll = CM.nation.reroll.cmd.nation(nation.getUrl());
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, reroll, "Reroll");
                //Open alliance info
                if (nation.getAlliance_id() != 0) {
                    CM.who info = CM.who.cmd.nationOrAlliances(nation.getAllianceUrl());
                    msg = msg.commandButton(CommandBehavior.EPHEMERAL, info, "Alliance");
                }
                //Score command

                CM.nation.score score = CM.nation.score.cmd.nation(nation.getUrl()).missiles("").nukes("").projects("").avg_infra("").builtMMR("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, score, "Score");
                //Revenue
                CM.nation.revenue revenue = CM.nation.revenue.cmd.nations(nation.getUrl());
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, revenue, "Revenue");
                //WarInfo
                CM.war.info warInfo = CM.war.info.cmd.nation(nation.getUrl());
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, warInfo, "War Info");
                //Counter
                String aaIdStr = db.getAllianceIds().stream().map(f -> "AA:" + f).collect(Collectors.joining(","));
                CM.war.counter.nation counter = CM.war.counter.nation.cmd.target(nation.getUrl()).counterWith(aaIdStr).ping("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, counter, "Counter");
                //Loot
                CM.nation.loot loot = CM.nation.loot.cmd.nationOrAlliance(nation.getUrl());
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, loot, "Loot");
                //Cost
                CM.alliance.cost cost = CM.alliance.cost.cmd.nations(nation.getUrl());
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, cost, "Cost");
                //unit history
                CM.unit.history history = CM.unit.history.cmd.nation(nation.getUrl()).unit("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, history, "Unit History");

                msg.send();
            } else {
                if (snapshotDate != null) {
                    throw new IllegalArgumentException("You specified a `snapshotDate`, but alliance snapshots are not currently supported.");
                }
                DBAlliance alliance = nationOrAA.asAlliance();
                title = alliance.getName();
                StringBuilder markdown = new StringBuilder(alliance.toMarkdown() + "\n");
                if (Roles.ADMIN.has(author, db.getGuild()) && myNation != null && myNation.getAlliance_id() == alliance.getId() && db.getAllianceIds().isEmpty()) {
                    markdown.append("\nSet as this guild's alliance: " + CM.settings_default.registerAlliance.cmd.toSlashMention() + "\n");
                }

                IMessageBuilder msg = channel.create().embed(title, markdown.toString());

                // Militarization graph
                CM.alliance.stats.metricsByTurn militarization =
                        CM.alliance.stats.metricsByTurn.cmd.metric(AllianceMetric.GROUND_PCT.name()).coalition(alliance.getQualifiedId()).time("7d");
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, militarization, "Military Graph");
                // Tiering graph
                CM.stats_tier.cityTierGraph tiering =
                        CM.stats_tier.cityTierGraph.cmd.coalition1(alliance.getQualifiedId()).coalition2("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, tiering, "City Tier Graph");
                // strength graph
                CM.stats_tier.strengthTierGraph strength =
                        CM.stats_tier.strengthTierGraph.cmd.coalition1(alliance.getQualifiedId()).coalition2("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, strength, "Strength Tier Graph");
                // mmr tier
                CM.stats_tier.mmrTierGraph mmr =
                        CM.stats_tier.mmrTierGraph.cmd.coalition1(alliance.getQualifiedId()).coalition2("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, mmr, "MMR Tier Graph");
                // spy tier
                CM.stats_tier.spyTierGraph spy =
                        CM.stats_tier.spyTierGraph.cmd.coalition1(alliance.getQualifiedId()).coalition2("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, spy, "Spy Tier Graph");

                //- /coalition create - add
                CM.coalition.create createCoalition =
                        CM.coalition.create.cmd.alliances(alliance.getQualifiedId()).coalitionName("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, createCoalition, "Create Coalition");

                //- /coalition generate - sphere
                CM.coalition.generate generateCoalition =
                        CM.coalition.generate.cmd.coalition("").rootAlliance(alliance.getQualifiedId()).topX("80");
                msg = msg.modal(CommandBehavior.EPHEMERAL, generateCoalition, "Add Sphere Coalition");
                //- /alliance departures
                CM.alliance.departures departures =
                        CM.alliance.departures.cmd.nationOrAlliance(alliance.getQualifiedId()).time("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, departures, "Departures");
                //- loot
                CM.nation.loot loot =
                        CM.nation.loot.cmd.nationOrAlliance(alliance.getQualifiedId()).nationScore("");
                msg = msg.modal(CommandBehavior.EPHEMERAL, loot, "Loot");

                // alliance cost
                CM.alliance.cost cost =
                        CM.alliance.cost.cmd.nations(alliance.getQualifiedId());
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, cost, "Cost");
                // offshore find
                CM.offshore.find.for_coalition findOffshore =
                        CM.offshore.find.for_coalition.cmd.alliance(alliance.getQualifiedId()).cutoffMs("200d");
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, findOffshore, "Find Offshores");

                msg.send();
            }
        } else {
            NationList nationList = new SimpleNationList(nations);
            int allianceId = -1;
            for (DBNation nation : nations) {
                if (allianceId == -1 || allianceId == nation.getAlliance_id()) {
                    allianceId = nation.getAlliance_id();
                } else {
                    allianceId = -2;
                }
            }
            if (allianceId != -2) {
                String name = PW.getName(allianceId, true);
                String url = PW.getUrl(allianceId, true);
                title = "AA: " + name;
                arg0 = MarkupUtil.markdownUrl(name, url);
            } else {
                arg0 = "coalition";
                title = "`" + arg0 + "`";
            }
            if (nations.isEmpty()) return "No nations found";
            title = "(" + nations.size() + " nations) " + title;
            IMessageBuilder msg = channel.create().embed(title, nationList.toMarkdown());

            msg = msg.commandButton(CommandBehavior.EPHEMERAL, command.put("list", "True").put("listMentions", "True"), "List");
//            // Tiering graph
//            CM.stats_tier.cityTierGraph tiering =
//                    CM.stats_tier.cityTierGraph.cmd.create(filter, "");
//            msg = msg.modal(CommandBehavior.EPHEMERAL, tiering, "City Tier Graph");
//            // strength graph
//            CM.stats_tier.strengthTierGraph strength =
//                    CM.stats_tier.strengthTierGraph.cmd.create(filter, "");
//            msg = msg.modal(CommandBehavior.EPHEMERAL, strength, "Strength Tier Graph");
//            // mmr tier
//            CM.stats_tier.mmrTierGraph mmr =
//                    CM.stats_tier.mmrTierGraph.cmd.create(filter, "");
//            msg = msg.modal(CommandBehavior.EPHEMERAL, mmr, "MMR Tier Graph");
//            // spy tier
//            CM.stats_tier.spyTierGraph spy =
//                    CM.stats_tier.spyTierGraph.cmd.create(filter, "");
//            msg = msg.modal(CommandBehavior.EPHEMERAL, spy, "Spy Tier Graph");
//            // alliance cost
//            CM.alliance.cost cost =
//                    CM.alliance.cost.cmd.create(filter);
//            msg = msg.commandButton(CommandBehavior.EPHEMERAL, cost, "Cost");

            msg.send();
        }
        if (!listInfo && page == null && !response.isEmpty()) {
            channel.create().embed(title, response.toString()).send();
        }

        if (list || listMentions || listRawUserIds || listChannels || listAlliances) {
//            if (perpage == null) perpage = 15;
            if (page == null) page = 0;
            List<String> nationList = new ArrayList<>();

            if (listAlliances) {
                // alliances
                Set<DBAlliance> alliances = nations.stream().map(DBNation::getAlliance).filter(Objects::nonNull).collect(Collectors.toSet());
                List<DBAlliance> alliancesSorted = new ArrayList<>(alliances);
                if (sortBy != null) {
                    alliancesSorted.sort((o1, o2) -> {
                        double v1 = o1.getTotal(sortBy, null);
                        double v2 = o2.getTotal(sortBy, null);
                        return Double.compare(v2, v1);
                    });
                }
                alliancesSorted.forEach(f -> nationList.add(f.getMarkdownUrl()));
            } else {
                IACategory iaCat = listChannels && db != null ? db.getIACategory() : null;
                List<DBNation> nationsSorted = new ArrayList<>(nations);
                if (sortBy != null) {
                    nationsSorted.sort((o1, o2) -> {
                        double v1 = sortBy.apply(o1);
                        double v2 = sortBy.apply(o2);
                        return Double.compare(v2, v1);
                    });
                }

                for (DBNation nation : nationsSorted) {
                    String nationStr = list ? nation.getNationUrlMarkup(true) : "";
                    if (listMentions) {
                        PNWUser user = nation.getDBUser();
                        if (user != null) {
                            nationStr += (" <@" + user.getDiscordId() + ">");
                        }
                    }
                    if (listRawUserIds) {
                        PNWUser user = nation.getDBUser();
                        if (user != null) {
                            nationStr += (" `<@" + user.getDiscordId() + ">`");
                        }
                    }
                    if (iaCat != null) {
                        IAChannel iaChan = iaCat.get(nation);
                        if (channel != null) {
                            if (listRawUserIds) {
                                nationStr += " `" + iaChan.getChannel().getAsMention() + "`";
                            } else {
                                nationStr += " " + iaChan.getChannel().getAsMention();
                            }
                        }
                    }
                    nationList.add(nationStr);
                }
            }
            int pages = (nations.size() + perpage - 1) / perpage;
            title += "(" + (page + 1) + "/" + pages + ")";

            channel.create().paginate(title, command, page, perpage, nationList).send();
        }
        if (listInfo) {
//            if (perpage == null) perpage = 5;
            perpage = 5;
            ArrayList<DBNation> sorted = new ArrayList<>(nations);

            if (sortBy != null) {
                Collections.sort(sorted, (o1, o2) -> Double.compare(((Number)sortBy.apply(o2)).doubleValue(), ((Number) sortBy.apply(o1)).doubleValue()));
            }

            List<String> results = new ArrayList<>();

            for (DBNation nation : sorted) {
                StringBuilder entry = new StringBuilder();
                entry.append("<" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nation.getNation_id() + ">")
                        .append(" | " + String.format("%16s", nation.getNation()))
                        .append(" | " + String.format("%16s", nation.getAllianceName()))
                        .append("\n```")
//                            .append(String.format("%5s", (int) nation.getScore())).append(" ns").append(" | ")
                        .append(String.format("%2s", nation.getCities())).append(" \uD83C\uDFD9").append(" | ")
                        .append(String.format("%5s", nation.getAvg_infra())).append(" \uD83C\uDFD7").append(" | ")
                        .append(String.format("%6s", nation.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                        .append(String.format("%5s", nation.getTanks())).append(" \u2699").append(" | ")
                        .append(String.format("%5s", nation.getAircraft())).append(" \u2708").append(" | ")
                        .append(String.format("%4s", nation.getShips())).append(" \u26F5").append(" | ")
                        .append(String.format("%1s", nation.getOff())).append(" \uD83D\uDDE1").append(" | ")
                        .append(String.format("%1s", nation.getDef())).append(" \uD83D\uDEE1").append(" | ")
                        .append(String.format("%2s", nation.getSpies())).append(" \uD83D\uDD0D").append(" | ");
//                Activity activity = nation.getActivity(14 * 12);
//                double loginChance = activity.loginChance((int) Math.max(1, (12 - (currentTurn % 12))), true);
//                int loginPct = (int) (loginChance * 100);
//                response.append("login=" + loginPct + "%").append(" | ");
                entry.append("```");

                results.add(entry.toString());
            }
            channel.create().paginate("Nations", command, page, perpage, results).send();
        }

        return null;
    }

    private static void printAA(StringBuilder response, DBNation nation, boolean spies) {
        response.append(String.format("%4s", TimeUtil.secToTime(TimeUnit.DAYS, nation.getAgeDays()))).append(" ");
        response.append(nation.toMarkdown(true, false, false, true, false, false));
        response.append(nation.toMarkdown(true, false, false, false, true, spies));
    }

    @Command(desc = "Add or subtract interest to a nation's balance based on their current balance")
    @RolePermission(value = Roles.ECON)
    public String interest(@Me IMessageIO channel, @Me GuildDB db, Set<DBNation> nations,
                           @Arg("A percent (out of 100) to apply to POSITIVE resources counts in their account balance")
                           @Range(min=-100, max=100) double interestPositivePercent,
                           @Arg("A percent (out of 100) to apply to NEGATIVE resources counts in their account balance")
                           @Range(min=-100, max=100) double interestNegativePercent) throws IOException, GeneralSecurityException {
        if (nations.isEmpty()) throw new IllegalArgumentException("No nations specified");
        if (nations.size() > 180) throw new IllegalArgumentException("Cannot do intest to that many people");

        interestPositivePercent /= 100d;
        interestNegativePercent /= 100d;

        Map<ResourceType, Double> total = new HashMap<>();
        Map<DBNation, Map<ResourceType, Double>> transfers = new HashMap<>();

        for (DBNation nation : nations) {
            Map<ResourceType, Double> deposits = ResourceType.resourcesToMap(nation.getNetDeposits(db, false));
            deposits.remove(ResourceType.CREDITS);
            deposits = PW.normalize(deposits);

            Map<ResourceType, Double> toAdd = new LinkedHashMap<>();
            for (Map.Entry<ResourceType, Double> entry : deposits.entrySet()) {
                ResourceType type = entry.getKey();
                double amt = entry.getValue();

                double interest;
                if (amt > 1 && interestPositivePercent > 0) {
                    interest = interestPositivePercent * amt;
                } else if (amt < -1 && interestNegativePercent > 0) {
                    interest = interestNegativePercent * amt;
                } else continue;

                toAdd.put(type, interest);
            }
            total = ResourceType.add(total, toAdd);

            if (toAdd.isEmpty()) continue;
            transfers.put(nation, toAdd);
        }

        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.TRANSFER_SHEET);
        List<String> header = new ArrayList<>(Arrays.asList("nation", "alliance", "cities"));
        for (ResourceType value : ResourceType.values) {
            if (value != ResourceType.CREDITS) {
                header.add(value.name());
            }
        }
        sheet.setHeader(header);

        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            ArrayList<Object> row = new ArrayList<>();
            DBNation nation = entry.getKey();

            row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
            row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));
            row.add(nation.getCities());
            Map<ResourceType, Double> transfer = entry.getValue();
            for (ResourceType type : ResourceType.values) {
                if (type != ResourceType.CREDITS) {
                    double amt = transfer.getOrDefault(type, 0d);
                    row.add(amt);
                }
            }

            sheet.addRow(row);
        }
        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        CM.deposits.addSheet cmd = CM.deposits.addSheet.cmd.sheet(sheet.getURL()).note("#deposit");

        IMessageBuilder msg = channel.create();
        StringBuilder result = new StringBuilder();
        sheet.attach(msg, "interest", result, false, 0);

        result.append("Total: `" + ResourceType.resourcesToString(total) + "`" +
                "\nWorth: $" + MathMan.format(ResourceType.convertedTotal(total)));
        result.append("\n\nUse " + CM.transfer.bulk.cmd.toSlashMention());
        result.append("\nOr press \uD83C\uDFE6 to run " + cmd.toSlashCommand() + "");

        String title = "Nation Interest";
        String emoji = "Confirm";

        msg.embed(title, result.toString())
                .commandButton(cmd, emoji)
                .send();

        return null;
    }

    @Command(aliases = {"dnr", "caniraid"}, desc = "Check if declaring war on a nation is allowed by the guild's Do Not Raid (DNR) settings")
    public String dnr(@Me GuildDB db, DBNation nation) {
        Integer dnrTopX = db.getOrNull(GuildKey.DO_NOT_RAID_TOP_X);
        Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);
        Set<Integer> canRaid = db.getCoalition(Coalition.CAN_RAID);
        Set<Integer> canRaidInactive = db.getCoalition(Coalition.CAN_RAID_INACTIVE);

        String title;
        Boolean dnr = db.getCanRaid().apply(nation);
        if (!dnr) {
            title = ("do NOT raid " + nation.getNation());
        }  else if (nation.getPosition() > 1 && nation.active_m() < 10000) {
            title = ("You CAN raid " + nation.getNation() + " (however they are an active member of an alliance), see also: " + CM.alliance.stats.counterStats.cmd.toSlashMention() + "");
        } else if (nation.getPosition() > 1) {
            title =  "You CAN raid " + nation.getNation() + " (however they are a member of an alliance), see also: " + CM.alliance.stats.counterStats.cmd.toSlashMention() + "";
        } else if (nation.getAlliance_id() != 0) {
            title =  "You CAN raid " + nation.getNation() + ", see also: " + CM.alliance.stats.counterStats.cmd.toSlashMention() + "";
        } else {
            title =  "You CAN raid " + nation.getNation();
        }
        StringBuilder response = new StringBuilder();
        response.append("**" + title + "**");
        if (dnrTopX != null) {
            response.append("\n\n> Do Not Raid Guidelines:");
            response.append("\n> - Avoid members of the top " + dnrTopX + " alliances and members of direct allies (check ingame treaties)");

            Set<String> enemiesStr = enemies.stream().map(f -> Locutus.imp().getNationDB().getAllianceName(f)).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> canRaidStr = canRaid.stream().map(f -> Locutus.imp().getNationDB().getAllianceName(f)).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> canRaidInactiveStr = canRaidInactive.stream().map(f -> Locutus.imp().getNationDB().getAllianceName(f)).filter(Objects::nonNull).collect(Collectors.toSet());

            if (!enemiesStr.isEmpty()) {
                response.append("\n> - Enemies: " + StringMan.getString(enemiesStr));
            }
            if (!canRaidStr.isEmpty()) {
                response.append("\n> - You CAN raid: " + StringMan.getString(canRaidStr));
            }
            if (!canRaidInactiveStr.isEmpty()) {
                response.append("\n> - You CAN raid inactives (1w) of: " + StringMan.getString(canRaidInactiveStr));
            }
        }
        return response.toString();
    }

    @Command(aliases = {"setloot"})
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setLoot(DBNation nation, Map<ResourceType, Double> resources, @Default("ESPIONAGE") NationLootType type, @Default("1") double fraction) {
        resources = PW.multiply(resources, 1d / fraction);
        double[] rssArr = ResourceType.resourcesToArray(resources);
        Locutus.imp().runEventsAsync(events ->
                LootEntry.forNation(nation.getNation_id(), System.currentTimeMillis(), rssArr, type)
                        .save(events));
        return "Set " + nation.getNation() + " to " + ResourceType.resourcesToString(resources) + " worth: ~$" + ResourceType.convertedTotal(resources);
    }

    @Command(desc = "Rank alliances by their new members over a timeframe")
    public String recruitmentRankings(@Me User author, @Me IMessageIO channel, @Me JSONObject command,
                                      @Arg("Date to start from")
                                      @Timestamp long cutoff,
                                      @Arg("Top X alliances to show in the ranking")
                                      @Range(min=1, max=150) @Default("80") int topX, @Switch("u") boolean uploadFile) {
        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances(true, true, true, topX);

        Map<DBAlliance, Integer> rankings = new HashMap<DBAlliance, Integer>();

        Set<Integer> aaIds = alliances.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());
        Map<Integer, List<AllianceChange>> removesByAlliance = Locutus.imp().getNationDB().getRemovesByAlliances(aaIds, cutoff);

        for (DBAlliance alliance : alliances) {
            Map<Integer, Long> noneToApp = new Int2LongOpenHashMap();
            Map<Integer, Long> appToMember = new Int2LongOpenHashMap();
            Map<Integer, Long> removed = new Int2LongOpenHashMap();

            List<AllianceChange> rankChanges = removesByAlliance.getOrDefault(alliance.getId(), new ArrayList<>());

            for (AllianceChange change : rankChanges) {
                int nationId = change.getNationId();
                if (change.getFromId() != alliance.getId()) {
                    if (change.getToId() == alliance.getId()) {
                        noneToApp.put(nationId, Math.max(noneToApp.getOrDefault(nationId, 0L), change.getDate()));
                        if (change.getToRank() != Rank.APPLICANT) {
                            appToMember.put(nationId, Math.max(appToMember.getOrDefault(nationId, 0L), change.getDate()));
                        }
                    }
                } else if (change.getToId() != alliance.getId()) {
                    removed.put(nationId, Math.max(removed.getOrDefault(nationId, 0L), change.getDate()));
                } else if (change.getToRank() != Rank.APPLICANT) {
                    appToMember.put(nationId, Math.max(appToMember.getOrDefault(nationId, 0L), change.getDate()));
                }
            }
            noneToApp.entrySet().removeIf(f -> removed.getOrDefault(f.getKey(), 0L) > f.getValue());
            appToMember.entrySet().removeIf(f -> removed.getOrDefault(f.getKey(), 0L) > f.getValue());

            // set where number is in both noneToApp and appToMember
            Set<Integer> both = noneToApp.keySet().stream().filter(appToMember::containsKey).collect(Collectors.toSet());
            int total = both.size();
            if (total > 0) {
                rankings.put(alliance, total);
            }
        }
        if (rankings.isEmpty()) {
            return "No new members found over the specified timeframe. Check your arguments are valid";
        }
        new SummedMapRankBuilder<>(rankings).sort().nameKeys(DBAlliance::getName).build(author, channel, command, "Most new members", uploadFile);
        return null;
    }

    @Command(desc = "Get the cost of military units and their upkeep")
    public String unitCost(Map<MilitaryUnit, Long> units,
                           @Arg("Show the upkeep during war time")
                           @Default Boolean wartime) {
        if (wartime == null) wartime = false;
        StringBuilder response = new StringBuilder();

        response.append("**Units " + StringMan.getString(units) + "**:\n");
        double[] cost = ResourceType.getBuffer();
        double[] upkeep = ResourceType.getBuffer();

        for (Map.Entry<MilitaryUnit, Long> entry : units.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            Long amt = entry.getValue();

            double[] unitCost = unit.getCost(amt.intValue()).clone();
            double[] unitUpkeep = unit.getUpkeep(wartime).clone();

            unitUpkeep = PW.multiply(unitUpkeep, amt);

            ResourceType.add(cost, unitCost);
            ResourceType.add(upkeep, unitUpkeep);
        }
        response.append("Cost:\n```" + ResourceType.resourcesToString(cost) + "``` ");
        if (ResourceType.resourcesToMap(cost).size() > 1) {
            response.append("Worth: ~$" + MathMan.format(ResourceType.convertedTotal(cost))).append("\n");
        }
        response.append("\nUpkeep:\n```" + ResourceType.resourcesToString(upkeep) + "``` ");
        if (ResourceType.resourcesToMap(upkeep).size() > 1) {
            response.append("Worth: ~$" + MathMan.format(ResourceType.convertedTotal(upkeep))).append("\n");
        }

        return response.toString();
    }

    // Helper function to check and update inactivity streaks
    private void checkAndUpdateStreak(Map<Integer, Long> lastActive, Map<Integer, Long> lastNotGray, Map<Integer, Long> lastStreak, Map<Integer, Integer> countMap, int nationId, long day, int daysInactive) {
        long lastActiveDay = lastActive.getOrDefault(nationId, day);
        long lastNotGrayDay = lastNotGray.getOrDefault(nationId, day);
        long lastActiveAdded = lastStreak.getOrDefault(nationId, 0L);
        if (lastActiveAdded == lastActiveDay) return;

        if (day - lastNotGrayDay >= daysInactive) {
            countMap.put(nationId, countMap.getOrDefault(nationId, 0) + 1);
            lastStreak.put(nationId, lastActiveAdded);
        }
    }

    @Command(desc = "Get the inactivity streak of a set of nations over a specified timeframe")
    public String grayStreak(@Me GuildDB db, @Me IMessageIO io,
                                    Set<DBNation> nations,
                                    int daysInactive,
                                    @Timestamp long timeframe,
                                    @Switch("s") SpreadSheet sheet) throws IOException, ParseException, GeneralSecurityException {
        long minNationAge = Long.MAX_VALUE;
        for (DBNation nation : nations) {
            minNationAge = Math.min(minNationAge, nation.getDate());
        }
        minNationAge = Math.max(minNationAge, timeframe);
        Set<Integer> nationIds = new IntOpenHashSet(nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet()));

        DataDumpParser parser = Locutus.imp().getDataDumper(true);
        List<Long> validDays = parser.getDays(true, false);
        long today = TimeUtil.getDay();
        long finalMinDay = TimeUtil.getDay(minNationAge);

        Map<Integer, Long> lastActive = new Int2LongOpenHashMap();
        Map<Integer, Long> lastNotGray = new Int2LongOpenHashMap();
        Map<Integer, Long> lastStreak = new Int2ObjectOpenHashMap<>();
        Map<Integer, Integer> countMap = new Int2ObjectOpenHashMap<>();

        parser.iterateAll(f -> f >= finalMinDay, (h, r) -> r.required(h.nation_id).optional(h.vm_turns).required(h.color), null, new BiConsumer<Long, NationHeader>() {
            @Override
            public void accept(Long day, NationHeader header) {
                int nationId = header.nation_id.get();
                if (!nationIds.contains(nationId)) return;

                NationColor color = header.color.get();
                if (color != NationColor.GRAY) {
                    lastNotGray.put(nationId, day);
                    if (color != NationColor.BEIGE) {
                        lastActive.put(nationId, day);
                    }
                    checkAndUpdateStreak(lastActive, lastNotGray, lastStreak, countMap, nationId, day, daysInactive);
                }
            }
        }, null, new Consumer<Long>() {
            @Override
            public void accept(Long day) {
                // Called when all nations have been processed for a day
            }
        });
        for (int nationId : nationIds) {
            checkAndUpdateStreak(lastActive, lastNotGray, lastStreak, countMap, nationId, today, daysInactive);
        }

        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.INACTIVITY_STREAK);
        }

        List<String> header = new ArrayList<>(Arrays.asList("nation", "alliance", "streak"));
        sheet.setHeader(header);
        for (DBNation nation : nations) {
            int nationId = nation.getNation_id();
            int streak = countMap.getOrDefault(nationId, 0);
            if (streak > 0) {
                ArrayList<Object> row = new ArrayList<>();
                row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
                row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));
                row.add(streak);
                sheet.addRow(row);
            }
        }
        sheet.updateClearCurrentTab();
        sheet.updateWrite();
        sheet.attach(io.create(), "inactivity").send();
        return null;
    }


    @Command(desc = "Get the VM history of a set of nations")
    public static String vmHistory(@Me IMessageIO io, Set<DBNation> nations, @Switch("s") SpreadSheet sheet) throws IOException, ParseException {
        if (nations.size() > 1000) {
            throw new IllegalArgumentException("Cannot check more than 1000 nations (" + nations.size() + " provided)");
        }

        long minDay = Long.MAX_VALUE;
        for (DBNation nation : nations) {
            minDay = Math.min(minDay, TimeUtil.getDay(nation.getDate()));
        }

        Map<Integer, DBNation> nationMap = nations.stream().collect(Collectors.toMap(DBNation::getNation_id, f -> f));
        Map<Integer, Set<Long>> daysVM = new HashMap<>();
        Map<Integer, Set<Long>> daysPresentNotVM = new HashMap<>();

        Predicate<Integer> contains;
        if (nations.size() == 1) {
            int natId = nations.iterator().next().getNation_id();
            contains = f -> f == natId;
        } else {
            Set<Integer> natIds = new IntOpenHashSet(nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet()));
            contains = natIds::contains;
        }

        long[] lastMsg = {System.currentTimeMillis()};
        CompletableFuture<IMessageBuilder> msgFuture = io.send("Mounting nation snapshots...");

        DataDumpParser parser = Locutus.imp().getDataDumper(true);
        List<Long> validDays = parser.getDays(true, false);
        long today = TimeUtil.getDay();

        long finalMinDay = minDay;
        parser.iterateAll(f -> f >= finalMinDay, (h, r) -> r.required(h.nation_id).optional(h.vm_turns), null, new BiConsumer<Long, NationHeader>() {
            private long lastDay = -1;

            @Override
            public void accept(Long day, NationHeader header) {
                int nationId = header.nation_id.get();
                if (!contains.test(nationId)) return;
                if (lastDay != day) {
                    lastDay = day;
                }
                Integer vmTurns = header.vm_turns.get();
                if (vmTurns != null && vmTurns > 0) {
                    daysVM.computeIfAbsent(nationId, f -> new LinkedHashSet<>()).add(day);
                } else {
                    daysPresentNotVM.computeIfAbsent(nationId, f -> new LinkedHashSet<>()).add(day);
                }
            }
        }, null, new Consumer<Long>() {
            @Override
            public void accept(Long day) {
                long now = System.currentTimeMillis();
                if (now - lastMsg[0] > 5000) {
                    lastMsg[0] = now;
                    io.updateOptionally(msgFuture, (day - finalMinDay) + "/" + (today - finalMinDay));
                }
            }
        });

        Map<Integer, List<String[]>> changesByNation = new LinkedHashMap<>();

        for (DBNation nation : nations) {
            long dayStart = TimeUtil.getDay(nation.getDate());
            long lastStatus = -1; // -1 indicates no previous status
            for (long day = dayStart; day < today; day++) {
                if (!validDays.contains(day)) continue;
                Set<Long> vmDays = daysVM.getOrDefault(nation.getNation_id(), Collections.emptySet());
                Set<Long> presentNotVMDays = daysPresentNotVM.getOrDefault(nation.getNation_id(), Collections.emptySet());

                long currentStatus;
                if (vmDays.contains(day)) {
                    currentStatus = 2; // VM
                } else if (presentNotVMDays.contains(day)) {
                    currentStatus = 0; // ACTIVE
                } else {
                    currentStatus = 1; // VM_ABSENT
                }
                if (lastStatus != -1 && lastStatus != currentStatus) {
                    String statusString = switch ((int) currentStatus) {
                        case 0 -> "ACTIVE";
                        case 1 -> "VM_ABSENT";
                        case 2 -> "VM";
                        default -> "UNKNOWN";
                    };
                    long timeMs = TimeUtil.getTimeFromDay(day);
                    changesByNation.computeIfAbsent(nation.getNation_id(), k -> new ArrayList<>())
                            .add(new String[]{TimeUtil.DD_MM_YYYY.format(new Date(timeMs)), statusString});
                }
                lastStatus = currentStatus;
            }
        }

        if (changesByNation.isEmpty()) {
            return "No changes found in VM status for the specified nations";
        }

        if (sheet != null) {
            List<String> header = new ArrayList<>(Arrays.asList("nation", "date", "status"));
            sheet.setHeader(header);
            for (Map.Entry<Integer, List<String[]>> entry : changesByNation.entrySet()) {
                int nationId = entry.getKey();
                DBNation nation = nationMap.get(nationId);
                for (String[] change : entry.getValue()) {
                    ArrayList<Object> row = new ArrayList<>();
                    row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
                    row.add(change[0]);
                    row.add(change[1]);
                    sheet.addRow(row);
                }
            }
            sheet.updateClearCurrentTab();
            sheet.updateWrite();
            sheet.attach(io.create(), "revenue").send();
        } else {
            StringBuilder file = new StringBuilder();
            file.append("nation\tdate\tstatus\n");
            for (Map.Entry<Integer, List<String[]>> entry : changesByNation.entrySet()) {
                int nationId = entry.getKey();
                DBNation nation = nationMap.get(nationId);
                for (String[] change : entry.getValue()) {
                    file.append(nation.getNation()).append("\t").append(change[0]).append("\t").append(change[1]).append("\n");
                }
            }
            IMessageBuilder msg = io.create();
            if (nations.size() == 1) {
                msg.append(file.toString());
            } else {
                msg = msg.file("vm_history.txt", file.toString());
            }
            msg.send();
        }
        return null;
    }

    @Command(aliases = {"alliancecost", "aacost"}, desc = "Get the value of nations including their cities, projects and units")
    public String allianceCost(@Me IMessageIO channel, @Me GuildDB db,
                               NationList nations, @Switch("u") boolean update, @Switch("s") @Timestamp Long snapshotDate) {
        Set<DBNation> nationSet = PW.getNationsSnapshot(nations.getNations(), nations.getFilter(), snapshotDate, db.getGuild(), false);
        double infraCost = 0;
        double landCost = 0;
        double cityCost = 0;
        Map<ResourceType, Double> projectCost = new HashMap<>();
        Map<ResourceType, Double> militaryCost = new HashMap<>();
        Map<ResourceType, Double> buildingCost = new HashMap<>();
        for (DBNation nation : nationSet) {
            Set<Project> projects = nation.getProjects();
            for (Project project : projects) {
                projectCost = ResourceType.addResourcesToA(projectCost, project.cost());
            }
            for (MilitaryUnit unit : MilitaryUnit.values) {
                int units = nation.getUnits(unit);
                militaryCost = ResourceType.addResourcesToA(militaryCost, ResourceType.resourcesToMap(unit.getCost(units)));
            }
            int cities = nation.getCities();
            for (int i = 1; i <= cities; i++) {
                boolean manifest = true;
                boolean cp = i > Projects.URBAN_PLANNING.requiredCities() && projects.contains(Projects.URBAN_PLANNING);
                boolean acp = i > Projects.ADVANCED_URBAN_PLANNING.requiredCities() && projects.contains(Projects.ADVANCED_URBAN_PLANNING);
                boolean mp = i > Projects.METROPOLITAN_PLANNING.requiredCities() && projects.contains(Projects.METROPOLITAN_PLANNING);
                boolean gsa = projects.contains(Projects.GOVERNMENT_SUPPORT_AGENCY);
                cityCost += PW.City.nextCityCost(i, manifest, cp, acp, mp, gsa);
            }
            Map<Integer, JavaCity> cityMap = nation.getCityMap(update, false);
            for (Map.Entry<Integer, JavaCity> cityEntry : cityMap.entrySet()) {
                JavaCity city = cityEntry.getValue();
                {
                    double landFactor = 1;
                    double infraFactor = 0.95;
                    if (projects.contains(Projects.ARABLE_LAND_AGENCY)) landFactor *= 0.95;
                    if (projects.contains(Projects.CENTER_FOR_CIVIL_ENGINEERING)) infraFactor *= 0.95;
                    landCost += PW.City.Land.calculateLand(PW.City.Land.NEW_CITY_BASE, city.getLand()) * landFactor;
                    infraCost += PW.City.Infra.calculateInfra(PW.City.Infra.NEW_CITY_BASE, city.getInfra()) * infraFactor;
                }
                city = cityEntry.getValue();
                JavaCity empty = new JavaCity();
                empty.setLand(city.getLand());
                empty.setInfra(city.getInfra());
                double[] myBuildingCost = city.calculateCost(empty);
                buildingCost = ResourceType.addResourcesToA(buildingCost, ResourceType.resourcesToMap(myBuildingCost));
            }
        }

        Map<ResourceType, Double> total = new HashMap<>();
        total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + infraCost);
        total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + landCost);
        total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + cityCost);
        total = ResourceType.add(total, projectCost);
        total = ResourceType.add(total, militaryCost);
        total = ResourceType.add(total, buildingCost);
        double totalConverted = ResourceType.convertedTotal(total);
        String title = nationSet.size() + " nations worth ~$" + MathMan.format(totalConverted);

        StringBuilder response = new StringBuilder();
        response.append("**Infra**: $" + MathMan.format(infraCost));
        response.append("\n").append("**Land**: $" + MathMan.format(landCost));
        response.append("\n").append("**Cities**: $" + MathMan.format(cityCost));
        response.append("\n").append("**Projects**: $" + MathMan.format(ResourceType.convertedTotal(projectCost)) + "\n`" + ResourceType.resourcesToString(projectCost) + "`");
        response.append("\n").append("**Military**: $" + MathMan.format(ResourceType.convertedTotal(militaryCost)) + "\n`" + ResourceType.resourcesToString(militaryCost) + "`");
        response.append("\n").append("**Buildings**: $" + MathMan.format(ResourceType.convertedTotal(buildingCost)) + "\n`" + ResourceType.resourcesToString(buildingCost) + "`");
        response.append("\n").append("**Total**: $" + MathMan.format(ResourceType.convertedTotal(total)) + "\n`" + ResourceType.resourcesToString(total) + "`");

        channel.create().embed(title, response.toString()).send();
        return null;
    }

    @Command(desc = "Get the cost a specific amount of buildings")
    public static String buildingCost(CityBuild build) {
        JavaCity jc = new JavaCity(build);
        jc.setInfra(0d);
        jc.setLand(0d);

        JavaCity origin = new JavaCity();
        double[] cost = jc.calculateCost(origin);

        StringBuilder response = new StringBuilder();
        // buildings
        Map<Building, Integer> buildings = new LinkedHashMap<>();
        for (Building building : Buildings.values()) {
            int amt = jc.getBuilding(building);
            if (amt > 0) {
                buildings.put(building, amt);
            }
        }
        // Append buildings
        response.append("**Buildings:**\n```json\n" + buildings + "\n```");
        // append cost
        response.append("\n**Cost:** ~$" + MathMan.format(ResourceType.convertedTotal(cost)));
        response.append("\n```json\n" + ResourceType.resourcesToString(cost) + "\n```");
        return response.toString();
    }


    @Command(desc = "Add a watermark to a discord image\n" +
            "Use \\n to add a new line\n" +
            "Default color will be light gray if image is dark and dark gray if image is light\n" +
            "Set `repeat: True` to repeat the watermark down the entire image")
    public String addWatermark(@Me IMessageIO io, String imageUrl, String watermarkText, @Default Color color, @Default("0.05") @Range(min = 0.01, max=1) double opacity, @Default("Arial") Font font, @Switch("r") boolean repeat) {
        float opacityF = (float) opacity;
        // remove anything after ? mark
        String imageStub = imageUrl.split("\\?")[0];

        if (!ImageUtil.isDiscordImage(imageUrl)) {
            throw new IllegalArgumentException("Image must be a discord image, not: `" + imageStub + "`");
        }

        BufferedImage image = ImageUtil.readImage(imageUrl);
        if (color == null) color = ImageUtil.getDefaultWatermarkColor(image);
        byte[] bytes = ImageUtil.addWatermark(image, watermarkText, color, opacityF, font, repeat);
        io.create().image("locutus-watermark.png", bytes).send();
        return null;
    }

    @Command(desc = "Calculate how many days it takes to ROI on the last improvement slot for a specified infra level")
    public String infraROI(DBCity city, @Range(min=600,max=3000) int infraLevel,
                           @Switch("c") Continent continent,
                           @Switch("r") Double rads,
                           @Switch("p") Set<Project> forceProjects,
                           @Switch("d") boolean openMarkets,
                           @Switch("m") MMRInt mmr,
                           @Switch("l") Double land
    ) {
        DBNation nation = DBNation.getById(city.getNationId());
        if (nation == null) return "Unknown nation: `" + city.nation_id + "`";
        if (infraLevel > 3500) return "Infra level too high (max 3,500)";

        JavaCity origin = city.toJavaCity(nation).setInfra(infraLevel).zeroNonMilitary();
        JavaCity originMinus50 = city.toJavaCity(nation).setInfra(infraLevel - 50).zeroNonMilitary();
        if (mmr != null) {
            origin.setMMR(mmr);
            originMinus50.setMMR(mmr);
        }
        if (land != null) {
            origin.setLand(land);
            originMinus50.setLand(land);
        }

        int numCities = nation.getCities();
        if (continent == null) continent = nation.getContinent();
        origin.setOptimalPower(continent);
        originMinus50.setOptimalPower(continent);

        if (rads == null) rads = nation.getRads();
        Predicate<Project> hasProject = forceProjects != null ? f -> forceProjects.contains(f) || nation.hasProject(f) : nation::hasProject;
        double grossModifier = DBNation.getGrossModifier(false, openMarkets, hasProject.test(Projects.GOVERNMENT_SUPPORT_AGENCY));

        JavaCity optimal1 = origin.optimalBuild(nation, 5000, false, null);
        if (optimal1 == null) {
            return "Cannot generate optimal city build";
        }
        JavaCity optimal2 = originMinus50.optimalBuild(nation, 5000, false, null);
        if (optimal2 == null) {
            return "Cannot generate optimal city build";
        }
        double revenue1 = optimal1.profitConverted2(continent, rads, hasProject, numCities, grossModifier);
        double revenue2 = optimal2.profitConverted2(continent, rads, hasProject, numCities, grossModifier);
        double profit = revenue1 - revenue2;
        if (profit <= 0) return "No ROI for `" + infraLevel + "`";
        double cost = nation.infraCost(infraLevel - 50, infraLevel);
        double daysROI = cost / profit;
        return "Infra: " + MathMan.format(infraLevel - 50) + "->" + MathMan.format(infraLevel) + " will break even in " + MathMan.format(daysROI) + " days";
    }

    @Command(desc = "Calculate how many days it takes to ROI on the last 50 land for a specified level")
    public String landROI(DBCity city, @Range(min=600,max=10000) double landLevel,
                           @Switch("c") Continent continent,
                           @Switch("r") Double rads,
                           @Switch("p") Set<Project> forceProjects,
                          @Switch("d") boolean openMarkets,
                          @Switch("m") MMRInt mmr,
                          @Switch("l") Double infra

    ) {
        DBNation nation = DBNation.getById(city.getNationId());
        if (nation == null) return "Unknown nation: `" + city.nation_id + "`";
        if (landLevel > 10000) return "Land level too high (max 10,000)";

        JavaCity origin = city.toJavaCity(nation).setLand(landLevel).zeroNonMilitary();
        JavaCity originMinus50 = city.toJavaCity(nation).setLand(landLevel - 50).zeroNonMilitary();
        if (mmr != null) {
            origin.setMMR(mmr);
            originMinus50.setMMR(mmr);
        }
        if (infra != null) {
            origin.setInfra(infra);
            originMinus50.setInfra(infra);
        }

        int numCities = nation.getCities();
        if (continent == null) continent = nation.getContinent();
        origin.setOptimalPower(continent);
        originMinus50.setOptimalPower(continent);
        if (rads == null) rads = nation.getRads();
        Predicate<Project> hasProject = forceProjects != null ? f -> forceProjects.contains(f) || nation.hasProject(f) : nation::hasProject;
        double grossModifier = DBNation.getGrossModifier(false, openMarkets, hasProject.test(Projects.GOVERNMENT_SUPPORT_AGENCY));

        JavaCity optimal1 = origin.optimalBuild(nation, 5000, false, null);
        if (optimal1 == null) {
            return "Cannot generate optimal city build";
        }
        JavaCity optimal2 = originMinus50.optimalBuild(nation, 5000, false, null);
        if (optimal2 == null) {
            return "Cannot generate optimal city build";
        }
        double revenue1 = optimal1.profitConverted2(continent, rads, hasProject, numCities, grossModifier);
        double revenue2 = optimal2.profitConverted2(continent, rads, hasProject, numCities, grossModifier);
        double profit = revenue1 - revenue2;
        if (profit <= 0) return "No ROI for `" + MathMan.format(landLevel) + "`";
        double cost = nation.landCost(landLevel - 50, landLevel);
        double daysROI = cost / profit;
        return "Land: " + MathMan.format(landLevel - 50) + "->" + MathMan.format(landLevel) + " will break even in " + MathMan.format(daysROI) + " days";
    }
}