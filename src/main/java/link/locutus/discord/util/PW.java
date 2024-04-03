package link.locutus.discord.util;

import com.google.common.hash.Hashing;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.AllianceBankContainer;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.CommerceBuilding;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.building.ResourceBuilding;
import link.locutus.discord.apiv1.enums.city.building.imp.APowerBuilding;
import link.locutus.discord.apiv1.enums.city.building.imp.AResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.stock.Exchange;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import net.dv8tion.jda.api.entities.Guild;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PW {

    public static class City {
        public static class Land {
            public static double calculateLand(double from, double to) {
                if (from < 0 || from == to) return 0;
                if (to <= from) return (from - to) * -50;
                if (to > 20000) throw new IllegalArgumentException("Land cannot exceed 10,000");
                double[] tmp = LAND_COST_CACHE;
                if (tmp != null && from == tmp[0] && to == tmp[1]) {
                    return tmp[2];
                }

                double total = 0;
                for (double i = Math.max(0, from); i < to; i += 500) {
                    double cost = 0.002d * Math.pow(Math.max(20, i - 20), 2) + 50;
                    double amt = Math.min(500, to - i);
                    total += cost * amt;
                }
                LAND_COST_CACHE = new double[]{from, to, total};

                return total;
            }
        }

        public static class Infra {
            private static int getInfraCostCents(double infra) {
                if (infra <= 4000) {
                    int index = Math.max(0, (int) (infra * 100) - 3000);
                    return INFRA_COST_FAST_CACHE[index];
                }
                return (int) Math.round(100 * (300d + (Math.pow(Math.max(infra - 10d, 20), (2.2d))) * 0.00140845070422535211267605633803));
            }

            private static int getInfraCostCents(int infra_cents) {
                if (infra_cents <= 400000) {
                    int index = Math.max(0, infra_cents - 3000);
                    return INFRA_COST_FAST_CACHE[index];
                }
                return (int) Math.round(100 * (300d + (Math.pow(Math.max(infra_cents - 1000, 2000) * 0.01, (2.2d))) * 0.00140845070422535211267605633803));
            }

            public static double calculateInfra(double from, double to, boolean aec, boolean cfce, boolean urbanization, boolean gsa) {
                double factor = 1;
                if (aec) factor -= 0.05;
                if (cfce) factor -= 0.05;
                if (urbanization) {
                    factor -= 0.05;
                    if (gsa) factor -= 0.025;
                }
                return calculateInfra(from, to) * (to > from ? factor : 1);
            }

            // precompute the cost for first 4k infra
            public static double calculateInfra(double from, double to) {
                if (from < 0) return 0;
                if (to <= from) return (from - to) * -150;
                if (to > 20000) throw new IllegalArgumentException("Infra cannot exceed 10,000 (" + to + ")");
                long total_cents = 0;
                int to_cents = (int) Math.round(to * 100);
                int from_cents = (int) Math.round(from * 100);
                for (int i = to_cents; i >= from_cents; i -= 10000) {
                    int amt = Math.min(10000, i - from_cents);
                    int cost_cents = getInfraCostCents(i - amt);
                    total_cents += ((long) cost_cents * amt);
                }
                total_cents = (total_cents + 50)  / 100;
                return total_cents * 0.01;
            }

            /**
             * Value of attacking target with infra, to take them from current infra -> 1500
             *
             * @param avg_infra
             * @param cities
             * @return net damage value that should be done
             */
            public static int calculateInfraAttackValue(int avg_infra, int cities) {
                if (avg_infra < 1500) return 0;
                double total = 0;
                for (int i = 1500; i < avg_infra; i++) {
                    total += 300d + (Math.pow((i - 10d), (2.2d))) / 710d;
                }
                return (int) (total * cities);
            }
        }

        public static int getPollution(Predicate<Project> hasProject, Function<Building, Integer> getBuildings, int nuke_turn) {
            int pollution = 0;
            if (nuke_turn > 0) {
                double pollutionMax = 400d;
                int turnsMax = 11 * 12;
                long turns = TimeUtil.getTurn() - nuke_turn;
                if (turns < turnsMax) {
                    double nukePollution = (turnsMax - turns) * pollutionMax / (turnsMax);
                    if (nukePollution > 0) {
                        pollution += (int) nukePollution;
                    }
                }
            }
            for (Building building : Buildings.POLLUTION_BUILDINGS) {
                int amt = getBuildings.apply(building);
                if (amt == 0) continue;
                int buildPoll = building.pollution(hasProject);
                if (buildPoll != 0) {
                    pollution += amt * buildPoll;
                }
            }
            return Math.max(0, pollution);
        }

        public static int getCommerce(Predicate<Project> hasProject, Function<Building, Integer> getBuildings) {
            int commerce = 0;
            for (Building building : Buildings.COMMERCE_BUILDINGS) {
                int amt = getBuildings.apply(building);
                if (amt == 0) continue;
                commerce += amt * ((CommerceBuilding) building).getCommerce();
            }
            int maxCommerce;
            if (hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER)) {
                if (hasProject.test(Projects.TELECOMMUNICATIONS_SATELLITE)) {
                    maxCommerce = 125;
                } else {
                    maxCommerce = 115;
                }
            } else {
                maxCommerce = 100;
            }
            if (commerce > maxCommerce) {
                commerce = maxCommerce;
            }
            return commerce;
        }

        public static double getCrime(Predicate<Project> hasProject, Function<Building, Integer> getBuildings, long infra_cents, int commerce) {
            int police = getBuildings.apply(Buildings.POLICE_STATION);
            double policeMod;
            if (police > 0) {
                double policePct = hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5 : 2.5;
                policeMod = police * (policePct);
            } else {
                policeMod = 0;
            }
            return Math.max(0, ((MathMan.sqr(103 - commerce) + (infra_cents))*(0.000009d) - policeMod));
        }

        public static double profitConverted(Continent continent, double rads, Predicate<Project> hasProject, int numCities, double grossModifier, ICity city) {
            double profit = 0;

            final boolean powered = (city.getPowered() != Boolean.FALSE) && (city.getPoweredInfra() >= city.getInfra());
            int unpoweredInfra = (int) Math.ceil(city.getInfra());

            if (powered) {
                for (int ordinal = 0; ordinal < 4; ordinal++) {
                    int amt = city.getBuildingOrdinal(ordinal);
                    if (amt == 0) continue;

                    Building building = Buildings.get(ordinal);

                    for (int i = 0; i < amt; i++) {
                        if (unpoweredInfra > 0) {
                            profit += ((APowerBuilding) building).consumptionConverted(unpoweredInfra);
                            unpoweredInfra = unpoweredInfra - ((APowerBuilding) building).getInfraMax();
                        }
                    }
                    profit += building.profitConverted(continent, rads, hasProject, city, amt);
                }
                for (int ordinal = Buildings.GAS_REFINERY.ordinal(); ordinal < Buildings.size(); ordinal++) {
                    int amt = city.getBuildingOrdinal(ordinal);
                    if (amt == 0) continue;

                    Building building = Buildings.get(ordinal);
                    profit += building.profitConverted(continent, rads, hasProject, city, amt);
                }
            }

            for (int ordinal = 4; ordinal < Buildings.GAS_REFINERY.ordinal(); ordinal++) {
                int amt = city.getBuildingOrdinal(ordinal);
                if (amt == 0) continue;

                Building building = Buildings.get(ordinal);
                profit += building.profitConverted(continent, rads, hasProject, city, amt);
            }

            int commerce = powered ? city.calcCommerce(hasProject) : 0;

            double newPlayerBonus = numCities < 10 ? Math.max(1, (200d - ((numCities - 1) * 10d)) * 0.01) : 1;

            double income = Math.max(0, (((commerce * 0.02) * 0.725) + 0.725) * city.calcPopulation(hasProject) * newPlayerBonus) * grossModifier;;


            profit += income;

            double basePopulation = city.getInfra() * 100;
            double food = (Math.pow(basePopulation, 2)) / 125_000_000 + ((basePopulation) * (1 + Math.log(city.getAgeDays()) / 15d) - basePopulation) / 850;

            profit -= ResourceType.convertedTotalNegative(ResourceType.FOOD, food);

            return profit;
        }

        public static double[] profit(Continent continent,
                               double rads,
                               long date,
                               Predicate<Project> hasProject,
                               double[] profitBuffer,
                               int numCities,
                               double grossModifier,
                               boolean forceUnpowered,
                               int turns,
                               ICity city
        ) {
            if (profitBuffer == null) profitBuffer = new double[ResourceType.values.length];

            boolean powered;
            if (forceUnpowered) {
                powered = false;
            } else {
                powered = true;
                Boolean setPowered = city.getPowered();
                if (setPowered != null) powered = setPowered;
                if (powered && city.getPoweredInfra() < city.getInfra()) powered = false;
            }

            int unpoweredInfra = (int) Math.ceil(city.getInfra());
            for (Building building : Buildings.values()) {
                int amt = city.getBuilding(building);
                if (amt == 0) continue;

                if (!powered) {
                    if (building instanceof CommerceBuilding || building instanceof MilitaryBuilding || (building instanceof ResourceBuilding && ((AResourceBuilding) building).getResourceProduced().isManufactured())) {
                        continue;
                    }
                }
                profitBuffer = building.profit(continent, rads, date, hasProject, city, profitBuffer, turns);
                if (building instanceof APowerBuilding) {
                    for (int i = 0; i < amt; i++) {
                        if (unpoweredInfra > 0) {
                            profitBuffer = ((APowerBuilding) building).consumption(unpoweredInfra, profitBuffer, turns);
                            unpoweredInfra = unpoweredInfra - ((APowerBuilding) building).getInfraMax();
                        }
                    }
                }
            }
            int commerce = city.calcCommerce(hasProject);
            double newPlayerBonus = 1 + Math.max(1 - (numCities - 1) * 0.05, 0);

            double income = (((commerce/50d) * 0.725d) + 0.725d) * city.calcPopulation(hasProject) * newPlayerBonus * grossModifier;

            profitBuffer[ResourceType.MONEY.ordinal()] += income * turns / 12;

            double basePopulation = city.getInfra() * 100;
            double food = (Math.pow(basePopulation, 2)) / 125_000_000 + ((basePopulation) * (1 + Math.log(city.getAgeDays()) / 15d) - basePopulation) / 850;
            profitBuffer[ResourceType.FOOD.ordinal()] -= food * turns / 12d;

            return profitBuffer;
        }

        public static double getDisease(Predicate<Project> hasProject, Function<Building, Integer> getBuildings, long infra_cents, long land_cents, double pollution) {
            int hospitals = getBuildings.apply(Buildings.HOSPITAL);
            double hospitalModifier;
            if (hospitals > 0) {
                double hospitalPct = hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5 : 2.5;
                hospitalModifier = hospitals * hospitalPct;
            } else {
                hospitalModifier = 0;
            }
            double pollutionModifier = pollution * 0.05;
            return Math.max(0, ((0.01 * MathMan.sqr((infra_cents) / (land_cents * 0.01 + 0.001)) - 25) * 0.01d) + (infra_cents * 0.01 * 0.001) - hospitalModifier + pollutionModifier);
        }

        public static double nextCityCost(DBNation nation, int amount) {
            int current = nation.getCities();
            return cityCost(nation, current, current + amount);
        }

        public static double cityCost(DBNation nation, int from, int to) {
            return cityCost(from, to,
                    nation != null && nation.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY,
                    nation != null && nation.hasProject(Projects.URBAN_PLANNING),
                    nation != null && nation.hasProject(Projects.ADVANCED_URBAN_PLANNING),
                    nation != null && nation.hasProject(Projects.METROPOLITAN_PLANNING),
                    nation != null && nation.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY));
        }

        public static int getPopulation(long infra_cents, double crime, double disease, long ageDays) {
            double ageBonus = (1 + Math.log(Math.max(1, ageDays)) * 0.0666666666666666666666666666666);
            double diseaseDeaths = ((disease * 0.01) * infra_cents);
            double crimeDeaths = Math.max((crime * 0.1) * (infra_cents) - 25, 0);

            return (int) Math.round(Math.max(10, ((infra_cents - diseaseDeaths - crimeDeaths) * ageBonus)));
        }

        public static double cityCost(int from, int to, boolean manifestDestiny, boolean cityPlanning, boolean advCityPlanning, boolean metPlanning, boolean govSupportAgency) {
            double total = 0;
            for (int city = Math.max(1, from); city < to; city++) {
                total += nextCityCost(city,
                        manifestDestiny,
                        cityPlanning,
                        advCityPlanning,
                        metPlanning, govSupportAgency);
            }
            return total;
        }

        public static double nextCityCost(int currentCity, boolean manifestDestiny, boolean cityPlanning, boolean advCityPlanning, boolean metPlanning, boolean govSupportAgency) {
            double cost = 50000*Math.pow(currentCity - 1, 3) + 150000 * (currentCity) + 75000;
            if (cityPlanning) {
                cost -= 50000000;
            }
            if (advCityPlanning) {
                cost -= 100000000;
            }
            if (metPlanning) {
                cost -= 150_000_000;
            }
            if (manifestDestiny) {
                double factor = 0.05;
                if (govSupportAgency) factor += 0.025;
                cost *= (1 - factor);
            }
            return Math.max(0, cost);
        }

        public static String getCityUrl(int cityId) {
            return "" + Settings.INSTANCE.PNW_URL() + "/city/id=" + cityId;
        }
    }

    public static List<Integer> getNationsFromTable(String html, int tableIndex) {
        List<Integer> results = new ArrayList<>();

        Document dom = Jsoup.parse(html);
        Elements tables = dom.getElementsByClass("nationtable");
        int finalTableIndex = tableIndex == -1 ? tables.size() - 1 : tableIndex;
        if (finalTableIndex < 0 || finalTableIndex >= tables.size()) {
            throw new IllegalArgumentException("Unable to fetch table" + "\n" + html);
        }
        Element table = tables.get(finalTableIndex);
        Elements rows = table.getElementsByTag("tr");

        List<Element> subList = rows.subList(1, rows.size());

        for (Element element : subList) {
            Elements row = element.getElementsByTag("td");
            String url = row.get(1).selectFirst("a").attr("href");
            int id = Integer.parseInt(url.split("=")[1]);
            results.add(id);
        }

        return results;
    }

    public static String getAlert(Document document) {
        for (Element element : document.getElementsByClass("alert")) {
            if (element.hasClass("alert-info")) continue;
            String text = element.text();
            if (text.startsWith("Player Advertisement by ") || text.contains("Current Market Index")) {
                continue;
            }
            return text;
        }
        return null;
    }
    
    public static Set<Long> expandCoalition(Collection<Long> coalition) {
        Set<Long> extra = new HashSet<>(coalition);
        for (Long id : coalition) {
            GuildDB other;
            if (id > Integer.MAX_VALUE) {
                other = Locutus.imp().getGuildDB(id);
            } else {
                other = Locutus.imp().getGuildDBByAA(id.intValue());
            }
            if (other != null) {
                for (Integer allianceId : other.getAllianceIds()) {
                    extra.add(allianceId.longValue());
                }
                extra.add(other.getGuild().getIdLong());
            }
        }
        return extra;
    }

    public static String getSphereName(int sphereId) {
        GuildDB db = Locutus.imp().getRootCoalitionServer();
        if (db != null) {
            for (String coalition : db.getCoalitionNames()) {
                Coalition namedCoal = Coalition.getOrNull(coalition);
                if (namedCoal != null) continue;
                Set<Long> ids = db.getCoalitionRaw(coalition);
                if (ids.contains((long) sphereId)) {
                    return coalition;
                }
            }
        }
        return "sphere:" + getName(sphereId, true);
    }

    public static Map<DepositType, double[]> sumNationTransactions(GuildDB guildDB, Set<Long> tracked, List<Map.Entry<Integer, Transaction2>> transactionsEntries) {
        return sumNationTransactions(guildDB, tracked, transactionsEntries, false, false, f -> true);
    }

    /**
     * Sum the nation transactions (assumes all transactions are valid and should be added)
     * @param tracked
     * @param transactionsEntries
     * @return
     */
    public static Map<DepositType, double[]> sumNationTransactions(GuildDB guildDB, Set<Long> tracked, List<Map.Entry<Integer, Transaction2>> transactionsEntries, boolean forceIncludeExpired, boolean forceIncludeIgnored, Predicate<Transaction2> filter) {
        long start = System.currentTimeMillis();
        Map<DepositType, double[]> result = new EnumMap<>(DepositType.class);

        boolean allowExpiryDefault = (guildDB.getOrNull(GuildKey.RESOURCE_CONVERSION) == Boolean.TRUE) || guildDB.getIdLong() == 790253684537688086L;
        long allowExpiryCutoff = 1635910300000L;
        Predicate<Transaction2> allowExpiry = transaction2 ->
                allowExpiryDefault || transaction2.tx_datetime > allowExpiryCutoff;
        if (forceIncludeExpired) allowExpiry = f -> false;

        if (tracked == null) {
            tracked = guildDB.getTrackedBanks();
        }

        for (Map.Entry<Integer, Transaction2> entry : transactionsEntries) {
            int sign = entry.getKey();
            Transaction2 record = entry.getValue();
            if (!filter.test(record)) continue;

            boolean isOffshoreSender = (record.sender_type == 2 || record.sender_type == 3) && record.receiver_type == 1;

            boolean allowConversion = record.tx_id != -1 && isOffshoreSender;
            boolean allowArbitraryConversion = record.tx_id != -1 && isOffshoreSender;

            PW.processDeposit(record, guildDB, tracked, sign, result, record.resources, record.note, record.tx_datetime, allowExpiry, allowConversion, allowArbitraryConversion, true, forceIncludeIgnored);
        }
        long diff = System.currentTimeMillis() - start;
        if (diff > 50) {
            System.out.println("Summed " + transactionsEntries.size() + " transactions in " + diff + "ms");
        }
        return result;
    }

    public static boolean aboveMMR(String currentMMR, String requiredMMR) {
        requiredMMR = requiredMMR.toLowerCase();
        for (int i = 0; i < 4; i++) {
            int val1 = currentMMR.charAt(i) - '0';
            char char2 = currentMMR.charAt(i);

            int val2;
            if (char2 == 'X') {
                val2 = 0;
            } else {
                val2 = char2 - '0';
            }
            if (val1 < val2) return false;
        }
        return true;
    }

    public static boolean matchesMMR(String currentMMR, String requiredMMR) {
        requiredMMR = requiredMMR.toLowerCase().replace('X', '.');
        return currentMMR.matches(requiredMMR);
    }

    public static Map<String, String> parseTransferHashNotes(String note) {
        if (note == null || note.isEmpty()) return Collections.emptyMap();

        Map<String, String> result = new LinkedHashMap<>();

        String[] split = note.split("(?=#)");
        for (String filter : split) {
            if (filter.charAt(0) != '#') continue;

            String[] tagSplit = filter.split("[=| ]", 2);
            String tag = tagSplit[0].toLowerCase();
            String value = tagSplit.length == 2 && !tagSplit[1].trim().isEmpty() ? tagSplit[1].split(" ")[0].trim() : null;

            result.put(tag.toLowerCase(), value);
        }
        return result;
    }

    public static void processDeposit(Transaction2 record, GuildDB guildDB, Set<Long> tracked, int sign, Map<DepositType, double[]> result, double[] amount, String note, long date, Predicate<Transaction2> allowExpiry, boolean allowConversion, boolean allowArbitraryConversion, boolean ignoreMarkedDeposits, boolean includeIgnored) {
        /*
        allowConversion sender is nation and alliance has conversion enabled
         */
        if (tracked == null) {
            tracked = guildDB.getTrackedBanks();
        }
        // TODO also update Grant.isNoteFromDeposits if this code is updated

        Map<String, String> notes = parseTransferHashNotes(note);
        DepositType type = DepositType.DEPOSIT;
        double decayFactor = 1;

        for (Map.Entry<String, String> entry : notes.entrySet()) {
            String tag = entry.getKey();
            String value = entry.getValue();

            switch (tag) {
                case "#nation":
                case "#alliance":
                case "#guild":
                case "#account":
                    return;
                case "#ignore":
                    if (includeIgnored) {
                        if (value != null && !value.isEmpty() && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && MathMan.isInteger(value) && !tracked.contains(Long.parseLong(value))) {
                            return;
                        }
                        continue;
                    }
                    return;
                case "#deposit":
                case "#deposits":
                case "#trade":
                case "#trades":
                case "#trading":
                case "#credits":
                case "#buy":
                case "#sell":
                case "#warchest":
                    if (value != null && !value.isEmpty() && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && MathMan.isInteger(value) && !tracked.contains(Long.parseLong(value))) {
                        return;
                    }
                    type = DepositType.DEPOSIT;
                    continue;
                case "#raws":
                case "#raw":
                case "#tax":
                case "#taxes":
                case "#disperse":
                case "#disburse":
                    type = DepositType.TAX;
                    if (value != null && !value.isEmpty() && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && MathMan.isInteger(value) && !tracked.contains(Long.parseLong(value))) {
                        return;
                    }
                    continue;
                default:
                    if (!tag.startsWith("#")) continue;
                    continue;
                case "#loan":
                case "#grant":
                    if (value != null && !value.isEmpty() && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && MathMan.isInteger(value) && !tracked.contains(Long.parseLong(value))) {
                        return;
                    }
                    if (type == DepositType.DEPOSIT) {
                        type = DepositType.LOAN;
                    }
                    continue;
                case "#decay": {
                    if (allowExpiry.test(record) && value != null && !value.isEmpty()) {
                        try {
                            long now = System.currentTimeMillis();
                            long expire = TimeUtil.timeToSec_BugFix1(value, record.tx_datetime) * 1000L;
                            if (now > date + expire) {
                                return;
                            }
                            decayFactor = Math.min(decayFactor, 1 - (now - date) / (double) expire);
                            type = DepositType.GRANT;
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                            type = DepositType.LOAN;
                        }
                    }
                    continue;
                }
                case "#expire": {
                    if (allowExpiry.test(record) && value != null && !value.isEmpty()) {
                        try {
                            long now = System.currentTimeMillis();
                            long expire = TimeUtil.timeToSec_BugFix1(value, record.tx_datetime) * 1000L;
                            if (now > date + expire) {
                                return;
                            }
                            type = DepositType.GRANT;
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                            type = DepositType.LOAN;
                        }
                    }
                    continue;
                }
                case "#cash":
                    if (allowConversion) {
                        Double cashValue = null;
                        if (value != null) {
                            if (allowArbitraryConversion) {
                                cashValue = MathMan.parseDouble(value);
                            }
                        }
                        if (cashValue == null) {
                            if (value != null) {
                                String hash = Hashing.md5()
                                        .hashString(CONVERSION_SECRET + record.tx_id, StandardCharsets.UTF_8)
                                        .toString();

                                if (record.note.contains(hash)) {
                                    cashValue = MathMan.parseDouble(value);
                                }
                            }

                            if (cashValue == null) {
                                long oneWeek = TimeUnit.DAYS.toMillis(7);
                                long start = date - oneWeek;
                                TradeDB tradeDb = Locutus.imp().getTradeManager().getTradeDb();

                                cashValue = 0d;
                                for (int i = 0; i < amount.length; i++) {
                                    ResourceType resource = ResourceType.values[i];
                                    double amt = amount[i];
                                    if (resource == ResourceType.MONEY) {
                                        cashValue += amt;
                                    } else {
                                        if (amt < 1) continue;

                                        List<DBTrade> trades = tradeDb.getTrades(resource, start, date);

                                        Double avg = Locutus.imp().getTradeManager().getAverage(trades).getKey().get(resource);
                                        if (avg != null) {
                                            cashValue += amt * avg;
                                        }
                                    }
                                }
                                {
                                    // set hash
                                    String hash = Hashing.md5()
                                            .hashString(CONVERSION_SECRET + record.tx_id, StandardCharsets.UTF_8)
                                            .toString();
                                    note = note.replaceAll(entry.getKey() + "[^ ]+", "#cash=" + MathMan.format(cashValue));
                                    note += " #" + hash;
                                    record.note = note;
                                    Locutus.imp().getBankDB().addTransaction(record, false);
                                }
                            }
                        }
                        Arrays.fill(amount, 0);
                        amount[0] = cashValue;
                    }
                    continue;
            }
        }
        double[] rss = result.computeIfAbsent(type, f -> ResourceType.getBuffer());
        if (sign == 1 && decayFactor == 1) {
            ResourceType.add(rss, amount);
        } else if (sign == -1 && decayFactor == 1) {
            ResourceType.subtract(rss, amount);
        } else {
            double factor = decayFactor * sign;
            for (int i = 0; i < rss.length; i++) {
                rss[i] += amount[i] * factor;
            }
        }
    }

    private static String CONVERSION_SECRET = "fe51a236d437901bc1650b0187ac3e46";

    public static double WAR_RANGE_MAX_MODIFIER = 2.50;
    public static double WAR_RANGE_MIN_MODIFIER = 0.75;

    /**
     * @param offensive (else defensive)
     * @param isWar (else spy)
     * @param isMin (else max)
     * @return
     */
    public static double getAttackRange(boolean offensive, boolean isWar, boolean isMin, double score) {
        long scoreInt = Math.round(score * 100);
        long range;
        if (offensive) {
            if (isWar) {
                if (isMin) {
                    range = Math.round(scoreInt * WAR_RANGE_MIN_MODIFIER);
                } else {
                    range = Math.round(scoreInt * WAR_RANGE_MAX_MODIFIER);
                }
            } else {
                if (isMin) {
                    range = Math.round(scoreInt * 0.4);
                } else {
                    range = Math.round(scoreInt * 2.5);
                }
            }
        } else {
            if (isWar) {
                if (isMin) {
                    range = Math.round(scoreInt / PW.WAR_RANGE_MAX_MODIFIER);
                } else {
                    range = Math.round(scoreInt / 0.75);
                }
            } else {
                if (isMin) {
                    range = Math.round(scoreInt / 2.5);
                } else {
                    range = Math.round(scoreInt / 0.4);
                }
            }
        }
        return range * 0.01;
    }

    public static Map.Entry<double[], String> createDepositEmbed(GuildDB db, NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuild, Map<DepositType, double[]> categorized, Boolean showCategories, double[] escrowed, long escrowExpire, boolean condenseFormat) {
        boolean withdrawIgnoresGrants = GuildKey.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS.getOrNull(db) == Boolean.TRUE;

        boolean hasEscrowed = (escrowed != null && !ResourceType.isZero(escrowed) && ResourceType.convertedTotal(escrowed) > 0);

        StringBuilder response = new StringBuilder();

        List<String> footers = new ArrayList<>();
        if (!condenseFormat) {
            footers.add("value is based on current market prices");
        }

        double[] balance = ResourceType.getBuffer();
        double[] nonBalance = ResourceType.getBuffer();
        List<String> balanceNotes = new ArrayList<>(Arrays.asList("#deposit", "#tax", "#loan", "#grant", "#expire", "#decay"));

        List<String> excluded = new ArrayList<>(Arrays.asList("/escrow"));
        if (withdrawIgnoresGrants) {
            balanceNotes.remove("#expire");
            balanceNotes.remove("#decay");
            excluded.add("#expire");
            excluded.add("#decay");
        }

        for (Map.Entry<DepositType, double[]> entry : categorized.entrySet()) {
            DepositType type = entry.getKey();
            double[] current = entry.getValue();
            if (!withdrawIgnoresGrants || type != DepositType.GRANT) {
                ResourceType.add(balance, current);
            } else {
                ResourceType.add(nonBalance, current);
            }
        }

        if (showCategories) {
            if (categorized.containsKey(DepositType.DEPOSIT)) {
                response.append("**`#DEPOSIT`** worth $" + MathMan.format(ResourceType.convertedTotal(categorized.get(DepositType.DEPOSIT))));
                response.append("\n```").append(ResourceType.resourcesToString(categorized.get(DepositType.DEPOSIT))).append("```\n");
            }
            if (categorized.containsKey(DepositType.TAX)) {
                response.append("**`#TAX`** worth $" + MathMan.format(ResourceType.convertedTotal(categorized.get(DepositType.TAX))));
                response.append("\n```").append(ResourceType.resourcesToString(categorized.get(DepositType.TAX))).append("```\n");
            } else if (nationOrAllianceOrGuild.isNation()) {
                footers.add("No tax records are added to deposits");
            }
            if (categorized.containsKey(DepositType.LOAN)) {
                response.append("**`#LOAN/#GRANT`** worth $" + MathMan.format(ResourceType.convertedTotal(categorized.get(DepositType.LOAN))));
                response.append("\n```").append(ResourceType.resourcesToString(categorized.get(DepositType.LOAN))).append("```\n");
            }
            if (categorized.containsKey(DepositType.GRANT)) {
                response.append("**`#EXPIRE`** worth $" + MathMan.format(ResourceType.convertedTotal(categorized.get(DepositType.GRANT))));
                response.append("\n```").append(ResourceType.resourcesToString(categorized.get(DepositType.GRANT))).append("```\n");
            }
            if (hasEscrowed) {
                response.append("**" + CM.escrow.withdraw.cmd.toSlashMention() + ":** worth: $" + MathMan.format(ResourceType.convertedTotal(escrowed)));
                if (escrowExpire > 0) {
                    response.append(" expires: " + DiscordUtil.timestamp(escrowExpire, null));
                }
                response.append("\n```").append(ResourceType.resourcesToString(escrowed)).append("``` ");
            }
            if (categorized.size() > 1) {
                response.append("**Balance:** (`" + StringMan.join(balanceNotes, "`|`") + "`) worth: $" + MathMan.format(ResourceType.convertedTotal(balance)) + ")");
                response.append("\n```").append(ResourceType.resourcesToString(balance)).append("``` ");
            }
        } else {
            String prefix = condenseFormat ? "**" : "## ";
            String suffix = condenseFormat ? "**" : "";
            response.append(prefix + "Balance:" + suffix);
            if (condenseFormat) {
                response.append(" worth: `$" + MathMan.format(ResourceType.convertedTotal(balance)) + "`\n");
                response.append("```" + ResourceType.resourcesToString(balance) + "``` ");
            } else {
                response.append("\n").append(ResourceType.resourcesToFancyString(balance)).append("\n");
            }
            response.append("**Includes:** `" + StringMan.join(balanceNotes, "`, `")).append("`\n");
            response.append("**Excludes:** `" + StringMan.join(excluded, "`, `")).append("`\n");

            if (hasEscrowed) {
                response.append("\n" + prefix + CM.escrow.withdraw.cmd.toSlashMention() + ":" + suffix);
                if (condenseFormat) {
                    response.append(" worth: `$" + MathMan.format(ResourceType.convertedTotal(escrowed)) + "`\n");
                    response.append("```" + ResourceType.resourcesToString(escrowed) + "``` ");
                } else {
                    response.append("\n").append(ResourceType.resourcesToFancyString(escrowed)).append("\n");
                }
                if (escrowExpire > 0) {
                    response.append("- expires: " + DiscordUtil.timestamp(escrowExpire, null) + "\n");
                }
            }

            if (!ResourceType.isZero(nonBalance)) {
                response.append("\n" + prefix + "Expiring Debt:" + suffix + "\n");
                response.append("In addition to your balance, you owe the following:\n");
                response.append("```\n" + ResourceType.resourcesToString(nonBalance)).append("```\n- worth: $" + MathMan.format(ResourceType.convertedTotal(nonBalance)) + "\n");
            }
        }
        return Map.entry(balance, response.toString());
    }

    public static Set<DBNation> getNationsSnapshot(Collection<DBNation> nations, String filterStr, Long snapshotDate, Guild guild, boolean includeVM) {
        if (snapshotDate == null) return nations instanceof Set<DBNation> ? (Set<DBNation>) nations : new ObjectOpenHashSet<>(nations);
        DataDumpParser dumper = Locutus.imp().getDataDumper(true);

        long day = TimeUtil.getDay(snapshotDate);
        boolean loadCities = true;
        Predicate<Integer> allowedNations = f -> true;
        Predicate<Integer> allowedAlliances = f -> f > 0;
        NationPlaceholders ph = Locutus.cmd().getV2().getNationPlaceholders();
        ValueStore store = ph.createLocals(guild, null, null);
        Predicate<DBNation> filter = ph.parseFilter(store, filterStr);
        try {   
            Map<Integer, DBNation> nationMap = dumper.getNations(day, loadCities, includeVM, allowedNations, allowedAlliances, filter);
            return new ObjectOpenHashSet<>(nationMap.values());
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static Integer parseAllianceId(String arg) {
        String lower = arg.toLowerCase();
        if (lower.startsWith("aa:")) arg = arg.substring(3);
        else if (lower.startsWith("alliance:")) arg = arg.substring(9);
        if (arg.charAt(0) == '"' && arg.charAt(arg.length() - 1) == '"') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.equalsIgnoreCase("none")) {
            return 0;
        }
        if (arg.startsWith(Settings.INSTANCE.PNW_URL() + "/alliance/id=") || arg.startsWith(Settings.INSTANCE.PNW_URL() + "//alliance/id=") || arg.startsWith("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=")) {
            String[] split = arg.split("=");
            if (split.length == 2) {
                arg = split[1].replaceAll("/", "");
            }
        }
        if (MathMan.isInteger(arg)) {
            try {
                return Integer.parseInt(arg);
            } catch (NumberFormatException e) {}
        }
        {
            DBAlliance alliance = Locutus.imp().getNationDB().getAllianceByName(arg);
            if (alliance != null) {
                return alliance.getAlliance_id();
            }
        }
        if (arg.contains("=HYPERLINK") && arg.contains("alliance/id=")) {
            String regex = "alliance/id=([0-9]+)";
            Matcher m = Pattern.compile(regex).matcher(arg);
            m.find();
            arg = m.group(1);
            return Integer.parseInt(arg);
        }
        return null;
    }

    public static String sharesToString(Map<Exchange, Long> shares) {
        Map<String, String> resultMap = new LinkedHashMap<>();
        for (Map.Entry<Exchange, Long> entry : shares.entrySet()) {
            resultMap.put(entry.getKey().symbol, MathMan.format(entry.getValue() / 100d));
        }
        return StringMan.getString(resultMap);
    }

    public static double[] normalize(double[] resources) {
        return ResourceType.resourcesToArray(PW.normalize(ResourceType.resourcesToMap(resources)));
    }

    public static Map<ResourceType, Double> normalize(Map<ResourceType, Double> resources) {
        resources = new LinkedHashMap<>(resources);
        double total = ResourceType.convertedTotal(resources);
        if (total == 0) return new HashMap<>();
        if (total < 0) {
            return new HashMap<>();
        }

        double negativeTotal = 0;

        Iterator<Map.Entry<ResourceType, Double>> iter = resources.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<ResourceType, Double> entry = iter.next();
            if (entry.getValue() < 0) {
                negativeTotal += Locutus.imp().getTradeManager().getHigh(entry.getKey()) * entry.getValue().doubleValue() * -1;
                iter.remove();
            }
        }
        double postiveTotal = ResourceType.convertedTotal(resources);

        double factor = Math.max(0, Math.min(1, total / postiveTotal));
//            factor = Math.min(factor, postiveTotal / (negativeTotal + postiveTotal));

        for (ResourceType type : ResourceType.values()) {
            Double value = resources.get(type);
            if (value == null || value == 0) continue;

            resources.put(type, value * factor);
        }
        return resources;
    }

    private static double[] LAND_COST_CACHE = null;

    public static Map<ResourceType, Double> adapt(AllianceBankContainer bank) {
        Map<ResourceType, Double> totals = new LinkedHashMap<ResourceType, Double>();
        String json = new Gson().toJson(bank);
        JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
        for (ResourceType type : ResourceType.values) {
            JsonElement amt = obj.get(type.name().toLowerCase());
            if (amt != null) {
                totals.put(type, amt.getAsDouble());
            }
        }
        return totals;
    }

    private static final int[] INFRA_COST_FAST_CACHE;

    static {
        int max = 4000;
        int min = 30;
        int minCents = min * 100;
        INFRA_COST_FAST_CACHE = new int[(max - min) * 100 + 1];
        for (int i = minCents; i <= max * 100; i++) {
            double x = (i * 0.01) - 10d;
            int cost = Math.toIntExact(Math.round(100 * (300d + (Math.pow(x, (2.2d))) * 0.00140845070422535211267605633803)));
            INFRA_COST_FAST_CACHE[i - minCents] = cost;
        }
    }

    public static <T> T withLogin(Callable<T> task, Auth auth) {
        synchronized (auth)
        {
            try {
                auth.login(false);
                return task.call();
            } catch (Exception e) {
                AlertUtil.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

    public static double[] multiply(double[] a, double factor) {
        for (int i = 0; i < a.length; i++) {
            a[i] *= factor;
        }
        return a;
    }

    public static <T extends Number> Map<ResourceType, T> multiply(Map<ResourceType, T> a, T value) {
        HashMap<ResourceType, T> copy = new HashMap<>(a);
        for (Map.Entry<ResourceType, T> entry : copy.entrySet()) {
            entry.setValue(MathMan.multiply(entry.getValue(), value));
        }
        return copy;
    }

    public static String parseDom(Element dom, String clazz) {
        for (Element element : dom.getElementsByClass(clazz)) {
            String text = element.text();
            if (text.startsWith("Player Advertisement by ")) {
                continue;
            }
            return element.text();
        }
        return null;
    }

    public static double[] getRevenue(double[] profitBuffer, int turns, DBNation nation, Collection<JavaCity> cities, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean noFood, boolean noPower, double treasureBonus) {
        double rads = nation.getRads();
        boolean atWar = nation.getNumWars() > 0;
        long date = -1L;
        return getRevenue(profitBuffer, turns, date, nation, cities, militaryUpkeep, tradeBonus, bonus, noFood, noPower, rads, atWar, treasureBonus);
    }

    public static double[] getRevenue(double[] profitBuffer, int turns, long date, DBNation nation, Collection<JavaCity> cities, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean noFood, boolean noPower, double rads, boolean atWar, double treasureBonus) {
        if (profitBuffer == null) profitBuffer = new double[ResourceType.values.length];

        Continent continent = nation.getContinent();
        double grossModifier = nation.getGrossModifier(noFood) + treasureBonus;
        int numCities = bonus ? nation.getCities() : 21;

        // Project revenue
//        if (checkRpc && nation.getCities() <= 15 && nation.hasProject(Projects.ACTIVITY_CENTER)) {
////            for (ResourceType type : ResourceType.values) {
////                if (type.isRaw() && type.getBuilding().canBuild(nation.getContinent())) {
////                    // profitBuffer[type.ordinal()] += turns * (Math.min(nation.getCities(), 10));
////                }
////            }
//        }

        // city revenue
        for (JavaCity build : cities) {
            profitBuffer = build.profit(continent, rads, date, nation::hasProject, profitBuffer, numCities, grossModifier, noPower, 12);
        }

        // trade revenue
        if (tradeBonus) {
            profitBuffer[0] += nation.getColor().getTurnBonus() * turns * grossModifier;
        }

        // Add military upkeep
        if (militaryUpkeep && !nation.hasUnsetMil()) {
            double factor = nation.getMilitaryUpkeepFactor();

            for (MilitaryUnit unit : MilitaryUnit.values) {
                int amt = nation.getUnits(unit);
                if (amt == 0) continue;

                double[] upkeep = unit.getUpkeep(atWar);
                for (int i = 0; i < upkeep.length; i++) {
                    double value = upkeep[i];
                    if (value != 0) {
                        profitBuffer[i] -= value * amt * factor * turns / 12;
                    }
                }
            }
        }

        return profitBuffer;
    }

    public static String getMarkdownUrl(int nationId, boolean isAA) {
        return MarkupUtil.markdownUrl(PW.getName(nationId, isAA), "<" + PW.getUrl(nationId, isAA) + ">");
    }

    public static int parseTaxId(String url) {
        String regex = "tax_id[=:]([0-9]+)";
        Matcher matcher = Pattern.compile(regex).matcher(url.toLowerCase(Locale.ROOT));
        if (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            return id;
        }
        throw new IllegalArgumentException("Not a valid tax url: `" + url + "`");
    }
    public static String getName(long nationOrAllianceId, boolean isAA) {
        if (isAA) {
            String name = Locutus.imp().getNationDB().getAllianceName((int) nationOrAllianceId);
            return name != null ? name : nationOrAllianceId + "";
        } else if (Math.abs(nationOrAllianceId) < Integer.MAX_VALUE) {
            DBNation nation = Locutus.imp().getNationDB().getNation((int) nationOrAllianceId);
            return nation != null ? nation.getNation() : nationOrAllianceId + "";
        } else {
            Guild guild = Locutus.imp().getDiscordApi().getGuildById(Math.abs(nationOrAllianceId));
            return guild != null ? guild.getName() : nationOrAllianceId + "";
        }
    }

    public static String getBBUrl(int nationOrAllianceId, boolean isAA) {
        String type;
        String name;
        if (isAA) {
            type = "alliance";
            name = Locutus.imp().getNationDB().getAllianceName(nationOrAllianceId);
            name = name != null ? name : nationOrAllianceId + "";
        } else {
            type = "nation";
            DBNation nation = Locutus.imp().getNationDB().getNation(nationOrAllianceId);
            name = nation != null ? nation.getNation() : nationOrAllianceId + "";
        }
        String url = "" + Settings.INSTANCE.PNW_URL() + "/" + type + "/id=" + nationOrAllianceId;
        return String.format("[%s](%s)", name, url);
    }

    public static String getUrl(int nationOrAllianceId, boolean isAA) {
        String type;
        String name;
        if (isAA) {
            type = "alliance";
            name = Locutus.imp().getNationDB().getAllianceName(nationOrAllianceId);
            name = name != null ? name : nationOrAllianceId + "";
        } else {
            type = "nation";
            DBNation nation = Locutus.imp().getNationDB().getNation(nationOrAllianceId);
            name = nation != null ? nation.getNation() : nationOrAllianceId + "";
        }
        return "" + Settings.INSTANCE.PNW_URL() + "/" + type + "/id=" + nationOrAllianceId;
    }

    public static String getNationUrl(int nationId) {
        return "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nationId;
    }

    public static String getAllianceUrl(int cityId) {
        return "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + cityId;
    }

    public static String getTradeUrl(ResourceType type, boolean isBuy) {
        String url = "https://politicsandwar.com/index.php?id=90&display=world&resource1=%s&buysell=" + (isBuy ? "buy" : "sell") + "&ob=price&od=DEF";
        return String.format(url, type.name().toLowerCase());
    }

    public static BiFunction<Integer, Integer, Integer> getIsNationsInCityRange(Collection<DBNation> attackers) {
        int[] cityRange = new int[50];
        for (DBNation attacker : attackers) {
            cityRange[attacker.getCities()]++;
        }
        int total = 0;
        for (int i = 0; i < cityRange.length; i++) {
            total += cityRange[i];
            cityRange[i] = total;
        }
        return new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer min, Integer max) {
                int minVal = min == 0 ? 0 : cityRange[Math.min(cityRange.length - 1, min - 1)];
                int maxVal = cityRange[Math.min(cityRange.length - 1, max)];
                return maxVal - minVal;
            }
        };
    }

    public static BiFunction<Double, Double, Integer> getIsNationsInScoreRange(Collection<DBNation> attackers) {
        int minScore = Integer.MAX_VALUE;
        int maxScore = 0;
        for (DBNation attacker : attackers) {
            minScore = (int) Math.min(minScore, attacker.getScore() * 0.75);
            maxScore = (int) Math.max(maxScore, attacker.getScore() / 0.75);
        }
        int[] scoreRange = new int[maxScore + 1];
        for (DBNation attacker : attackers) {
            scoreRange[(int) attacker.getScore()]++;
        }
        int total = 0;
        for (int i = 0; i < scoreRange.length; i++) {
            total += scoreRange[i];
            scoreRange[i] = total;
        }
        return new BiFunction<Double, Double, Integer>() {
            @Override
            public Integer apply(Double min, Double max) {
                max = Math.min(scoreRange.length - 1, max);
                min = Math.min(scoreRange.length - 1, min);
                return scoreRange[(int) Math.ceil(max)] - scoreRange[min.intValue()];
            }
        };
    }

    public static BiFunction<Double, Double, Double> getXInRange(Collection<DBNation> attackers, Function<DBNation, Double> valueFunc) {
        int minScore = Integer.MAX_VALUE;
        int maxScore = 0;
        for (DBNation attacker : attackers) {
            minScore = (int) Math.min(minScore, attacker.getScore() * 0.75);
            maxScore = (int) Math.max(maxScore, attacker.getScore() / 0.75);
        }
        double[] scoreRange = new double[maxScore + 1];
        for (DBNation attacker : attackers) {
            scoreRange[(int) attacker.getScore()] += valueFunc.apply(attacker);
        }
        int total = 0;
        for (int i = 0; i < scoreRange.length; i++) {
            total += scoreRange[i];
            scoreRange[i] = total;
        }
        return new BiFunction<Double, Double, Double>() {
            @Override
            public Double apply(Double min, Double max) {
                max = Math.min(scoreRange.length - 1, max);
                return scoreRange[(int) Math.ceil(max)] - scoreRange[min.intValue()];
            }
        };
    }

    public static BiFunction<Double, Double, Integer> getIsNationsInSpyRange(Collection<DBNation> attackers) {
        int minScore = Integer.MAX_VALUE;
        int maxScore = 0;
        for (DBNation attacker : attackers) {
            minScore = (int) Math.min(minScore, attacker.getScore() * 0.4);
            maxScore = (int) Math.max(maxScore, attacker.getScore() * 1.5);
        }
        int[] scoreRange = new int[maxScore + 1];
        for (DBNation attacker : attackers) {
            scoreRange[(int) attacker.getScore()]++;
        }
        int total = 0;
        for (int i = 0; i < scoreRange.length; i++) {
            total += scoreRange[i];
            scoreRange[i] = total;
        }
        return new BiFunction<Double, Double, Integer>() {
            @Override
            public Integer apply(Double min, Double max) {
                int minVal = min == 0 ? 0 : scoreRange[Math.min(scoreRange.length - 1, min.intValue() - 1)];
                int maxVal = scoreRange[Math.min(scoreRange.length - 1, max.intValue())];
                return maxVal - minVal;
            }
        };
    }

    public static String getPostScript(String name, boolean nation, Map<ResourceType, Double> rss, String note) {
        return ResourceType.resourcesToJson(name, nation, rss, note);
    }

    public static double getOdds(double attStrength, double defStrength, int success) {
        attStrength = Math.pow(attStrength, 0.75);
        defStrength = Math.pow(defStrength, 0.75);

        double a1 = attStrength * 0.4;
        double a2 = attStrength;
        double b1 = defStrength * 0.4;
        double b2 = defStrength;

        // Skip formula for common cases (for performance)
        if (attStrength <= 0) return 0;
        if (defStrength * 2.5 <= attStrength) return success == 3 ? 1 : 0;
        if (a2 <= b1 || b2 <= a1) return 0;

        double sampleSpace = (a2 - a1) * (b2 - b1);
        double overlap = Math.min(a2, b2) - Math.max(a1, b1);
        double p = (overlap * overlap * 0.5) / sampleSpace;
        if (attStrength > defStrength) p = 1 - p;

        if (p <= 0) return 0;
        if (p >= 1) return 1;

        int k = success;
        int n = 3;

        double odds = Math.pow(p, k) * Math.pow(1 - p, n - k);
        double npr = MathMan.factorial(n) / (double) (MathMan.factorial(k) * MathMan.factorial(n - k));
        return odds * npr;
    }


    public static Set<Integer> parseAlliances(GuildDB db, String arg) {
        Set<Integer> aaIds = new LinkedHashSet<>();
        for (String allianceName : arg.split(",")) {
            Set<Integer> coalition = db == null ? Collections.emptySet() : db.getCoalition(allianceName);
            if (!coalition.isEmpty()) aaIds.addAll(coalition);
            else {
                Integer aaId = PW.parseAllianceId(allianceName);
                if (aaId == null) throw new IllegalArgumentException("Unknown alliance: `" + allianceName + "`");
                aaIds.add(aaId);
            }
        }
        return aaIds;
    }

    public static Map<MilitaryUnit, Long> parseUnits(String arg) {
        arg = arg.trim();
        if (!arg.contains(":") && !arg.contains("=")) arg = arg.replaceAll("[ ]+", ":");
        arg = arg.replace(" ", "").replace('=', ':').replaceAll("([0-9]),([0-9])", "$1$2").toUpperCase();
        for (MilitaryUnit unit : MilitaryUnit.values()) {
            String name = unit.getName();
            if (name == null || name.equalsIgnoreCase(unit.name())) continue;
            arg = arg.replace(name.toUpperCase() + ":", unit.name() + ":");
        }

        double sign = 1;
        if (arg.charAt(0) == '-') {
            sign = -1;
            arg = arg.substring(1);
        }
        int preMultiply = arg.indexOf("*{");
        int postMultiply = arg.indexOf("}*");
        if (preMultiply != -1) {
            String[] split = arg.split("\\*\\{", 2);
            arg = "{" + split[1];
            sign *= MathMan.parseDouble(split[0]);
        }
        if (postMultiply != -1) {
            String[] split = arg.split("\\}\\*", 2);
            arg = split[0] + "}";
            sign *= MathMan.parseDouble(split[1]);
        }

        Type type = new TypeToken<Map<MilitaryUnit, Long>>() {}.getType();
        if (arg.charAt(0) != '{' && arg.charAt(arg.length() - 1) != '}') {
            arg = "{" + arg + "}";
        }
        Map<MilitaryUnit, Long> result = new Gson().fromJson(arg, type);
        if (result.containsKey(null)) {
            throw new IllegalArgumentException("Invalid resource type specified in map: `" + arg + "`");
        }
        if (sign != 1) {
            for (Map.Entry<MilitaryUnit, Long> entry : result.entrySet()) {
                entry.setValue((long) (entry.getValue() * sign));
            }
        }
        return result;
    }

    public static Map<String, String> parseMap(String arg) {
        arg = arg.trim();
        if (!arg.contains(":") && !arg.contains("=")) arg = arg.replaceAll("[ ]+", ":");
        arg = arg.replace(" ", "").replace('=', ':');

        Type type = new TypeToken<Map<String, String>>() {}.getType();
        if (arg.charAt(0) != '{' && arg.charAt(arg.length() - 1) != '}') {
            arg = "{" + arg + "}";
        }
        Map<String, String> result = new Gson().fromJson(arg, type);
        if (result.containsKey(null)) {
            throw new IllegalArgumentException("Invalid type specified in map: `" + arg + "`");
        }
        return result;
    }

    public static double[] capManuFromRaws(double[] revenue, double[] totalRss) {
        for (ResourceType type : ResourceType.values) {
            double amt = revenue[type.ordinal()];
            if (amt > 0 && type.isManufactured()) {
                double required = amt * type.getBaseInput();
                for (ResourceType input : type.getInputs()) {
                    double inputAmt = totalRss[input.ordinal()];
                    double revenueAmt = revenue[input.ordinal()];
                    if (revenueAmt > -required) {
                        inputAmt += revenueAmt + required;
                    }
                    double cap = inputAmt / type.getBaseInput();
                    if (amt > cap) {
                        revenue[type.ordinal()] = cap;
                    }
                }
            }
        }
        return revenue;
    }

    public static String getTaxUrl(int taxId) {
        return String.format("" + Settings.INSTANCE.PNW_URL() + "/index.php?id=15&tax_id=%s", taxId);
    }

}