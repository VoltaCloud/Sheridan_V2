package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.politicsandwar.graphql.model.BBGame;
import com.ptsmods.mysqlw.query.QueryCondition;
import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.graphics.Insets2D;
import de.erichseifert.gral.graphics.Location;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.BarPlot;
import de.erichseifert.gral.plots.colors.ColorMapper;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.AlliancePlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.rankings.WarCostAB;
import link.locutus.discord.commands.rankings.WarCostRanking;
import link.locutus.discord.commands.rankings.builder.*;
import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeDualNumericTable;
import link.locutus.discord.commands.rankings.table.TimeFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BaseballDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.OrbisMetric;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.math.CIEDE2000;
import link.locutus.discord.util.scheduler.TriFunction;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StatCommands {
    @Command(desc = "Display a graph of the number of attacks by the specified nations per day over a time period")
    public String warAttacksByDay(@Me IMessageIO io, @Default Set<DBNation> nations,
                                  @Arg("Period of time to graph") @Default @Timestamp Long cutoff,
                                  @Arg("Restrict to a list of attack types") @Default Set<AttackType> allowedTypes) throws IOException {
        if (cutoff == null) cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        Long finalCutoff = cutoff;

        long minDay = TimeUtil.getDay(cutoff);
        long maxDay = TimeUtil.getDay();
        if (maxDay - minDay > 50000) {
            throw new IllegalArgumentException("Too many days.");
        }

        Predicate<AttackType> alllowedType = allowedTypes == null ? f -> true : allowedTypes::contains;

        Map<Integer, DBWar> wars;
        if (nations == null) {
            wars = Locutus.imp().getWarDb().getWarsSince(cutoff);
        } else {
            Set<Integer> nationIds = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
            wars = Locutus.imp().getWarDb().getWarsForNationOrAlliance(nationIds::contains, null, f -> f.possibleEndDate() >= finalCutoff);
        }

        final List<AbstractCursor> attacks = new ArrayList<>();
        Locutus.imp().getWarDb().getAttacks(wars.values(), alllowedType, new Predicate<AbstractCursor>() {
            @Override
            public boolean test(AbstractCursor attack) {
                if (attack.getDate() > finalCutoff) {
                    attacks.add(attack);
                }
                return false;
            }
        }, f -> false);

        Map<Long, Integer> totalAttacksByDay = new HashMap<>();

        for (AbstractCursor attack : attacks) {
            long day = TimeUtil.getDay(attack.getDate());
            totalAttacksByDay.put(day, totalAttacksByDay.getOrDefault(day, 0) + 1);
        }

        List<String> sheet = new ArrayList<>(List.of(
                "Day,Attacks"
        ));
        TimeNumericTable<Void> table = new TimeNumericTable<>("Total attacks by day", "day", "attacks", "") {
            @Override
            public void add(long day, Void ignore) {
                long offset = day - minDay;
                int attacks = totalAttacksByDay.getOrDefault(day, 0);
                add(offset, attacks);
                String dayStr = TimeUtil.YYYY_MM_DD_FORMAT.format(new Date(TimeUtil.getTimeFromDay(day)));
                sheet.add(dayStr + "," + attacks);
            }
        };

        for (long day = minDay; day <= maxDay; day++) {
            table.add(day, (Void) null);
        }

        io.create()
                .file("img.png", table.write(TimeFormat.DAYS_TO_DATE, TableNumberFormat.SI_UNIT))
                .file("data.csv", StringMan.join(sheet, "\n"))
                .append("Done!")
                .send();
        return null;
    }

    @Command(desc = "Rank war costs between two parties", groups = {
            "Time period",
            "Nations",
            "Ranking Statistic",
            "Cost Exclusions",
            "Grouping/Scaling",
            "War Filtering"

    }, groupDescs = {
            "The time period to rank the wars",
            "The nations/alliances to include",
            "The statistic and mode to rank by",
            "Exclude certain kinds of costs from the ranking",
            "City, war scaling, and enable ranking by alliance (instead of nation)",
            "Specify the kind of wars to include"
    })
    public String warCostRanking(@Me IMessageIO io, @Me User author, @Me JSONObject command,
                                 @Arg(value = "Start time of the period to rank\n" +
                                 "Defaults to 7d", group = 0)
                                 @Default("7d") @Timestamp long timeStart,
                                 @Arg(value = "End time of the period to rank\n" +
                                         "Defaults to now", group = 0)
                                 @Timestamp @Default Long timeEnd,
                                 @Arg(value = "Nations required to be in the conflicts\n" +
                                         "Defaults to all existing nations", group = 1)
                                 @Default("*") Set<NationOrAlliance> coalition1,
                                 @Arg(value = "Nations required to be in the conflicts against `coalition1`\n" +
                                         "Defaults to all nations", group = 1)
                                 @Default() Set<NationOrAlliance> coalition2,
                                 @Arg(value = "Only rank the nations in `coalition1`\n" +
                                         "Defaults to false", group = 1)
                                 @Switch("a") boolean onlyRankCoalition1,
                                 @Arg(value = "Cost Type", group = 2)
                                     @Switch("t") WarCostMode type,
                                 @Arg(value = "Rank a specific stat, such as soldiers\n" +
                                         "Defaults to `DAMAGE`", group = 2, aliases = {"attacktype", "resource", "unitloss", "unitkill"})
                                     @Switch("s") WarCostStat stat,
                                 @Arg(value = "Exclude infrastructure", group = 3)
                                 @Switch("i") boolean excludeInfra,
                                    @Arg(value = "Exclude consumption", group = 3)
                                 @Switch("c") boolean excludeConsumption,
                                    @Arg(value = "Exclude loot", group = 3)
                                 @Switch("l") boolean excludeLoot,
                                    @Arg(value = "Exclude building losses", group = 3)
                                 @Switch("b") boolean excludeBuildings,
                                    @Arg(value = "Exclude unit losses", group = 3)
                                 @Switch("u") boolean excludeUnits,
                                 @Arg(value = "Rank alliances", group = 4) @Switch("g") boolean groupByAlliance,
                                 @Arg(value = "Scale rankings per war", group = 4) @Switch("w") boolean scalePerWar,
                                 @Arg(value = "Scale rankings by city count", group = 4) @Switch("p") boolean scalePerCity,
                                 @Arg(value = "Filter the war types included", group = 5)
                                 @Switch("wartype") Set<WarType> allowedWarTypes,
                                 @Arg(value = "Filter the war statuses included", group = 5)
                                 @Switch("status") Set<WarStatus> allowedWarStatuses,
                                 @Arg(value = "Filter the attack types included", group = 5)
                                 @Switch("attacks") Set<AttackType> allowedAttacks,
                                 @Switch("off") @Arg(value = "Only include wars declared by coalition1", group = 5) boolean onlyOffensiveWars,
                                 @Switch("def") @Arg(value = "Only include wars declared by coalition2", group = 5) boolean onlyDefensiveWars,
                                 @Switch("f") boolean uploadFile
    ) {
        if (timeEnd == null) timeEnd = Long.MAX_VALUE;
        if (stat == null) stat = WarCostStat.WAR_VALUE;
        if (type == null) type = WarCostMode.DEALT;

        WarParser parser = WarParser.of(coalition1, coalition2, timeStart, timeEnd)
                .allowWarStatuses(allowedWarStatuses)
                .allowedWarTypes(allowedWarTypes)
                .allowedAttackTypes(allowedAttacks);
        if (onlyOffensiveWars) {
            parser.getAttacks().removeIf(f -> !parser.getIsPrimary().apply(f.getWar()));
        }
        if (onlyDefensiveWars) {
            parser.getAttacks().removeIf(f -> parser.getIsPrimary().apply(f.getWar()));
        }
        if (type == WarCostMode.PROFIT && stat.unit() != null) {
            throw new IllegalArgumentException("Cannot rank by `type: profit` with a unit stat");
        }
        if (type == WarCostMode.PROFIT && stat.attack() != null) {
            throw new IllegalArgumentException("Cannot rank by `type: profit` with an attack type stat");
        }

        String diffStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, (Math.min(System.currentTimeMillis(), timeEnd) - timeStart));

        String title = (groupByAlliance ? "Alliance" : "Nation") + " " + stat.name() + " " + type.name();
        if (scalePerWar && scalePerCity) {
            title += "/war*city";
        } else if (scalePerWar) {
            title += "/war";
        } else if (scalePerCity) {
            title += "/city";
        }
        title += " (%s)";
        title = String.format(title, diffStr);

        Map<Integer, DBWar> wars = parser.getWars();

        BiFunction<Boolean, AbstractCursor, Double> valueFunc = stat.getFunction(excludeUnits, excludeInfra, excludeConsumption, excludeLoot, excludeBuildings);

        GroupedRankBuilder<Integer, AbstractCursor> nationAllianceGroup = new RankBuilder<>(parser.getAttacks())
                .group((attack, map) -> {
                    // Group attacks into attacker and defender
                    DBWar war = wars.get(attack.getWar_id());
                    int attId,defId;
                    if (groupByAlliance) {
                        attId = war.getAttacker_aa();
                        defId = war.getDefender_aa();
                    } else {
                        attId = war.getAttacker_id();
                        defId = war.getDefender_id();
                    }
                    if (onlyRankCoalition1) {
                        if (parser.getIsPrimary().apply(war)) {
                            map.put(attId, attack);
                        } else {
                            map.put(defId, attack);
                        }
                    } else {
                        map.put(attId, attack);
                        map.put(defId, attack);
                    }
                });

        // war
        // nation
        // alliance
        BiFunction<Integer, AbstractCursor, Integer> groupBy;
        if (groupByAlliance) groupBy = (attacker, attack) -> wars.get(attack.getWar_id()).getNationId(attacker);
        else groupBy = (attacker, attack) -> attack.getWar_id();

        NumericMappedRankBuilder<Integer, Integer, Double> byGroupMap;

        BiFunction<Boolean, AbstractCursor, Double> applyDealt;
        BiFunction<Boolean, AbstractCursor, Double> applyReceived;
        if (type.includeDealt()) {
            if (type.addDealt()) {
                applyDealt = (isAttacker, attack) -> valueFunc.apply(!isAttacker, attack);
            } else {
                applyDealt = (isAttacker, attack) -> -valueFunc.apply(!isAttacker, attack);
            }
        } else {
            applyDealt = null;
        }
        if (type.includeReceived()) {
            if (type.addReceived()) {
                applyReceived = (isAttacker, attack) -> valueFunc.apply(isAttacker, attack);
            } else {
                applyReceived = (isAttacker, attack) -> -valueFunc.apply(isAttacker, attack);
            }
        } else {
            applyReceived = null;
        }

        BiFunction<Boolean, AbstractCursor, Double> applyBoth;
        if (applyDealt != null) {
            if (applyReceived != null) {
                applyBoth = (isAttacker, attack) -> applyDealt.apply(isAttacker, attack) + applyReceived.apply(isAttacker, attack);
            } else {
                applyBoth = applyDealt;
            }
        } else if (applyReceived != null) {
            applyBoth = applyReceived;
        } else {
            applyBoth = (isAttacker, attack) -> 0d;
        }
        Map<Integer, Integer> warsByGroup = null;
        if (scalePerWar) {
            warsByGroup = new Int2IntOpenHashMap();
            if (groupByAlliance) {
                for (DBWar war : wars.values()) {
                    warsByGroup.merge(war.getAttacker_aa(), 1, Integer::sum);
                    warsByGroup.merge(war.getDefender_aa(), 1, Integer::sum);
                }
            } else {
                for (DBWar war : wars.values()) {
                    warsByGroup.merge(war.getAttacker_id(), 1, Integer::sum);
                    warsByGroup.merge(war.getDefender_id(), 1, Integer::sum);
                }
            }
        }
        TriFunction<Integer, DBWar, Double, Double> scaleFunc = WarCostRanking.getScaleFunction(scalePerCity, warsByGroup, groupByAlliance);

        byGroupMap = nationAllianceGroup.map(groupBy, (byNatOrAA, attack) -> {
            DBWar war = wars.get(attack.getWar_id());
            int nationId = groupByAlliance ? war.getNationId(byNatOrAA) : byNatOrAA;
            boolean isAttacker = attack.getAttacker_id() == nationId;
            double totalVal = applyBoth.apply(isAttacker, attack);
            return scaleFunc.apply(nationId, war, totalVal);
        });

        SummedMapRankBuilder<Integer, Number> byGroupSum;
        byGroupSum = byGroupMap.sum();

        RankBuilder<String> ranks = byGroupSum
                .sort()
                .nameKeys(id -> PW.getName(id, groupByAlliance));

        // Embed the rank list

        ranks.build(io, command, title, uploadFile);
        return null;
    }

    @Command(desc = "War costs stats between two coalitions")
    public String myloot(@Me IMessageIO channel, @Me DBNation nation, @Me JSONObject command,
                         Set<NationOrAlliance> coalition2, @Timestamp long timeStart,
                         @Default @Timestamp Long timeEnd,
                         @Switch("u") boolean ignoreUnits,
                         @Switch("i") boolean ignoreInfra,
                         @Switch("c") boolean ignoreConsumption,
                         @Switch("l") boolean ignoreLoot,
                         @Switch("b") boolean ignoreBuildings,

                         @Arg("Return a list of war ids") @Switch("id") boolean listWarIds,
                         @Arg("Return a tally of war types") @Switch("t") boolean showWarTypes,

                         @Switch("w") Set<WarType> allowedWarTypes,
                         @Switch("s") Set<WarStatus> allowedWarStatus,
                         @Switch("a") Set<AttackType> allowedAttackTypes,
                         @Switch("v") Set<SuccessType> allowedVictoryTypes) {
        if (command != null && coalition2 != null && command.getString("coalition2").equalsIgnoreCase("*")) {
            coalition2 = null;
        }
        return warsCost(channel, null, Collections.singleton(nation), coalition2, timeStart, timeEnd,
                ignoreUnits, ignoreInfra, ignoreConsumption, ignoreLoot, ignoreBuildings, listWarIds, showWarTypes,
                allowedWarTypes, allowedWarStatus, allowedAttackTypes, allowedVictoryTypes, false, false, false, false);
    }

    @Command(desc = "War costs of a single war\n(use warsCost for multiple wars)")
    public static String warCost(@Me User author, @Me Guild guild, @Me IMessageIO channel, DBWar war,
                          @Switch("u") boolean ignoreUnits,
                          @Switch("i") boolean ignoreInfra,
                          @Switch("c") boolean ignoreConsumption,
                          @Switch("l") boolean ignoreLoot,
                          @Switch("b") boolean ignoreBuildings) {
        AttackCost cost = war.toCost();
        if (Roles.ECON.has(author, guild)) {
            WarCostAB.reimburse(cost, war, guild, channel);
        }
        return cost.toString(!ignoreUnits, !ignoreInfra, !ignoreConsumption, !ignoreLoot, !ignoreBuildings);
    }

    @Command(desc = "War costs between two coalitions over a time period", groups = {
        "The Sides Fighting",
        "Time period",
        "Cost Exclusions",
        "Display Options",
        "War and attack type filters"
    })
    public static String warsCost(@Me IMessageIO channel,
                           @Me JSONObject command,
                          @Arg(value = "Nations required to be in the conflict against `coalition2`", group = 0)
                          Set<NationOrAlliance> coalition1,
                          @Arg(value = "Nations required to be in the conflicts against `coalition1`", group = 0)
                          Set<NationOrAlliance> coalition2,
                           @Arg(value = "Start time of the period to include", group = 1)
                           @Timestamp long timeStart,
                                  @Arg(value = "End time of the period to rank\n" +
                                          "Defaults to now", group = 1)
                           @Default @Timestamp Long timeEnd,

                           @Arg(value = "Exclude unit costs", group = 2)
                           @Switch("u") boolean ignoreUnits,
                            @Arg(value = "Exclude infrastructure costs", group = 2)
                           @Switch("i") boolean ignoreInfra,
                            @Arg(value = "Exclude consumption costs", group = 2)
                           @Switch("c") boolean ignoreConsumption,
                            @Arg(value = "Exclude loot costs", group = 2)
                           @Switch("l") boolean ignoreLoot,
                            @Arg(value = "Exclude building costs", group = 2)
                           @Switch("b") boolean ignoreBuildings,

                           // display options
                           @Arg(value = "Attach a list of war ids", group = 3)
                           @Switch("id") boolean listWarIds,
                           @Arg(value = "Attach a tally of war types", group = 3)
                           @Switch("t") boolean showWarTypes,

                           // War and attack type filters
                           @Arg(value = "Filter the war types included", group = 4)
                           @Switch("w") Set<WarType> allowedWarTypes,
                           @Arg(value = "Filter the war statuses included", group = 4)
                           @Switch("s") Set<WarStatus> allowedWarStatus,
                            @Arg(value = "Filter the attack types included", group = 4)
                           @Switch("a") Set<AttackType> allowedAttackTypes,
                            @Arg(value = "Filter the success types included", group = 4)
                           @Switch("v") Set<SuccessType> allowedVictoryTypes,

                           @Switch("o") @Arg(value = "Only include wars declared by coalition1", group = 4)
                                      boolean onlyOffensiveWars,
                           @Switch("d") @Arg(value = "Only include wars declared by coalition2", group = 4)
                                      boolean onlyDefensiveWars,
                           @Switch("oa") @Arg(value = "Only include attacks done by coalition1", group = 4) boolean onlyOffensiveAttacks,
                            @Switch("da") @Arg(value = "Only include attacks done by coalition2", group = 4) boolean onlyDefensiveAttacks
                           ) {
        if (onlyOffensiveWars && onlyDefensiveWars) throw new IllegalArgumentException("Cannot combine `onlyOffensiveWars` and `onlyDefensiveWars`");
        if (onlyOffensiveAttacks && onlyDefensiveAttacks) throw new IllegalArgumentException("Cannot combine `onlyOffensiveAttacks` and `onlyDefensiveAttacks`");
        if (timeEnd == null) timeEnd = Long.MAX_VALUE;
        if (coalition1 != null && command != null && command.getString("coalition1").equalsIgnoreCase("*")) {
            coalition1 = null;
        }
        if (coalition2 != null && command != null && command.getString("coalition2").equalsIgnoreCase("*")) {
            coalition2 = null;
        }
        WarParser parser = WarParser.of(coalition1, coalition2, timeStart, timeEnd)
                .allowWarStatuses(allowedWarStatus)
                .allowedWarTypes(allowedWarTypes)
                .allowedAttackTypes(allowedAttackTypes)
                .allowedSuccessTypes(allowedVictoryTypes);
        if (onlyOffensiveWars) {
            parser.getAttacks().removeIf(f -> !parser.getIsPrimary().apply(f.getWar()));
        }
        if (onlyDefensiveWars) {
            parser.getAttacks().removeIf(f -> parser.getIsPrimary().apply(f.getWar()));
        }
        if (onlyOffensiveAttacks) {
            parser.getAttacks().removeIf(f -> {
                DBWar war = f.getWar();
                if (war == null) return true;
                boolean warDeclarerCol1 = parser.getIsPrimary().apply(war);
                return warDeclarerCol1 != (f.getAttacker_id() == war.getAttacker_id());
            });
        }
        if (onlyDefensiveAttacks) {
            parser.getAttacks().removeIf(f -> {
                DBWar war = f.getWar();
                if (war == null) return true;
                boolean warDeclarerCol2 = parser.getIsSecondary().apply(war);
                return warDeclarerCol2 != (f.getAttacker_id() == war.getAttacker_id());
            });
        }
        AttackCost cost = parser.toWarCost(true, true, listWarIds, listWarIds || showWarTypes, false);

        IMessageBuilder msg = channel.create();
        msg.append(cost.toString(!ignoreUnits, !ignoreInfra, !ignoreConsumption, !ignoreLoot, !ignoreBuildings));
        if (listWarIds) {
            msg.file(cost.getNumWars() + " wars", "- " + StringMan.join(cost.getWarIds(), "\n- "));
        }
        if (showWarTypes) {
            Set<DBWar> wars = Locutus.imp().getWarDb().getWarsById(cost.getWarIds());
            Map<WarType, Integer> byType = new HashMap<>();
            for (DBWar war : wars) {
                byType.put(war.getWarType(), byType.getOrDefault(war.getWarType(), 0) + 1);
            }
            StringBuilder response = new StringBuilder();
            for (Map.Entry<WarType, Integer> entry : byType.entrySet()) {
                response.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
            }
            msg.embed("War Types", response.toString());
        }
        if (listWarIds) {
            Set<DBWar> wars = Locutus.imp().getWarDb().getWarsById(cost.getWarIds());
            Map<CoalitionWarStatus, Integer> byStatus = new HashMap<>();
            for (DBWar war : wars) {
                CoalitionWarStatus status = switch (war.getStatus()) {
                    case ATTACKER_OFFERED_PEACE, DEFENDER_OFFERED_PEACE, ACTIVE -> CoalitionWarStatus.ACTIVE;
                    case PEACE -> CoalitionWarStatus.PEACE;
                    case EXPIRED -> CoalitionWarStatus.EXPIRED;
                    default -> null;
                };
                if (status != null) {
                    byStatus.put(status, byStatus.getOrDefault(status, 0) + 1);
                }
            }
            int attVictory = cost.getVictories(true).size();
            int defVictory = cost.getVictories(false).size();
            byStatus.put(CoalitionWarStatus.COL1_VICTORY, attVictory);
            byStatus.put(CoalitionWarStatus.COL1_DEFEAT, defVictory);

            StringBuilder response = new StringBuilder();
            for (Map.Entry<CoalitionWarStatus, Integer> entry : byStatus.entrySet()) {
                response.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
            }
            msg.embed("War Status", response.toString());
        }
        msg.append("\n\nSee also: <" + Settings.INSTANCE.WEB.S3.SITE + ">");
        msg.send();
        return null;
    }

    @Command(desc = "List resources available and radiation level in each continent")
    public String continent(@Me IMessageIO io) {
        List<List<String>> table = new ArrayList<>();
        List<String> header = new ArrayList<>(Arrays.asList(""));
        for (ResourceType type : ResourceType.values) {
            if (!type.isRaw()) continue;
            header.add(type.getShorthand().toLowerCase(Locale.ROOT));
        }
        table.add(header);
        for (Continent continent : Continent.values) {
            List<String> row = new ArrayList<>();
            row.add(continent.getAcronym());
            for (ResourceType type : ResourceType.values) {
                if (!type.isRaw()) continue;
                boolean available = continent.hasResource(type);
                row.add(available ? "X" : "");
            }
            table.add(row);
        }
        IMessageBuilder msg = io.create().writeTable("Continents", table, false, "");

        List<List<String>> radsTable = new ArrayList<>();
        radsTable.add(Arrays.asList("continent", "rads"));
        for (Continent continent : Continent.values) {
            radsTable.add(Arrays.asList(continent.name().toLowerCase(Locale.ROOT), MathMan.format(continent.getRadIndex())));
        }
        String footer = "See also: " + CM.stats_other.radiationByTurn.cmd.toSlashMention();
        msg.writeTable("Radiation", radsTable, false, footer).send();
        return null;
    }

    @Command(desc = "Rank alliances by a metric")
    public void allianceRanking(@Me IMessageIO channel, @Me JSONObject command, Set<DBAlliance> alliances, AllianceMetric metric, @Switch("r") boolean reverseOrder, @Switch("f") boolean uploadFile) {
        long turn = TimeUtil.getTurn();
        Set<Integer> aaIds = alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet());

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metrics = Locutus.imp().getNationDB().getAllianceMetrics(aaIds, metric, turn);

        Map<DBAlliance, Double> metricsDiff = new LinkedHashMap<>();
        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry : metrics.entrySet()) {
            DBAlliance alliance = entry.getKey();
            double diff = entry.getValue().get(metric).values().iterator().next();
            metricsDiff.put(alliance, diff);
        }
        System.out.println(metricsDiff);
        displayAllianceRanking(channel, command, metric, metricsDiff, reverseOrder, uploadFile);
    }

    public void displayAllianceRanking(IMessageIO channel, JSONObject command, AllianceMetric metric, Map<DBAlliance, Double> metricsDiff, boolean reverseOrder, boolean uploadFile) {

        SummedMapRankBuilder<DBAlliance, Double> builder = new SummedMapRankBuilder<>(metricsDiff);
        if (reverseOrder) {
            builder = builder.sortAsc();
        } else {
            builder = builder.sort();
        }
        String title = "Top " + metric + " by alliance";

        RankBuilder<String> named = builder.nameKeys(DBAlliance::getName);
        named.build(channel, command, title, uploadFile);
    }

    @Command(desc = "Rank alliances by a metric over a specified time period")
    public void allianceRankingTime(@Me IMessageIO channel, @Me JSONObject command, Set<DBAlliance> alliances, AllianceMetric metric, @Timestamp long timeStart, @Timestamp long timeEnd, @Switch("r") boolean reverseOrder, @Switch("f") boolean uploadFile) {
        long turnStart = TimeUtil.getTurn(timeStart);
        long turnEnd = TimeUtil.getTurn(timeEnd);
        System.out.println(timeStart);
        System.out.println(timeEnd);
        Set<Integer> aaIds = alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet());

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricsStart = Locutus.imp().getNationDB().getAllianceMetrics(aaIds, metric, turnStart);
        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricsEnd = Locutus.imp().getNationDB().getAllianceMetrics(aaIds, metric, turnEnd);

        Map<DBAlliance, Double> metricsDiff = new LinkedHashMap<>();
        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry : metricsEnd.entrySet()) {
            DBAlliance alliance = entry.getKey();
            if (!metricsStart.containsKey(alliance)) continue;
            double dataStart = metricsStart.get(alliance).get(metric).values().iterator().next();
            double dataEnd = entry.getValue().get(metric).values().iterator().next();
            metricsDiff.put(alliance, dataEnd - dataStart);
        }
        displayAllianceRanking(channel, command, metric, metricsDiff, reverseOrder, uploadFile);
    }

    @Command(desc = "Rank nations by an attribute")
    public void nationRanking(@Me IMessageIO channel, @Me GuildDB db,
                              @Me JSONObject command,
                              NationList nations,
                              NationAttributeDouble attribute,
                              @Switch("a") boolean groupByAlliance,
                              @Switch("r") boolean reverseOrder,
                              @Switch("s") @Timestamp Long snapshotDate,
                              @Arg("Total value instead of average per nation") @Switch("t") boolean total) {
        Set<DBNation> nationsSet = PW.getNationsSnapshot(nations.getNations(), nations.getFilter(), snapshotDate, db.getGuild(), false);
        Map<DBNation, Double> attributeByNation = new HashMap<>();
        for (DBNation nation : nationsSet) {
            Double value = attribute.apply(nation);
            if (!Double.isFinite(value)) continue;
            attributeByNation.put(nation, value);
        }

        String title = (total ? "Total" : groupByAlliance ? "Average" : "Top") + " " + attribute.getName() + " by " + (groupByAlliance ? "alliance" : "nation");

        SummedMapRankBuilder<DBNation, Double> builder = new SummedMapRankBuilder<>(attributeByNation);

        RankBuilder<String> named;
        if (groupByAlliance) {
            NumericGroupRankBuilder<Integer, Double> grouped = builder.group((entry, builder1) -> builder1.put(entry.getKey().getAlliance_id(), entry.getValue()));
            SummedMapRankBuilder<Integer, ? extends Number> summed;
            if (total) {
                summed = grouped.sum();
            } else {
                summed = grouped.average();
            }
            summed = reverseOrder ? summed.sortAsc() : summed.sort();
            named = summed.nameKeys(f -> PW.getName(f, true));
        } else {
            builder = reverseOrder ? builder.sortAsc() : builder.sort();
            named = builder.nameKeys(DBNation::getName);
        }
        named.build(channel, command, title, true);
    }

    @Command(desc = "List the radiation in each continent")
    public String radiation(TradeManager manager) {
        double global = 0;
        Map<Continent, Double> radsByCont = new LinkedHashMap<>();
        for (Continent continent : Continent.values) {
            double rads = manager.getGlobalRadiation(continent);
            global += rads;
            radsByCont.put(continent, rads);
        }
        global /= 5;

        StringBuilder result = new StringBuilder();
        result.append("Global: ").append(MathMan.format(global)).append("rads\n");
        for (Map.Entry<Continent, Double> entry : radsByCont.entrySet()) {
            Continent continent = entry.getKey();
            Double local = entry.getValue();

            double modifier = continent.getSeasonModifier();
            modifier *= (1 - (local + global) / 1000d);

            result.append("- ").append(continent).append(": ").append(MathMan.format(local)).append("rads. (").append(MathMan.format(modifier * 100)).append("% food production)")
                    .append("\n");
        }
        return result.toString();
    }

    @Command(desc = "View the percent times an alliance counters in-game wars")
    @RolePermission(Roles.MEMBER)
    public String counterStats(@Me IMessageIO channel, DBAlliance alliance) {
        List<Map.Entry<DBWar, CounterStat>> counters = Locutus.imp().getWarDb().getCounters(Collections.singleton(alliance.getAlliance_id()));

        if (counters.isEmpty()) return "No data (to include treatied alliances, append `-a`";

        int[] uncontested = new int[2];
        int[] countered = new int[2];
        int[] counter = new int[2];
        for (Map.Entry<DBWar, CounterStat> entry : counters) {
            CounterStat stat = entry.getValue();
            DBWar war = entry.getKey();
            switch (stat.type) {
                case ESCALATION, IS_COUNTER -> countered[stat.isActive ? 1 : 0]++;
                case UNCONTESTED -> {
                    if (war.getStatus() == WarStatus.ATTACKER_VICTORY) {
                        uncontested[stat.isActive ? 1 : 0]++;
                    } else {
                        counter[stat.isActive ? 1 : 0]++;
                    }
                }
                case GETS_COUNTERED -> counter[stat.isActive ? 1 : 0]++;
            }
        }

        int totalActive = counter[1] + uncontested[1];
        int totalInactive = counter[0] + uncontested[0];

        double chanceActive = ((double) counter[1] + 1) / (totalActive + 1);
        double chanceInactive = ((double) counter[0] + 1) / (totalInactive + 1);

        if (!Double.isFinite(chanceActive)) chanceActive = 0.5;
        if (!Double.isFinite(chanceInactive)) chanceInactive = 0.5;

        String title = "% of wars that are countered (" + alliance.getName() + ")";
        String response = MathMan.format(chanceActive * 100) + "% for actives (" + totalActive + " wars)" + '\n' +
                MathMan.format(chanceInactive * 100) + "% for inactives (" + totalInactive + " wars)";

        channel.create().embed(title, response).send();
        return null;
    }

    @Command(desc = "Graph the attributes of the nations of two coalitions by city count\n" +
            "e.g. How many nations, soldiers etc. are at each city")
    public String attributeTierGraph(@Me IMessageIO channel, @Me GuildDB db,
                                     NationAttributeDouble metric,
                                     NationList coalition1,
                                     NationList coalition2,
                                     @Switch("i") boolean includeInactives,
                                     @Switch("a") boolean includeApplicants,
                                     @Arg("Compare the sum of each nation's attribute in the coalition instead of average")
                                         @Switch("t") boolean total,
                                     @Switch("s") @Timestamp Long snapshotDate) throws IOException {
        Set<DBNation> coalition1Nations = PW.getNationsSnapshot(coalition1.getNations(), coalition1.getFilter(), snapshotDate, db.getGuild(), false);
        Set<DBNation> coalition2Nations = PW.getNationsSnapshot(coalition2.getNations(), coalition2.getFilter(), snapshotDate, db.getGuild(), false);
        int num1 = coalition1Nations.size();
        int num2 = coalition2Nations.size();
        coalition1Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        coalition2Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));

        if (coalition1Nations.isEmpty()) {
            if (num1 > 0) {
                throw new IllegalArgumentException("No active nations in `coalition1` (" + num1 + " removed for inactivity, see: `includeApplicants: True`)\n" +
                        "Note: Use the `AA:` qualifier or the alliance url to avoid nations by the same name.");
            }
        }
        if (coalition2Nations.isEmpty()) {
            if (num2 > 0) {
                throw new IllegalArgumentException("No active nations in `coalition2` (" + num2 + " removed for inactivity, see: `includeApplicants: True`)\n" +
                        "Note: Use the `AA:` qualifier or the alliance url to avoid nations by the same name.");
            }
        }
        if (coalition1Nations.isEmpty() || coalition2Nations.isEmpty()) throw new IllegalArgumentException("No nations provided");
        if (coalition1Nations.size() < 3 || coalition2Nations.size() < 3) return "Coalitions are too small to compare";

        Map<Integer, List<DBNation>> coalition1ByCity = new HashMap<>();
        Map<Integer, List<DBNation>> coalition2ByCity = new HashMap<>();

        for (DBNation n : coalition1Nations) coalition1ByCity.computeIfAbsent(n.getCities(), f -> new ArrayList<>()).add(n);
        for (DBNation n : coalition2Nations) coalition2ByCity.computeIfAbsent(n.getCities(), f -> new ArrayList<>()).add(n);

        int min = Math.min(Collections.min(coalition1ByCity.keySet()), Collections.min(coalition2ByCity.keySet()));
        int max = Math.max(Collections.max(coalition1ByCity.keySet()), Collections.max(coalition2ByCity.keySet()));

        DataTable data = new DataTable(Double.class, Double.class, String.class);

        for (int cities = min; cities <= max; cities++) {
            List<DBNation> natAtCity1 = coalition1ByCity.getOrDefault(cities, Collections.emptyList());
            List<DBNation> natAtCity2 = coalition2ByCity.getOrDefault(cities, Collections.emptyList());
            List<DBNation>[] coalitions = new List[]{natAtCity1, natAtCity2};

            for (int j = 0; j < coalitions.length; j++) {
                List<DBNation> coalition = coalitions[j];
                SimpleNationList natCityList = new SimpleNationList(coalition);

                String name = j == 0 ? "" + cities : "";

                double valueTotal = 0;
                int count = 0;
                Collection<DBNation> nations = natCityList.getNations();
                for (DBNation nation : nations) {
                    if (nation.hasUnsetMil()) continue;
                    count++;
                    valueTotal += metric.apply(nation);
                }
                if (count > 1 && !total) {
                    valueTotal /= count;
                }

                data.add(cities + (j * 0.5d), valueTotal, name);
            }
        }

        int segments = 1;
        // Create new bar plot
        BarPlot plot = new BarPlot(data);
        plot.getTitle().setText((total ? "Total" : "Average") + " " + metric.getName() + " by city count");

        // Format plot
        plot.setInsets(new Insets2D.Double(20.0, 100.0, 40.0, 0.0));
        plot.setBarWidth(0.5);
        plot.setBackground(Color.WHITE);

        // Format bars
        BarPlot.BarRenderer pointRenderer = (BarPlot.BarRenderer) plot.getPointRenderers(data).get(0);
        pointRenderer.setColor(new ColorMapper() {
            @Override
            public Paint get(Number number) {
                int column = (number.intValue() / segments);
                return (column % 2) == 0 ? Color.RED : Color.BLUE;
            }

            @Override
            public ColorMapper.Mode getMode() {
                return null;
            }
        });
        pointRenderer.setBorderStroke(new BasicStroke(1f));
        pointRenderer.setBorderColor(Color.LIGHT_GRAY);
        pointRenderer.setValueVisible(true);
        pointRenderer.setValueColumn(2);
        pointRenderer.setValueLocation(Location.NORTH);
        pointRenderer.setValueRotation(90);
        pointRenderer.setValueColor(new ColorMapper() {
            @Override
            public Paint get(Number number) {
                return Color.BLACK;
            }

            @Override
            public ColorMapper.Mode getMode() {
                return null;
            }
        });
        pointRenderer.setValueFont(Font.decode(null).deriveFont(12.0f));

        DrawableWriter writer = DrawableWriterFactory.getInstance().get("image/png");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(plot, baos, 1400, 600);

        IMessageBuilder msg = channel.create();
        msg.file("img.png", baos.toByteArray());

        plot.setInsets(new Insets2D.Double(20.0, 100.0, 40.0, 0.0));
        plot.getTitle().setText("MMR by city count");

        String response = """
                > Each coalition is grouped by city count and color coded.
                > **RED** = {coalition1}
                > **BLUE** = {coalition2}
                """.replace("{coalition1}", coalition1.getFilter())
                .replace("{coalition2}", coalition2.getFilter());
        msg.append(response);
        msg.send();
        return null;
    }

    @Command(desc = "Generate a graph of nation military strength by score between two coalitions", aliases = {"strengthTierGraph"})
    public String strengthTierGraph(@Me GuildDB db, @Me IMessageIO channel,
                                    NationList coalition1,
                                    NationList coalition2,
                                    @Switch("i") boolean includeInactives,
                                    @Switch("n") boolean includeApplicants,
                                    @Arg("Use the score/strength of coalition 1 nations at specific military unit levels") @Switch("a") MMRDouble col1MMR,
                                    @Arg("Use the score/strength of coalition 2 nations at specific military unit levels") @Switch("b") MMRDouble col2MMR,
                                    @Arg("Use the score of coalition 1 nations at specific average infrastructure levels") @Switch("c") Double col1Infra,
                                    @Arg("Use the score of coalition 2 nations at specific average infrastructure levels") @Switch("d") Double col2Infra,
                                    @Switch("s") @Timestamp Long snapshotDate,
                                    @Switch("j") boolean attachJson,
                                    @Switch("v") boolean attachCsv) throws IOException {
        Set<DBNation> coalition1Nations = PW.getNationsSnapshot(coalition1.getNations(), coalition1.getFilter(), snapshotDate, db.getGuild(), false);
        Set<DBNation> coalition2Nations = PW.getNationsSnapshot(coalition2.getNations(), coalition2.getFilter(), snapshotDate, db.getGuild(), false);
        Set<DBNation> allNations = new HashSet<>();
        coalition1Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        coalition2Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        allNations.addAll(coalition1Nations);
        allNations.addAll(coalition2Nations);
        if (coalition1Nations.isEmpty() || coalition2Nations.isEmpty()) throw new IllegalArgumentException("No nations provided");

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (DBNation nation : allNations) {
            maxScore = (int) Math.max(maxScore, nation.estimateScore(col1MMR, col1Infra, null, null));
            minScore = (int) Math.min(minScore, nation.estimateScore(col2MMR, col2Infra, null, null));
        }
        double[] coal1Str = new double[(int) (maxScore * PW.WAR_RANGE_MAX_MODIFIER)];
        double[] coal2Str = new double[(int) (maxScore * PW.WAR_RANGE_MAX_MODIFIER)];

        double[] coal1StrSpread = new double[coal1Str.length];
        double[] coal2StrSpread = new double[coal2Str.length];

        // min = x * 0.75;
        // max = x * 1.25
        // max = (min / 0.6)

        for (DBNation nation : coalition1Nations) {
            coal1Str[(int) (nation.estimateScore(col1MMR, col1Infra, null, null) * 0.75)] += nation.getStrengthMMR(col1MMR);
        }
        for (DBNation nation : coalition2Nations) {
            coal2Str[(int) (nation.estimateScore(col2MMR, col2Infra, null, null) * 0.75)] += nation.getStrengthMMR(col2MMR);
        }
        for (int min = 10; min < coal1Str.length; min++) {
            double val = coal1Str[min];
            if (val == 0) continue;
            int max = (int) (min / 0.6);

            for (int i = min; i <= max; i++) {
                double shaped = val - 0.4 * val * ((double) (i - min) / (max - min));
                coal1StrSpread[i] += shaped;
            }
        }
        for (int min = 10; min < coal2Str.length; min++) {
            double val = coal2Str[min];
            if (val == 0) continue;
            int max = (int) (min / 0.6);

            for (int i = min; i <= max; i++) {
                double shaped = val - 0.4 * val * ((double) (i - min) / (max - min));
                coal2StrSpread[i] += shaped;
            }
        }

        TimeDualNumericTable<Void> table = new TimeDualNumericTable<>("Effective military strength by score range", "score", "strength", coalition1.getFilter(), coalition2.getFilter()) {
            @Override
            public void add(long score, Void ignore) {
                add(score, coal1StrSpread[(int) score], coal2StrSpread[(int) score]);
            }
        };
        for (int score = (int) Math.max(10, minScore * 0.75 - 10); score < maxScore * 1.25 + 10; score++) {
            table.add(score, (Void) null);
        }
        table.write(channel, TimeFormat.DECIMAL_ROUNDED, TableNumberFormat.SI_UNIT, 0, attachJson, attachCsv);
        return null;
    }

    @Command(desc = "Generate a graph of spy counts by city count between two coalitions\n" +
            "Nations which are applicants, in vacation mode or inactive (2 days) are excluded")
    public String spyTierGraph(@Me GuildDB db, @Me IMessageIO channel,
                               NationList coalition1,
                               NationList coalition2,
                               @Switch("i") boolean includeInactives,
                               @Switch("a") boolean includeApplicants,
                               @Arg("Graph the total spies instead of average per nation")
                               @Switch("t") boolean total,
                               @Switch("b") boolean barGraph,
                               @Switch("j") boolean attachJson,
                               @Switch("c") boolean attachCsv) throws IOException {
        Collection<DBNation> coalition1Nations = coalition1.getNations();
        Collection<DBNation> coalition2Nations = coalition2.getNations();
        Set<DBNation> allNations = new HashSet<>();
        coalition1Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 2880));
        coalition2Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 2880));
        allNations.addAll(coalition1Nations);
        allNations.addAll(coalition2Nations);
        if (coalition1Nations.isEmpty() || coalition2Nations.isEmpty()) throw new IllegalArgumentException("No nations provided");
        int min = 0;
        int max = 0;
        for (DBNation nation : allNations) max = Math.max(nation.getCities(), max);
        max++;

        Collection<DBNation>[] coalitions = new Collection[]{coalition1Nations, coalition2Nations};
        int[][] counts = new int[coalitions.length][];
        for (int i = 0; i < coalitions.length; i++) {
            Collection<DBNation> coalition = coalitions[i];
            int[] cities = new int[max + 1];
            int[] spies = new int[max + 1];
            counts[i] = spies;
            int k = 0;
            for (DBNation nation : coalition) {
                cities[nation.getCities()]++;
                spies[nation.getCities()] += nation.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, 1);
            }
            if (!total) {
                for (int j = 0; j < cities.length; j++) {
                    if (cities[j] > 1) spies[j] /= cities[j];
                }
            }
        }

        String title = (total ? "Total" : "Average") + " spies by city count";
        TimeDualNumericTable<Void> table = new TimeDualNumericTable<>(title, "cities", "spies", coalition1.getFilter(), coalition2.getFilter()) {
            @Override
            public void add(long cities, Void ignore) {
                add(cities, counts[0][(int) cities], counts[1][(int) cities]);
            }
        };
        for (int cities = min; cities <= max; cities++) {
            table.add(cities, (Void) null);
        }
        if (barGraph) table.setBar(true);
        table.write(channel, TimeFormat.DECIMAL_ROUNDED, TableNumberFormat.SI_UNIT, 0, attachJson, attachCsv);
        return null;
    }

    @Command(desc = "Generate a graph of nation counts by score between two coalitions", aliases = {"scoreTierGraph", "scoreTierSheet"})
    public String scoreTierGraph(@Me GuildDB db, @Me IMessageIO channel,
                                 NationList coalition1,
                                 NationList coalition2,
                                 @Switch("i") boolean includeInactives,
                                 @Switch("a") boolean includeApplicants,
                                 @Switch("s") @Timestamp Long snapshotDate,
                                 @Switch("j") boolean attachJson,
                                 @Switch("c") boolean attachCsv) throws IOException {
        Set<DBNation> coalition1Nations = PW.getNationsSnapshot(coalition1.getNations(), coalition1.getFilter(), snapshotDate, db.getGuild(), false);
        Set<DBNation> coalition2Nations = PW.getNationsSnapshot(coalition2.getNations(), coalition2.getFilter(), snapshotDate, db.getGuild(), false);
        Set<DBNation> allNations = new HashSet<>();
        coalition1Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        coalition2Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        allNations.addAll(coalition1Nations);
        allNations.addAll(coalition2Nations);

        if (coalition1Nations.isEmpty() || coalition2Nations.isEmpty()) throw new IllegalArgumentException("No nations provided");

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (DBNation nation : allNations) {
            maxScore = (int) Math.max(maxScore, nation.getScore());
            minScore = (int) Math.min(minScore, nation.getScore());
        }
        double[] coal1Str = new double[(int) (maxScore * PW.WAR_RANGE_MAX_MODIFIER)];
        double[] coal2Str = new double[(int) (maxScore * PW.WAR_RANGE_MAX_MODIFIER)];

        double[] coal1StrSpread = new double[coal1Str.length];
        double[] coal2StrSpread = new double[coal2Str.length];

        for (DBNation nation : coalition1Nations) {
            coal1Str[(int) (nation.getScore() * 0.75)] += 1;
        }
        for (DBNation nation : coalition2Nations) {
            coal2Str[(int) (nation.getScore() * 0.75)] += 1;
        }
        for (int min = 10; min < coal1Str.length; min++) {
            double val = coal1Str[min];
            if (val == 0) continue;
            int max = Math.min(coal1StrSpread.length, (int) (PW.WAR_RANGE_MAX_MODIFIER * (min / 0.75)));

            for (int i = min; i < max; i++) {
                coal1StrSpread[i] += val;
            }
        }
        for (int min = 10; min < coal2Str.length; min++) {
            double val = coal2Str[min];
            if (val == 0) continue;
            int max = Math.min(coal2StrSpread.length, (int) (PW.WAR_RANGE_MAX_MODIFIER * (min / 0.75)));

            for (int i = min; i < max; i++) {
                coal2StrSpread[i] += val;
            }
        }

        TimeDualNumericTable<Void> table = new TimeDualNumericTable<>("Nations by score range", "score", "nations", coalition1.getFilter(), coalition2.getFilter()) {
            @Override
            public void add(long score, Void ignore) {
                add(score, coal1StrSpread[(int) score], coal2StrSpread[(int) score]);
            }
        };
        for (int score = (int) Math.max(10, minScore * 0.75 - 10); score < maxScore * 1.25 + 10; score++) {
            table.add(score, (Void) null);
        }
        table.write(channel, TimeFormat.DECIMAL_ROUNDED, TableNumberFormat.SI_UNIT, 0, attachJson, attachCsv);
        return null;
    }

    @Command(desc = "Rank of nations by number of challenge baseball games from a specified date")
    public void baseballRanking(BaseballDB db, @Me JSONObject command, @Me IMessageIO channel,
                                @Arg("Date to start from")
                                @Timestamp long date, @Switch("f") boolean uploadFile, @Switch("a") boolean byAlliance) {
        List<BBGame> games = db.getBaseballGames(f -> f.where(QueryCondition.greater("date", date)));

        Map<Integer, Integer> mostGames = new HashMap<>();
        for (BBGame game : games) {
            if (byAlliance) {
                DBNation home = DBNation.getById(game.getHome_nation_id());
                DBNation away = DBNation.getById(game.getAway_nation_id());
                if (home != null) mostGames.merge(home.getAlliance_id(), 1, Integer::sum);
                if (away != null) mostGames.merge(away.getAlliance_id(), 1, Integer::sum);
            } else {
                mostGames.merge(game.getHome_nation_id(), 1, Integer::sum);
                mostGames.merge(game.getAway_nation_id(), 1, Integer::sum);
            }
        }

        String title = "# BB Games (" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - date) + ")";
        RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostGames).sort().nameKeys(f -> PW.getName(f, byAlliance));
        ranks.build(channel, command, title, uploadFile);
    }

    @Command(desc = "Rank of nations by number of challenge baseball games")
    public void baseballChallengeRanking(BaseballDB db, @Me IMessageIO channel, @Me JSONObject command, @Switch("f") boolean uploadFile,
                                         @Arg("Group the rankings by alliance instead of nations") @Switch("a") boolean byAlliance) {
        List<BBGame> games = db.getBaseballGames(f -> f.where(QueryCondition.greater("wager", 0)));

        Map<Integer, Integer> mostGames = new HashMap<>();
        for (BBGame game : games) {
            if (byAlliance) {

                DBNation home = DBNation.getById(game.getHome_nation_id());
                DBNation away = DBNation.getById(game.getAway_nation_id());
                if (home != null) mostGames.merge(home.getAlliance_id(), 1, Integer::sum);
                if (away != null) mostGames.merge(away.getAlliance_id(), 1, Integer::sum);
            } else {
                mostGames.merge(game.getHome_nation_id(), 1, Integer::sum);
                mostGames.merge(game.getAway_nation_id(), 1, Integer::sum);
            }
        }

        String title = "# Challenge BB Games";
        RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostGames).sort().nameKeys(f -> PW.getName(f, byAlliance));
        ranks.build(channel, command, title, uploadFile);
    }

    @Command(desc = "List the baseball wager inflows for a nation id")
    public String baseBallChallengeInflow(BaseballDB db, @Me IMessageIO channel, @Me JSONObject command, int nationId, @Default("timestamp:0") @Timestamp long dateSince, @Switch("u") boolean uploadFile) {
        List<BBGame> games = db.getBaseballGames(f -> f.where(QueryCondition.greater("wager", 0)
                .and(QueryCondition.equals("home_nation_id", nationId).or(QueryCondition.equals("away_nation_id", nationId)))
                .and(QueryCondition.greater("date", dateSince))
        ));

        if (games.isEmpty()) return "No games found";

        {
            String title = "# Wagers with " + PW.getName(nationId, false);
            Map<Integer, Integer> mostWageredGames = new HashMap<>();
            for (BBGame game : games) {
                mostWageredGames.merge(game.getHome_nation_id(), 1, Integer::sum);
                mostWageredGames.merge(game.getAway_nation_id(), 1, Integer::sum);
            }
            RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostWageredGames).sort().nameKeys(f -> PW.getName(f, false));
            ranks.build(channel, command, title, uploadFile);
        }
        {
            String title = "$ Wagered with " + PW.getName(nationId, false);
            Map<Integer, Long> mostWageredWinnings = new HashMap<>();
            for (BBGame game : games) {
                int otherId = game.getAway_nation_id() == nationId ? game.getHome_nation_id() : game.getAway_nation_id();
                mostWageredWinnings.merge(otherId, game.getWager().longValue(), Long::sum);
            }
            RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostWageredWinnings).sort().nameKeys(f -> PW.getName(f, false));
            ranks.build(channel, command, title, uploadFile);
        }
        return null;
    }

    @Command(desc = "Rank of nations by challenge baseball game earnings")
    public void baseballEarningsRanking(BaseballDB db, @Me IMessageIO channel, @Me JSONObject command,
                                        @Arg("Date to start from")
                                        @Timestamp long date, @Switch("f") boolean uploadFile,
                                        @Arg("Group the rankings by alliance instead of nations")
                                        @Switch("a") boolean byAlliance) {
        List<BBGame> games = db.getBaseballGames(f -> f.where(QueryCondition.greater("date", date)));

        Map<Integer, Long> mostWageredWinnings = new HashMap<>();
        for (BBGame game : games) {
            int id;
            if (game.getHome_score() > game.getAway_score()) {
                id = game.getHome_nation_id();

            } else if (game.getAway_score() > game.getHome_score()) {
                id = game.getAway_nation_id();
            } else continue;
            if (byAlliance) {
                DBNation nation = DBNation.getById(id);
                if (nation == null) continue;
                id = nation.getAlliance_id();
            }
            mostWageredWinnings.merge(id, game.getSpoils().longValue(), Long::sum);
        }

        String title = "BB Earnings $ (" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - date) + ")";
        RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostWageredWinnings).sort().nameKeys(f -> PW.getName(f, byAlliance));
        ranks.build(channel, command, title, uploadFile);
    }

    @Command(desc = "Rank of nations by challenge baseball challenge earnings")
    public void baseballChallengeEarningsRanking(BaseballDB db, @Me IMessageIO channel, @Me JSONObject command, @Switch("f") boolean uploadFile,
                                                 @Arg("Group the rankings by alliance instead of nations")
                                                 @Switch("a") boolean byAlliance) {
        List<BBGame> games = db.getBaseballGames(f -> f.where(QueryCondition.greater("wager", 0)));

        Map<Integer, Long> mostWageredWinnings = new HashMap<>();
        for (BBGame game : games) {
            int id;
            if (game.getHome_score() > game.getAway_score()) {
                id = game.getHome_nation_id();

            } else if (game.getAway_score() > game.getHome_score()) {
                id = game.getAway_nation_id();
            } else continue;
            if (byAlliance) {
                DBNation nation = DBNation.getById(id);
                if (nation == null) continue;
                id = nation.getAlliance_id();
            }
            mostWageredWinnings.merge(id, game.getWager().longValue(), Long::sum);
        }

        String title = "BB Challenge Earnings $";
        RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostWageredWinnings).sort().nameKeys(f -> PW.getName(f, byAlliance));
        ranks.build(channel, command, title, uploadFile);
    }

    @Command(desc = "Generate ranking of war status by Alliance")
    public void warStatusRankingByAA(@Me GuildDB db, @Me IMessageIO channel, @Me JSONObject command, Set<DBNation> attackers, Set<DBNation> defenders,
                                      @Arg("Date to start from")
                                     @Timestamp long time) {
        warStatusRankingBy(true, db, channel, command, attackers, defenders, time);
    }

    @Command(desc = "Generate ranking of war status by Nation")
    public void warStatusRankingByNation(@Me GuildDB db, @Me IMessageIO channel, @Me JSONObject command, Set<DBNation> attackers, Set<DBNation> defenders,
                                         @Arg("Date to start from")
                                         @Timestamp long time) {
        warStatusRankingBy(false, db, channel, command, attackers, defenders, time);
    }

    public void warStatusRankingBy(boolean isAA, @Me GuildDB db, @Me IMessageIO channel, @Me JSONObject command, Set<DBNation> attackers, Set<DBNation> defenders, @Timestamp long time) {
        BiFunction<Boolean, DBWar, Integer> getId;
        if (isAA) getId = (primary, war) -> primary ? war.getAttacker_aa() : war.getDefender_aa();
        else getId = (primary, war) -> primary ? war.getAttacker_id() : war.getDefender_id();

        WarParser parser = WarParser.ofAANatobj(null, attackers, null, defenders, time, Long.MAX_VALUE);

        Map<Integer, Integer> victoryByEntity = new HashMap<>();
        Map<Integer, Integer> expireByEntity = new HashMap<>();

        for (Map.Entry<Integer, DBWar> entry : parser.getWars().entrySet()) {
            DBWar war = entry.getValue();
            boolean primary = parser.getIsPrimary().apply(war);

            if (war.getStatus() == WarStatus.DEFENDER_VICTORY) primary = !primary;
            int id = getId.apply(primary, war);

            if (war.getStatus() == WarStatus.ATTACKER_VICTORY || war.getStatus() == WarStatus.DEFENDER_VICTORY) {
                victoryByEntity.put(id, victoryByEntity.getOrDefault(id, 0) + 1);
            } else if (war.getStatus() == WarStatus.EXPIRED) {
                expireByEntity.put(id, expireByEntity.getOrDefault(id, 0) + 1);
            }
        }

        if (!victoryByEntity.isEmpty())
            new SummedMapRankBuilder<>(victoryByEntity).sort().nameKeys(f -> PW.getName(f, isAA)).build(channel, command, "Victories");
        if (!expireByEntity.isEmpty())
            new SummedMapRankBuilder<>(expireByEntity).sort().nameKeys(f -> PW.getName(f, isAA)).build(channel, command, "Expiries");
    }

    @Command(desc = "Generate a graph of nation military levels by city count between two coalitions")
    public String mmrTierGraph(@Me GuildDB db, @Me IMessageIO channel,
                               NationList coalition1,
                               NationList coalition2, @Switch("i") boolean includeInactives, @Switch("a") boolean includeApplicants, @Switch("s") SpreadSheet sheet,
                                @Arg("Graph the average military buildings instead of units")
                               @Switch("b") boolean buildings,
                               @Switch("t") @Timestamp Long snapshotDate) throws IOException {
        Set<DBNation> nations1 = PW.getNationsSnapshot(coalition1.getNations(), coalition1.getFilter(), snapshotDate, db.getGuild(), false);
        Set<DBNation> nations2 = PW.getNationsSnapshot(coalition2.getNations(), coalition2.getFilter(), snapshotDate, db.getGuild(), false);

        nations1.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        nations2.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));

        if (nations1.isEmpty() || nations2.isEmpty()) throw new IllegalArgumentException("No nations provided");
        if (nations1.size() < 3 || nations2.size() < 3) return "Coalitions are too small to compare";

        Map<Integer, List<DBNation>> coalition1ByCity = new HashMap<>();
        Map<Integer, List<DBNation>> coalition2ByCity = new HashMap<>();

        for (DBNation n : nations1) coalition1ByCity.computeIfAbsent(n.getCities(), f -> new ArrayList<>()).add(n);
        for (DBNation n : nations2) coalition2ByCity.computeIfAbsent(n.getCities(), f -> new ArrayList<>()).add(n);

        int min = Math.min(Collections.min(coalition1ByCity.keySet()), Collections.min(coalition2ByCity.keySet()));
        int max = Math.max(Collections.max(coalition1ByCity.keySet()), Collections.max(coalition2ByCity.keySet()));

        List<Object> headers = new ArrayList<>(Arrays.asList("cities", "coalition", "num_nations", "soldier%", "tankpct", "air%", "ship%", "avg%"));
        if (sheet != null) sheet.setHeader(headers);

        {
            DataTable data = new DataTable(Double.class, Double.class, String.class);

            for (int cities = min; cities <= max; cities++) {
                List<DBNation> natAtCity1 = coalition1ByCity.getOrDefault(cities, Collections.emptyList());
                List<DBNation> natAtCity2 = coalition2ByCity.getOrDefault(cities, Collections.emptyList());
                List<DBNation>[] coalitions = new List[]{natAtCity1, natAtCity2};

                for (int j = 0; j < coalitions.length; j++) {
                    List<DBNation> coalition = coalitions[j];
                    NationList natCityList = new SimpleNationList(coalition);
                    DBNation nation = natCityList.getNations().isEmpty() ? null : natCityList.getAverage();
                    String name = j == 0 ? "" + cities : "";
                    if (nation == null) {
                        nation = new DBNation();
                        nation.setSoldiers(0);
                        nation.setTanks(0);
                        nation.setAircraft(0);
                        nation.setShips(0);
                    }
                    double total = 0;

                    MilitaryUnit[] units = new MilitaryUnit[]{MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP};
                    double[] pcts = new double[units.length];

                    double[] arr = buildings ? nation.getMMRBuildingArr() : null;
                    for (int i = 0; i < units.length; i++) {
                        MilitaryUnit unit = units[i];
                        double pct;
                        if (buildings) {
                            pct = arr[i] / unit.getBuilding().cap(nation::hasProject);
                        } else {
                            double amt = nation.getUnits(unit);
                            double cap = unit.getBuilding().cap(nation::hasProject) * unit.getBuilding().getUnitCap() * cities;
                            pct = amt / cap;
                        }
                        pcts[i] = pct;

                        total += pct * 25;
                    }
                    for (int i = units.length - 1; i >= 0; i--) {
                        data.add(cities + (j * 0.5d), total, i == 3 ? name : "");
                        total -= pcts[i] * 25;
                    }

                    if (sheet != null) {
                        headers.set(0, cities);
                        headers.set(1, j + 1);
                        headers.set(2, coalition.size());
                        headers.set(3, pcts[0]);
                        headers.set(4, pcts[1]);
                        headers.set(5, pcts[2]);
                        headers.set(6, pcts[3]);
                        headers.set(7, total / 100d);
                        sheet.addRow(headers);
                    }
                }
            }

            // Create new bar plot
            BarPlot plot = new BarPlot(data);
            plot.getTitle().setText("MMR by city count");

            // Format plot
            plot.setInsets(new Insets2D.Double(40.0, 40.0, 40.0, 0.0));
            plot.setBarWidth(0.5);
            plot.setBackground(Color.WHITE);

            // Format bars
            BarPlot.BarRenderer pointRenderer = (BarPlot.BarRenderer) plot.getPointRenderers(data).get(0);
            pointRenderer.setColor(new ColorMapper() {
                @Override
                public Paint get(Number number) {
                    int column = (number.intValue() / 4);
                    return (column % 2) == 0 ? Color.decode("#8b0000") : Color.BLUE;
                }

                @Override
                public ColorMapper.Mode getMode() {
                    return null;
                }
            });
            pointRenderer.setBorderStroke(new BasicStroke(1f));
            pointRenderer.setBorderColor(Color.LIGHT_GRAY);
            pointRenderer.setValueVisible(true);
            pointRenderer.setValueColumn(2);
            pointRenderer.setValueLocation(Location.NORTH);
            pointRenderer.setValueRotation(90);
            pointRenderer.setValueColor(new ColorMapper() {
                @Override
                public Paint get(Number number) {
                    return Color.BLACK;
                }

                @Override
                public ColorMapper.Mode getMode() {
                    return null;
                }
            });
            pointRenderer.setValueFont(Font.decode(null).deriveFont(12.0f));

            DrawableWriter writer = DrawableWriterFactory.getInstance().get("image/png");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            plot.setInsets(new Insets2D.Double(20.0, 100.0, 40.0, 0.0));
            plot.getTitle().setText("MMR by city count");
            writer.write(plot, baos, 1400, 600);

            String response = """
                    > Each bar is segmented into four sections, from bottom to top: (soldiers, tanks, planes, ships)
                    > Each coalition is grouped by city count and color coded.
                    > **RED** = {coalition1}
                    > **BLUE** = {coalition2}
                    """.replace("{coalition1}", coalition1.getFilter()).replace("{coalition2}", coalition2.getFilter());

            IMessageBuilder msg = channel.create();
            msg.image("img.png", baos.toByteArray());

            if (sheet != null) {
                sheet.updateClearCurrentTab();
                sheet.updateWrite();
                sheet.attach(msg, "mmr_tiering");
            }

            msg.append(response);
            msg.send();

            return null;
        }
    }

    @Command(desc = "Generate a bar char comparing the nation at each city count (tiering) between two coalitions")
    public String cityTierGraph(@Me GuildDB db, @Me IMessageIO channel, NationList coalition1, NationList coalition2,
                                @Switch("i") boolean includeInactives,
                                @Switch("b") boolean barGraph,
                                @Switch("a") boolean includeApplicants,
                                @Switch("j") boolean attachJson,
                                @Switch("c") boolean attachCsv,
                                @Switch("s") @Timestamp Long snapshotDate) throws IOException {
        Set<DBNation> nations1 = PW.getNationsSnapshot(coalition1.getNations(), coalition1.getFilter(), snapshotDate, db.getGuild(), false);
        Set<DBNation> nations2 = PW.getNationsSnapshot(coalition2.getNations(), coalition2.getFilter(), snapshotDate, db.getGuild(), false);
        Set<DBNation> allNations = new HashSet<>();
        nations1.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        nations2.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        allNations.addAll(nations1);
        allNations.addAll(nations2);
        if (nations1.isEmpty() || nations2.isEmpty())
            throw new IllegalArgumentException("No nations provided");
        int min = 0;
        int max = 0;
        for (DBNation nation : allNations) max = Math.max(nation.getCities(), max);
        max++;
        int[] count1 = new int[max + 1];
        int[] count2 = new int[max + 1];
        for (DBNation nation : nations1) count1[nation.getCities()]++;
        for (DBNation nation : nations2) count2[nation.getCities()]++;
        TimeDualNumericTable<Void> table = new TimeDualNumericTable<>("Nations by city count", "city", "nations", coalition1.getFilter(), coalition2.getFilter()) {
            @Override
            public void add(long cities, Void ignore) {
                add(cities, count1[(int) cities], count2[(int) cities]);
            }
        };
        for (int cities = min; cities <= max; cities++) {
            table.add(cities, (Void) null);
        }
        if (barGraph) table.setBar(true);
        table.write(channel, TimeFormat.DECIMAL_ROUNDED, TableNumberFormat.SI_UNIT, 0, attachJson, attachCsv);
        return null;
    }

    @Command(desc = "Compare the metric over time between multiple alliances")
    public String allianceMetricsCompareByTurn(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db, AllianceMetric metric, Set<DBAlliance> alliances,
                                               @Arg("Date to start from")
                                               @Timestamp long time, @Switch("j") boolean attachJson,
                                               @Switch("c") boolean attachCsv) throws IOException {
        System.out.println("Alliances " + alliances.size());
        long turnStart = TimeUtil.getTurn(time);
        Set<DBAlliance>[] coalitions = alliances.stream().map(Collections::singleton).toList().toArray(new Set[0]);
        List<String> coalitionNames = alliances.stream().map(DBAlliance::getName).collect(Collectors.toList());
        TimeNumericTable table = AllianceMetric.generateTable(metric, turnStart, coalitionNames, coalitions);
        table.write(channel, TimeFormat.TURN_TO_DATE, metric.getFormat(), turnStart, attachJson, attachCsv);
        return "Done!";
    }

    @Command(desc = "Graph an alliance metric over time for two coalitions")
    public String allianceMetricsAB(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db, AllianceMetric metric, Set<DBAlliance> coalition1, Set<DBAlliance> coalition2,
                                    @Arg("Date to start from")
                                    @Timestamp long time, @Switch("j") boolean attachJson,
                                    @Switch("c") boolean attachCsv) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        TimeNumericTable table = AllianceMetric.generateTable(metric, turnStart, null, coalition1, coalition2);
        table.write(channel, TimeFormat.TURN_TO_DATE, metric.getFormat(), turnStart, attachJson, attachCsv);
        return "Done!";
    }

    @RolePermission(value = {Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS}, any = true)
    @Command(desc = "Create a google sheet of nations, grouped by alliance, with the specified columns\n" +
            "Prefix a column with `avg:` to force an average\n" +
            "Prefix a column with `total:` to force a total")
    @NoFormat
    public String allianceNationsSheet(NationPlaceholders placeholders, AlliancePlaceholders aaPlaceholders, ValueStore store, @Me IMessageIO channel, @Me DBNation me, @Me User author, @Me Guild guild, @Me GuildDB db,
                                       Set<DBNation> nations,
                                       @Arg("The columns to have. See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>") @TextArea List<String> columns,
                                       @Switch("s") SpreadSheet sheet,
                                       @Arg("Use the sum of each nation's attributes instead of the average")
                                       @Switch("t") boolean useTotal, @Switch("i") boolean includeInactives, @Switch("a") boolean includeApplicants) throws IOException, GeneralSecurityException, IllegalAccessException, InvocationTargetException {
        nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        Map<Integer, Set<DBNation>> natByAA = new HashMap<>();
        for (DBNation nation : nations) {
            natByAA.computeIfAbsent(nation.getAlliance_id(), f -> new LinkedHashSet<>()).add(nation);
        }
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.ALLIANCES_SHEET);
        }
        List<String> header = (columns.stream().map(f -> f.replace("{", "").replace("}", "").replace("=", "")).collect(Collectors.toList()));
        sheet.setHeader(header);


        for (Map.Entry<Integer, Set<DBNation>> entry : natByAA.entrySet()) {
            Integer aaId = entry.getKey();

            DBAlliance alliance = DBAlliance.getOrCreate(aaId);

            SimpleNationList list = new SimpleNationList(entry.getValue());

            for (int i = 0; i < columns.size(); i++) {
                String arg = columns.get(i);
                if (arg.equalsIgnoreCase("{nations}")) {
                    arg = list.getNations().size() + "";
                } else if (arg.equalsIgnoreCase("{alliance}")) {
                    arg = alliance.getName() + "";
                } else {
                    boolean total = true;
                    if (arg.startsWith("avg:")) {
                        arg = arg.substring(4);
                        total = false;
                    } else if (arg.startsWith("total:")) {
                        arg = arg.substring(6);
                        total = true;
                    }
                    if (arg.contains("{") && arg.contains("}")) {
                        arg = aaPlaceholders.format2(guild, me, author, arg, alliance, true);
                        if (arg.contains("{") && arg.contains("}")) {
                            NationAttributeDouble metric = placeholders.getMetricDouble(store, arg.substring(1, arg.length() - 1));
                            if (metric == null) {
                                throw new IllegalAccessException("Unknown metric: `" + arg + "`");
                            }
                            double value;
                            if (total) {
                                value = alliance.getTotal(metric, list);
                            } else {
                                value = alliance.getAverage(metric, list);
                            }
                            arg = value + "";
                        }

                    }
                }

                header.set(i, arg);
            }

            sheet.addRow(header);
        }


        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(channel.create(), "alliances").send();
        return null;
    }

    @Command(desc = "Create a graph of the radiation by turn")
    public String radiationByTurn(@Me IMessageIO channel, Set<Continent> continents,
                                  @Arg("Date to start from")
                                  @Timestamp long time, @Switch("j") boolean attachJson,
                                  @Switch("c") boolean attachCsv) throws IOException {
        TimeNumericTable<Void> table = TimeNumericTable.createForContinents(continents, time, Long.MAX_VALUE);

        table.write(channel, TimeFormat.TURN_TO_DATE, TableNumberFormat.SI_UNIT, TimeUtil.getTurn(time), attachJson, attachCsv);
        return "Done!";
    }

    @Command(desc = "Graph the metric over time for a coalition")
    public String allianceMetricsByTurn(@Me IMessageIO channel, @Me User user, @Me Guild guild, @Me GuildDB db, AllianceMetric metric, Set<DBAlliance> coalition,
                                        @Arg("Date to start from")
                                        @Timestamp long time, @Switch("j") boolean attachJson,
                                        @Switch("c") boolean attachCsv) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        List<String> coalitionNames = List.of(metric.name());
        TimeNumericTable table = AllianceMetric.generateTable(metric, turnStart, coalitionNames, coalition);
        table.write(channel, TimeFormat.TURN_TO_DATE, metric.getFormat(), turnStart, attachJson, attachCsv);
        return "Done! " + user.getAsMention();
    }

    @Command(
            desc = "Transfer sheet of war cost (for each nation) broken down by resource type\n" +
                    "Useful to see costs incurred by fighting for each nation, to plan for future wars, or to help with reimbursement"
    )
    @RolePermission(Roles.MILCOM)
    public static String WarCostByResourceSheet(@Me IMessageIO channel, @Me JSONObject command, @Me GuildDB db,
                                                Set<NationOrAlliance> attackers,
                                                Set<NationOrAlliance> defenders,
                                                @Timestamp long time,
                                                @Switch("c") boolean excludeConsumption,
                                                @Switch("i") boolean excludeInfra,
                                                @Switch("l") boolean excludeLoot,
                                                @Switch("u") boolean excludeUnitCost,
                                                @Arg("Include nations on the gray color bloc")
                                         @Switch("g") boolean includeGray,
                                                @Arg("Include defensive wars")
                                         @Switch("d") boolean includeDefensives,
                                                @Arg("Use the average cost per city")
                                         @Switch("n") boolean normalizePerCity,
                                                @Arg("Use the average cost per war")
                                         @Switch("w") boolean normalizePerWar,
                                                @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.WAR_COST_SHEET);
        }
        if ("*".equals(command.getString("attackers"))) attackers = null;
        if ("*".equals(command.getString("defenders"))) defenders = null;
        WarParser parser1 = WarParser.of(attackers, defenders, time, Long.MAX_VALUE);

        Map<Integer, DBWar> allWars = new HashMap<>(parser1.getWars());

        Map<DBNation, List<DBWar>> warsByNation = new RankBuilder<>(allWars.values()).group((BiConsumer<DBWar, GroupedRankBuilder<DBNation, DBWar>>) (war, group) -> {
            DBNation attacker = war.getNation(true);
            DBNation defender = war.getNation(false);
            if (attacker != null && parser1.getIsPrimary().apply(war)) {
                group.put(attacker, war);
            }
            if (defender != null && parser1.getIsSecondary().apply(war)) {
                group.put(defender, war);
            }
        }).get();

        List<Object> header = new ArrayList<>(Arrays.asList(
                // Raids	Raid profit	Raid Ratio	Def	Def Loss	Def Dmg	Def Ratio	Off	Off Loss	Off Dmg	Off Ratio	Wars	War Loss	War Dmg	War Ratio
                "nation",
                "alliance",
                "wars"
        ));

        for (ResourceType type : ResourceType.values) {
            header.add(type.name());
        }
        header.add("convertedTotal");

        sheet.updateClearCurrentTab();

        sheet.setHeader(header);

        Set<Integer> warIdsAttAllowed = new IntOpenHashSet();
        Set<Integer> warIdsDefAllowed = new IntOpenHashSet();

        Set<Integer> warIdsAttAttacks = new IntOpenHashSet();
        Set<Integer> warIdsDefAttacks = new IntOpenHashSet();

        Map<Integer, AttackCost> costByNation = AttackCost.groupBy(ArrayUtil.select(allWars.values(), f -> f.getDate() >= time),
                AttackType::canDamage,
                f -> f.getDate() >= time, null,
                new BiConsumer<AbstractCursor, BiConsumer<AbstractCursor, Integer>>() {
                    @Override
                    public void accept(AbstractCursor attack, BiConsumer<AbstractCursor, Integer> groupFunc) {
                        groupFunc.accept(attack, attack.getAttacker_id());
                        groupFunc.accept(attack, attack.getDefender_id());
                    }
                }, new TriFunction<Function<Boolean, AttackCost>, AbstractCursor, Integer, Map.Entry<AttackCost, Boolean>>() {
                    @Override
                    public Map.Entry<AttackCost, Boolean> apply(Function<Boolean, AttackCost> getCost, AbstractCursor attack, Integer nationId) {
                        DBWar war = allWars.get(attack.getWar_id());
                        Set<Integer> isAllowed = nationId == war.getAttacker_id() ? warIdsAttAllowed : warIdsDefAllowed;

                        Set<Integer> warIdsAttacks = attack.getAttacker_id() == war.getAttacker_id() ? warIdsAttAttacks : warIdsDefAttacks;
                        warIdsAttacks.add(attack.getWar_id());

                        boolean allowed = isAllowed.contains(attack.getWar_id());
                        if (!allowed) {
                            Set<Integer> selfAttacksByWar = nationId == war.getAttacker_id() ? warIdsAttAttacks : warIdsDefAttacks;
                            Set<Integer> enemyAttacksByWar = nationId == war.getAttacker_id() ? warIdsDefAttacks : warIdsAttAttacks;
                            boolean selfAttack = selfAttacksByWar.contains(attack.getWar_id());
                            boolean enemyAttack = enemyAttacksByWar.contains(attack.getWar_id());
                            if ((selfAttack && (includeGray || enemyAttack)) || (war.getAttacker_id() != nationId && enemyAttack && includeDefensives)) {
                                isAllowed.add(attack.getWar_id());
                                allowed = true;
                            }
                        }
                        return Map.entry(
                                getCost.apply(allowed),
                                attack.getAttacker_id() == nationId
                        );
                    }
                }
        );

        for (Map.Entry<DBNation, List<DBWar>> nationEntry : warsByNation.entrySet()) {
            DBNation nation = nationEntry.getKey();
            int nationId = nation.getNation_id();

            AttackCost activeCost = costByNation.get(nationId);
            if (activeCost == null) activeCost = new AttackCost("", "", false, false, false, false, false);

            header = new ArrayList<>();
            header.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
            header.add(MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));

            int numWars = activeCost.getNumWars();
            header.add(numWars);

            Map<ResourceType, Double> total = new HashMap<>();

            if (!excludeConsumption) {
                total = ResourceType.add(total, activeCost.getConsumption(true));
            }
            if (!excludeInfra) {
                double infraCost = activeCost.getInfraLost(true);
                total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + infraCost);
            }
            if (!excludeLoot) {
                total = ResourceType.add(total, activeCost.getLoot(true));
            }
            if (!excludeUnitCost) {
                total = ResourceType.add(total, activeCost.getUnitCost(true));
            }

            if (normalizePerCity) {
                for (Map.Entry<ResourceType, Double> entry : total.entrySet()) {
                    entry.setValue(entry.getValue() / nation.getCities());
                }
            }
            if (normalizePerWar) {
                for (Map.Entry<ResourceType, Double> entry : total.entrySet()) {
                    entry.setValue(numWars == 0 ? 0 : entry.getValue() / numWars);
                }
            }

            for (ResourceType type : ResourceType.values) {
                header.add(total.getOrDefault(type, 0d));
            }
            header.add(ResourceType.convertedTotal(total));

            sheet.addRow(header);
        }

        sheet.updateWrite();

        sheet.attach(channel.create(), "war_cost_rss").send();
        return null;
    }

    @Command(aliases = {"WarCostByAllianceSheet", "WarCostByAASheet", "WarCostByAlliance", "WarCostByAA"},
            desc = "War cost (for each alliance) broken down\n" +
                    "Damage columns:\n" +
                    "- net damage (unit, infrastructure, loot, consumption, total)\n" +
                    "- losses (unit, infrastructure, consumption, total)\n" +
                    "- damage inflicted (unit, infrastructure, consumption, total)\n" +
                    "- net resources (money, gasoline, munitions, aluminum, steel)")
    @RolePermission(Roles.MILCOM)
    public static String WarCostByAllianceSheet(@Me IMessageIO channel, @Me Guild guild,
                                         Set<DBNation> nations,
                                         @Timestamp long time,
                                         @Switch("i") boolean includeInactives,
                                         @Switch("a") boolean includeApplicants) throws IOException, GeneralSecurityException {
        Map<Integer, List<DBNation>> nationsByAA = Locutus.imp().getNationDB().getNationsByAlliance(nations, false, !includeInactives, !includeApplicants, true);
        Set<Integer> nationIds = new HashSet<>();
        for (List<DBNation> nationsInAA : nationsByAA.values()) {
            for (DBNation nation : nationsInAA) {
                nationIds.add(nation.getNation_id());
            }
        }

        GuildDB guildDb = Locutus.imp().getGuildDB(guild);

        SpreadSheet sheet = SpreadSheet.create(guildDb, SheetKey.WAR_COST_BY_ALLIANCE_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                "alliance",
                "score",
                "cities",

                "net unit dmg",
                "net infra dmg",
                "net loot dmg",
                "net consumption",
                "net dmg",

                "unit loss",
                "infra loss",
                "consume loss",
                "loss",

                "unit dmg",
                "infra dmg",
                "consume dmg",
                "dmg",

                "money",
                "gasoline",
                "munitions",
                "aluminum",
                "steel"
        ));
        sheet.setHeader(header);

        Map<Integer, DBWar> allWars = Locutus.imp().getWarDb().getWarsForNationOrAlliance(f -> nationIds.contains(f), null, f -> f.getDate() >= time);

        Map<Integer, AttackCost> costByAlliance = AttackCost.groupBy(ArrayUtil.select(allWars.values(), f -> f.getDate() >= time),
                AttackType::canDamage,
                f -> f.getDate() >= time,
                new Predicate<AbstractCursor>() {
                    @Override
                    public boolean test(AbstractCursor n) {
                        DBNation nat1 = Locutus.imp().getNationDB().getNation(n.getAttacker_id());
                        DBNation nat2 = Locutus.imp().getNationDB().getNation(n.getAttacker_id());
                        return nat1 != null && nat2 != null && (nationsByAA.containsKey(nat1.getAlliance_id()) || nationsByAA.containsKey(nat2.getAlliance_id()));
                    }
                },
                new BiConsumer<AbstractCursor, BiConsumer<AbstractCursor, Integer>>() {
                    @Override
                    public void accept(AbstractCursor attack, BiConsumer<AbstractCursor, Integer> groupBy) {
                        DBWar war = allWars.get(attack.getWar_id());
                        if (nationsByAA.containsKey(war.getAttacker_aa())) groupBy.accept(attack, war.getAttacker_aa());
                        if (nationsByAA.containsKey(war.getDefender_aa())) groupBy.accept(attack, war.getDefender_aa());
                    }
                },
                new TriFunction<Function<Boolean, AttackCost>, AbstractCursor, Integer, Map.Entry<AttackCost, Boolean>>() {
                    @Override
                    public Map.Entry<AttackCost, Boolean> apply(Function<Boolean, AttackCost> getCost, AbstractCursor attack, Integer allianceId) {
                        AttackCost cost = getCost.apply(true);
                        DBWar war = allWars.get(attack.getWar_id());
                        boolean isAttacker = war.getAttacker_id() == attack.getAttacker_id() ? war.getAttacker_aa() == allianceId : war.getDefender_aa() == allianceId;
                        cost.addCost(attack, isAttacker);
                        return Map.entry(cost, isAttacker);
                    }
                });


        for (Map.Entry<Integer, List<DBNation>> entry : nationsByAA.entrySet()) {
            int aaId = entry.getKey();
            AttackCost warCost = costByAlliance.get(aaId);
            if (warCost == null) warCost = new AttackCost("", "", false, false, false, false, false);
            DBAlliance alliance = DBAlliance.getOrCreate(entry.getKey());


            ArrayList<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(alliance.getName(), alliance.getUrl()));

            SimpleNationList aaMembers = new SimpleNationList(alliance.getNations());
            row.add(aaMembers.getScore());
            row.add(aaMembers.getTotal().getCities());

            row.add(-ResourceType.convertedTotal(warCost.getNetUnitCost(true)));
            row.add(-ResourceType.convertedTotal(warCost.getNetInfraCost(true)));
            row.add(-ResourceType.convertedTotal(warCost.getLoot(true)));
            row.add(-ResourceType.convertedTotal(warCost.getNetConsumptionCost(true)));
            row.add(-ResourceType.convertedTotal(warCost.getNetCost(true)));

            row.add(ResourceType.convertedTotal(warCost.getUnitCost(true)));
            row.add((warCost.getInfraLost(true)));
            row.add(ResourceType.convertedTotal(warCost.getConsumption(true)));
            row.add(ResourceType.convertedTotal(warCost.getTotal(true)));

            row.add(ResourceType.convertedTotal(warCost.getUnitCost(false)));
            row.add((warCost.getInfraLost(false)));
            row.add(ResourceType.convertedTotal(warCost.getConsumption(false)));
            row.add(ResourceType.convertedTotal(warCost.getTotal(false)));

            row.add(warCost.getUnitCost(true).getOrDefault(ResourceType.MONEY, 0d) - warCost.getLoot(true).getOrDefault(ResourceType.MONEY, 0d));
            row.add(warCost.getTotal(true).getOrDefault(ResourceType.GASOLINE, 0d));
            row.add(warCost.getTotal(true).getOrDefault(ResourceType.MUNITIONS, 0d));
            row.add(warCost.getTotal(true).getOrDefault(ResourceType.ALUMINUM, 0d));
            row.add(warCost.getTotal(true).getOrDefault(ResourceType.STEEL, 0d));

            sheet.addRow(row);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(channel.create(), "war_cost_aa").send();
        return null;
    }

    @Command(desc = "War cost (for each nation) broken down by war type\n" +
            "The sheet is divided into groups for:\n" +
            "- Raids: Attacking nations which do not fight back\n" +
            "- Defenses: Attacked by a nation and fighting back\n" +
            "- Offenses: Attacking a nation which fights back\n" +
            "- Wars: Combination of defensive and offensive wars (not raids)")
    @RolePermission(Roles.MILCOM)
    public String WarCostSheet(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db, Set<NationOrAlliance> attackers, Set<NationOrAlliance> defenders, @Timestamp long time, @Default @Timestamp Long endTime,
                               @Switch("c") boolean excludeConsumption,
                               @Switch("i") boolean excludeInfra,
                               @Switch("l") boolean excludeLoot,
                               @Switch("u") boolean excludeUnitCost,
                               @Arg("Average the cost by the nation's city count")
                               @Switch("n") boolean normalizePerCity,
                               @Switch("leader") boolean useLeader,
                               @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.WAR_COST_SHEET);
        }

        WarParser parser1 = WarParser.of(attackers, defenders, time, endTime == null ? Long.MAX_VALUE : endTime);

        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",

                "#raids",
                "profit_total",
                "proft_avg",

                "#def",
                "loss_avg",
                "dmg_avg",
                "ratio",

                "#off",
                "loss_avg",
                "dmg_avg",
                "ratio",

                "#wars",
                "loss_avg",
                "dmg_avg",
                "ratio"
        ));

        sheet.updateClearCurrentTab();

        sheet.setHeader(header);
        long start = System.currentTimeMillis();

        Map<Integer, DBWar> allWars = new HashMap<>(parser1.getWars());

        Map<Integer, List<DBWar>> warsByNation = new RankBuilder<>(allWars.values()).group((BiConsumer<DBWar, GroupedRankBuilder<Integer, DBWar>>) (war, group) -> {
            if (parser1.getIsPrimary().apply(war)) {
                group.put(war.getAttacker_id(), war);
            } else if (parser1.getIsSecondary().apply(war)) {
                group.put(war.getDefender_id(), war);
            }
        }).get();

        Set<Integer> aaIds = attackers.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
        Set<Integer> natIds = attackers.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());

        warsByNation.entrySet().removeIf(entry -> {
            int nationId = entry.getKey();
            DBNation nation = DBNation.getById(nationId);
            if (nation == null) return true;
            if (!natIds.contains(nation.getId()) && !aaIds.contains(nation.getAlliance_id())) return true;
            return false;
        });

        Map<Integer, List<AbstractCursor>> attacksByNation = new Int2ObjectOpenHashMap<>();
        for (AbstractCursor attack : parser1.getAttacks()) {
            if (warsByNation.containsKey(attack.getAttacker_id())) {
                attacksByNation.computeIfAbsent(attack.getAttacker_id(), f -> new ObjectArrayList<>()).add(attack);
            }
            if (warsByNation.containsKey(attack.getDefender_id())) {
                attacksByNation.computeIfAbsent(attack.getDefender_id(), f -> new ObjectArrayList<>()).add(attack);
            }
        }

        for (Map.Entry<Integer, List<DBWar>> entry : warsByNation.entrySet()) {
            int nationId = entry.getKey();
            DBNation nation = DBNation.getById(nationId);
            if (nation == null) continue;
            if (!natIds.contains(nation.getId()) && !aaIds.contains(nation.getAlliance_id())) continue;

            AttackCost attInactiveCost = new AttackCost("", "", false, false, false, true, false);
            AttackCost attActiveCost = new AttackCost("", "", false, false, false, true, false);
            AttackCost defActiveCost = new AttackCost("", "", false, false, false, true, false);

            AttackCost attSuicides = new AttackCost("", "", false, false, false, true, false);
            AttackCost defSuicides = new AttackCost("", "", false, false, false, true, false);

            {
                List<DBWar> wars = entry.getValue();
                List<AbstractCursor> attacks = attacksByNation.remove(nationId);
                Map<Integer, List<AbstractCursor>> attacksByWar = attacks == null ? new HashMap<>() : new RankBuilder<>(attacks).group(f -> f.getWar_id()).get();

                for (DBWar war : wars) {
                    List<AbstractCursor> warAttacks = attacksByWar.getOrDefault(war.warId, Collections.emptyList());

                    boolean selfAttack = false;
                    boolean enemyAttack = false;

                    for (AbstractCursor attack : warAttacks) {
                        if (attack.getAttacker_id() == nationId) {
                            selfAttack = true;
                        } else {
                            enemyAttack = true;
                        }
                    }

                    Function<AbstractCursor, Boolean> isPrimary = a -> a.getAttacker_id() == nationId;
                    Function<AbstractCursor, Boolean> isSecondary = a -> a.getAttacker_id() != nationId;

                    AttackCost cost;
                    if (war.getAttacker_id() == nationId) {
                        if (selfAttack) {
                            if (enemyAttack) {
                                cost = attActiveCost;
                            } else {
                                cost = attInactiveCost;
                            }
                        } else if (enemyAttack) {
                            cost = attSuicides;
                        } else {
                            continue;
                        }
                    } else {
                        if (selfAttack) {
                            if (enemyAttack) {
                                cost = defActiveCost;
                            } else {
                                cost = defSuicides;
                            }
                        } else if (enemyAttack) {
                            cost = defActiveCost;//defInactiveCost;
                        } else {
                            continue;
                        }
                    }

                    cost.addCost(warAttacks, isPrimary, isSecondary);
                }
            }

            header.set(0, MarkupUtil.sheetUrl(useLeader ? nation.getLeader() : nation.getNation(), nation.getUrl()));
            header.set(1, MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));

            header.set(2, attInactiveCost.getNumWars());
            header.set(3, -total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attInactiveCost, true));
            header.set(4, attInactiveCost.getNumWars() == 0 ? 0 : (-total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attInactiveCost, true)) / (double) attInactiveCost.getNumWars());

            header.set(5, defActiveCost.getNumWars());
            header.set(6, defActiveCost.getNumWars() == 0 ? 0 : total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, defActiveCost, true) / defActiveCost.getNumWars());
            header.set(7, defActiveCost.getNumWars() == 0 ? 0 : total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, defActiveCost, false) / defActiveCost.getNumWars());
            double defRatio = (double) header.get(7) / (double) header.get(6);
            header.set(8, defActiveCost.getNumWars() == 0 ? 0 : Double.isFinite(defRatio) ? defRatio : 0);

            header.set(9, attActiveCost.getNumWars());
            header.set(10, attActiveCost.getNumWars() == 0 ? 0 : total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attActiveCost, true) / attActiveCost.getNumWars());
            header.set(11, attActiveCost.getNumWars() == 0 ? 0 : total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attActiveCost, false) / attActiveCost.getNumWars());
            double attRatio = (double) header.get(11) / (double) header.get(10);
            header.set(12, attActiveCost.getNumWars() == 0 ? 0 : Double.isFinite(attRatio) ? attRatio : 0);

            int numTotal = defActiveCost.getNumWars() + attActiveCost.getNumWars();
            double lossTotal = total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, defActiveCost, true) + total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attActiveCost, true);
            double dmgTotal = total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, defActiveCost, false) + total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attActiveCost, false);
            header.set(13, numTotal);
            header.set(14, numTotal == 0 ? 0 : lossTotal / numTotal);
            header.set(15, numTotal == 0 ? 0 : dmgTotal / numTotal);
            double ratio = (double) header.get(15) / (double) header.get(14);
            header.set(16, numTotal == 0 ? 0 : Double.isFinite(ratio) ? ratio : 0);

            sheet.addRow(header);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(channel.create(), "war_cost").send();
        return null;
    }

    private double total(boolean consumption, boolean infra, boolean loot, boolean units, boolean normalize, DBNation nation, AttackCost cost, boolean isPrimary) {
        Map<ResourceType, Double> total = new HashMap<>();

        if (consumption) {
            total = ResourceType.add(total, cost.getConsumption(isPrimary));
        }
        if (infra) {
            double infraCost = cost.getInfraLost(isPrimary);
            total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + infraCost);
        }
        if (loot) {
            total = ResourceType.add(total, cost.getLoot(isPrimary));
        }
        if (units) {
            total = ResourceType.add(total, cost.getUnitCost(isPrimary));
        }

        if (normalize) {
            for (Map.Entry<ResourceType, Double> entry : total.entrySet()) {
                entry.setValue(entry.getValue() / nation.getCities());
            }
        }
        return ResourceType.convertedTotal(total);
    }

    @Command(desc = """
                Get the militirization levels of top 80 alliances.
                Each bar is segmented into four sections, from bottom to top: (soldiers, tanks, planes, ships)
                Each alliance is grouped by sphere and color coded.""")
    public static String militaryRanking(@Me GuildDB db, @Me IMessageIO channel,
                                         @Default NationList nations2,
                                         @Switch("n") Integer top_n_alliances,
                                         @Switch("s") SpreadSheet sheet,
                                         @Switch("t") boolean removeUntaxable,
                                         @Switch("i") boolean removeInactive,
                                         @Switch("a") boolean includeApplicants,
                                        @Switch("l") @Timestamp Long snapshotDate) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.MILITARY_RANKING);
        }
        if (top_n_alliances == null) top_n_alliances = 80;
        Map<Integer, List<DBNation>> byAA;
        if (nations2 == null) {
            byAA = Locutus.imp().getNationDB().getNationsByAlliance(removeUntaxable, removeInactive, !includeApplicants, true, true);
        } else {
            Set<DBNation> tmp;
            if (snapshotDate != null) {
                tmp = PW.getNationsSnapshot(nations2.getNations(), nations2.getFilter(), snapshotDate, db.getGuild(), false);
            } else {
                tmp = nations2.getNations();
            }
            tmp.removeIf(f -> f.getVm_turns() > 0);
            byAA = Locutus.imp().getNationDB().getNationsByAlliance(tmp, removeUntaxable, removeInactive, !includeApplicants, true);
        }
        Map<Integer, Color> sphereColors = new HashMap<>();
        Map<Integer, Double> sphereScore = new HashMap<>();
        Map<Integer, Map<Integer, NationList>> sphereAllianceMembers = new HashMap<>();

        Map<Integer, DBAlliance> aaCache = new HashMap<>();

        int topX = top_n_alliances;
        for (Map.Entry<Integer, List<DBNation>> entry : byAA.entrySet()) {
            if (topX-- <= 0) break;
            Integer aaId = entry.getKey();
            DBAlliance alliance = aaCache.computeIfAbsent(aaId, f -> DBAlliance.getOrCreate(aaId));
            List<DBAlliance> sphere = alliance.getSphereRankedCached(aaCache);
            int sphereId = sphere.get(0).getAlliance_id();

            {
                List<DBNation> aaNations = new ArrayList<>(entry.getValue());
                sphereAllianceMembers.computeIfAbsent(sphereId, f -> new HashMap<>()).put(alliance.getAlliance_id(), new SimpleNationList(aaNations));
            }

            if (!sphereScore.containsKey(sphereId)) {
                List<DBNation> nations = new ArrayList<>();
                for (DBAlliance other : sphere) {
                    List<DBNation> otherNations = byAA.get(other.getAlliance_id());
                    if (otherNations != null) {
                        nations.addAll(otherNations);
                    }
                }
                SimpleNationList nationList = new SimpleNationList(nations);

                sphereScore.put(sphereId, nationList.getScore());
                if (sphere.size() > 1) {
                    sphereAllianceMembers.computeIfAbsent(sphereId, f -> new HashMap<>()).put(0, nationList);
                }
            }
        }

        List<String> header = Arrays.asList(
                "alliance",
                "sphere_id",
                "score",
                "cities",
                "soldiers",
                "tanks",
                "planes",
                "ships",

                "soldier_%",
                "tank_%",
                "plane_%",
                "ship_%",

                "barracks",
                "factories",
                "hangars",
                "drydocks",

                "soldier_change",
                "factory_change",
                "plane_change",
                "ship_change"
        );

        sheet.setHeader(header);

        sphereScore = new SummedMapRankBuilder<>(sphereScore).sort().get();
        for (Map.Entry<Integer, Double> entry : sphereScore.entrySet()) {
            int sphereId = entry.getKey();

            Color color = sphereColors.computeIfAbsent(sphereId, f -> CIEDE2000.randomColor(sphereId, DiscordUtil.BACKGROUND_COLOR, sphereColors.values()));
            String colorStr = WebUtil.getColorHex(color);

            ArrayList<Map.Entry<Integer, NationList>> sphereAAs = new ArrayList<>(sphereAllianceMembers.get(sphereId).entrySet());
            sphereAAs.sort((o1, o2) -> Double.compare(o2.getValue().getScore(), o1.getValue().getScore()));
            for (Map.Entry<Integer, NationList> aaEntry : sphereAAs) {
                int aaId = aaEntry.getKey();
                NationList nations = aaEntry.getValue();

                DBNation total = nations.getTotal();

                ArrayList<Object> row = new ArrayList<>();
                if (aaId != 0) {
                    DBAlliance alliance = DBAlliance.getOrCreate(aaId);
                    row.add(MarkupUtil.sheetUrl(alliance.getName(), alliance.getUrl()));
                } else {
                    row.add("");
                }
                row.add(colorStr);
                row.add(nations.getScore());
                row.add(total.getCities());

                row.add(total.getSoldiers());
                row.add(total.getTanks());
                row.add(total.getAircraft());
                row.add(total.getShips());

                double soldierPct = 100 * (double) total.getSoldiers() / (Buildings.BARRACKS.getUnitCap() * Buildings.BARRACKS.cap(total::hasProject) * total.getCities());
                double tankPct = 100 * (double) total.getTanks() / (Buildings.FACTORY.getUnitCap() * Buildings.FACTORY.cap(total::hasProject) * total.getCities());
                double airPct = 100 * (double) total.getAircraft() / (Buildings.HANGAR.getUnitCap() * Buildings.HANGAR.cap(total::hasProject) * total.getCities());
                double navyPct = 100 * (double) total.getShips() / (Buildings.DRYDOCK.getUnitCap() * Buildings.DRYDOCK.cap(total::hasProject) * total.getCities());

                row.add(soldierPct);
                row.add(tankPct);
                row.add(airPct);
                row.add(navyPct);

                double[] mmr = nations.getAverageMMR(false);
                row.add(mmr[0] * 100 / Buildings.BARRACKS.cap(total::hasProject));
                row.add(mmr[1] * 100 / Buildings.FACTORY.cap(total::hasProject));
                row.add(mmr[2] * 100 / Buildings.HANGAR.cap(total::hasProject));
                row.add(mmr[3] * 100 / Buildings.DRYDOCK.cap(total::hasProject));

                double[] buy = nations.getMilitaryBuyPct(false);
                row.add(buy[0]);
                row.add(buy[1]);
                row.add(buy[2]);
                row.add(buy[3]);

                for (int i = 0; i < row.size(); i++) {
                    Object val = row.get(i);
                    if (val instanceof Number && !Double.isFinite(((Number) val).doubleValue())) {
                        row.set(i, 0);
                    }
                }

                sheet.addRow(row);
            }
        }

        IMessageBuilder msg = channel.create();
        {
            List<List<Object>> values = sheet.getCachedValues(null);

            DataTable data = new DataTable(Double.class, Double.class, String.class);
            Function<Number, Color> colorFunction = f -> Color.decode((String) values.get((f.intValue() / 4) + 1).get(1));

            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                String[] allianceSplit = ((String) row.get(0)).split("\"");
                String alliance = allianceSplit.length > 2 ? allianceSplit[allianceSplit.length - 2] : "bloc average.";
                Color color = Color.decode((String) row.get(1));

                double total = 0;
                for (int j = 8; j < 12; j++) {
                    double val = ((Number) row.get(j)).doubleValue() / 4d;
                    total += val;
                }
                for (int j = 11; j >= 8; j--) {
                    double val = ((Number) row.get(j)).doubleValue() / 4d;
                    data.add(i + 0d, total, j == 8 ? alliance : "");
                    total -= val;
                }
            }

            // Create new bar plot
            BarPlot plot = new BarPlot(data);

            // Format plot
            plot.setInsets(new Insets2D.Double(40.0, 40.0, 40.0, 0.0));
            plot.setBarWidth(0.9);
            plot.setBackground(Color.WHITE);

            Color COLOR1 = Color.DARK_GRAY;
            // Format bars
            BarPlot.BarRenderer pointRenderer = (BarPlot.BarRenderer) plot.getPointRenderers(data).get(0);
            pointRenderer.setColor(new ColorMapper() {
                @Override
                public Paint get(Number number) {
                    return colorFunction.apply(number);
                }

                @Override
                public Mode getMode() {
                    return null;
                }
            });
            pointRenderer.setBorderStroke(new BasicStroke(1f));
            pointRenderer.setBorderColor(Color.LIGHT_GRAY);
            pointRenderer.setValueVisible(true);
            pointRenderer.setValueColumn(2);
            pointRenderer.setValueLocation(Location.NORTH);
            pointRenderer.setValueRotation(90);
            pointRenderer.setValueColor(new ColorMapper() {
                @Override
                public Paint get(Number number) {
                    return CIEDE2000.findComplement(colorFunction.apply(number));
                }

                @Override
                public Mode getMode() {
                    return null;
                }
            });
            pointRenderer.setValueFont(Font.decode(null).deriveFont(12.0f));

            DrawableWriter writer = DrawableWriterFactory.getInstance().get("image/png");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(plot, baos, 1400, 600);
            msg.image("img.png", baos.toByteArray());
        }

        msg.append("> Each bar is segmented into four sections, from bottom to top: (soldiers, tanks, planes, ships)\n" +
                "> Each alliance is grouped by sphere and color coded");

        sheet.attach(msg, "alliance_ranking").send();
        return null;
    }

    @Command(desc = "Create a google sheet of nations and the number of bad attacks they did over a timeframe")
    public void attackBreakdownSheet(@Me IMessageIO io, @Me GuildDB db, Set<NationOrAlliance> attackers, Set<NationOrAlliance> defenders, @Timestamp Long start, @Timestamp @Default Long end, @Switch("s") SpreadSheet sheet, @Switch("a") @Arg("Also checks defender activity, to provide more fine grained attack information, but takes longer") boolean checkActivity) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.ATTACK_BREAKDOWN_SHEET);
        }
        WarParser parser1 = WarParser.of(attackers, defenders, start, end == null ? Long.MAX_VALUE : end);

        Map<Integer, Integer> offWarsByNat = new Int2IntOpenHashMap();
        Map<Integer, Integer> defWarsByNat = new Int2IntOpenHashMap();

        Map<Integer, DBWar> wars = parser1.getWars();
        Set<Integer> aaIds = attackers.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
        Set<Integer> natIds = attackers.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
        {
            Set<Integer> attackerNations = new IntOpenHashSet();
            for (DBWar war : wars.values()) {
                if (natIds.contains(war.getAttacker_id()) || aaIds.contains(war.getAttacker_aa())) {
                    attackerNations.add(war.getAttacker_id());
                    offWarsByNat.merge(war.getAttacker_id(), 1, Integer::sum);
                }
                if (natIds.contains(war.getDefender_id()) || aaIds.contains(war.getDefender_aa())) {
                    attackerNations.add(war.getDefender_id());
                    defWarsByNat.merge(war.getDefender_id(), 1, Integer::sum);
                }
            }
            if (attackerNations.size() > 1000) {
                throw new IllegalArgumentException("Too many attackers (max: 1000, provided: " + attackerNations.size() + ")");
            }
        }

        List<AbstractCursor> allAttacks = parser1.getAttacks();

        Predicate<AbstractCursor> isAttacker = f -> {
            if (natIds.contains(f.getAttacker_id())) return true;
            DBWar war = wars.get(f.getWar_id());
            if (war != null) {
                int aaId = war.getAttacker_id() == f.getAttacker_id() ? war.getAttacker_aa() : war.getDefender_aa();
                return aaIds.contains(aaId);
            }
            return false;
        };

        List<AttackTypeSubCategory> types = new ObjectArrayList<>();
        types.add(AttackTypeSubCategory.DOUBLE_FORTIFY);
        types.add(AttackTypeSubCategory.GROUND_TANKS_MUNITIONS_USED_UNNECESSARY);
        types.add(AttackTypeSubCategory.GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY);
        types.add(AttackTypeSubCategory.GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY_INACTIVE);
        types.add(AttackTypeSubCategory.GROUND_TANKS_NO_LOOT_NO_ENEMY_AIR_INACTIVE);
        types.add(AttackTypeSubCategory.GROUND_TANKS_NO_LOOT_NO_ENEMY_AIR);
        types.add(AttackTypeSubCategory.AIRSTRIKE_SOLDIERS_NONE);
        types.add(AttackTypeSubCategory.AIRSTRIKE_SOLDIERS_SHOULD_USE_GROUND);
        types.add(AttackTypeSubCategory.AIRSTRIKE_TANKS_NONE);
        types.add(AttackTypeSubCategory.AIRSTRIKE_SHIP_NONE);
        types.add(AttackTypeSubCategory.AIRSTRIKE_INACTIVE_NO_GROUND);
        types.add(AttackTypeSubCategory.AIRSTRIKE_INACTIVE_NO_SHIP);
        types.add(AttackTypeSubCategory.AIRSTRIKE_FAILED_NOT_DOGFIGHT);
        types.add(AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_NONE);
        types.add(AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_NONE_INACTIVE);
        types.add(AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_LOW);
        types.add(AttackTypeSubCategory.AIRSTRIKE_INFRA);
        types.add(AttackTypeSubCategory.AIRSTRIKE_MONEY);
        types.add(AttackTypeSubCategory.NAVAL_MAX_VS_NONE);
        boolean[] allowedTypes = new boolean[AttackTypeSubCategory.values().length];
        types.forEach(f -> allowedTypes[f.ordinal()] = true);

        Map<Integer, Integer> attacksByNation = new Int2IntOpenHashMap();
        Map<Integer, Map<AttackTypeSubCategory, Integer>> countsByNation = new Int2ObjectOpenHashMap<>();
        for (AbstractCursor attack : allAttacks) {
            if (!isAttacker.test(attack)) continue;
            attacksByNation.merge(attack.getAttacker_id(), 1, Integer::sum);
            AttackTypeSubCategory subType = attack.getSubCategory(true);
            if (subType == null) continue;
            if (allowedTypes[subType.ordinal()]) {
                countsByNation.computeIfAbsent(attack.getAttacker_id(), f -> new EnumMap<>(AttackTypeSubCategory.class)).merge(subType, 1, Integer::sum);
            }
        }

        StringBuilder response = new StringBuilder();
        if (!checkActivity) {
            response.append("Set `checkActivity: True` to also check for unnecessary attacks against inactive nations (slower)");
        }

        sheet.updateClearCurrentTab();

        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "cities",
                "off",
                "def",
                "attacks",
                "bad_attacks"
        ));
        for (AttackTypeSubCategory type : types) {
            header.add(type.name().toLowerCase());
        }

        sheet.setHeader(header);

        for (Map.Entry<Integer, Integer> entry : attacksByNation.entrySet()) {
            int id = entry.getKey();
            DBNation nation = DBNation.getById(entry.getKey());
            int aaId = nation == null ? 0 : nation.getAlliance_id();
            int cities = nation == null ? 0 : nation.getCities();
            int off = offWarsByNat.getOrDefault(entry.getKey(), 0);
            int def = defWarsByNat.getOrDefault(entry.getKey(), 0);
            int attacks = entry.getValue();

            Map<AttackTypeSubCategory, Integer> counts = countsByNation.getOrDefault(entry.getKey(), Collections.emptyMap());
            int badAttacks = counts.values().stream().mapToInt(f -> f).sum();

            header.set(0, MarkupUtil.sheetUrl(PW.getName(id, false), PW.getNationUrl(id)));
            header.set(1, MarkupUtil.sheetUrl(PW.getName(aaId, true), PW.getAllianceUrl(aaId)));
            header.set(2, cities);
            header.set(3, off);
            header.set(4, def);
            header.set(5, attacks);
            header.set(6, badAttacks);
            for (int i = 0; i < types.size(); i++) {
                header.set(i + 7, counts.getOrDefault(types.get(i), 0));
            }
            sheet.addRow(header);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();
        IMessageBuilder msg = io.create();
        sheet.attach(msg, "attack_breakdown");
        if (!response.isEmpty()) msg.append(response.toString());
        msg.send();
    }

    @Command(desc = "Get a game graph by day")
    public String orbisStatByDay(@Me IMessageIO channel, NationDB db, Set<OrbisMetric> metrics, @Default @Timestamp Long start, @Default @Timestamp Long end, @Switch("j") boolean attachJson, @Switch("c") boolean attachCsv) throws IOException {
        // Update
        OrbisMetric.update(db);

        if (start == null) start = 0L;
        if (end == null) end = Long.MAX_VALUE;
        boolean hasTurn = metrics.stream().anyMatch(OrbisMetric::isTurn);
        boolean hasDay = metrics.stream().anyMatch(f -> !f.isTurn());
        if (hasTurn && hasDay) throw new IllegalArgumentException("Cannot mix turn and day metrics");
        Map<OrbisMetric, Map<Long, Double>> metricData = db.getMetrics(metrics, start, end);
        Map<Long, Map<OrbisMetric, Double>> dataByTurn = new Long2ObjectOpenHashMap<>();

        long minTurn = Long.MAX_VALUE;
        long maxTurn = 0;
        for (Map.Entry<OrbisMetric, Map<Long, Double>> entry : metricData.entrySet()) {
            OrbisMetric metric = entry.getKey();
            Map<Long, Double> turnValue = entry.getValue();
            for (Map.Entry<Long, Double> turnValueEntry : turnValue.entrySet()) {
                long turn = turnValueEntry.getKey();
                double value = turnValueEntry.getValue();
                dataByTurn.computeIfAbsent(turn, f -> new EnumMap<>(OrbisMetric.class)).put(metric, value);
                minTurn = Math.min(minTurn, turn);
                maxTurn = Math.max(maxTurn, turn);
            }
        }
        if (maxTurn == 0) return "No data found";


        List<OrbisMetric> metricList = new ArrayList<>(metrics);
        metricList.sort(Comparator.comparing(OrbisMetric::ordinal));

        String turnOrDayStr = hasTurn ? "turn" : "day";
        String title = (metrics.size() == 1 ? metrics.iterator().next() : "Game Stats") + " by " + turnOrDayStr;
        String[] labels = metrics.stream().map(f -> f.name().toLowerCase().replace("_", " ")).toArray(String[]::new);
        double[] buffer = new double[labels.length];

        long finalMinTurn = minTurn;
        TimeNumericTable<Void> table = new TimeNumericTable<>(title, turnOrDayStr, "Radiation", labels) {
            @Override
            public void add(long turn, Void ignore) {
                int turnRelative = (int) (turn - finalMinTurn);
                Map<OrbisMetric, Double> turnData = dataByTurn.get(turn);
                if (turnData != null) {
                    for (int i = 0; i < metricList.size(); i++) {
                        double rads = turnData.getOrDefault(metricList.get(i), 0d);
                        buffer[i] = rads;
                    }
                    double total = 0;
                    for (double val : turnData.values()) total += val;
                    buffer[buffer.length - 1] = total / 5d;
                }
                add(turnRelative, buffer);
            }
        };

        for (long turn = minTurn; turn <= maxTurn; turn++) {
            table.add(turn, (Void) null);
        }
        TimeFormat format = hasTurn ? TimeFormat.TURN_TO_DATE : TimeFormat.DAYS_TO_DATE;
        table.write(channel, format, TableNumberFormat.SI_UNIT, minTurn, attachJson, attachCsv);
        return "Done!";
    }

    private void add(SpreadSheet sheet,
                     Map<Integer, String> aaNames,
                     Map<Long, Map<Integer, Set<Integer>>> nationsByAAByDay,
                     Map<Long, Map<Integer, Integer>> nationAllianceByDay,
                     int aaId, long day, long currentDay, long dayWindow) {
        Set<Integer> nations = new IntOpenHashSet();
        for (long prevDay = day - dayWindow; prevDay <= Math.min(currentDay, day + dayWindow); prevDay++) {
            nations.addAll(nationsByAAByDay.getOrDefault(prevDay, Map.of()).getOrDefault(aaId, Set.of()));
        }
        int prevMembers = nations.size();
        int currMembers = nationsByAAByDay.getOrDefault(day, Map.of()).getOrDefault(aaId, Set.of()).size();

        long futureDay = Math.min(currentDay, day + dayWindow);
        Map<Integer, Integer> countsByAA = new Int2IntOpenHashMap();
        for (int nationId : nations) {
            int futureAA = nationAllianceByDay.getOrDefault(futureDay, Map.of()).getOrDefault(nationId, 0);
            if (futureAA != 0 && futureAA != aaId) {
                countsByAA.merge(futureAA, 1, Integer::sum);
            }
        }
        Map<Integer, Integer> sorted = ArrayUtil.sortMap(countsByAA, false);
        if (sorted.isEmpty()) return;
//        for (Map.Entry<Integer, Integer> entry3 : sorted.entrySet()) {
//            double percent = 100d * entry3.getValue() / nations.size();
//        }

        int mergedAA = sorted.keySet().iterator().next();
        int mergedAAAmt = sorted.values().iterator().next();
        String dayStr = TimeUtil.YYYY_MM_DD_FORMAT.format(new Date(TimeUtil.getTimeFromDay(day)));
        List<String> row = new ArrayList<>(Arrays.asList(
                MarkupUtil.sheetUrl(aaNames.getOrDefault(aaId, "AA:" + aaId), PW.getAllianceUrl(aaId)),
                dayStr,
                prevMembers + "",
                currMembers + "",
                (prevMembers - currMembers) + "",
                (100d * (prevMembers - currMembers) / prevMembers) + "%",
                MarkupUtil.sheetUrl(aaNames.getOrDefault(mergedAA, "AA:" + mergedAA), PW.getAllianceUrl(mergedAA)),
                mergedAAAmt + "",
                (100d * mergedAAAmt / (nations.size())) + "%"
        ));

        sheet.addRow(row);
    }

    @Command
    @RolePermission(Roles.ADMIN)
    public void listMerges(
            @Me GuildDB db,
            @Me IMessageIO io,
                           @Switch("s") SpreadSheet sheet,
                           @Arg("Required percent of departures percent(between 0 and 1)\n" +
                                   "Default: 0.3")
                           @Switch("t") Double threshold,
                           @Arg("Number of days to check the departures over\n" +
                                   "Default: 30")
                           @Switch("w") Integer dayWindow,
                           @Arg("Minimum number of starting members per alliance\n" +
                                   "Default: 10")
                           @Switch("m") Integer minMembers) throws IOException, ParseException, GeneralSecurityException {
        if (threshold == null) threshold = 0.3;
        if (dayWindow == null) dayWindow = 30;
        if (minMembers == null) minMembers = 10;

        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.MERGES_SHEET);
        }

        CompletableFuture<IMessageBuilder> msg = io.create().append("Please wait...").send();
        long currentDay = TimeUtil.getDay() - 1;

        Map<Long, Map<Integer, Set<Integer>>> nationsByAAByDay = new Long2ObjectLinkedOpenHashMap<>();
        Map<Long, Map<Integer, Integer>> nationAllianceByDay = new Long2ObjectLinkedOpenHashMap<>();
        Map<Integer, String> allianceNames = new Int2ObjectOpenHashMap<>();

        Map<Integer, Map<Long, Integer>> membersByAAByTurn = new Int2ObjectLinkedOpenHashMap<>();
        DataDumpParser dumper = Locutus.imp().getDataDumper(true);

        AtomicLong start = new AtomicLong(System.currentTimeMillis());
        dumper.iterateAll(f -> true, (h, r) -> {
            r.required(h.alliance_position, h.alliance_id, h.nation_id, h.alliance);
        }, null, (day, header) -> {
            Rank position = header.alliance_position.get();
            if (position.id <= Rank.APPLICANT.id) return;
            int aaId = header.alliance_id.get();
            membersByAAByTurn.computeIfAbsent(aaId, k -> new Long2IntLinkedOpenHashMap()).merge(day, 1, Integer::sum);
            int nationId = header.nation_id.get();
            nationsByAAByDay.computeIfAbsent(day, k -> new Int2ObjectLinkedOpenHashMap<>()).computeIfAbsent(aaId, k -> new IntOpenHashSet()).add(nationId);
            nationAllianceByDay.computeIfAbsent(day, k -> new Int2IntLinkedOpenHashMap()).put(nationId, aaId);

            if (!allianceNames.containsKey(aaId)) {
                String aaName = header.alliance.get();
                allianceNames.put(aaId, aaName);
            }
        }, null, aLong -> {
            long now = System.currentTimeMillis();
            if (start.get() + 5000 < now) {
                start.set(now);
                io.updateOptionally(msg, "Processing day " + aLong + "/" + (currentDay + 1));
            }
        });;

