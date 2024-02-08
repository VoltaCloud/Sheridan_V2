package link.locutus.discord.db;

import com.google.common.eventbus.Subscribe;
import com.locutus.wiki.game.PWWikiUtil;
import de.siegmar.fastcsv.reader.CsvRow;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv3.DataDumpParser;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.conflict.ConflictMetric;
import link.locutus.discord.event.war.AttackEvent;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.scheduler.TriConsumer;
import link.locutus.discord.web.jooby.AwsManager;
import link.locutus.discord.web.jooby.JteUtil;
import org.bson.BsonBinaryWriter;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConflictManager {
    private final WarDB db;
    private final Map<Integer, Conflict> conflictMap = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, String> legacyNames = new Int2ObjectOpenHashMap<>();
    private final Map<String, Integer> legacyIds = new ConcurrentHashMap<>();
    private final Set<Integer> activeConflicts = new IntOpenHashSet();
    private long lastTurn = 0;
    private final Map<Integer, Set<Integer>> activeConflictsByAllianceId = new Int2ObjectOpenHashMap<>();
    private final Map<Long, Map<Integer, int[]>> mapTurnAllianceConflictIds = new Long2ObjectOpenHashMap<>();

    private synchronized void initTurn() {
        long currTurn = TimeUtil.getTurn();
        if (lastTurn != currTurn) {
            Iterator<Integer> iter = activeConflicts.iterator();
            activeConflicts.removeIf(f -> {
                Conflict conflict = conflictMap.get(f);
                return (conflict == null || conflict.getEndTurn() <= currTurn);
            });
            recreateConflictsByAlliance();

            for (Conflict conflict : conflictMap.values()) {
                long startTurn = Math.max(lastTurn + 1, conflict.getStartTurn());
                long endTurn = Math.min(currTurn + 1, conflict.getEndTurn());
                if (startTurn >= endTurn) continue;
                Set<Integer> aaIds = conflict.getAllianceIds();
                for (long turn = startTurn; turn < endTurn; turn++) {
                    Map<Integer, int[]> conflictIdsByAA = mapTurnAllianceConflictIds.computeIfAbsent(turn, k -> new Int2ObjectOpenHashMap<>());
                    for (int aaId : aaIds) {
                        if (conflict.getStartTurn(aaId) > turn) continue;
                        if (conflict.getEndTurn(aaId) <= turn) continue;
                        int[] currIds = conflictIdsByAA.get(aaId);
                        if (currIds == null) {
                            currIds = new int[]{conflict.getId()};
                        } else {
                            int[] newIds = new int[currIds.length + 1];
                            System.arraycopy(currIds, 0, newIds, 0, currIds.length);
                            newIds[currIds.length] = conflict.getId();
                            Arrays.sort(newIds);
                            currIds = newIds;
                        }
                        conflictIdsByAA.put(aaId, currIds);
                    }
                }
            }
            lastTurn = currTurn;
        }
    }

    private boolean applyConflicts(long turn, int allianceId1, int allianceId2, Consumer<Conflict> conflictConsumer) {
        if (allianceId1 == 0 && allianceId2 == 0) return false;
        synchronized (mapTurnAllianceConflictIds) {
            Map<Integer, int[]> conflictIdsByAA = mapTurnAllianceConflictIds.get(turn);
            if (conflictIdsByAA == null) return false;
            int[] conflictIds1 = allianceId1 != 0 ? conflictIdsByAA.get(allianceId1) : null;
            int[] conflictIds2 = allianceId2 != 0 && allianceId2 != allianceId1 ? conflictIdsByAA.get(allianceId2) : null;
            if (conflictIds2 != null && conflictIds1 != null) {
                int i = 0, j = 0;
                while (i < conflictIds1.length && j < conflictIds2.length) {
                    if (conflictIds1[i] < conflictIds2[j]) {
                        applyConflictConsumer(conflictIds1[i], conflictConsumer);
                        i++;
                    } else if (conflictIds1[i] > conflictIds2[j]) {
                        applyConflictConsumer(conflictIds2[j], conflictConsumer);
                        j++;
                    } else {
                        applyConflictConsumer(conflictIds1[i], conflictConsumer);
                        i++;
                        j++;
                    }
                }
                while (i < conflictIds1.length) {
                    applyConflictConsumer(conflictIds1[i], conflictConsumer);
                    i++;
                }
                while (j < conflictIds2.length) {
                    applyConflictConsumer(conflictIds2[j], conflictConsumer);
                    j++;
                }
            } else if (conflictIds1 != null) {
                for (int conflictId : conflictIds1) {
                    applyConflictConsumer(conflictId, conflictConsumer);
                }
            } else if (conflictIds2 != null) {
                for (int conflictId : conflictIds2) {
                    applyConflictConsumer(conflictId, conflictConsumer);
                }
            }
            return true;
        }
    }

    private void applyConflictConsumer(int conflictId, Consumer<Conflict> conflictConsumer) {
        Conflict conflict = conflictMap.get(conflictId);
        if (conflict != null) {
            conflictConsumer.accept(conflict);
        }
    }

    public boolean updateWar(DBWar previous, DBWar current) {
        long turn = TimeUtil.getTurn(current.getDate());
        if (turn > lastTurn) initTurn();
        return applyConflicts(turn, current.getAttacker_aa(), current.getDefender_aa(), f -> f.updateWar(previous, current, turn));
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        AbstractCursor attack = event.getAttack();
        DBWar war = attack.getWar();
        if (war != null) {
            updateAttack(war, attack);
        }
    }

    public void updateAttack(DBWar war, AbstractCursor attack) {
        long turn = TimeUtil.getTurn(war.getDate());
        if (turn > lastTurn) initTurn();
        applyConflicts(turn, war.getAttacker_aa(), war.getDefender_aa(), f -> f.updateAttack(war, attack, turn));
    }

    private void recreateConflictsByAlliance() {
        synchronized (activeConflictsByAllianceId) {
            activeConflictsByAllianceId.clear();
            for (int id : activeConflicts) {
                addConflictsByAlliance(conflictMap.get(id));
            }
        }
    }

    private void addConflictsByAlliance(Conflict conflict) {
        if (conflict == null) return;
        synchronized (activeConflictsByAllianceId) {
            for (int aaId : conflict.getAllianceIds()) {
                activeConflictsByAllianceId.computeIfAbsent(aaId, k -> new IntArraySet()).add(conflict.getId());
            }
        }
    }

    public ConflictManager(WarDB db) {
        this.db = db;
    }

    protected void loadConflicts() {
        long start = System.currentTimeMillis();
        conflictMap.clear();
        db.query("SELECT * FROM conflicts", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                long startTurn = rs.getLong("start");
                long endTurn = rs.getLong("end");
                String wiki = rs.getString("wiki");
                String col1 = rs.getString("col1");
                String col2 = rs.getString("col2");
                if (col1.isEmpty()) col1 = "Coalition 1";
                if (col2.isEmpty()) col2 = "Coalition 2";
                conflictMap.put(id, new Conflict(id, name, col1, col2, wiki, startTurn, endTurn));
            }
        });
