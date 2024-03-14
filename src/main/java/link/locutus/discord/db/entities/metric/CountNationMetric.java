package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.csv.column.NumberColumn;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CountNationMetric implements IAllianceMetric {
    private final Function<DBNation, Number> countNation;
    private final AllianceMetricMode mode;
    private final Predicate<DBNation> filter;
    private final Function<NationHeader, ? extends NumberColumn<DBNation, ? extends Number>> getHeader;
    private Predicate<Integer> allianceFilter;


    public CountNationMetric(Function<DBNation, Number> countNation, AllianceMetricMode mode) {
        this(countNation, null, mode);
    }

    public CountNationMetric(Function<DBNation, Number> countNation) {
        this(countNation, null, AllianceMetricMode.TOTAL);
    }

    public CountNationMetric(Function<DBNation, Number> countNation, Function<NationHeader, ? extends NumberColumn<DBNation, ? extends Number>> getHeader) {
        this(countNation, getHeader, AllianceMetricMode.TOTAL, f -> true);
    }

    public CountNationMetric(Function<DBNation, Number> countNation, Function<NationHeader, ? extends NumberColumn<DBNation, ? extends Number>> getHeader, AllianceMetricMode mode) {
        this(countNation, getHeader, mode, f -> true);
    }

    public CountNationMetric(Function<DBNation, Number> countNation, Function<NationHeader, ? extends NumberColumn<DBNation, ? extends Number>> getHeader, AllianceMetricMode mode, Predicate<DBNation> filter) {
        this.countNation = countNation;
        this.getHeader = getHeader;
        this.mode = mode;
        this.filter = filter;
    }

    public CountNationMetric allianceFilter(Predicate<Integer> filter) {
        this.allianceFilter = filter;
        return this;
    }

    @Override
    public Double apply(DBAlliance alliance) {
        if (allianceFilter != null && !allianceFilter.test(alliance.getId())) return null;
        double total = 0;
        int nations = 0;
        int cities = 0;
        for (DBNation nation : alliance.getMemberDBNations()) {
            if (filter != null && !filter.test(nation)) continue;
            nations++;
            cities += nation.getCities();
            total += this.countNation.apply(nation).doubleValue();
        }
        if (total == 0) return 0d;
        return switch (mode) {
            case TOTAL -> total;
            case PER_NATION -> total / nations;
            case PER_CITY -> total / cities;
        };
    }

    private final Map<Integer, Double> countByAA = new Int2DoubleOpenHashMap();
    private final Map<Integer, Integer> countAllianceMetricModeByAA = new Int2IntOpenHashMap();

    @Override
    public void setupReaders(IAllianceMetric metric, DataDumpImporter importer) {
        importer.setNationReader(metric, new BiConsumer<Long, NationHeader>() {
            @Override
            public void accept(Long day, NationHeader header) {
                Rank position = header.alliance_position.get();
                if (position.id <= Rank.APPLICANT.id) return;
                int allianceId = header.alliance_id.get();
                if (allianceId == 0) return;
                if (allianceFilter != null && !allianceFilter.test(allianceId)) return;
                Integer vm_turns = header.vm_turns.get();
                if (vm_turns == null || vm_turns > 0) return;
                double amt;
                if (getHeader != null) {
                    amt = ((Number) row.get(getHeader.apply(header), Double::parseDouble)).doubleValue();
                } else {
                    DBNation nation = row.getNation(header, false, true);
                    if (filter != null && !filter.test(nation)) return;
                    amt = countNation.apply(nation).doubleValue();
                }
                countByAA.merge(allianceId, amt, Double::sum);
                switch (mode) {
                    case PER_NATION:
                        countAllianceMetricModeByAA.merge(allianceId, 1, Integer::sum);
                        break;
                    case PER_CITY:
                        int cities = header.cities.get();
                        countAllianceMetricModeByAA.merge(allianceId, cities, Integer::sum);
                        break;
                    case TOTAL:
                        countAllianceMetricModeByAA.put(allianceId, 1);
                }
            }
        });

    }

    @Override
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
        Map<Integer, Double> result = countByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (double) countAllianceMetricModeByAA.get(f.getKey())));
        countByAA.clear();
        countAllianceMetricModeByAA.clear();
        return result;
    }

    @Override
    public List<AllianceMetricValue> getAllValues() {
        return null;
    }
}
