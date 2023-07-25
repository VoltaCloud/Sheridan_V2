package link.locutus.discord.db;

import com.politicsandwar.graphql.model.*;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.WarAttacksContainer;
import link.locutus.discord.apiv1.domains.subdomains.attack.AbstractAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.WarAttackWrapper;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AttackCursorFactory;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.handlers.ActiveWarHandler;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.nation.NationChangeColorEvent;
import link.locutus.discord.event.war.AttackEvent;
import link.locutus.discord.event.bounty.BountyCreateEvent;
import link.locutus.discord.event.bounty.BountyRemoveEvent;
import link.locutus.discord.event.war.WarStatusChangeEvent;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.update.NationUpdateProcessor;
import link.locutus.discord.util.update.WarUpdateProcessor;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.SWarContainer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WarDB extends DBMainV2 {


    private final  ActiveWarHandler activeWars = new ActiveWarHandler();
    private Map<Integer, DBWar> warsById;
    private Map<Integer, Map<Integer, DBWar>> warsByAllianceId;
    private Map<Integer, Map<Integer, DBWar>> warsByNationId;
    private final Int2ObjectOpenHashMap<Object> attacksByWar = new Int2ObjectOpenHashMap<>();
    private ObjectArrayList<DBAttack> allAttacks2 = new ObjectArrayList<>();
    public WarDB() throws SQLException {
        super(Settings.INSTANCE.DATABASE, "war");
        warsById = new Int2ObjectOpenHashMap<>();
        warsByAllianceId = new Int2ObjectOpenHashMap<>();
        warsByNationId = new Int2ObjectOpenHashMap<>();
    }

    public static void main(String[] args) throws SQLException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        WarDB warDb = new WarDB();

        Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS = -1;

        warDb.testLoadAttacks2();

        System.exit(0);

//        warDb.loadWars();
//        warDb.testLoadAttacks();
    }

    public void testLoadAttacks2() {
        String whereClause;
        if (Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS == 0) {
            return ;
        } else if (Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS >= 0) {
            long date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS);
            whereClause = " WHERE date > " + date;
        } else {
            whereClause = "";
        }

        AttackCursorFactory cursorManager = new AttackCursorFactory();

        try (PreparedStatement stmt= getConnection().prepareStatement("select * FROM `attacks2`" + whereClause + " ORDER BY `war_attack_id` ASC", ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
            stmt.setFetchSize(2 << 16);
            try (ResultSet rs = stmt.executeQuery()) {
                DBAttack legacy = createAttack(rs);
                AbstractCursor cursor = cursorManager.load(legacy, true);

                // check values match

                // serialize then deserialize to check matches

                // test api v3 war attack matches
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void testLoadAttacks() {
        String whereClause;
        if (Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS == 0) {
            return ;
        } else if (Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS >= 0) {
            long date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS);
            whereClause = " WHERE date > " + date;
        } else {
            whereClause = "";
        }
        System.out.println("Start loading");
        try (PreparedStatement stmt= getConnection().prepareStatement("select * FROM `attacks2`" + whereClause + " ORDER BY `war_attack_id` ASC", ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
            stmt.setFetchSize(2 << 16);
            try (ResultSet rs = stmt.executeQuery()) {
                AbstractAttack[] attackBuf = new AbstractAttack[1];
                int[] warBuf = new int[1];
                long start  = System.currentTimeMillis();
                long[] timePerType = new long[AttackType.values.length];
                long[] numTypes = new long[AttackType.values.length];

                ObjectArrayList<AbstractAttack> all = new ObjectArrayList<>();
//                ObjectArrayList<DBAttack> all2 = new ObjectArrayList<>();

                int check1 = 0;
                int check2 = 0;
                while (rs.next()) {
//                    long start2 = System.nanoTime();
                    AbstractAttack.createSingle(rs, f -> warBuf[0] = f, f -> attackBuf[0] = f);
//                    long diff = System.nanoTime() - start2;
//                    timePerType[attackBuf[0].getAttack_type().ordinal()] += diff;
//                    numTypes[attackBuf[0].getAttack_type().ordinal()]++;
//
                    AbstractAttack attack = attackBuf[0];
                    all.add(attack);
                    int war_id = warBuf[0];

                    DBAttack legacy = createAttack(rs);
                    if (legacy.getDate() < 0) continue;
                    // validate attacks are the same
                    if (war_id != legacy.getWar_id()) {
                        throw new IllegalStateException("War id mismatch " + war_id + " != " + legacy.getWar_id());
                    }

                    if (attack.getAttack_type() != legacy.getAttack_type()) {
                        throw new IllegalStateException("Attack type mismatch " + attack.getAttack_type() + " != " + legacy.getAttack_type());
                    }
                    if (attack.getDate() != legacy.getDate()) {
                        throw new IllegalStateException("Attack date mismatch " + attack.getDate() + " != " + legacy.getDate());
                    }
                    boolean greater = legacy.getAttacker_nation_id() > legacy.getDefender_nation_id();
                    if (greater != attack.isAttackerIdGreater()) {
                        throw new IllegalStateException("Attack greater id mismatch " + greater + " != " + attack.isAttackerIdGreater());
                    }

                    // type
                    if (attack.getAttack_type() != legacy.getAttack_type()) {
                        throw new IllegalStateException("Attack type mismatch " + attack.getAttack_type() + " != " + legacy.getAttack_type());
                    }

                    check1++;

                    DBWar war = warsById.get(war_id);
                    if (war == null) continue;

                    check2++;
                    WarAttackWrapper wrapper = new WarAttackWrapper(war, attack);
                    // attacker
                    // defender
                    if (wrapper.getAttackerId() != legacy.getAttacker_nation_id()) {
                        throw new IllegalStateException("Attack attacker mismatch " + wrapper.getAttackerId() + " != " + legacy.getAttacker_nation_id());
                    }
                    if (wrapper.getDefenderId() != legacy.getDefender_nation_id()) {
                        throw new IllegalStateException("Attack defender mismatch " + wrapper.getDefenderId() + " != " + legacy.getDefender_nation_id());
                    }
                    if (wrapper.getAttackerAA() != (legacy.getAttacker_nation_id() == war.attacker_id ? war.attacker_aa : war.defender_aa)) {
                        throw new IllegalStateException("Attack attacker aa mismatch " + check2 + " | " + wrapper.getAttackerAA() + " != " + war.attacker_aa);
                    }
                    if (wrapper.getDefenderAA() != (legacy.getAttacker_nation_id() == war.attacker_id ? war.defender_aa : war.attacker_aa)) {
                        throw new IllegalStateException("Attack defender aa mismatch " + wrapper.getDefenderAA() + " != " + war.defender_aa);
                    }
                    if (legacy.getDefcas1() < 0) continue;
                    if (wrapper.getImprovements_destroyed() != legacy.getImprovements_destroyed()) {
                        throw new IllegalStateException("Attack improvements destroyed mismatch " + wrapper.getImprovements_destroyed() + " != " + legacy.getImprovements_destroyed());
                    }

                    switch (attack.getAttack_type()) {
                        case VICTORY -> {
                            double[] lootAmt = legacy.loot;
                            if (lootAmt != null && !ResourceType.isZero(lootAmt)) {
                                if (!Arrays.equals(lootAmt, wrapper.getLoot())) {
                                    throw new IllegalStateException("Attack loot mismatch " + Arrays.toString(lootAmt) + " != " + Arrays.toString(wrapper.getLoot()));
                                }
                                if (Math.abs(wrapper.getLootPercent() - legacy.getLootPercent()) > 0.01) {
                                    throw new IllegalStateException("Attack loot percent mismatch " + wrapper.getLootPercent() + " != " + legacy.getLootPercent());
                                }
                            }
                        }
                        case FORTIFY -> {

                        }
                        case A_LOOT -> {
//                            double[] rss1 = attack.getLoot();
                        }
                        case AIRSTRIKE_AIRCRAFT,AIRSTRIKE_SHIP,AIRSTRIKE_MONEY,AIRSTRIKE_TANK,AIRSTRIKE_SOLDIER,AIRSTRIKE_INFRA,NAVAL,GROUND -> {
                            if (attack.getAttack_type() == AttackType.GROUND) {
                                if (Math.abs(attack.getMoney_looted() - legacy.getMoney_looted()) > 1 && legacy.getMoney_looted() >= 0) {
                                    throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getWar_attack_id() + " | " + attack.getClass().getSimpleName() + " | " + check2 + " | Attack money mismatch " + attack.getMoney_looted() + " != " + legacy.getMoney_looted());
                                }
                            }
                            if (attack.getSuccess().ordinal() != legacy.getSuccess()) {
                                throw new IllegalStateException(attack.getAttack_type() + " | " + attack.getWar_attack_id() + " | " + attack.getClass().getSimpleName() + " | " + check2 + " Attack success mismatch " + attack.getSuccess() + " != " + legacy.getSuccess());
                            }

                            double infraDamage1 = attack.getInfra_destroyed();
                            if (Math.abs(infraDamage1 - legacy.getInfra_destroyed()) > 0.02) {
                                System.out.println(attack.getCity_infra_before() + " | " + legacy.getCity_infra_before() + " -> " + attack.getInfra_destroyed() + " | " + legacy.getInfra_destroyed());
                                System.out.println(legacy.getAttcas1() + " | " + legacy.getDefcas1());
                                throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getWar_attack_id() + " | " + attack.getClass().getSimpleName() + " | " + check2 + " | Attack infra damage mismatch " + infraDamage1 + " != " + legacy.getInfra_destroyed());
                            }
                            if (legacy.getInfra_destroyed() > 0 && Math.abs(attack.getCity_infra_before() - legacy.getCity_infra_before()) > 0.02) {
                                System.out.println(attack.getCity_infra_before() + " | " + legacy.getCity_infra_before() + " -> " + attack.getInfra_destroyed() + " | " + legacy.getInfra_destroyed());
                                System.out.println(legacy.getMoney_looted() + " | " + legacy.getMoney_looted());
                                throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getWar_attack_id() + " | " + attack.getClass().getSimpleName() + " | " + check2 + " | Attack infra before mismatch " + attack.getCity_infra_before() + " != " + legacy.getCity_infra_before());
                            }
                            long gas1 = Math.round(attack.getAtt_gas_used() * 100);
                            long muni1 = Math.round(attack.getAtt_mun_used() * 100);
                            long gas2 = Math.round(legacy.getAtt_gas_used() * 100);
                            long muni2 = Math.round(legacy.getAtt_mun_used() * 100);
                            if (Math.abs(gas1 - gas2) > 2) {
                                throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getClass().getSimpleName() + " | " + check2 + " | " + attack.getWar_attack_id() + " | Attack gas used mismatch " + gas1 + " != " + gas2);
                            }
                            if (Math.abs(muni1 - muni2) > 2) {
                                throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getClass().getSimpleName() + " | " + check2 + " | " + attack.getWar_attack_id() + " | Attack muni used mismatch " + muni1 + " != " + muni2);
                            }
                            if (attack.getAttcas1() != legacy.getAttcas1()) {
                                throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getClass().getSimpleName() + " | " + check2 + " | " + attack.getWar_attack_id() + " | Attack attacker casualties 1 mismatch " + attack.getAttcas1() + " != " + legacy.getAttcas1());
                            }
                            if (attack.getAttcas2() != legacy.getAttcas2()) {
                                throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getClass().getSimpleName() + " | " + check2 + " | " + attack.getWar_attack_id() + " | Attack attacker casualties 2 mismatch " + attack.getAttcas2() + " != " + legacy.getAttcas2());
                            }
                            if (attack.getDefcas1() != legacy.getDefcas1() && legacy.getDefcas1() > 0) {
                                throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getClass().getSimpleName() + " | " + check2 + " | " + attack.getWar_attack_id() + " | Attack defender casualties 1 mismatch " + attack.getDefcas1() + " != " + legacy.getDefcas1());
                            }
                            if (attack.getDefcas2() != legacy.getDefcas2()) {
                                throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getClass().getSimpleName() + " | " + check2 + " | " + attack.getWar_attack_id() + " | Attack defender casualties 2 mismatch " + attack.getDefcas2() + " != " + legacy.getDefcas2());
                            }
                            if (attack.getDefcas3() != legacy.getDefcas3()) {
                                throw new IllegalStateException(attack.getAttack_type() + ": " + attack.getClass().getSimpleName() + " | " + check2 + " | " + attack.getWar_attack_id() + " | Attack defender casualties 3 mismatch " + attack.getDefcas3() + " != " + legacy.getDefcas3());
                            }
                        }
                        case PEACE -> {
                        }

                        case MISSILE -> {
                            if (attack.getSuccess().ordinal() != legacy.getSuccess()) {
                                throw new IllegalStateException(attack.getAttack_type() + " | " + attack.getWar_attack_id() + " Attack success mismatch " + attack.getSuccess() + " != " + legacy.getSuccess());
                            }
                        }
                        case NUKE -> {
                            if (attack.getSuccess().ordinal() != legacy.getSuccess()) {
                                throw new IllegalStateException(attack.getAttack_type() + " | " + attack.getWar_attack_id() + " Attack success mismatch " + attack.getSuccess() + " != " + legacy.getSuccess());
                            }
                        }
                    }
                }
                long diff = System.currentTimeMillis() - start;
                System.out.println("Loaded " + allAttacks2.size() + " attacks in " + diff + "ms");
                System.out.println("Check " + check1 + " | " + check2);
                System.gc();
                System.gc();
                System.gc();
                System.gc();
                long memUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                System.out.println("Memory usage: " + MathMan.format(memUsage / 1024d / 1024d) + "MB");
                System.out.println(all.hashCode() + " | " + all.size() + " | " + all.get(ThreadLocalRandom.current().nextInt(5)).getWar_attack_id());