//        db.update("DELETE FROM conflict_participant WHERE alliance_id = 0");
        db.query("SELECT * FROM conflict_participant", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int conflictId = rs.getInt("conflict_id");
                int allianceId = rs.getInt("alliance_id");
                boolean side = rs.getBoolean("side");
                long startTurn = rs.getLong("start");
                long endTurn = rs.getLong("end");
                Conflict conflict = conflictMap.get(conflictId);
                if (conflict != null) {
                    conflict.addParticipant(allianceId, side, false, startTurn, endTurn);
                }
            }
        });
        // load legacyNames
        db.query("SELECT * FROM legacy_names", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                legacyNames.put(id, name);
                legacyIds.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
            }
        });
        for (Map.Entry<String, Integer> entry : getDefaultNames().entrySet()) {
            legacyIds.putIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            legacyNames.putIfAbsent(entry.getValue(), entry.getKey());
        }

        System.out.println("Loaded " + conflictMap.size() + " conflicts in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
        if (legacyNames.isEmpty()) {
            saveDefaultNames();
        }
        System.out.println("Default names: " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
        initTurn();
        System.out.println("Init turns: " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
        ObjectOpenHashSet<DBWar> wars = new ObjectOpenHashSet<>();
        long currentTurn = TimeUtil.getTurn();
        for (DBWar war : this.db.getWars()) {
            if (updateWar(null, war)) {
                if (war.isActive() && TimeUtil.getTurn(war.getDate()) + 61 < currentTurn) {
                    System.out.println("INVALID WAR EPIRED " + war.getWarId() + " | " + war.getDate() + " | " + war.getStatus());
                }
                wars.add(war);
            }
        }
        long finalStart = start;

        Locutus.imp().getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                long start = finalStart;
                System.out.println("Load wars: " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
                db.iterateAttacks(wars, f -> true, f -> true, new Consumer<AbstractCursor>() {
                    @Override
                    public void accept(AbstractCursor attack) {
                        DBWar war = wars.get(new ArrayUtil.IntKey(attack.getWar_id()));
                        updateAttack(war, attack);
                    }
                });
                //
//        db.update("DELETE FROM conflict_graphs WHERE conflict_id = 0");
                System.out.println("Load attacks: " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
                db.query("SELECT * FROM conflict_graphs", stmt -> {
                }, (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        int conflictId = rs.getInt("conflict_id");
                        boolean side = rs.getBoolean("side");
                        int metricOrd = rs.getInt("metric");
                        long turnOrDay = rs.getLong("turn");
                        int city = rs.getInt("city");
                        int value = rs.getInt("value");
                        Conflict conflict = conflictMap.get(conflictId);
                        if (conflict != null) {
                            ConflictMetric metric = ConflictMetric.values[metricOrd];
                            conflict.getSide(side).addGraphData(metric, turnOrDay, city, value);
                        }
                    }
                });
            }
        });
        System.out.println("Load graph data: " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
    }

    private Map<String, Integer> getDefaultNames() {
        Map<String, Integer> legacyIds = new HashMap<>();
        legacyIds.put("Arrgh!", 913);
        legacyIds.put("Zodiac", 4145);
        legacyIds.put("Resplendent Inc.", 2844);
        legacyIds.put("Phoenix", 1809);
        legacyIds.put("Hogwarts", 9110);
        legacyIds.put("Global United Nations", 107);
        legacyIds.put("Blue Moon", 3003);
        legacyIds.put("Animal Empire", 4158);
        legacyIds.put("Cobalt", 770);
        legacyIds.put("IKEA", 8777);
        legacyIds.put("Ignis Aternum", 11713);
        legacyIds.put("IronFront", 5462);
        legacyIds.put("InGen", 1634);
        legacyIds.put("Dutch East India Company", 9997);
        legacyIds.put("The Chola", 2047);
        legacyIds.put("The Kings Parliament", 1804);
        legacyIds.put("Ex Machina", 7414);
        legacyIds.put("DawnGuard", 11513);
        legacyIds.put("Dawn Rising", 8520);
        legacyIds.put("Clover Kingdom", 7094);
        legacyIds.put("New Pacific Order", 2082);
        legacyIds.put("Divine Phoenix Empire", 8173);
        legacyIds.put("Divine Phoenix", 8173);
        legacyIds.put("Aeterna", 11841);
        legacyIds.put("The Empire of the Moonlit Sakura", 4623);
        legacyIds.put("The Federation", 4638);
        legacyIds.put("The Manhattan Cartel", 5851);
        legacyIds.put("The North Western South Region of the Far East", 6703);
        legacyIds.put("The Paladin", 11568);
        legacyIds.put("The Rohirrim", 7540);
        legacyIds.put("The Ronin Empire", 7376);
        legacyIds.put("The Three Swords", 10374);
        legacyIds.put("The United Armies", 5326);
        legacyIds.put("ThunderStruck", 7635);
        legacyIds.put("Titanio", 7642);
        legacyIds.put("United Commerce Republic", 8651);
        legacyIds.put("VooDoo", 8804);
        legacyIds.put("Waffle House", 10936);
        legacyIds.put("Swords of Sanghelios", 8173);
        legacyIds.put("Terran Federation", 9110);
        legacyIds.put("The Circus", 9521);
        legacyIds.put("The Coal Mines (2nd)", 7094);
        legacyIds.put("The Coven", 9573);
        legacyIds.put("The Elites", 8429);
        legacyIds.put("Order of the White Rose", 2510);
        legacyIds.put("Polaris", 2358);
        legacyIds.put("Prima Victoria", 7570);
        legacyIds.put("Roman Empire", 9600);
        legacyIds.put("Serene Repubblica Fiorentina", 9927);
        legacyIds.put("Serene Wei", 1742);
        legacyIds.put("Seven Kingdoms", 615);
        legacyIds.put("Soldiers of Liberty", 6215);
        legacyIds.put("Spartan Brotherhood", 6161);
        legacyIds.put("Spartan Republic", 7380);
        legacyIds.put("Sunray 1-1", 9651);
        legacyIds.put("Sunray Victoria", 10466);
        legacyIds.put("Atlas Technological Cooperative", 7306);
        legacyIds.put("Ghost Division", 9588);
        legacyIds.put("Children of the Light", 7452);
        legacyIds.put("iriririr", 12438);
        legacyIds.put("United Nations 2", 12359);
        legacyIds.put("insinsaneane", 12450);
        legacyIds.put("Noble Wei", 12382);
        legacyIds.put("Convent of Atom",7531);
        legacyIds.put("The Ampersand",5722);
        legacyIds.put("Brotherhood of the Clouds",7703);
        legacyIds.put("Bank Robbers",7923);
        legacyIds.put("The Outhouse",7990);
        legacyIds.put("Not Rohans Bank",8014);
        legacyIds.put("Democracy",8060);
        legacyIds.put("Prusso Roman Imperial Union",7920);
        legacyIds.put("Avalanche",8150);
        legacyIds.put("Sanctuary",8368);
        legacyIds.put("Union of Soviet Socialist Republics",8531);
        legacyIds.put("Ad Astra",7719);
        legacyIds.put("Otaku Shougaku",8594);
        legacyIds.put("Wizards",8624);
        legacyIds.put("MDC",8615);
        legacyIds.put("Christmas",8614);
        legacyIds.put("MySpacebarIsBroken",8678);
        legacyIds.put("Lords of Wumbology",8703);
        legacyIds.put("Paragon",8502);
        legacyIds.put("Shuba2M",8909);
        legacyIds.put("Shuba69M",8929);
        legacyIds.put("Mensa HQ",8930);
        legacyIds.put("Shuba99M",8955);
        legacyIds.put("Not A Scam",8984);
        legacyIds.put("The Dead Rabbits",7540);
        legacyIds.put("The Vatican",9321);
        legacyIds.put("High Temple",9341);
        legacyIds.put("Shuba666M",9385);
        legacyIds.put("Crimson Dragons",9406);
        legacyIds.put("Apollo",9427);
        legacyIds.put("Nibelheim",9580);
        legacyIds.put("Starfleet",9850);
        legacyIds.put("OTSN",9883);
        legacyIds.put("The Knights Of The Round Table",9830);
        legacyIds.put("Wayne Enterprises",9931);
        legacyIds.put("LegoLand",9961);
        legacyIds.put("Wayne Enterprises Inc",9971);
        legacyIds.put("Paradise",9986);
        legacyIds.put("The Afterlyfe",10060);
        legacyIds.put("Esquire Templar",10070);
        legacyIds.put("The Naughty Step",10074);
        legacyIds.put("The Cove",10104);
        legacyIds.put("Pacific Polar",10248);
        legacyIds.put("Stigma",10326);
        legacyIds.put("Sparkle Party People",10329);
        legacyIds.put("Age of Darkness",10100);
        legacyIds.put("Lunacy",9278);
        legacyIds.put("The Entente",10396);
        legacyIds.put("Crawling Crawfish Conundrum",10398);
        legacyIds.put("Western Republic",10408);
        legacyIds.put("General Patton",10411);
        legacyIds.put("Crab Creation Contraption",10416);
        legacyIds.put("The Bugs palace",10414);
        legacyIds.put("Aggravated Conch Assault",10425);
        legacyIds.put("Castle Wall",10436);
        legacyIds.put("Mukbang Lobster ASMR",10440);
        legacyIds.put("House of the Dragon",10445);
        legacyIds.put("lobster emoji",10447);
        legacyIds.put("LobsterGEDDON",10449);
        legacyIds.put("ARMENIA FOREVER",10452);
        legacyIds.put("Stigma 1",10450);
        legacyIds.put("General Custer",10454);
        legacyIds.put("Scyllarides Saloon",10464);
        legacyIds.put("Camelot Squires",10468);
        legacyIds.put("bruh momento",10467);
        legacyIds.put("Limp Lobster",10474);
        legacyIds.put("OSNAP",10472);
        legacyIds.put("AAAAAAAAA",10486);
        legacyIds.put("Alpha Lobster",10485);
        legacyIds.put("Shuba73M",10489);
        legacyIds.put("Borgs Assisted Loot Liberation Service",10504);
        legacyIds.put("Cornhub",10521);
        legacyIds.put("xXxJaredLetoFanxXx",10529);
        legacyIds.put("Anti-Horridism Obocchama Kun Fan Club",10540);
        legacyIds.put("Iraq Lobster",10552);
        legacyIds.put("Mole Rats",10574);
        legacyIds.put("God I Love Frogs",10573);
        legacyIds.put("A-HOK Fan Club Fan Club",10583);
        legacyIds.put("MyKeyboardIsBroken",10683);
        legacyIds.put("Show Me The Money",10694);
        legacyIds.put("Banana Stand London Branch",10709);
        legacyIds.put("Sparkle of the Night",10712);
        legacyIds.put("Cru Whole Hole",10716);
        legacyIds.put("Arrghs offshore",10717);
        legacyIds.put("Banana Stand New York",10720);
        legacyIds.put("Anything",10739);
        legacyIds.put("Banana Stand Los Angeles",10733);
        legacyIds.put("Wayne Foundation",10746);
        legacyIds.put("Banana Stand On The Run",10747);
        legacyIds.put("Master Basters",10759);
        legacyIds.put("Turkey land",10757);
        legacyIds.put("Theres No Place Like Home",10764);
        legacyIds.put("Drake - Hotline Bling",8520);
        legacyIds.put("Yer A Wizard Harry",10783);
        legacyIds.put("A Truth Universally Acknowledged",10805);
        legacyIds.put("An Offer He Cant Refuse",10815);
        legacyIds.put("HAHA England lost to France",10834);
        legacyIds.put("Banco dei Medici",8520);
        legacyIds.put("The Bank of Orbis",10092);
        legacyIds.put("May The Force Be With You",10839);
        legacyIds.put("Shaken Not Stirred",10845);
        legacyIds.put("Shaken Not Stired",10848);
        legacyIds.put("ET Phone Home",10854);
        legacyIds.put("Cock of destiny",10855);
        legacyIds.put("Yo Adrian",10868);
        legacyIds.put("Autocephalous Patriarchate of the Free",10869);
        legacyIds.put("Mama Always Said",10878);
        legacyIds.put("offshoreassss",10887);
        legacyIds.put("Youre Tacky and I Hate You",10905);
        legacyIds.put("O Captain My Captain",10912);
        legacyIds.put("HIDUDE GIB TIERING REPORT",10917);
        legacyIds.put("Bank of The Holy Grail",10925);
        legacyIds.put("Shuba45M",10933);
        legacyIds.put("The IX Legion",10934);
        legacyIds.put("Sparkle Forever",10946);
        legacyIds.put("BOSNIA MODE",10949);
        legacyIds.put("Jotunheimr",8429);
        legacyIds.put("Fallen Monarchy",10988);
        legacyIds.put("Gunga Ginga",11005);
        legacyIds.put("Grand Union of Nations",11018);
        legacyIds.put("Calamity",11019);
        legacyIds.put("borgborgborgborgborgborgborg",11023);
        legacyIds.put("Fargos",11027);
        legacyIds.put("Event Horizon",11039);
        legacyIds.put("CATA_IS_SO_COOL",11036);
        legacyIds.put("Pasta Factory",11042);
        legacyIds.put("MERDE",11059);
        legacyIds.put("The Black League",11066);
        legacyIds.put("United Nations Space Command",10995);
        legacyIds.put("Loopsnake alliance",11064);
        legacyIds.put("Old Praxis",11075);
        legacyIds.put("DecaDeezKnuttz",11077);
        legacyIds.put("Eurovision 2023 incoming",11090);
        legacyIds.put("Animal Pharm",11165);
        legacyIds.put("The Imperial Vault",11209);
        legacyIds.put("The House of Bugs",11288);
        legacyIds.put("Midnight Blues",11304);
        legacyIds.put("Mace & Chain",11312);
        legacyIds.put("Skull & Bones",11008);
        legacyIds.put("Swiss Account",11350);
        legacyIds.put("No offshore here",11353);
        legacyIds.put("Dunce Cap Supreme",11359);
        legacyIds.put("Aunt Jemima",11360);
        legacyIds.put("Fortuna sucks",11372);
        legacyIds.put("Home Hero",11375);
        legacyIds.put("Pharm Animal",11368);
        legacyIds.put("The Children of Yakub",11370);
        legacyIds.put("Shuba65M",11371);
        legacyIds.put("Tower of London",11376);
        legacyIds.put("Tintagel Castle",11384);
        legacyIds.put("Killer Tomatoes",11386);
        legacyIds.put("Nessa Barrett",11391);
        legacyIds.put("Legion of Dusk",11390);
        legacyIds.put("The Semimortals",11394);
        legacyIds.put("State of Orbis",11401);
        legacyIds.put("King Tiger",11398);
        legacyIds.put("Toilet Worshipping Lunatics",11403);
        legacyIds.put("The Peaceful Warmongers",11405);
        legacyIds.put("North Mexico",11407);
        legacyIds.put("Ketamine Therapy",11406);
        legacyIds.put("Kiwi Taxidermy",11420);
        legacyIds.put("Kazakhstani Tramway",11435);
        legacyIds.put("Prenadores de Burras Profesionales",11441);
        legacyIds.put("Kidney Transplant",11444);
        legacyIds.put("Shuba63m",11450);
        legacyIds.put("Knockoff Tetragrammatons",11457);
        legacyIds.put("Castillo de Coca",11454);
        legacyIds.put("Kangaroo Testicles",11473);
        legacyIds.put("New Church Republic",11494);
        legacyIds.put("Kleptomaniac Tunisians",11493);
        legacyIds.put("Shuba777M",11510);
        legacyIds.put("Kitten Toes",11514);
        legacyIds.put("Atlas Three",11515);
        legacyIds.put("Kaleidoscope Technology",11525);
        legacyIds.put("Koala Tornado",11531);
        legacyIds.put("Skylines",11533);
        legacyIds.put("Palo Mayombe",11604);
        legacyIds.put("Mouseleys Superfan Fun Cheese Corner 5",11619);
        legacyIds.put("General Area of Two Ostritches",11643);
        legacyIds.put("The Persian Empire",10671);
        legacyIds.put("Bakerstreet",11699);
        legacyIds.put("The Radiant Syndication",11719);
        legacyIds.put("Make More Monitors",11714);
        legacyIds.put("Quack",11718);
        legacyIds.put("Panama City Beach",11710);
        legacyIds.put("Shadowhunters",11715);
        legacyIds.put("Storm",11721);
        legacyIds.put("Three Inch Surprise",11730);
        legacyIds.put("Greywater Watch",11731);
        legacyIds.put("Port St Lucie",11740);
        legacyIds.put("The Orphanage",11746);
        legacyIds.put("Halo Revived",11751);
        legacyIds.put("Saint Augustine",11753);
        legacyIds.put("Orange Brotherhood",11764);
        legacyIds.put("Demon Slayer",11765);
        legacyIds.put("The Hippo Horde",11763);
        legacyIds.put("Yeehaw Junction",11769);
        legacyIds.put("House Weeb",11772);
        legacyIds.put("Ockey Multi Mass Production Facility 7",11779);
        legacyIds.put("TCM Extension",11797);
        legacyIds.put("Bohemian Grove",11811);
        legacyIds.put("House Stark Crypto Wallet",11805);
        legacyIds.put("Two Egg",11817);
        legacyIds.put("Elfers",11830);
        legacyIds.put("Gamblers Anonymous",11862);
        legacyIds.put("Humza Useless",11876);
        legacyIds.put("The Merry Men",11900);
        legacyIds.put("Jacobite Rebellion",11899);
        legacyIds.put("The Media",11912);
        legacyIds.put("World of Farce",11952);
        legacyIds.put("Lyra",12022);
        legacyIds.put("Free Alrea",12029);
        legacyIds.put("Black Banana",12031);
        legacyIds.put("Cassiopeia",12034);
        legacyIds.put("Basil Land",12036);
        legacyIds.put("Better eclipse",12037);
        legacyIds.put("Planet express",12043);
        legacyIds.put("Seven WHO",12047);
        legacyIds.put("Aquila",12057);
        legacyIds.put("Rum Raiders",12062);
        legacyIds.put("Free Alrea 3",12067);
        legacyIds.put("Zapp spammigan",12068);
        legacyIds.put("Eridanus",12064);
        legacyIds.put("Neighborhood watch alliance",12066);
        legacyIds.put("House Apathy",12069);
        legacyIds.put("Free Alrea 4",12076);
        legacyIds.put("Taurus",12084);
        legacyIds.put("Vela",12090);
        legacyIds.put("Chavez Nuestro que Estas en los Cielos",12102);
        legacyIds.put("Cygnus",12134);
        legacyIds.put("Thin Skin Singularity",12190);
        legacyIds.put("Red Wine on THT",12261);
        legacyIds.put("Cute Cats Cuddling in a Cayak",12290);
        legacyIds.put("Narutos",12318);
        legacyIds.put("insane",12344);
        legacyIds.put("enasni",12362);
        legacyIds.put("Tax Scheme",12364);
        legacyIds.put("aneins",12369);
        legacyIds.put("aneane",12380);
        legacyIds.put("insane transposed",12421);
        legacyIds.put("anti insane",12429);
        legacyIds.put("Biker Haven", 11389);
        legacyIds.put("Hegemoney", 11709);
        return legacyIds;
    }

    public void saveDataCsvAllianceNames() throws IOException, ParseException {
        Locutus.imp().getDataDumper(true).iterateAll(f -> true, new TriConsumer<Long, DataDumpParser.NationHeader, CsvRow>() {
            @Override
            public void consume(Long day, DataDumpParser.NationHeader header, CsvRow row) {
                int aaId = Integer.parseInt(row.getField(header.alliance_id));
                if (aaId == 0) return;
                if (getAllianceNameOrNull(aaId) == null) {
                    String name = row.getField(header.alliance);
                    if (name != null && !name.isEmpty()) {
                        addLegacyName(aaId, name);
                        System.out.println("Added " + aaId + " | " + name);
                    }
                }
            }
        }, null, null);
    }

    private void saveDefaultNames() {
        Map<String, Integer> legacyIds = getDefaultNames();
        for (Map.Entry<String, Integer> entry : legacyIds.entrySet()) {
            addLegacyName(entry.getValue(), entry.getKey());
        }

    }

    public void createTables() {
        db.executeStmt("CREATE TABLE IF NOT EXISTS conflicts (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR NOT NULL, start BIGINT NOT NULL, end BIGINT NOT NULL)");
        // add col1 and col2 (string) to conflicts, default ""
//        db.executeStmt("ALTER TABLE conflicts ADD COLUMN col1 VARCHAR DEFAULT ''");
//        db.executeStmt("ALTER TABLE conflicts ADD COLUMN col2 VARCHAR DEFAULT ''");
        // add wiki column, default empty
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN wiki VARCHAR DEFAULT ''");

        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_participant (conflict_id INTEGER NOT NULL, alliance_id INTEGER NOT NULL, side BOOLEAN, start BIGINT NOT NULL, end BIGINT NOT NULL, PRIMARY KEY (conflict_id, alliance_id), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
        db.executeStmt("CREATE TABLE IF NOT EXISTS legacy_names (id INTEGER PRIMARY KEY, name VARCHAR NOT NULL)");
//        db.executeStmt("DELETE FROM conflict_participant");
//        db.executeStmt("DELETE FROM conflicts");

        // conflict_graphs: int conflict_id, boolean side, int metric, bigint turn, byte[] data
        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_graphs (conflict_id INTEGER NOT NULL, side BOOLEAN NOT NULL, metric INTEGER NOT NULL, turn BIGINT NOT NULL, city INTEGER NOT NULL, value INTEGER NOT NULL, PRIMARY KEY (conflict_id, side, metric, turn, city), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
    }

    public static void main(String[] args) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "John Doe");
        map.put("age", 30);
        map.put("city", "New York");

        // Convert the map to a BSON document
        Document bson = new Document(map);
        // Create a DocumentCodec to handle the encoding
        DocumentCodec codec = new DocumentCodec();

        BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        BsonWriter writer = new BsonBinaryWriter(outputBuffer);
        // Write the BSON document to the writer
        codec.encode(writer, bson, EncoderContext.builder().isEncodingCollectibleDocument(true).build());

        // Print the BSON document
        System.out.println(bson.toJson());

        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        String key = Settings.INSTANCE.WEB.S3.ACCESS_KEY;
        String secret = Settings.INSTANCE.WEB.S3.SECRET_ACCESS_KEY;
        String region = Settings.INSTANCE.WEB.S3.REGION;
        String bucket = Settings.INSTANCE.WEB.S3.BUCKET;
        AwsManager aws = new AwsManager(key, secret, bucket, region);
        aws.putObject("test.gzip", JteUtil.compress(outputBuffer.toByteArray()));
    }

    public void clearGraphData(ConflictMetric metric, int conflictId, boolean side, long turn) {
        db.update("DELETE FROM conflict_graphs WHERE conflict_id = ? AND side = ? AND metric = ? AND turn = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
            stmt.setBoolean(2, side);
            stmt.setInt(3, metric.ordinal());
            stmt.setLong(4, turn);
        });
    }

    public void clearGraphData(Collection<ConflictMetric> metric, int conflictId, boolean side, long turn) {
        if (metric.isEmpty()) return;
        if (metric.size() == 1) {
            clearGraphData(metric.iterator().next(), conflictId, side, turn);
            return;
        }
        db.update("DELETE FROM conflict_graphs WHERE conflict_id = ? AND side = ? AND metric IN (" + metric.stream().map(Enum::ordinal).map(String::valueOf).collect(Collectors.joining(",")) + ") AND turn = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
            stmt.setBoolean(2, side);
            stmt.setLong(3, turn);
        });
    }

    public void deleteGraphData(int conflictId) {
        db.update("DELETE FROM conflict_graphs WHERE conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
        });
    }

    public void addGraphData(List<ConflictMetric.Entry> metrics) {
        String query = "INSERT OR REPLACE INTO conflict_graphs (conflict_id, side, metric, turn, city, value) VALUES (?, ?, ?, ?, ?, ?)";
        db.executeBatch(metrics, query, (ThrowingBiConsumer<ConflictMetric.Entry, PreparedStatement>) (entry, stmt) -> {
            stmt.setInt(1, entry.conflictId());
            stmt.setBoolean(2, entry.side());
            stmt.setInt(3, entry.metric().ordinal());
            stmt.setLong(4, entry.turnOrDay());
            stmt.setInt(5, entry.city());
            stmt.setInt(6, entry.value());
        });
    }

    public void addLegacyName(int id, String name) {
        if (legacyNames.containsKey(id)) return;
        legacyNames.put(id, name);
        legacyIds.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
        db.update("INSERT OR IGNORE INTO legacy_names (id, name) VALUES (?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, id);
            stmt.setString(2, name);
        });
    }

    public Conflict addConflict(String name, String col1, String col2, String wiki, long turnStart, long turnEnd) {
        String query = "INSERT INTO conflicts (name, col1, col2, wiki, start, end) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, col1);
            stmt.setString(3, col2);
            stmt.setString(4, wiki);
            stmt.setLong(5, turnStart);
            stmt.setLong(6, turnEnd);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
                Conflict conflict = new Conflict(id, name, col1, col2, wiki, turnStart, turnEnd);
                conflictMap.put(id, conflict);

                synchronized (activeConflicts) {
                    long turn = TimeUtil.getTurn();
                    if (turnEnd > turn) {
                        activeConflicts.add(id);
                        addConflictsByAlliance(conflict);
                    }
                }

                return conflict;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void updateConflict(int conflictId, long start, long end) {
        synchronized (activeConflicts) {
            if (activeConflicts.contains(conflictId)) {
                if (end <= TimeUtil.getTurn()) {
                    activeConflicts.remove(conflictId);
                    recreateConflictsByAlliance();
                }
            }
        }
        db.update("UPDATE conflicts SET start = ?, end = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, start);
            stmt.setLong(2, end);
            stmt.setInt(3, conflictId);
        });
    }

    public void updateConflictName(int conflictId, String name) {
        db.update("UPDATE conflicts SET name = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setInt(2, conflictId);
        });
    }

    public void updateConflictName(int conflictId, String name, boolean isPrimary) {
        db.update("UPDATE conflicts SET `col" + (isPrimary ? "1" : "2") + "` = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setInt(2, conflictId);
        });
    }


    public void updateConflictWiki(int conflictId, String wiki) {
        db.update("UPDATE conflicts SET `wiki` = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, wiki);
        });
    }

    protected void addParticipant(int allianceId, int conflictId, boolean side, long start, long end) {
        db.update("INSERT OR REPLACE INTO conflict_participant (conflict_id, alliance_id, side, start, end) VALUES (?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
            stmt.setInt(2, allianceId);
            stmt.setBoolean(3, side);
            stmt.setLong(4, start);
            stmt.setLong(5, end);
        });
        DBAlliance aa = DBAlliance.get(allianceId);
        if (aa != null) addLegacyName(allianceId, aa.getName());
        synchronized (activeConflicts) {
            if (activeConflicts.contains(conflictId)) {
                addConflictsByAlliance(conflictMap.get(conflictId));
            }
        }
    }

    protected void removeParticipant(int allianceId, int conflictId) {
        db.update("DELETE FROM conflict_participant WHERE alliance_id = ? AND conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, conflictId);
        });
        if (activeConflicts.contains(conflictId)) {
            recreateConflictsByAlliance();
        }
    }

    public Map<Integer, Conflict> getConflictMap() {
        synchronized (conflictMap) {
            return new Int2ObjectOpenHashMap<>(conflictMap);
        }
    }

    public List<Conflict> getActiveConflicts() {
        return conflictMap.values().stream().filter(conflict -> conflict.getEndTurn() == Long.MAX_VALUE).toList();
    }

    public Conflict getConflict(String conflictName) {
        for (Conflict conflict : getConflictMap().values()) {
            if (conflict.getName().equalsIgnoreCase(conflictName)) {
                return conflict;
            }
        }
        return null;
    }

    public Integer getLegacyId(String name) {
        return legacyIds.get(name.toLowerCase(Locale.ROOT));
    }

    public String getAllianceName(int id) {
        String name = getAllianceNameOrNull(id);
        if (name == null) name = "AA:" + id;
        return name;
    }

    public String getAllianceNameOrNull(int id) {
        DBAlliance alliance = DBAlliance.get(id);
        if (alliance != null) return alliance.getName();
        String name;
        synchronized (legacyNames) {
            name = legacyNames.get(id);
        }
        return name;
    }

    public void deleteConflict(Conflict conflict) {
        synchronized (activeConflicts) {
            if (activeConflicts.contains(conflict.getId())) {
                activeConflicts.remove(conflict.getId());
                recreateConflictsByAlliance();
            }
        }
        synchronized (conflictMap) {
            conflictMap.remove(conflict.getId());
        }
        db.update("DELETE FROM conflicts WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflict.getId());
        });
        db.update("DELETE FROM conflict_participant WHERE conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflict.getId());
        });
    }

    public Conflict getConflictById(int id) {
        synchronized (conflictMap) {
            return conflictMap.get(id);
        }
    }

    public Set<String> getConflictNames() {
        Set<String> names = new ObjectOpenHashSet<>();
        synchronized (conflictMap) {
            for (Conflict conflict : conflictMap.values()) {
                names.add(conflict.getName());
            }
        }
        return names;
    }

    public byte[] getPsonGzip() {
        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        Map<Integer, Conflict> map = getConflictMap();
        Map<Integer, String> aaNameById = new HashMap<>();

        Map<String, Function<Conflict, Object>> headerFuncs = new LinkedHashMap<>();
        headerFuncs.put("id", Conflict::getId);
        headerFuncs.put("name", Conflict::getName);
        headerFuncs.put("c1_name", f -> f.getSide(true).getName());
        headerFuncs.put("c2_name", f -> f.getSide(false).getName());
        headerFuncs.put("start", f -> TimeUtil.getTimeFromTurn(f.getStartTurn()));
        headerFuncs.put("end", f -> f.getEndTurn() == Long.MAX_VALUE ? -1 : TimeUtil.getTimeFromTurn(f.getEndTurn()));
        headerFuncs.put("wars", Conflict::getTotalWars);
        headerFuncs.put("active_wars", Conflict::getActiveWars);
        headerFuncs.put("c1_dealt", f -> (long) f.getDamageConverted(true));
        headerFuncs.put("c2_dealt", f -> (long) f.getDamageConverted(false));
        headerFuncs.put("c1", f -> new IntArrayList(f.getCoalition1()));
        headerFuncs.put("c2", f -> new IntArrayList(f.getCoalition1()));

        List<String> headers = new ObjectArrayList<>();
        List<Function<Conflict, Object>> funcs = new ObjectArrayList<>();
        for (Map.Entry<String, Function<Conflict, Object>> entry : headerFuncs.entrySet()) {
            headers.add(entry.getKey());
            funcs.add(entry.getValue());
        }
        root.put("headers", headers);
        System.out.println("Headers " + root.get("headers"));

        List<List<Object>> rows = new ObjectArrayList<>();
        JteUtil.writeArray(rows, funcs, map.values());

        for (List<Object> row : rows) {
            System.out.println("Start end " + row.get(2) + " | " + row.get(3));
        }

        root.put("conflicts", rows);

        for (Conflict conflict : map.values()) {
            for (int id : conflict.getAllianceIds()) {
                if (!aaNameById.containsKey(id)) {
                    String name = getAllianceNameOrNull(id);
                    aaNameById.put(id, name == null ? "" : name);
                }
            }
        }
        List<Integer> allianceIds = new ArrayList<>(aaNameById.keySet());
        Collections.sort(allianceIds);
        List<String> aaNames = allianceIds.stream().map(aaNameById::get).toList();
        root.put("alliance_ids", allianceIds);
        root.put("alliance_names", aaNames);

        return JteUtil.compress(JteUtil.toBinary(root));
    }

    public Map.Entry<String, Integer> getMostSimilar(String name) {
        int distance = Integer.MAX_VALUE;
        String similar = null;
        for (Map.Entry<String, Integer> entry : legacyIds.entrySet()) {
            int d = StringMan.getLevenshteinDistance(name, entry.getKey());
            if (d < distance) {
                distance = d;
                similar = entry.getKey();
            }
        }
        return distance == Integer.MAX_VALUE ? null : Map.entry(similar, distance);
    }
}