//        System.out.println("Alliance " + conflictManager.getAllianceName(aaId) + "\t" + aaId + "\t" + PW.getMarkdownUrl(aaId, true) + "\t" + new Date(TimeUtil.getTimeFromDay(day)) + "\t" + prevMembers + "\t" + members + "\t" + diff + "\t" + MathMan.format(100d * diff / prevMembers) + "%");
        List<String> header = new ArrayList<>(Arrays.asList(
                "alliance",
                "date",
                "members",
                "remaining",
                "left",
                "percent",
                "joined_aa",
                "joined_aa_amt",
                "joined_percent"
        ));

        sheet.setHeader(header);

        System.out.println("START CHECK ALLIANCE MEMBERS");
        for (Map.Entry<Integer, Map<Long, Integer>> entry : membersByAAByTurn.entrySet()) {
            int aaId = entry.getKey();
            Map<Long, Integer> membersByDay = entry.getValue();
            long[] lastDayBuffer = new long[dayWindow]; // circular buffer
            int[] membersBuffer = new int[dayWindow];
            boolean unfilled = true;
            int index = 0;

            long maxDiff = 0;
            long maxDay = 0;
            for (Map.Entry<Long, Integer> entry2 : membersByDay.entrySet()) {
                long day = entry2.getKey();

                int members = entry2.getValue();
                lastDayBuffer[index] = day;
                membersBuffer[index] = members;

                int prevIndex = unfilled ? 0 : (index + dayWindow - 1) % dayWindow;

                index++;
                if (index >= dayWindow) {
                    index = 0;
                    unfilled = false;
                }
                int prevMembers = membersBuffer[prevIndex];
                if (prevMembers < minMembers) {
                    if (maxDay > 0) {
                        add(sheet, allianceNames, nationsByAAByDay, nationAllianceByDay, aaId, maxDay, currentDay, dayWindow);
                    }
                    maxDiff = 0;
                    maxDay = 0;
                    continue;
                }

                int diff = (int) (prevMembers - members);

                if ((double) diff / prevMembers > threshold) {
                    if (diff > maxDiff) {
                        maxDiff = diff;
                        maxDay = day;
                    }
                } else {
                    if (maxDay > 0) {
                        add(sheet, allianceNames, nationsByAAByDay, nationAllianceByDay, aaId, maxDay, currentDay, dayWindow);
                    }
                    maxDiff = 0;
                    maxDay = 0;
                }
            }
            if (maxDay != 0) {
                add(sheet, allianceNames, nationsByAAByDay, nationAllianceByDay, aaId, maxDay, currentDay, dayWindow);
            }
        }
        sheet.updateClearCurrentTab();
        sheet.updateWrite();
        sheet.attach(io.create(), "merges").send();
    }
}