//                System.out.println(all.hashCode());
//                System.out.println(all2.hashCode());
                // Memory usage: 13.22MB
                // Memory usage: 552.15MB
                // Memory usage: 552.29MB
                // Memory usage: 2,136.96MB

                // print time per type
                // print total per type
                for (AttackType type : AttackType.values) {
                    System.out.println(type + " " + MathMan.format(timePerType[type.ordinal()]) + "ns | " + (MathMan.format(timePerType[type.ordinal()] / (double) numTypes[type.ordinal()])));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setWar(DBWar war) {
        synchronized (warsByAllianceId) {
            if (war.attacker_aa != 0)
                warsByAllianceId.computeIfAbsent(war.attacker_aa, f -> new Int2ObjectOpenHashMap<>()).put(war.warId, war);
            if (war.defender_aa != 0)
                warsByAllianceId.computeIfAbsent(war.defender_aa, f -> new Int2ObjectOpenHashMap<>()).put(war.warId, war);
        }
        synchronized (warsByNationId) {
            warsByNationId.computeIfAbsent(war.attacker_id, f -> new Int2ObjectOpenHashMap<>()).put(war.warId, war);
            warsByNationId.computeIfAbsent(war.defender_id, f -> new Int2ObjectOpenHashMap<>()).put(war.warId, war);
        }
        synchronized (warsById) {
            warsById.put(war.warId, war);
        }
    }

    public void load() {
        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6);
        Locutus.imp().getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                if (Settings.INSTANCE.TASKS.UNLOAD_WARS_AFTER_DAYS > 6 || Settings.INSTANCE.TASKS.UNLOAD_WARS_AFTER_DAYS <= 0) {
                    loadWars(Settings.INSTANCE.TASKS.UNLOAD_WARS_AFTER_DAYS);
                }
                if (Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS > 6 || Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS <= 0) {
                    loadAttacks(Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS);
                }
            }
        });
        System.out.println("Loaded " + allAttacks2.size() + " attacks in " + (System.currentTimeMillis() - start) + "ms");
    }

    public void loadWars(int days) {
        if (days != 0) {
            long date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
            String whereClause = days > 0 ? " WHERE date > " + date : "";
            query("SELECT * FROM wars" + whereClause, f -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    DBWar war = create(rs);
                    setWar(war);
                }
            });
        }

        List<DBWar> wars = getWarByStatus(WarStatus.ACTIVE, WarStatus.ATTACKER_OFFERED_PEACE, WarStatus.DEFENDER_OFFERED_PEACE);

        long currentTurn = TimeUtil.getTurn();
        for (DBWar war : wars) {
            long warTurn = TimeUtil.getTurn(war.date);
            if (currentTurn - warTurn < 60) {
                activeWars.addActiveWar(war);
            }
        }
    }

    public Map<Integer, DBWar> getWarsForNationOrAlliance(Predicate<Integer> nations, Predicate<Integer> alliances, Predicate<DBWar> warFilter) {
        Map<Integer, DBWar> result = new Int2ObjectOpenHashMap<>();
        if (alliances != null) {
            synchronized (warsByAllianceId) {
                for (Map.Entry<Integer, Map<Integer, DBWar>> entry : warsByAllianceId.entrySet()) {
                    if (alliances.test(entry.getKey())) {
                        if (warFilter != null) {
                            for (Map.Entry<Integer, DBWar> warEntry : entry.getValue().entrySet()) {
                                if (warFilter.test(warEntry.getValue())) {
                                    result.put(warEntry.getKey(), warEntry.getValue());
                                }
                            }
                        } else {
                            result.putAll(entry.getValue());
                        }
                    }
                }
            }
        }
        if (nations != null) {
            synchronized (warsByNationId) {
                for (Map.Entry<Integer, Map<Integer, DBWar>> entry : warsByNationId.entrySet()) {
                    if (nations.test(entry.getKey())) {
                        if (warFilter != null) {
                            for (Map.Entry<Integer, DBWar> warEntry : entry.getValue().entrySet()) {
                                if (warFilter.test(warEntry.getValue())) {
                                    result.put(warEntry.getKey(), warEntry.getValue());
                                }
                            }
                        } else {
                            result.putAll(entry.getValue());
                        }
                    }
                }
            }
        }
        else if (alliances == null) {
            synchronized (warsById) {
                if (warFilter == null) {

                    result.putAll(warsById);
                } else {
                    for (Map.Entry<Integer, DBWar> warEntry : warsById.entrySet()) {
                        if (warFilter.test(warEntry.getValue())) {
                            result.put(warEntry.getKey(), warEntry.getValue());
                        }
                    }
                }
            }
        }
        return result;
    }

    public Map<Integer, DBWar> getWars(Predicate<DBWar> filter) {
        return getWarsForNationOrAlliance(null, null, filter);
    }

    public void loadNukeDates() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(12);

        List<Integer> attackIds = new ArrayList<>();
        iterateAttacks(cutoff, attack -> {
            if (attack.getAttack_type() == AttackType.NUKE && attack.getSuccess() != 0) {
                attackIds.add(attack.getWar_attack_id());
            }
        });
        if (attackIds.isEmpty()) return;//no nule data?s

        for (int i = 0; i < attackIds.size(); i += 500) {
            List<Integer> subList = attackIds.subList(i, Math.min(i + 500, attackIds.size()));
            for (WarAttack attack : Locutus.imp().getV3().fetchAttacks(f -> f.setId(subList), new Consumer<WarAttackResponseProjection>() {
                @Override
                public void accept(WarAttackResponseProjection proj) {
                    proj.def_id();
                    proj.city_id();
                    proj.date();
                }
            })) {
                int nationId = attack.getDef_id();
                int cityId = attack.getCity_id();
                long date = attack.getDate().toEpochMilli();
                Locutus.imp().getNationDB().setCityNukeFromAttack(nationId, cityId, date, null);
            }
        }
    }

    @Override
    public void createTables() {
        {
            TablePreset.create("BOUNTIES_V3")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("nation_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("posted_by", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("attack_type", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("amount", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());

            String subCatQuery = TablePreset.create("ATTACK_SUBCATEGORY_CACHE")
                    .putColumn("attack_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("subcategory_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("war_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .buildQuery(getDb().getType());
            subCatQuery = subCatQuery.replace(");", ", PRIMARY KEY(attack_id, subcategory_id));");
            getDb().executeUpdate(subCatQuery);
        }

        {
            String create = "CREATE TABLE IF NOT EXISTS `WARS` (`id` INT NOT NULL PRIMARY KEY, `attacker_id` INT NOT NULL, `defender_id` INT NOT NULL, `attacker_aa` INT NOT NULL, `defender_aa` INT NOT NULL, `war_type` INT NOT NULL, `status` INT NOT NULL, `date` BIGINT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        {
            String create = "CREATE TABLE IF NOT EXISTS `BLOCKADED` (`blockader`, `blockaded`, PRIMARY KEY(`blockader`, `blockaded`))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        executeStmt("CREATE INDEX IF NOT EXISTS index_WARS_date ON WARS (date);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_WARS_attacker ON WARS (attacker_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_WARS_defender ON WARS (defender_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_WARS_status ON WARS (status);");

        {
            String create = "CREATE TABLE IF NOT EXISTS `COUNTER_STATS` (`id` INT NOT NULL PRIMARY KEY, `type` INT NOT NULL, `active` INT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        {
            String nations = "CREATE TABLE IF NOT EXISTS `attacks2` (" +
                    "`war_attack_id` INT NOT NULL PRIMARY KEY, " +
                    "`date` BIGINT NOT NULL, " +
                    "war_id INT NOT NULL, " +
                    "attacker_nation_id INT NOT NULL, " +
                    "defender_nation_id INT NOT NULL, " +
                    "attack_type INT NOT NULL, " +
                    "victor INT NOT NULL, " +
                    "success INT NOT NULL," +
                    "attcas1 INT NOT NULL," +
                    "attcas2 INT NOT NULL," +
                    "defcas1 INT NOT NULL," +
                    "defcas2 INT NOT NULL," +
                    "defcas3 INT NOT NULL," +
                    "city_id INT NOT NULL," + // Not used anymore
                    "infra_destroyed INT," +
                    "improvements_destroyed INT," +
                    "money_looted BIGINT," +
                    "looted INT," +
                    "loot BLOB," +
                    "pct_looted INT," +
                    "city_infra_before INT," +
                    "infra_destroyed_value INT," +
                    "att_gas_used INT," +
                    "att_mun_used INT," +
                    "def_gas_used INT," +
                    "def_mun_used INT" +
                    ")";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        executeStmt("CREATE INDEX IF NOT EXISTS index_attack_warid ON attacks2 (war_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_attack_attacker_nation_id ON attacks2 (attacker_nation_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_attack_defender_nation_id ON attacks2 (defender_nation_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_attack_date ON attacks2 (date);");

        // create custom bounties table
        {
            String create = "CREATE TABLE IF NOT EXISTS `CUSTOM_BOUNTIES` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`placed_by` INT NOT NULL, " +
                    "`date_created` BIGINT NOT NULL, " +
                    "`claimed_by` BIGINT NOT NULL, " +
                    "`amount` BLOB NOT NULL, " +
                    "`nations` BLOB NOT NULL, " +
                    "`alliances` BLOB NOT NULL, " +
                    "`filter` VARCHAR NOT NULL, " +
                    "`total_damage` BIGINT NOT NULL, " +
                    "`infra_damage` BIGINT NOT NULL, " +
                    "`unit_damage` BIGINT NOT NULL, " +
                    "`only_offensives` INT NOT NULL, " +
                    "`unit_kills` BLOB NOT NULL, " +
                    "`unit_attacks` BLOB NOT NULL, " +
                    "`allowed_war_types` BIGINT NOT NULL, " +
                    "`allowed_war_status` BIGINT NOT NULL, " +
                    "`allowed_attack_types` BIGINT NOT NULL, " +
                    "`allowed_attack_rolls` BIGINT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

    }

    public Set<DBWar> getActiveWars() {
        return activeWars.getActiveWars();
    }

    public List<DBWar> getActiveWars(int nationId) {
        return activeWars.getActiveWars(nationId);
    }

    public Map<Integer, DBWar> getActiveWars(Predicate<Integer> nationId, Predicate<DBWar> warPredicate) {
        return activeWars.getActiveWars(nationId, warPredicate);
    }

    public void addCustomBounty(CustomBounty bounty) {
        String query = "INSERT OR IGNORE INTO `CUSTOM_BOUNTIES`(" +
                "`placed_by`, " +
                "`date_created`, " +
                "`claimed_by`, " +
                "`amount`, " +
                "`nations`, " +
                "`alliances`, " +
                "`filter`, " +
                "`total_damage`, " +
                "`infra_damage`, " +
                "`unit_damage`, " +
                "`only_offensives`, " +
                "`unit_kills`, " +
                "`unit_attacks`, " +
                "`allowed_war_types`, " +
                "`allowed_war_status`, " +
                "`allowed_attack_types`, " +
                "`allowed_attack_rolls`) " +
                " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";


        ThrowingBiConsumer<CustomBounty, PreparedStatement> setStmt = (bounty1, stmt) -> {
            stmt.setInt(1, bounty1.placedBy);
            stmt.setLong(2, bounty1.date);
            stmt.setLong(3, bounty1.claimedBy);

            stmt.setBytes(4, ArrayUtil.toByteArray(bounty1.resources));
            stmt.setBytes(5, ArrayUtil.writeIntSet(bounty1.nations));
            stmt.setBytes(6, ArrayUtil.writeIntSet(bounty1.alliances));

            stmt.setString(7, bounty1.filter2.toString());
            stmt.setLong(8, (long) bounty1.totalDamage);
            stmt.setLong(9, (long) bounty1.infraDamage);
            stmt.setLong(10, (long) bounty1.unitDamage);
            stmt.setInt(11, bounty1.onlyOffensives ? 1 : 0);

            stmt.setBytes(12, ArrayUtil.writeEnumMap(bounty1.unitKills));
            stmt.setBytes(13, ArrayUtil.writeEnumMap(bounty1.unitAttacks));
            stmt.setLong(14, ArrayUtil.writeEnumSet(bounty1.allowedWarTypes));
            stmt.setLong(15, ArrayUtil.writeEnumSet(bounty1.allowedWarStatus));
            stmt.setLong(16, ArrayUtil.writeEnumSet(bounty1.allowedAttackTypes));
            stmt.setLong(17, ArrayUtil.writeEnumSet(bounty1.allowedAttackRolls));
        };

        // insert and get generated id
        try (PreparedStatement stmt = getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            setStmt.accept(bounty, stmt);

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    bounty.id = rs.getInt(1);
                } else {
                    throw new SQLException("Creating offer failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateBounty(CustomBounty bounty) {
        String query = "UPDATE `CUSTOM_BOUNTIES`(" +
                "SET `placed_by` = ?, " +
                "`date_created` = ?, " +
                "`claimed_by` = ?, " +
                "`amount` = ?, " +
                "`nations` = ?, " +
                "`alliances` = ?, " +
                "`filter` = ?, " +
                "`total_damage` = ?, " +
                "`infra_damage` = ?, " +
                "`unit_damage` = ?, " +
                "`only_offensives` = ?, " +
                "`unit_kills` = ?, " +
                "`unit_attacks` = ?, " +
                "`allowed_war_types` = ?, " +
                "`allowed_war_status` = ?, " +
                "`allowed_attack_types` = ?, " +
                "`allowed_attack_rolls` = ? " +
                "WHERE `id` = ?";
        ThrowingBiConsumer<CustomBounty, PreparedStatement> setStmt = (bounty1, stmt) -> {
            stmt.setInt(1, bounty1.placedBy);
            stmt.setLong(2, bounty1.date);
            stmt.setLong(3, bounty1.claimedBy);

            stmt.setBytes(4, ArrayUtil.toByteArray(bounty1.resources));
            stmt.setBytes(5, ArrayUtil.writeIntSet(bounty1.nations));
            stmt.setBytes(6, ArrayUtil.writeIntSet(bounty1.alliances));

            stmt.setString(7, bounty1.filter2.toString());
            stmt.setLong(8, (long) bounty1.totalDamage);
            stmt.setLong(9, (long) bounty1.infraDamage);
            stmt.setLong(10, (long) bounty1.unitDamage);
            stmt.setInt(11, bounty1.onlyOffensives ? 1 : 0);

            stmt.setBytes(12, ArrayUtil.writeEnumMap(bounty1.unitKills));
            stmt.setBytes(13, ArrayUtil.writeEnumMap(bounty1.unitAttacks));
            stmt.setLong(14, ArrayUtil.writeEnumSet(bounty1.allowedWarTypes));
            stmt.setLong(15, ArrayUtil.writeEnumSet(bounty1.allowedWarStatus));
            stmt.setLong(16, ArrayUtil.writeEnumSet(bounty1.allowedAttackTypes));
            stmt.setLong(17, ArrayUtil.writeEnumSet(bounty1.allowedAttackRolls));
            stmt.setInt(18, bounty1.id);
        };
        update(query, stmt -> setStmt.accept(bounty, stmt));
    }

    public List<CustomBounty> getCustomBounties() {
        List<CustomBounty> list = new ArrayList<>();
        String query = "SELECT * `CUSTOM_BOUNTIES`";
        query(query, f -> {}, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws SQLException, IOException {
                CustomBounty bounty = new CustomBounty();

                bounty.id = rs.getInt("id");
                bounty.placedBy = rs.getInt("placed_by");
                bounty.date = rs.getLong("date_created");
                bounty.claimedBy = rs.getLong("claimed_by");
                bounty.resources = ArrayUtil.toDoubleArray(rs.getBytes("amount"));
                bounty.nations = ArrayUtil.readIntSet(rs.getBytes("nations"));
                bounty.alliances = ArrayUtil.readIntSet(rs.getBytes("alliances"));
                bounty.filter2 = new NationFilterString(rs.getString("filter"), null);
                bounty.totalDamage = rs.getLong("total_damage");
                bounty.infraDamage = rs.getLong("infra_damage");
                bounty.unitDamage = rs.getLong("unit_damage");
                bounty.onlyOffensives = rs.getInt("only_offensives") == 1;
                bounty.unitKills = ArrayUtil.readEnumMap(rs.getBytes("unit_kills"), MilitaryUnit.class);
                bounty.unitAttacks = ArrayUtil.readEnumMap(rs.getBytes("unit_attacks"), MilitaryUnit.class);
                bounty.allowedWarTypes = ArrayUtil.readEnumSet(rs.getLong("allowed_war_types"), WarType.class);
                bounty.allowedWarStatus = ArrayUtil.readEnumSet(rs.getLong("allowed_war_status"), WarStatus.class);
                bounty.allowedAttackTypes = ArrayUtil.readEnumSet(rs.getLong("allowed_attack_types"), AttackType.class);
                bounty.allowedAttackRolls = ArrayUtil.readEnumSet(rs.getLong("allowed_attack_rolls"), SuccessType.class);
                list.add(bounty);
            }
        });
        return list;
    }

    public void addSubCategory(List<WarAttackSubcategoryEntry> entries) {
        if (entries.isEmpty()) return;
        String query = "INSERT OR IGNORE INTO `ATTACK_SUBCATEGORY_CACHE`(`attack_id`, `subcategory_id`, `war_id`) VALUES(?, ?, ?)";

        ThrowingBiConsumer<WarAttackSubcategoryEntry, PreparedStatement> setStmt = (entry, stmt) -> {
            stmt.setInt(1, entry.attack_id);
            stmt.setLong(2, entry.subcategory.ordinal());
            stmt.setInt(3, entry.war_id);
        };
        if (entries.size() == 1) {
            WarAttackSubcategoryEntry value = entries.iterator().next();
            update(query, stmt -> setStmt.accept(value, stmt));
        } else {
            executeBatch(entries, query, setStmt);
        }
    }

    public void deleteBlockaded(int blockaded) {
        update("DELETE FROM BLOCKADED WHERE blockaded = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, blockaded);
            }
        });
    }

    public void deleteBlockaded(int blockaded, int blockader) {
        if (blockadedMap != null) {
            synchronized (blockadeLock) {
                blockadedMap.getOrDefault(blockaded, Collections.emptySet()).remove(blockader);
                blockaderMap.getOrDefault(blockader, Collections.emptySet()).remove(blockaded);
            }
        }
        update("DELETE FROM BLOCKADED WHERE blockaded = ? AND blockader = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, blockaded);
                stmt.setInt(2, blockader);
            }
        });
    }

    public void addBlockaded(int blockaded, int blockader) {
        if (blockadedMap != null) {
            synchronized (blockadeLock) {
                blockadedMap.computeIfAbsent(blockaded, f -> new HashSet<>()).add(blockader);
                blockaderMap.computeIfAbsent(blockader, f -> new HashSet<>()).add(blockaded);
            }
        }
        update("INSERT OR REPLACE INTO `BLOCKADED`(`blockaded`, `blockader`) VALUES(?,?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, blockaded);
            stmt.setInt(2, blockader);
        });
    }

    private Object blockadeLock = new Object();
    private Map<Integer, Set<Integer>> blockadedMap = null;
    private Map<Integer, Set<Integer>> blockaderMap = null;

    public Map<Integer, Set<Integer>> getBlockadedByNation(boolean update) {
        updateBlockaded(update);
        return blockadedMap;
    }

    public Map<Integer, Set<Integer>> getBlockaderByNation(boolean update) {
        updateBlockaded(update);
        return blockaderMap;
    }

    public void updateBlockaded(boolean force) {
        if (!force && blockadedMap != null) {
            return;
        }
        Map<Integer, Set<Integer>> blockadedMap = new ConcurrentHashMap<>();
        Map<Integer, Set<Integer>> blockaderMap = new ConcurrentHashMap<>();
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM BLOCKADED")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int blockader = rs.getInt("blockader");
                    int blockaded = rs.getInt("blockaded");

                    blockadedMap.computeIfAbsent(blockaded, f -> new HashSet<>()).add(blockader);
                    blockaderMap.computeIfAbsent(blockader, f -> new HashSet<>()).add(blockaded);
                }
            }
            synchronized (blockadeLock) {
                this.blockadedMap = blockadedMap;
                this.blockaderMap = blockaderMap;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * active prob / inactive prob (0-1)
     * @param allianceId
     * @return
     */
    public Map.Entry<Double, Double> getAACounterStats(int allianceId) {
        List<Map.Entry<DBWar, CounterStat>> counters = Locutus.imp().getWarDb().getCounters(Collections.singleton(allianceId));
        if (counters.isEmpty()) {
            for (Map.Entry<Integer, Treaty> entry : Locutus.imp().getNationDB().getTreaties(allianceId).entrySet()) {
                Treaty treaty = entry.getValue();
                switch (treaty.getType()) {
                    case MDP:
                    case MDOAP:
                    case ODP:
                    case ODOAP:
                    case PROTECTORATE:
                        int other = treaty.getFromId() == allianceId ? treaty.getToId() : treaty.getFromId();
                        counters.addAll(Locutus.imp().getWarDb().getCounters(Collections.singleton(other)));
                }
            }
            if (counters.isEmpty()) return null;
        }

        int[] uncontested = new int[2];
        int[] countered = new int[2];
        int[] counter = new int[2];
        for (Map.Entry<DBWar, CounterStat> entry : counters) {
            CounterStat stat = entry.getValue();
            DBWar war = entry.getKey();
            switch (stat.type) {
                case ESCALATION:
                case IS_COUNTER:
                    countered[stat.isActive ? 1 : 0]++;
                    continue;
                case UNCONTESTED:
                    if (war.status == WarStatus.ATTACKER_VICTORY) {
                        uncontested[stat.isActive ? 1 : 0]++;
                    } else {
                        counter[stat.isActive ? 1 : 0]++;
                    }
                    break;
                case GETS_COUNTERED:
                    counter[stat.isActive ? 1 : 0]++;
                    break;
            }
        }

        int totalActive = counter[1] + uncontested[1];
        int totalInactive = counter[0] + uncontested[0];

        double chanceActive = ((double) counter[1] + 1) / (totalActive + 1);
        double chanceInactive = ((double) counter[0] + 1) / (totalInactive + 1);

        if (!Double.isFinite(chanceActive)) chanceActive = 0.5;
        if (!Double.isFinite(chanceInactive)) chanceInactive = 0.5;

        return new AbstractMap.SimpleEntry<>(chanceActive, chanceInactive);
    }

    public List<Map.Entry<DBWar, CounterStat>> getCounters(Collection<Integer> alliances) {
        Map<Integer, DBWar> wars = getWarsForNationOrAlliance(null, alliances::contains, f -> alliances.contains(f.defender_aa));
        String queryStr = "SELECT * FROM COUNTER_STATS WHERE id IN " + StringMan.getString(wars.values().stream().map(f -> f.warId).collect(Collectors.toList()));
        try (PreparedStatement stmt= getConnection().prepareStatement(queryStr)) {
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map.Entry<DBWar, CounterStat>> result = new ArrayList<>();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    CounterStat stat = new CounterStat();
                    stat.isActive = rs.getBoolean("active");
                    stat.type = CounterType.values[rs.getInt("type")];
                    DBWar war = getWar(id);
                    AbstractMap.SimpleEntry<DBWar, CounterStat> entry = new AbstractMap.SimpleEntry<>(war, stat);
                    result.add(entry);
                }
                return result;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CounterStat getCounterStat(DBWar war) {
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM COUNTER_STATS WHERE id = ?")) {
            stmt.setInt(1, war.warId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    CounterStat stat = new CounterStat();
                    stat.isActive = rs.getBoolean("active");
                    stat.type = CounterType.values[rs.getInt("type")];
                    return stat;
                }
            }
            return updateCounter(war);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public CounterStat updateCounter(DBWar war) {
        DBNation attacker = Locutus.imp().getNationDB().getNation(war.attacker_id);
        DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
        if (war.attacker_aa == 0 || war.defender_aa == 0) {
            CounterStat stat = new CounterStat();
            stat.type = CounterType.UNCONTESTED;
            stat.isActive = defender != null && defender.getActive_m() < 2880;
            return stat;
        }
        int warId = war.warId;
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacksByWar(war);

        long startDate = war.date;
        long startTurn = TimeUtil.getTurn(startDate);

        long endTurn = startTurn + 60 - 1;
        long endDate = TimeUtil.getTimeFromTurn(endTurn + 1);

        boolean isOngoing = war.status == WarStatus.ACTIVE || war.status == WarStatus.DEFENDER_OFFERED_PEACE || war.status == WarStatus.ATTACKER_OFFERED_PEACE;
        boolean isActive = war.status == WarStatus.DEFENDER_OFFERED_PEACE || war.status == WarStatus.DEFENDER_VICTORY || war.status == WarStatus.ATTACKER_OFFERED_PEACE;
        for (DBAttack attack : attacks) {
            if (attack.getAttack_type() == AttackType.VICTORY && attack.getAttacker_nation_id() == war.attacker_id) {
                war.status = WarStatus.ATTACKER_VICTORY;
            }
            if (attack.getAttacker_nation_id() == war.defender_id) isActive = true;
            switch (attack.getAttack_type()) {
                case A_LOOT:
                case VICTORY:
                case PEACE:
                    endTurn = TimeUtil.getTurn(attack.getDate());
                    endDate = attack.getDate();
                    break;
            }
        }

        Set<Integer> attAA = new HashSet<>(Collections.singleton(war.attacker_aa));
        for (Map.Entry<Integer, Treaty> entry : Locutus.imp().getNationDB().getTreaties(war.attacker_aa).entrySet()) {
            switch (entry.getValue().getType()) {
                case MDP:
                case MDOAP:
                case ODP:
                case ODOAP:
                case PROTECTORATE:
                    attAA.add(entry.getKey());
            }
        }

        Set<Integer> defAA = new HashSet<>(Collections.singleton(war.defender_aa));
        for (Map.Entry<Integer, Treaty> entry : Locutus.imp().getNationDB().getTreaties(war.defender_aa).entrySet()) {
            switch (entry.getValue().getType()) {
                case MDP:
                case MDOAP:
                case ODP:
                case ODOAP:
                case PROTECTORATE:
                    defAA.add(entry.getKey());
            }
        }

        Set<Integer> counters = new HashSet<>();
        Set<Integer> isCounter = new HashSet<>();

        Set<Integer> nationIds = new HashSet<>(Arrays.asList(war.attacker_id, war.defender_id));
        long finalEndDate = endDate;
        Collection<DBWar> possibleCounters = getWarsForNationOrAlliance(nationIds::contains, null,
                f -> f.date >= startDate - TimeUnit.DAYS.toMillis(5) && f.date <= finalEndDate).values();
        for (DBWar other : possibleCounters) {
            if (other.warId == war.warId) continue;
            if (attAA.contains(other.attacker_aa) || !(defAA.contains(other.attacker_aa))) continue;
            if (other.date < war.date) {
                if (other.attacker_id == war.defender_id && attAA.contains(other.defender_aa)) {
                    isCounter.add(other.warId);
                }
            } else if (other.defender_id == war.attacker_id) {
                counters.add(other.warId);
            }
        }

        boolean isEscalated = !counters.isEmpty() && !isCounter.isEmpty();

        CounterType type;
        if (isEscalated) {
            type = CounterType.ESCALATION;
        } else if (!counters.isEmpty()) {
            type = CounterType.GETS_COUNTERED;
        } else if (!isCounter.isEmpty()) {
            type = CounterType.IS_COUNTER;
        } else {
            type = CounterType.UNCONTESTED;
        }

        boolean finalIsActive = isActive;
        if (!isOngoing) {
            update("INSERT OR REPLACE INTO `COUNTER_STATS`(`id`, `type`, `active`) VALUES(?, ?, ?)", new ThrowingConsumer<PreparedStatement>() {
                @Override
                public void acceptThrows(PreparedStatement stmt) throws Exception {
                    stmt.setInt(1, warId);
                    stmt.setInt(2, type.ordinal());
                    stmt.setBoolean(3, finalIsActive);
                }
            });
        }

        CounterStat stat = new CounterStat();
        stat.type = type;
        stat.isActive = isActive;
        return stat;
    }

    public Set<DBBounty> getBounties(int nationId) {
        LinkedHashSet<DBBounty> result = new LinkedHashSet<>();

        query("SELECT * FROM `BOUNTIES_V3` WHERE nation_id = ? ORDER BY date DESC", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                DBBounty bounty = new DBBounty(rs);
                result.add(bounty);
            }
        });
        return result;
    }

    public Map<Integer, List<DBBounty>> getBountiesByNation() {
        return getBounties().stream().collect(Collectors.groupingBy(DBBounty::getId, Collectors.toList()));
    }

    public Set<DBBounty> getBounties() {
        LinkedHashSet<DBBounty> result = new LinkedHashSet<>();
        query("SELECT * FROM `BOUNTIES_V3` ORDER BY date DESC", (ThrowingConsumer<PreparedStatement>) stmt -> {},
                (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                DBBounty bounty = new DBBounty(rs);
                result.add(bounty);
            }
        });
        return result;
    }

    private Object bountyLock = new Object();

    public void updateBountiesV3() throws IOException {
        synchronized (bountyLock) {
            Set<DBBounty> removedBounties = getBounties();
            Set<DBBounty> newBounties = new LinkedHashSet<>();

            boolean callEvents = !removedBounties.isEmpty();

            PoliticsAndWarV3 v3 = Locutus.imp().getV3();
            Collection<Bounty> bounties = v3.fetchBounties(null, f -> f.all$(-1));

            if (bounties.isEmpty()) return;
            bounties = new HashSet<>(bounties); // Ensure uniqueness (in case of pagination concurrency issues)

            for (Bounty bounty : bounties) {
                WarType type = WarType.parse(bounty.getType().name());
                long date = bounty.getDate().toEpochMilli();
                int id = bounty.getId();
                int nationId = bounty.getNation_id();
                long amount = bounty.getAmount();

                int postedBy = 0;

                DBBounty dbBounty = new DBBounty(id, date, nationId, postedBy, type, amount);
                if (removedBounties.contains(dbBounty)) {
                    removedBounties.remove(dbBounty);
                    continue;
                } else {
                    newBounties.add(dbBounty);
                }
            }

            for (DBBounty bounty : removedBounties) {
                removeBounty(bounty);
                if (callEvents) new BountyRemoveEvent(bounty).post();
            }
            for (DBBounty bounty : newBounties) {
                addBounty(bounty);
                if (Settings.INSTANCE.LEGACY_SETTINGS.DEANONYMIZE_BOUNTIES) {
                    // TODO remove this
                }
                if (callEvents) new BountyCreateEvent(bounty).post();
            }
        }
    }

    public void addBounty(DBBounty bounty) {
        update("INSERT OR REPLACE INTO `BOUNTIES_V3`(`id`, `date`, `nation_id`, `posted_by`, `attack_type`, `amount`) VALUES(?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, bounty.getId());
            stmt.setLong(2, bounty.getDate());
            stmt.setLong(3, bounty.getNationId());
            stmt.setInt(4, bounty.getPostedBy());
            stmt.setInt(5, bounty.getType().ordinal());
            stmt.setLong(6, bounty.getAmount());
        });
    }

    public void removeBounty(DBBounty bounty) {
        update("DELETE FROM `BOUNTIES_V3` where `id` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, bounty.getId());
        });
    }

    public boolean updateAllWars(Consumer<Event> eventConsumer) {
        long start = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 61);
        return updateWarsSince(eventConsumer, start);
    }

    public boolean updateWarsSince(Consumer<Event> eventConsumer, long date) {
        Set<Integer> activeWarsToFetch = new LinkedHashSet<>(getWarsSince(date).keySet());
        PoliticsAndWarV3 api = Locutus.imp().getV3();
        List<War> wars = api.fetchWarsWithInfo(r -> {
            r.setAfter(new Date(date));
            r.setActive(false); // needs to be set otherwise inactive wars wont be fetched
        });

        if (wars.isEmpty()) {
            AlertUtil.error("Failed to fetch wars", new Exception());
            return false;
        }

        List<DBWar> dbWars = wars.stream().map(DBWar::new).collect(Collectors.toList());
        updateWars(dbWars, eventConsumer);
        int numActive = activeWarsToFetch.size();
        for (DBWar war : dbWars) {
            activeWarsToFetch.remove(war.getWarId());
        }

        if (activeWarsToFetch.size() > 0) {
            int notDeleted = 0;
            for (int warId : activeWarsToFetch) {
                DBWar war = activeWars.getWar(warId);
                if (war == null) {
                    // no issue
                    continue;
                }
                if (war.getNation(true) != null && war.getNation(false) != null) {
                    notDeleted++;
                }
                DBWar copy = new DBWar(war);
                war.status = WarStatus.EXPIRED;
                activeWars.makeWarInactive(war);
                saveWars(Collections.singleton(war));
                if (eventConsumer != null) {
                    eventConsumer.accept(new WarStatusChangeEvent(copy, war));
                }
            }
            if (notDeleted > 0) {
                AlertUtil.error("Unable to fetch " + notDeleted + "/" + numActive + " active wars:", new RuntimeException("Ignore if these wars correspond to deleted nations:\n" + StringMan.getString(activeWarsToFetch)));
            }
        }
        return true;
    }

    public boolean updateAllWarsV2(Consumer<Event> eventConsumer) throws IOException {
        List<SWarContainer> wars = Locutus.imp().getPnwApiV2().getWarsByAmount(5000).getWars();
        List<DBWar> dbWars = new ArrayList<>();
        int minId = Integer.MAX_VALUE;
        int maxId = 0;
        for (SWarContainer container : wars) {
            if (container == null) continue;
            DBWar war = new DBWar(container);
            dbWars.add(war);
            minId = Math.min(minId, war.warId);
            maxId = Math.max(maxId, war.warId);
        }

        if (dbWars.isEmpty()) {
            AlertUtil.error("Unable to fetch wars", new Exception());
            return false;
        }
        Set<Integer> fetchedWarIds = dbWars.stream().map(DBWar::getWarId).collect(Collectors.toSet());
        Map<Integer, DBWar> activeWarsById = activeWars.getActiveWarsById();

        // Find deleted wars
        for (int id = minId; id <= maxId; id++) {
            if (fetchedWarIds.contains(id)) continue;
            DBWar war = activeWarsById.get(id);
            if (war == null) continue;

            DBWar newWar = new DBWar(war);
            newWar.status = WarStatus.EXPIRED;
            dbWars.add(newWar);
        }

        boolean result = updateWars(dbWars, eventConsumer);
        return result;
    }

    public boolean updateActiveWars(Consumer<Event> eventConsumer, boolean useV2) throws IOException {
        if (activeWars.isEmpty()) {
            if (useV2) {
                return updateAllWarsV2(eventConsumer);
            } else {
                return updateAllWars(eventConsumer);
            }
        }
        return updateActiveWars2(eventConsumer);
    }

    public boolean updateActiveWars2(Consumer<Event> eventConsumer) throws IOException {
        int newWarsToFetch = 100;
        int numToUpdate = Math.min(999, PoliticsAndWarV3.WARS_PER_PAGE);

        List<DBWar> mostActiveWars = new ArrayList<>(activeWars.getActiveWars());
        if (mostActiveWars.isEmpty()) return false;

        int latestWarId = 0;

        Map<DBWar, Long> lastActive = new HashMap<>();
        for (DBWar war : mostActiveWars) {
            DBNation nat1 = war.getNation(true);
            DBNation nat2 = war.getNation(false);
            long date = Math.max(nat1 == null ? 0 : nat1.lastActiveMs(), nat2 == null ? 0 : nat2.lastActiveMs());
            lastActive.put(war, date);
        }
        mostActiveWars.sort((o1, o2) -> Long.compare(lastActive.get(o2), lastActive.get(o1)));

        List<Integer> warIdsToUpdate = new ArrayList<>(999);
        for (DBWar war : mostActiveWars) latestWarId = Math.max(latestWarId, war.warId);

        for (int i = latestWarId + 1; i <= latestWarId + newWarsToFetch; i++) {
            warIdsToUpdate.add(i);
        }

        Set<Integer> activeWarsToFetch = new HashSet<>();

        for (int i = 0; i < mostActiveWars.size(); i++) {
            int warId = mostActiveWars.get(i).getWarId();
            warIdsToUpdate.add(warId);
            activeWarsToFetch.add(warId);
            if (warIdsToUpdate.size() >= numToUpdate) break;
        }

        Collections.sort(warIdsToUpdate);

        PoliticsAndWarV3 api = Locutus.imp().getV3();
        List<War> wars = api.fetchWarsWithInfo(r -> {
            r.setId(warIdsToUpdate);
            r.setActive(false); // needs to be set otherwise inactive wars wont be fetched
        });

        if (wars.isEmpty()) {
            AlertUtil.error("Failed to fetch wars", new Exception());
            return false;
        }

        List<DBWar> dbWars = wars.stream().map(DBWar::new).collect(Collectors.toList());
        updateWars(dbWars, eventConsumer);
        int numActive = activeWarsToFetch.size();
        for (DBWar war : dbWars) {
            activeWarsToFetch.remove(war.getWarId());
        }

        if (activeWarsToFetch.size() > 0) {
            int notDeleted = 0;
            for (int warId : activeWarsToFetch) {
                DBWar war = activeWars.getWar(warId);
                if (war == null) {
                    // no issue
                    continue;
                }
                if (war.getNation(true) != null && war.getNation(false) != null) {
                    notDeleted++;
                }
                DBWar copy = new DBWar(war);
                war.status = WarStatus.EXPIRED;
                activeWars.makeWarInactive(war);
                saveWars(Collections.singleton(war));
                if (eventConsumer != null) {
                    eventConsumer.accept(new WarStatusChangeEvent(copy, war));
                }
            }
            if (notDeleted > 0) {
                AlertUtil.error("Unable to fetch " + notDeleted + "/" + numActive + " active wars:", new RuntimeException("Ignore if these wars correspond to deleted nations:\n" + StringMan.getString(activeWarsToFetch)));
            }
        }

        return true;
    }

    public boolean updateWars(List<DBWar> dbWars, Consumer<Event> eventConsumer) {
        List<DBWar> prevWars = new ArrayList<>();
        List<DBWar> newWars = new ArrayList<>();
        Set<Integer> newWarIds = new LinkedHashSet<>();

        for (DBWar war : dbWars) {
            DBWar existing = warsById.get(war.warId);

            if ((existing == null && !war.isActive()) || (existing != null && war.status == existing.status)) continue;

            prevWars.add(existing);
            newWars.add(war);
            newWarIds.add(war.getWarId());

            if (existing == null && war.date > System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15) && war.status == WarStatus.ACTIVE) {
                Locutus.imp().getNationDB().setNationActive(war.attacker_id, war.date, eventConsumer);
                DBNation attacker = war.getNation(true);
                if (attacker != null && attacker.isBeige()) {
                    DBNation copy = eventConsumer == null ? null : new DBNation(attacker);
                    attacker.setColor(NationColor.GRAY);
                    if (eventConsumer != null) eventConsumer.accept(new NationChangeColorEvent(copy, attacker));
                }
            }

            DBNation attacker = Locutus.imp().getNationDB().getNation(war.attacker_id);
            DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
            if (war.attacker_aa == 0 && attacker != null) {
                war.attacker_aa = attacker.getAlliance_id();
            }
            if (war.defender_aa == 0 && defender != null) {
                war.defender_aa = defender.getAlliance_id();
            }
        }

        long currentTurn = TimeUtil.getTurn();
        for (DBWar war : activeWars.getActiveWars()) {
            if (!newWarIds.add(war.getWarId())) continue;

            long warTurn = TimeUtil.getTurn(war.date);

            if (currentTurn - warTurn >= 60 && false) { // TODO disable
                prevWars.add(new DBWar(war));
                war.status = WarStatus.EXPIRED;
                newWars.add(war);

            } else if (war.status != WarStatus.EXPIRED){
                DBNation attacker = Locutus.imp().getNationDB().getNation(war.attacker_id);
                DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
                if ((attacker == null || defender == null) && war.date < System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)) {

                    prevWars.add(new DBWar(war));
                    war.status = WarStatus.EXPIRED;
                    newWars.add(war);
                }
            }
        }

        saveWars(newWars);

        List<Map.Entry<DBWar, DBWar>> warUpdatePreviousNow = new ArrayList<>();

        for (int i = 0 ; i < prevWars.size(); i++) {
            DBWar previous = prevWars.get(i);
            DBWar newWar = newWars.get(i);
            if (newWar.isActive()) {
                activeWars.addActiveWar(newWar);
            } else {
                if (previous != null && previous.isActive() && (newWar.status == WarStatus.DEFENDER_VICTORY || newWar.status == WarStatus.ATTACKER_VICTORY)) {
                    boolean isAttacker = newWar.status == WarStatus.ATTACKER_VICTORY;
                    DBNation defender = newWar.getNation(!isAttacker);
                    if (defender.getColor() != NationColor.BEIGE) {
                        DBNation copyOriginal = new DBNation(defender);
                        defender.setColor(NationColor.BEIGE);
                        defender.setBeigeTimer(TimeUtil.getTurn() + 24);
                        if (eventConsumer != null)
                            eventConsumer.accept(new NationChangeColorEvent(copyOriginal, defender));
                    }
                }
                activeWars.makeWarInactive(newWar);
            }

            warUpdatePreviousNow.add(new AbstractMap.SimpleEntry<>(previous, newWar));
        }

        if (!warUpdatePreviousNow.isEmpty() && eventConsumer != null) {
            try {
                WarUpdateProcessor.processWars(warUpdatePreviousNow, eventConsumer);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    public void saveWars(Collection<DBWar> values) {
        if (values.isEmpty()) return;
        for (DBWar war : values) {
            setWar(war);
        }
        List<Map.Entry<Integer, DBNation>> nationSnapshots = new ArrayList<>();
        for (DBWar war : values) {
            DBNation attacker = war.getNation(true);
            DBNation defender = war.getNation(false);
            if (attacker != null) {
                nationSnapshots.add(Map.entry(war.getWarId(), attacker));
            }
            if (defender != null) {
                nationSnapshots.add(Map.entry(war.getWarId(), defender));
            }
        }

        System.out.println("Done save war snaptshots");

        String query = "INSERT OR REPLACE INTO `wars`(`id`, `attacker_id`, `defender_id`, `attacker_aa`, `defender_aa`, `war_type`, `status`, `date`) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";

        ThrowingBiConsumer<DBWar, PreparedStatement> setStmt = (war, stmt) -> {
            stmt.setInt(1, war.warId);
            stmt.setLong(2, war.attacker_id);
            stmt.setInt(3, war.defender_id);
            stmt.setInt(4, war.attacker_aa);
            stmt.setInt(5, war.defender_aa);
            stmt.setInt(6, war.warType.ordinal());
            stmt.setInt(7, war.status.ordinal());
            stmt.setLong(8, war.date);
        };
        if (values.size() == 1) {
            DBWar value = values.iterator().next();
            update(query, stmt -> setStmt.accept(value, stmt));
        } else {
            executeBatch(values, query, setStmt);
        }
        System.out.println("Done save war");
    }

    public Map<Integer, DBWar> getWars(WarStatus status) {
        return getWars(f -> f.status == status);
    }

    public Map<Integer, DBWar> getWarsSince(long date) {
        return getWars(f -> f.date > date);
    }

    public Map<Integer, DBWar> getWars() {
        synchronized (warsById) {
            return new Int2ObjectOpenHashMap<>(warsById);
        }
    }

    public Map<Integer, List<DBWar>> getActiveWarsByAttacker(Set<Integer> attackers, Set<Integer> defenders, WarStatus... statuses) {
        Set<Integer> all = new HashSet<>();

        Map<Integer, List<DBWar>> map = new Int2ObjectOpenHashMap<>();
        activeWars.getActiveWars(f -> all.contains(f), new Predicate<DBWar>() {
            @Override
            public boolean test(DBWar war) {
                if (attackers.contains(war.attacker_id) || defenders.contains(war.defender_id)) {
                    List<DBWar> list = map.computeIfAbsent(war.attacker_id, k -> new ArrayList<>());
                    list.add(war);
                }
                return false;
            }
        });
        return map;
    }

    private DBWar create(ResultSet rs) throws SQLException {
        int warId = rs.getInt("id");
        //  `attacker_id`, `defender_id`, `attacker_aa`, `defender_aa`, `war_type`, `status`, `date`
        int attacker_id = rs.getInt("attacker_id");
        int defender_id = rs.getInt("defender_id");
        int attacker_aa = rs.getInt("attacker_aa");
        int defender_aa = rs.getInt("defender_aa");
        WarType war_type = WarType.values[rs.getInt("war_type")];
        WarStatus status = WarStatus.values[rs.getInt("status")];
        long date = rs.getLong("date");

        return new DBWar(warId, attacker_id, defender_id, attacker_aa, defender_aa, war_type, status, date);
    }

    public DBWar getWar(int warId) {
        return warsById.get(warId);
    }

    public List<DBWar> getWars(int nation1, int nation2, long start, long end) {
        List<DBWar> list = new ArrayList<>();
        synchronized (warsByNationId) {
            Map<Integer, DBWar> wars = warsByNationId.get(nation1);
            if (wars != null) {
                for (DBWar war : wars.values()) {
                    if ((war.defender_id == nation2 || war.attacker_id == nation1) && war.date > start && war.date < end) {
                        list.add(war);
                    }
                }
            }
        }
        return list;
    }

    public DBWar getActiveWarByNation(int attacker, int defender) {
        for (DBWar war : activeWars.getActiveWars(attacker)) {
            if (war.attacker_id == attacker && war.defender_id == defender) {
                return war;
            }
        }
        return null;
    }

    public List<DBWar> getWarsByNation(int nation, WarStatus status) {
        if (status == WarStatus.ACTIVE || status == WarStatus.ATTACKER_OFFERED_PEACE || status == WarStatus.DEFENDER_OFFERED_PEACE) {
            return activeWars.getActiveWars(nation).stream().filter(f -> f.status == status).collect(Collectors.toList());
        }
        synchronized (warsByNationId) {
            return (warsByNationId.getOrDefault(nation, Collections.emptyMap()).values().stream().filter(f -> f.status == status).collect(Collectors.toList()));
        }
    }

    public List<DBWar> getActiveWarsByAlliance(Set<Integer> attackerAA, Set<Integer> defenderAA) {
        return new ArrayList<>(activeWars.getActiveWars(f -> true, f -> (attackerAA == null || attackerAA.contains(f.attacker_aa)) && (defenderAA == null || defenderAA.contains(f.defender_aa))).values());
    }

    public List<DBWar> getWarsByAlliance(int attacker) {
        synchronized (warsByAllianceId) {
            Map<Integer, DBWar> wars = warsByAllianceId.get(attacker);
            if (wars == null) return Collections.emptyList();
            return new ArrayList<>(wars.values());
        }
    }

    public List<DBWar> getWarsByNation(int nationId) {
        synchronized (warsByNationId) {
            return new ArrayList<>(warsByNationId.getOrDefault(nationId, Collections.emptyMap()).values());
        }
    }

    public DBWar getLastOffensiveWar(int nation) {
        return getWarsByNation(nation).stream().filter(f -> f.attacker_id == nation).max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public DBWar getLastDefensiveWar(int nation) {
        return getWarsByNation(nation).stream().filter(f -> f.defender_id == nation).max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public DBWar getLastWar(int nationId) {
        return getWarsByNation(nationId).stream().max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public List<DBAttack> selectAttacks(Consumer<SelectBuilder> query) {
        List<DBAttack> list = new ArrayList<>();
        SelectBuilder builder = getDb().selectBuilder("attacks2")
                .select("*");
        if (query != null) query.accept(builder);
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                list.add(createAttack(rs));
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public List<DBWar> getWarsByNation(int nation, WarStatus... statuses) {
        if (statuses.length == 0) return getWarsByNation(nation);
        if (statuses.length == 1) return getWarsByNation(nation, statuses[0]);
        Set<WarStatus> statusSet = new HashSet<>(Arrays.asList(statuses));

        synchronized (warsByNationId) {
            return (warsByNationId.getOrDefault(nation, Collections.emptyMap()).values().stream().filter(f -> statusSet.contains(f.status)).collect(Collectors.toList()));
        }
    }

    public List<DBWar> getActiveWars(Set<Integer> alliances, WarStatus... statuses) {
        Set<WarStatus> statusSet = new HashSet<>(Arrays.asList(statuses));
        return new ArrayList<>(activeWars.getActiveWars(f -> true, f -> (alliances.contains(f.attacker_aa) || alliances.contains(f.defender_aa)) && statusSet.contains(f.status)).values());
    }

    public List<DBWar> getWarByStatus(WarStatus... statuses) {
        Set<WarStatus> statusSet = new HashSet<>(Arrays.asList(statuses));
        return new ArrayList<>(getWars(f -> statusSet.contains(f.status)).values());
    }

    public List<DBWar> getWars(Set<Integer> alliances, long start) {
        return getWars(alliances, start, Long.MAX_VALUE);
    }

    public List<DBWar> getWars(Set<Integer> alliances, long start, long end) {
        return new ArrayList<>(getWarsForNationOrAlliance(null, f -> alliances.contains(f), f -> f.date > start && f.date < end).values());
    }

    public List<DBWar> getWarsById(Set<Integer> warIds) {
        List<DBWar> result = new ArrayList<>();
        synchronized (warsById) {
            for (Integer id : warIds) {
                DBWar war = warsById.get(id);
                if (war != null) result.add(war);
            }
        }
        return result;
    }
    public Map<Integer, DBWar> getWars(Collection<Integer> coal1Alliances, Collection<Integer> coal1Nations, Collection<Integer> coal2Alliances, Collection<Integer> coal2Nations, long start, long end) {
        if (coal1Alliances.isEmpty() && coal1Nations.isEmpty() && coal2Alliances.isEmpty() && coal2Nations.isEmpty()) return Collections.emptyMap();

        Set<Integer> alliances = new HashSet<>();
        alliances.addAll(coal1Alliances);
        alliances.addAll(coal2Alliances);
        Set<Integer> nations = new HashSet<>();
        nations.addAll(coal1Nations);
        nations.addAll(coal2Nations);

        Predicate<DBWar> isAllowed;
        if (coal1Alliances.isEmpty() && coal1Nations.isEmpty()) {
            isAllowed = new Predicate<DBWar>() {
                @Override
                public boolean test(DBWar war) {
                    if (war.date < start || war.date > end) return false;
                    return coal2Alliances.contains(war.attacker_aa) || coal2Nations.contains(war.attacker_id) || coal2Alliances.contains(war.defender_aa) || coal2Nations.contains(war.defender_id);
                }
            };
        } else if (coal2Alliances.isEmpty() && coal2Nations.isEmpty()) {
            isAllowed = new Predicate<DBWar>() {
                @Override
                public boolean test(DBWar war) {
                    if (war.date < start || war.date > end) return false;
                    return coal1Alliances.contains(war.attacker_aa) || coal1Nations.contains(war.attacker_id) || coal1Alliances.contains(war.defender_aa) || coal1Nations.contains(war.defender_id);
                }
            };
        } else {
            isAllowed = new Predicate<DBWar>() {
                @Override
                public boolean test(DBWar war) {
                    if (war.date < start || war.date > end) return false;
                    return ((coal1Alliances.contains(war.attacker_aa) || coal1Nations.contains(war.attacker_id)) && (coal2Alliances.contains(war.defender_aa) || coal2Nations.contains(war.defender_id))) ||
                            ((coal1Alliances.contains(war.defender_aa) || coal1Nations.contains(war.defender_id)) && (coal2Alliances.contains(war.attacker_aa) || coal2Nations.contains(war.attacker_id)));
                }
            };
        }

        return getWarsForNationOrAlliance(nations.isEmpty() ? null : f -> nations.contains(f), alliances.isEmpty() ? null : f -> alliances.contains(f), isAllowed);
    }
//
//    private String generateWarQuery(String prefix, Collection<Integer> coal1Alliances, Collection<Integer> coal1Nations, Collection<Integer> coal2Alliances, Collection<Integer> coal2Nations, long start, long end, boolean union) {
//        List<String> requirements = new ArrayList<>();
//        if (start > 0) {
//            requirements.add(prefix + "date > " + start);
//        }
//        if (end < System.currentTimeMillis()) {
//            requirements.add(prefix + "date < " + end);
//        }
//
//        List<String> attReq = new ArrayList<>();
//        if (!coal1Alliances.isEmpty()) {
//            if (coal1Alliances.size() == 1) {
//                Integer id = coal1Alliances.iterator().next();
//                attReq.add(prefix + "attacker_aa = " + id);
//            } else {
//                attReq.add(prefix + "attacker_aa in " + StringMan.getString(coal1Alliances));
//            }
//        }
//        if (!coal1Nations.isEmpty()) {
//            if (coal1Nations.size() == 1) {
//                Integer id = coal1Nations.iterator().next();
//                attReq.add(prefix + "attacker_id = " + id);
//            } else {
//                attReq.add(prefix + "attacker_id in " + StringMan.getString(coal1Nations));
//            }
//        }
//
//        List<String> defReq = new ArrayList<>();
//        if (!coal2Alliances.isEmpty()) {
//            if (coal2Alliances.size() == 1) {
//                Integer id = coal2Alliances.iterator().next();
//                defReq.add(prefix + "defender_aa = " + id);
//            } else {
//                defReq.add(prefix + "defender_aa in " + StringMan.getString(coal2Alliances));
//            }
//        }
//        if (!coal2Nations.isEmpty()) {
//            if (coal2Nations.size() == 1) {
//                Integer id = coal2Nations.iterator().next();
//                defReq.add(prefix + "defender_id = " + id);
//            } else {
//                defReq.add(prefix + "defender_id in " + StringMan.getString(coal2Nations));
//            }
//        }
//
//        List<String> natOrAAReq = new ArrayList<>();
//        if (!attReq.isEmpty()) natOrAAReq.add("(" + StringMan.join(attReq, " AND ") + ")");
//        if (!defReq.isEmpty()) natOrAAReq.add("(" + StringMan.join(defReq, " AND ") + ")");
//        String natOrAAReqStr = "(" + StringMan.join(natOrAAReq, union ? " AND " : " OR ") + ")";
//        requirements.add(natOrAAReqStr);
//
//
//        String query = "SELECT * from wars WHERE " + StringMan.join(requirements, " AND ");
//        return query;
//    }

    public void saveAttacks(Collection<DBAttack> values) {
        if (values.isEmpty()) return;

        // sort attacks
        ArrayList<DBAttack> valuesList = new ArrayList<>(values);
        Collections.sort(valuesList, Comparator.comparingInt(f -> f.getWar_attack_id()));
        values = valuesList;

        for (DBAttack attack : values) {
            if (attack.getAttack_type() != AttackType.VICTORY && attack.getAttack_type() != AttackType.A_LOOT) continue;

            Map<ResourceType, Double> loot = attack.getLoot();
            Double pct;
            if (loot == null) {
                pct = 1d;
            } else {
                pct = attack.getLootPercent();
            }
            if (pct == 0) pct = 0.1;
            double factor = 1/pct;

            double[] lootCopy;
            if (attack.loot != null) {
                lootCopy = attack.loot.clone();
                for (int i = 0; i < lootCopy.length; i++) {
                    lootCopy[i] = (lootCopy[i] * factor) - lootCopy[i];
                }
            } else {
                lootCopy = ResourceType.getBuffer();
            }
            if (attack.getAttack_type() == AttackType.VICTORY) {
                Locutus.imp().getNationDB().saveLoot(attack.getDefender_nation_id(), attack.getDate(), lootCopy, NationLootType.WAR_LOSS);
            } else if (attack.getAttack_type() == AttackType.A_LOOT) {
                Integer allianceId = attack.getLooted();
                if (allianceId != null) {
                    Locutus.imp().getNationDB().saveAllianceLoot(allianceId, attack.getDate(), lootCopy, NationLootType.WAR_LOSS);
                }
            }
        }
        synchronized (allAttacks2) {
            if (allAttacks2.isEmpty()) {
                allAttacks2.addAll(valuesList);
            } else {
                DBAttack latest = getLatestAttack();
                for (DBAttack attack : valuesList) {
                    if (attack.getWar_attack_id() > latest.getWar_attack_id()) {
                        allAttacks2.add(attack);
                    } else {
                        int indexFound = ArrayUtil.binarySearchLess(allAttacks2, f -> f.getWar_attack_id() <= attack.getWar_attack_id());
                        if (indexFound < 0) {
                            // insert at 0 index
                            allAttacks2.add(0, attack);
                        } else {
                            DBAttack existing = allAttacks2.get(indexFound);
                            if (existing.getWar_attack_id() == attack.getWar_attack_id()) {
                                // replace
                                allAttacks2.set(indexFound, attack);
                            } else {
                                // insert at indexFound + 1
                                allAttacks2.add(indexFound + 1, attack);
                            }
                        }
                    }
                }
            }
        }
        // ctiy_id not used anymore, but sqlite doesn't allow removing
        String query = "INSERT OR REPLACE INTO `attacks2`(`war_attack_id`, `date`, `war_id`, `attacker_nation_id`, `defender_nation_id`, `attack_type`, `victor`, `success`, attcas1, attcas2, defcas1, defcas2, defcas3,city_id,infra_destroyed,improvements_destroyed,money_looted,looted,loot,pct_looted,city_infra_before,infra_destroyed_value,att_gas_used,att_mun_used,def_gas_used,def_mun_used) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        ThrowingBiConsumer<DBAttack, PreparedStatement> setStmt = (attack, stmt) -> {
            stmt.setInt(1, attack.getWar_attack_id());
            stmt.setLong(2, attack.getDate());
            stmt.setInt(3, attack.getWar_id());
            stmt.setInt(4, attack.getAttacker_nation_id());
            stmt.setInt(5, attack.getDefender_nation_id());
            stmt.setInt(6, attack.getAttack_type().ordinal());
            stmt.setInt(7, attack.getVictor());
            stmt.setInt(8, attack.getSuccess());
            stmt.setInt(9, attack.getAttcas1());
            stmt.setInt(10, attack.getAttcas2());
            stmt.setInt(11, attack.getDefcas1());
            stmt.setInt(12, attack.getDefcas2());
            stmt.setInt(13, attack.getDefcas3());
            stmt.setLong(14, attack.city_cached);
            stmt.setLong(15, (long) (attack.getInfra_destroyed() * 100));
            stmt.setLong(16, attack.getImprovements_destroyed());
            stmt.setLong(17, (long) (attack.getMoney_looted() * 100));

            stmt.setInt(18, attack.getLooted());

            if (attack.loot == null) stmt.setNull(19, Types.BLOB);
            else stmt.setBytes(19, ArrayUtil.toByteArray(attack.loot));

            stmt.setInt(20, (int) (attack.getLootPercent() * 100 * 100));

            stmt.setLong(21, (int) (attack.getCity_infra_before() * 100));
            stmt.setLong(22, (long) (attack.getInfra_destroyed_value() * 100));
            stmt.setLong(23, (int) (attack.getAtt_gas_used() * 100));
            stmt.setLong(24, (int) (attack.getAtt_mun_used() * 100));
            stmt.setLong(25, (int) (attack.getDef_gas_used() * 100));
            stmt.setLong(26, (int) (attack.getDef_mun_used() * 100));
        };
        if (values.size() == 1) {
            DBAttack value = values.iterator().next();
            update(query, stmt -> setStmt.accept(value, stmt));
        } else {
            executeBatch(values, query, setStmt);
        }
    }

    public boolean updateAttacks(Consumer<Event> eventConsumer) {
        return updateAttacks( eventConsumer != null, eventConsumer);
    }

    private DBAttack getLatestAttack() {
        if (allAttacks2.isEmpty()) return null;
        synchronized (allAttacks2) {
            return allAttacks2.get(allAttacks2.size() - 1);
        }
    }
    public boolean updateAttacks(boolean runAlerts, Consumer<Event> eventConsumer) {
        return updateAttacks(runAlerts, eventConsumer, false);
    }

    public boolean updateAttacks(boolean runAlerts, Consumer<Event> eventConsumer, boolean v2) {
        long start = System.currentTimeMillis();
        DBAttack latest = getLatestAttack();
        Integer maxId = latest == null ? null : latest.getWar_attack_id();
        if (maxId == null || maxId == 0) runAlerts = false;

        // Dont run events if attacks are > 1 day old
        if (!v2 && (latest == null || latest.getDate() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))) {
            PoliticsAndWarV3 v3 = Locutus.imp().getV3();
            System.out.println("No recent attack data in DB. Updating attacks without event handling: " + maxId);
            List<DBAttack> attackList = new ArrayList<>();
            v3.fetchAttacksSince(maxId, new Predicate<WarAttack>() {
                @Override
                public boolean test(WarAttack v3Attack) {
                    DBAttack attack = new DBAttack(v3Attack);
                    synchronized (attackList) {
                        attackList.add(attack);
                        if (attackList.size() > 1000) {
                            System.out.println("Save " + attack.getWar_attack_id());
                            saveAttacks(attackList);
                            attackList.clear();
                            System.out.println("Save end");
                        }
                    }
                    return false;
                }
            });
            saveAttacks(attackList);
            return true;
        }

        List<DBAttack> dbAttacks = new ArrayList<>();
        List<DBAttack> newAttacks;
        Set<DBWar> warsToSave = new LinkedHashSet<>();
        List<DBAttack> dirtyCities = new ArrayList<>();

        synchronized (activeWars) {
            Set<Integer> existingIds = new IntOpenHashSet();
            {
                synchronized (allAttacks2) {
                    int index = ArrayUtil.binarySearchGreater(allAttacks2, f -> f.getWar_attack_id() > maxId);
                    if (index > 0) {
                        for (int i = index; i < allAttacks2.size(); i++) {
                            existingIds.add(allAttacks2.get(i).getWar_attack_id());
                        }
                    }
                }
            }

            if (v2) {
                try {
                    List<WarAttacksContainer> attacksv2 = Locutus.imp().getPnwApiV2().getWarAttacksByMinWarAttackId(maxId).getWarAttacksContainers();
                    newAttacks = attacksv2.stream().map(DBAttack::new).filter(f -> !existingIds.contains(f.getWar_attack_id())).collect(Collectors.toList());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                PoliticsAndWarV3 v3 = Locutus.imp().getV3();
                newAttacks = v3
                        .fetchAttacksSince(maxId, f -> true)
                        .stream().filter(f -> !existingIds.contains(f.getAtt_id())).map(DBAttack::new).toList();
            }

            Map<DBAttack, Double> attackInfraPctMembers = new HashMap<>();

            long now = System.currentTimeMillis();
            for (DBAttack attack : newAttacks) {
                if (runAlerts) {
                    Locutus.imp().getNationDB().setNationActive(attack.getAttacker_nation_id(), attack.getDate(), eventConsumer);
                    Map<MilitaryUnit, Integer> attLosses = attack.getUnitLosses(true);
                    Map<MilitaryUnit, Integer> defLosses = attack.getUnitLosses(false);
                    if (!attLosses.isEmpty()) {
                        Locutus.imp().getNationDB().updateNationUnits(attack.getAttacker_nation_id(), attack.getDate(), attLosses, eventConsumer);
                    }
                    if (!defLosses.isEmpty()) {
                        Locutus.imp().getNationDB().updateNationUnits(attack.getDefender_nation_id(), attack.getDate(), defLosses, eventConsumer);
                    }
                }

                if (attack.getAttack_type() == AttackType.NUKE && attack.getSuccess() > 0 && attack.city_cached != 0) {
                    Locutus.imp().getNationDB().setCityNukeFromAttack(attack.getDefender_nation_id(), attack.city_cached, attack.getDate(), eventConsumer);
                }

                if (attack.getAttack_type() == AttackType.VICTORY) {
                    DBWar war = activeWars.getWar(attack.getAttacker_nation_id(), attack.getWar_id());
                    if (war != null) {

                        if (runAlerts) {
                            DBNation defender = DBNation.getById(attack.getDefender_nation_id());
                            if (defender != null && defender.getLastFetchedUnitsMs() < attack.getDate()) {
                                DBNation copyOriginal = null;
                                if (!defender.isBeige()) copyOriginal = new DBNation(defender);
                                defender.setColor(NationColor.BEIGE);
                                defender.setBeigeTimer(defender.getBeigeAbsoluteTurn() + 24);
                                if (copyOriginal != null && eventConsumer != null)
                                    eventConsumer.accept(new NationChangeColorEvent(copyOriginal, defender));
                            }
                            DBWar oldWar = new DBWar(war);
                            war.status = war.attacker_id == attack.getAttacker_nation_id() ? WarStatus.ATTACKER_VICTORY : WarStatus.DEFENDER_VICTORY;
                            if (eventConsumer != null) {
                                eventConsumer.accept(new WarStatusChangeEvent(oldWar, war));
                            }
                        }
                    }
                }

                if (attack.getCity_infra_before() > 0 && attack.getInfra_destroyed() > 0 && attack.city_cached != 0 && attack.getAttack_type() != AttackType.VICTORY && attack.getAttack_type() != AttackType.A_LOOT) {
                    double infra = attack.getCity_infra_before() - attack.getInfra_destroyed();
                    Locutus.imp().getNationDB().setCityInfraFromAttack(attack.getDefender_nation_id(), attack.city_cached, infra, attack.getDate(), eventConsumer);
                }
                if (attack.getImprovements_destroyed() > 0 && attack.city_cached != 0) {
                    dirtyCities.add(attack);
                }
                if (attack.getDate() > now) {
                    attack.setDate(now);
                }
                if (attack.getAttack_type() == AttackType.GROUND && attack.getMoney_looted() != 0 && Settings.INSTANCE.LEGACY_SETTINGS.ATTACKER_DESKTOP_ALERTS.contains(attack.getAttacker_nation_id())) {
                    AlertUtil.openDesktop(attack.toUrl());
                }
                if ((attack.getAttack_type() == AttackType.NUKE || attack.getAttack_type() == AttackType.MISSILE) && attack.getSuccess() == 0) {
                    attack.setInfra_destroyed_value(0);
                }

                {
                    if (attack.getAttack_type() == AttackType.VICTORY && attack.infraPercent_cached > 0) {
                        DBNation defender = Locutus.imp().getNationDB().getNation(attack.getDefender_nation_id());
                        DBWar war = getWar(attack.getWar_id());
                        if (war != null) {
                            war.status = attack.getVictor() == attack.getAttacker_nation_id() ? WarStatus.ATTACKER_VICTORY : WarStatus.DEFENDER_VICTORY;

                            activeWars.makeWarInactive(war);
                            warsToSave.add(war);
                        }

                        if (defender != null) {
                            if (defender.getColor() != NationColor.BEIGE) {
                                DBNation copyOriginal = new DBNation(defender);
                                defender.setColor(NationColor.BEIGE);
                                defender.setBeigeTimer(TimeUtil.getTurn() + 24);
                                if (eventConsumer != null)
                                    eventConsumer.accept(new NationChangeColorEvent(copyOriginal, defender));
                            }
                            if (attack.getInfra_destroyed_value() == 0) {
                                double pct = attack.infraPercent_cached / 100d;

                                if (runAlerts) {
                                    attackInfraPctMembers.put(attack, pct);
                                }
                            }
                        }
                    }
                }

                dbAttacks.add(attack);
            }

            if (!attackInfraPctMembers.isEmpty()) { // update infra

                // get infra before
                for (Map.Entry<DBAttack, Double> entry : attackInfraPctMembers.entrySet()) {
                    DBAttack attack = entry.getKey();
                    double pct = entry.getValue();
                    if (pct > 0) {
                        Map<Integer, DBCity> cities = Locutus.imp().getNationDB().getCitiesV3(attack.getDefender_nation_id());
                        if (cities != null && !cities.isEmpty()) {
                            attack.setInfra_destroyed(0d);
                            attack.setInfra_destroyed_value(0d);
                            for (DBCity city : cities.values()) {
                                double infraStart, infraEnd;
                                if (city.fetched > attack.getDate() + 1) {
                                    infraStart = city.infra / (1 - pct);
                                    infraEnd = city.infra;
                                } else {
                                    infraStart = city.infra;
                                    infraEnd = (city.infra) * (1 - pct);
                                }
                                System.out.println("Set infra to " + infraEnd + " | " + pct);
                                attack.setInfra_destroyed(attack.getInfra_destroyed() + infraStart - infraEnd);
                                if (infraStart > infraEnd) {
                                    attack.setInfra_destroyed_value(attack.getInfra_destroyed_value() + PnwUtil.calculateInfra(infraEnd, infraStart));
                                    Locutus.imp().getNationDB().setCityInfraFromAttack(attack.getDefender_nation_id(), city.id, infraEnd, attack.getDate(), eventConsumer);
                                }
                            }
                        } else {
                            DBNation defender = DBNation.getById(attack.getDefender_nation_id());
                            if (defender != null) {
                                int numCities = defender.getCities();
                                double scoreNoInfra = defender.estimateScore(0);
                                double score = defender.getScore();
                                double infra = (score - scoreNoInfra) / MilitaryUnit.INFRASTRUCTURE.getScore(1);

                                double cityInfra = infra / numCities;
                                double infraStart, infraEnd;

                                long date = defender.getDateCheckedUnits();

                                if (attack.getInfra_destroyed() > 0) {
                                    double destroyedPerCity = attack.getInfra_destroyed() / numCities;
                                    if (date > attack.getDate()) {
                                        infraStart = cityInfra + destroyedPerCity;
                                        infraEnd = cityInfra;
                                    } else {
                                        infraStart = cityInfra;
                                        infraEnd = cityInfra - destroyedPerCity;
                                    }
                                } else {
                                    if (date > attack.getDate()) {
                                        infraStart = cityInfra / (1 - pct);
                                        infraEnd = cityInfra;
                                    } else {
                                        infraStart = cityInfra;
                                        infraEnd = (cityInfra) * (1 - pct);
                                    }
                                }
                                if (infraStart > infraEnd) {
                                    attack.setInfra_destroyed_value(PnwUtil.calculateInfra(infraEnd, infraStart) * numCities);
                                }
                            }
                        }
                    }
                }
            }
        }

        saveWars(warsToSave);

        if (runAlerts) {
            for (DBAttack attack : dirtyCities) {
                Locutus.imp().getNationDB().markCityDirty(attack.getDefender_nation_id(), attack.city_cached, attack.getDate());
            }
        }

        { // add to db
            saveAttacks(newAttacks);
        }

        if (runAlerts && eventConsumer != null) {
            long start2 = System.currentTimeMillis();
            NationUpdateProcessor.updateBlockades();
            long diff = System.currentTimeMillis() - start2;

            if (diff > 200) {
                System.err.println("Took too long to update blockades (" + diff + "ms)");
            }

            for (DBAttack attack : dbAttacks) {
                eventConsumer.accept(new AttackEvent(attack));
            }
        }
        return true;
    }

//    public List<DBAttack> getAttacks(List<Alliance> coal1Alliances, List<DBNation> coal1Nations, List<Alliance> coal2Alliances, List<DBNation> coal2Nations, long start, long end) {
//
//    }

    public List<DBAttack> getAttacksByWar(DBWar war) {
        long start = war.date;
        long end = war.possibleEndDate();
        List<DBAttack> list = new ArrayList<>();
        iterateAttacks(start, end, f -> {
            if (f.getWar_id() == war.getWarId()) {
                list.add(f);
            }
        });
        return list;
    }

    public List<DBAttack> getAttacksByWarId(int warId, long expectedDate) {
        DBWar war = getWar(warId);
        if (war == null) {
            List<DBAttack> list = new ArrayList<>();
            long turn = TimeUtil.getTurn(expectedDate);
            long start = TimeUtil.getTimeFromTurn(turn - 60);
            long end = TimeUtil.getTimeFromTurn(turn + 60);
            iterateAttacks(start, end, f -> {
                if (f.getWar_id() == warId) {
                    list.add(f);
                }
            });
            return list;
        }
        return getAttacksByWar(war);
    }

    public Collection<DBAttack> loadAttacks(int days) {
        String whereClause;
        if (days == 0) {
            return allAttacks2;
        } else if (days >= 0) {
            long date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
            whereClause = " WHERE date > " + date;
        } else {
            whereClause = "";
        }
        ObjectArrayList<DBAttack> tmp = new ObjectArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2`" + whereClause + " ORDER BY `war_attack_id` ASC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tmp.add(createAttack(rs));
                }
            }
            return allAttacks2 = tmp;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, List<DBAttack>> getAttacksByWar(int nationId, long cuttoff) {
        List<DBAttack> attacks = getAttacks(nationId, cuttoff);
        return new RankBuilder<>(attacks).group(a -> a.getWar_id()).get();
    }

    public List<DBAttack> getAttacks(Predicate<DBAttack> filter) {
        ObjectArrayList<DBAttack> list = new ObjectArrayList<>();
        synchronized (allAttacks2) {
            for (DBAttack attack : allAttacks2) {
                if (filter.test(attack)) list.add(attack);
            }
        }
        return list;
    }

    public void iterateAttacks(long start, long end, Consumer<DBAttack> consumer) {
        if (start > end) return;
        if (end >= System.currentTimeMillis()) {
            iterateAttacks(start, consumer);
            return;
        }
        synchronized (allAttacks2) {
            int indexStart = ArrayUtil.binarySearchGreater(allAttacks2, f -> f.getDate() >= start);
//            int indexEnd = ArrayUtil.binarySearchGreater(allAttacks2, f -> f.epoch <= end);
            if (indexStart == -1) return;
            for (int i = indexStart; i < allAttacks2.size(); i++) {
                DBAttack attack = allAttacks2.get(i);
                if (attack.getDate() > end) break;
                consumer.accept(attack);
            }
        }
    }

    public void iterateAttacks(long start, Consumer<DBAttack> consumer) {
        if (start >= System.currentTimeMillis()) return;
        synchronized (allAttacks2) {
            int indexStart = ArrayUtil.binarySearchGreater(allAttacks2, f -> f.getDate() >= start);
            if (indexStart == -1) return;
            for (int i = indexStart; i < allAttacks2.size(); i++) {
                consumer.accept(allAttacks2.get(i));
            }
        }
    }

    public List<DBAttack> getAttacks(long start, long end, Predicate<DBAttack> filter) {
        List<DBAttack> list = new ObjectArrayList<>();
        iterateAttacks(start, end, f -> {
            if (filter.test(f)) list.add(f);
        });
        return list;
    }

    public List<DBAttack> getAttacks(long start, Predicate<DBAttack> filter) {
        List<DBAttack> list = new ObjectArrayList<>();
        iterateAttacks(start, f -> {
            if (filter.test(f)) list.add(f);
        });
        return list;
    }

    public List<DBAttack> getAttacks(long cuttoffMs) {
        return getAttacks(cuttoffMs, Long.MAX_VALUE);
    }
    public List<DBAttack> getAttacks(long start, long end) {
        ObjectArrayList<DBAttack> list = new ObjectArrayList<>();
        iterateAttacks(start, end, list::add);
        return list;
    }

    public DBAttack createAttack(ResultSet rs) throws SQLException {
        DBAttack attack = new DBAttack();
        attack.setWar_attack_id(rs.getInt(1));
        attack.setDate(rs.getLong(2));
        attack.setWar_id(rs.getInt(3));
        attack.setAttacker_nation_id(rs.getInt(4));
        attack.setDefender_nation_id(rs.getInt(5));
        attack.setAttack_type(AttackType.values[rs.getInt(6)]);
        attack.setSuccess(rs.getInt(8));

        if (attack.getSuccess() > 0 || attack.getAttack_type() == AttackType.VICTORY)
        {
            attack.setLooted(attack.getDefender_nation_id());

            attack.setInfra_destroyed(getLongDef0(rs, 15) * 0.01);
            if (attack.getInfra_destroyed() > 0) {
                attack.setImprovements_destroyed(getIntDef0(rs, 16));
                attack.setCity_infra_before(getLongDef0(rs, 21) * 0.01);
                attack.setInfra_destroyed_value(getLongDef0(rs, 22) * 0.01);
            }
        }

        switch (attack.getAttack_type()) {
            case GROUND:
                if (attack.getSuccess() < 0) break;
            case VICTORY:
            case A_LOOT:
                attack.setMoney_looted(getLongDef0(rs, 17) * 0.01);
        }

        if (attack.getAttack_type() == AttackType.VICTORY || attack.getAttack_type() == AttackType.A_LOOT) {
            attack.setLooted(rs.getInt(18));
            byte[] lootBytes = getBytes(rs, 19);
            if (lootBytes != null) {
                attack.setLoot(ArrayUtil.toDoubleArray(lootBytes));
                attack.setLootPercent(rs.getInt(20) * 0.0001);
            }
        }

        switch (attack.getAttack_type()) {
            case VICTORY:
            case FORTIFY:
            case A_LOOT:
            case PEACE:
                break;
            default:
                attack.setAtt_gas_used(getLongDef0(rs, 23) * 0.01);
                attack.setAtt_mun_used(getLongDef0(rs, 24) * 0.01);
                attack.setDef_gas_used(getLongDef0(rs, 25) * 0.01);
                attack.setDef_mun_used(getLongDef0(rs, 26) * 0.01);
            case MISSILE:
            case NUKE:
                attack.setAttcas1(rs.getInt(9));
                attack.setAttcas2(rs.getInt(10));
                attack.setDefcas1(rs.getInt(11));
                attack.setDefcas2(rs.getInt(12));
                attack.setDefcas3(rs.getInt(13));
                break;
        }

        return attack;
    }
//
//    public DBAttack createLegacy(ResultSet rs) throws SQLException {
//        DBAttack attack = new DBAttack();
//        attack.war_attack_id = rs.getInt("war_attack_id");
//        attack.epoch = rs.getLong("date");
//        attack.war_id = rs.getInt("war_id");
//        attack.attacker_nation_id = rs.getInt("attacker_nation_id");
//        attack.defender_nation_id = rs.getInt("defender_nation_id");
//        attack.attack_type = AttackType.values[rs.getInt("attack_type")];
//        attack.victor = rs.getInt("victor");
//        attack.success = rs.getInt("success");
//        attack.attcas1 = rs.getInt("attcas1");
//        attack.attcas2 = rs.getInt("attcas2");
//        attack.defcas1 = rs.getInt("defcas1");
//        attack.defcas2 = rs.getInt("defcas2");
//        attack.defcas3 = 0;
//        attack.city_id = rs.getInt("city_id");
//        Long infra_destroyed = getLong(rs, "infra_destroyed");
//        attack.infra_destroyed = infra_destroyed == null ? null : infra_destroyed / 100d;
//        attack.improvements_destroyed = getInt(rs, "improvements_destroyed");
//        Long money_looted = getLong(rs, "money_looted");
//        attack.money_looted = money_looted == null ? null : money_looted / 100d;
//
//        // looted,loot,pct_looted
//        String note = rs.getString("note");
//        if (note != null) {
//            attack.parseLootLegacy(note);
//        }
//
//        Long city_infra_before = getLong(rs, "city_infra_before");
//        attack.city_infra_before = city_infra_before == null ? null : city_infra_before / 100d;
//        Long infra_destroyed_value = getLong(rs, "infra_destroyed_value");
//        attack.infra_destroyed_value = infra_destroyed_value == null ? null : infra_destroyed_value / 100d;
//        Long att_gas_used = getLong(rs, "att_gas_used");
//        attack.att_gas_used = att_gas_used == null ? null : att_gas_used / 100d;
//        Long att_mun_used = getLong(rs, "att_mun_used");
//        attack.att_mun_used = att_mun_used == null ? null : att_mun_used / 100d;
//        Long def_gas_used = getLong(rs, "def_gas_used");
//        attack.def_gas_used = def_gas_used == null ? null : def_gas_used / 100d;
//        Long def_mun_used = getLong(rs, "def_mun_used");
//        attack.def_mun_used = def_mun_used == null ? null : def_mun_used / 100d;
//
//        return attack;
//    }

    public Map<ResourceType, Double> getAllianceBankEstimate(int allianceId, double nationScore) {
        DBAlliance alliance = DBAlliance.get(allianceId);
        if (allianceId == 0 || alliance == null) return Collections.emptyMap();
        LootEntry lootInfo = alliance.getLoot();
        if (lootInfo == null) return Collections.emptyMap();

        double[] allianceLoot = lootInfo.getTotal_rss();

        double aaScore = alliance.getScore();
        if (aaScore == 0) return Collections.emptyMap();

        double ratio = ((nationScore * 10000) / aaScore) / 2d;
        double percent = Math.min(Math.min(ratio, 10000) / 30000, 0.33);
        Map<ResourceType, Double> yourLoot = PnwUtil.resourcesToMap(allianceLoot);
        yourLoot = PnwUtil.multiply(yourLoot, percent);
        return yourLoot;
    }

    public Map<Integer, Map.Entry<Long, double[]>> getNationLootFromAttacksLegacy() {
        Map<Integer, Map.Entry<Long, double[]>> nationLoot = new ConcurrentHashMap<>();

        // `attacker_nation_id`, `defender_nation_id`
        String nationReq = "";
        try (PreparedStatement stmt= prepareQuery("select * FROM (SELECT * FROM `attacks2` WHERE `attack_type` = 1" + nationReq + " ORDER BY date DESC) AS tmp_table GROUP BY case when victor = attacker_nation_id then defender_nation_id else attacker_nation_id end")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBAttack attack = createAttack(rs);
                    if (attack.loot == null) continue;

                    int looted = attack.getLooted();
                    int looter = attack.getLooter();

//                    double factor = 0.1;
//                    if (victor != null && victor.getPolicy().equalsIgnoreCase("Pirate")) {
//                        factor = 0.14;
//                    }
//                    if (loser != null && loser.getPolicy().equalsIgnoreCase("Moneybags")) {
//                        factor *= 0.6;
//                    }
                    double factor = 1 / attack.getLootPercent();
                    double[] lootCopy = attack.loot.clone();
                    for (int i = 0; i < lootCopy.length; i++) {
                        lootCopy[i] = lootCopy[i] * factor - lootCopy[i];
                    }

                    AbstractMap.SimpleEntry<Long, double[]> newEntry = new AbstractMap.SimpleEntry<>(attack.getDate(), lootCopy);
                    Map.Entry<Long, double[]> existing = nationLoot.put(looted, newEntry);
                    if (existing != null && existing.getKey() > attack.getDate()) {
                        nationLoot.put(looted, existing);
                    }
                }
            }
            return nationLoot;
            // nation loot
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<DBAttack> getAttacks(int nation_id) {
        List<DBWar> wars = getWarsByNation(nation_id);
        return getAttacksByWars(wars);
    }
    public List<DBAttack> getAttacks(int nation_id, long cuttoffMs) {
        return getAttacks(nation_id, cuttoffMs, Long.MAX_VALUE);
    }
    public List<DBAttack> getAttacks(int nation_id, long start, long end) {
        if (start <= 0 && end == Long.MAX_VALUE) return getAttacks(nation_id);

        List<DBWar> wars = getWarsByNation(nation_id);
        // remove wars outside the date
        long startWithExpire = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        wars.removeIf(f -> f.date < startWithExpire || f.date > end);
        List<DBAttack> attacks = getAttacksByWars(wars, start, end);
        return attacks;
    }

    public List<DBAttack> getAttacks(long cuttoffMs, AttackType type) {
        return getAttacks(cuttoffMs, f -> f.getAttack_type() == type);
    }
    public List<DBAttack> getAttacksByWars(Collection<DBWar> wars) {
        return getAttacksByWars(wars, 0, Long.MAX_VALUE);
    }
    public List<DBAttack> getAttacksByWars(Collection<DBWar> wars, long start, long end) {
        List<DBAttack> list = new ObjectArrayList<>();

        Set<Integer> warIds = new IntOpenHashSet(wars.stream().map(war -> war.warId).toList());

        List<DBWar> warsSorted = new ArrayList<>(wars);
        warsSorted.sort(Comparator.comparingLong(war -> war.date));
        // sort wars by date
        long lastWarEnd = -1;
        int lastWarIndexEnd = 0;
        synchronized (allAttacks2) {
            outer:
            for (DBWar war : warsSorted) {
                long warStart = Math.max(start, war.date);
                long warTurn = TimeUtil.getTurn(war.date);
                long warEnd = Math.min(end, TimeUtil.getTimeFromTurn(warTurn + 60));

                if (warEnd <= lastWarEnd || warStart >= warEnd) continue;

                int indexStart;
                if (warStart < lastWarEnd) {
                    indexStart = lastWarIndexEnd;
                } else {
                    indexStart = ArrayUtil.binarySearchGreater(allAttacks2, f -> f.getDate() >= warStart, lastWarIndexEnd, allAttacks2.size() - 1);
                }
                if (indexStart <= -1) continue;
                indexStart = Math.max(lastWarIndexEnd, indexStart);

                lastWarEnd = warEnd;

                for (int i = indexStart; i < allAttacks2.size(); i++) {
                    DBAttack attack = allAttacks2.get(i);
                    if (attack.getDate() > warEnd) {
                        lastWarIndexEnd = i;
                        continue outer;
                    }
                    if (warIds.contains(attack.getWar_id())) {
                        list.add(attack);
                    }
                }
                break outer;
            }
        }
        return list;
    }

    public List<DBAttack> getAttacksByWars(List<DBWar> wars, long cuttoffMs) {
        return getAttacksByWars(wars, cuttoffMs, Long.MAX_VALUE);
    }

    public List<DBAttack> getAttacks(Set<Integer> nationIds, long cuttoffMs) {
        return getAttacks(nationIds, cuttoffMs, Long.MAX_VALUE);
    }
    public List<DBAttack> getAttacks(Set<Integer> nationIds, long start, long end) {
        Set<DBWar> allWars = new LinkedHashSet<>();
        long startWithExpire = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        synchronized (warsByNationId) {
            for (int nationId : nationIds) {
                Map<Integer, DBWar> natWars = warsByNationId.get(nationId);
                if (natWars != null) {
                    for (DBWar war : natWars.values()) {
                        if (!nationIds.contains(war.attacker_id) || !nationIds.contains(war.defender_id)) continue;
                        if (war.date < startWithExpire || war.date > end) continue;
                        allWars.add(war);
                    }
                }
            }
        }
        return getAttacksByWars(allWars, start, end);
    }

    public List<DBAttack> getAttacksAny(Set<Integer> nationIds, long cuttoffMs) {
        return getAttacksAny(nationIds, cuttoffMs, Long.MAX_VALUE);
    }
    public List<DBAttack> getAttacksAny(Set<Integer> nationIds, long start, long end) {
        Set<DBWar> allWars = new LinkedHashSet<>();
        long startWithExpire = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        synchronized (warsByNationId) {
            for (int nationId : nationIds) {
                Map<Integer, DBWar> natWars = warsByNationId.get(nationId);
                if (natWars != null) {
                    for (DBWar war : natWars.values()) {
                        if (war.date < startWithExpire || war.date > end) continue;
                        allWars.add(war);
                    }
                }
            }
        }
        return getAttacksByWars(allWars, start, end);
    }

    public int countWarsByNation(int nation_id, long date) {
        if (date == 0) {
            return warsByNationId.getOrDefault(nation_id, Collections.emptyMap()).size();
        }
        synchronized (warsByNationId) {
            Map<Integer, DBWar> wars = warsByNationId.get(nation_id);
            if (wars == null) return 0;
            int total = 0;
            for (DBWar war : wars.values()) {
                if (war.date > date) total++;
            }
            return total;
        }
    }

    public int countOffWarsByNation(int nation_id, long date) {
        synchronized (warsByNationId) {
            Map<Integer, DBWar> wars = warsByNationId.get(nation_id);
            if (wars == null) return 0;
            int total = 0;
            for (DBWar war : wars.values()) {
                if (war.attacker_id == nation_id && war.date > date) total++;
            }
            return total;
        }
    }

    public int countDefWarsByNation(int nation_id, long date) {
        synchronized (warsByNationId) {
            Map<Integer, DBWar> wars = warsByNationId.get(nation_id);
            if (wars == null) return 0;
            int total = 0;
            for (DBWar war : wars.values()) {
                if (war.defender_id == nation_id && war.date > date) total++;
            }
            return total;
        }
    }

    public int countWarsByAlliance(int alliance_id, long date) {
        if (date == 0) {
            return warsByAllianceId.getOrDefault(alliance_id, Collections.emptyMap()).size();
        }
        synchronized (warsByAllianceId) {
            Map<Integer, DBWar> wars = warsByAllianceId.get(alliance_id);
            if (wars == null) return 0;
            int total = 0;
            for (DBWar war : wars.values()) {
                if (war.date > date) total++;
            }
            return total;
        }
    }
}
