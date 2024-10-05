package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.War;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.apiv1.domains.subdomains.SWarContainer;
import link.locutus.discord.apiv1.enums.WarType;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class DBWar {
    public final int warId;
    public final long nationIdPair;
    public int allianceIdPair;
    private byte warStatusType;
    private final long date;
    private char attDefCities;

    public static final class DBWarKey {
        public final int id;
        public DBWarKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return ((DBWar) o).warId == id;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    public int getTurnsLeft() {
        return (int) (TimeUtil.getTurn() - TimeUtil.getTurn(getDate()) + 60);
    }

    public DBWar(int warId, int attacker_id, int defender_id, int attacker_aa, int defender_aa, WarType warType, WarStatus status, long date, int attCities, int defCities) {
        this.warId = warId;
        this.nationIdPair = MathMan.pairInt(attacker_id, defender_id);
        this.allianceIdPair = MathMan.pairChars((char) attacker_aa, (char) defender_aa);
        this.warStatusType = (byte) (status.ordinal() << 2 | warType.ordinal());
        this.date = date;
        if (attCities == 0) attCities = defCities;
        if (defCities == 0) defCities = attCities;
        this.attDefCities = (char) (attCities | (defCities << 8));
    }

    public int getAttCities() {
        return attDefCities & 0xFF;
    }

    public int getDefCities() {
        return attDefCities >> 8;
    }

    public int getCities(boolean isAttacker) {
        return isAttacker ? getAttCities() : getDefCities();
    }

    public void setStatus(WarStatus status) {
        warStatusType = (byte) (status.ordinal() << 2 | getWarType().ordinal());
    }

    public void setAttacker_aa(int allianceId) {
        allianceIdPair = MathMan.pairChars((char) allianceId, (char) getDefender_aa());
    }

    public void setDefender_aa(int allianceId) {
        allianceIdPair = MathMan.pairChars((char) getAttacker_aa(), (char) allianceId);
    }

    public int getAttacker_id() {
        return MathMan.unpairIntX(nationIdPair);
    }

    public int getDefender_id() {
        return MathMan.unpairIntY(nationIdPair);
    }

    public int getAttacker_aa() {
        return MathMan.getXFromInt(allianceIdPair);
    }

    public int getDefender_aa() {
        return MathMan.getYFromInt(allianceIdPair);
    }

    public WarType getWarType() {
        return WarType.values[warStatusType & 0b11];
    }

    public WarStatus getStatus() {
        return WarStatus.values[warStatusType >> 2];
    }

    private static WarStatus getStatus(War war) {
        if (war.getWinner_id() != null && war.getWinner_id() > 0) {
            if (Objects.equals(war.getWinner_id(), war.getAtt_id())) {
                return WarStatus.ATTACKER_VICTORY;
            } else {
                return WarStatus.DEFENDER_VICTORY;
            }
        } else if (war.getAtt_peace() && war.getDef_peace()) {
            return WarStatus.PEACE;
        } else if (TimeUtil.getTurn() - TimeUtil.getTurn(war.getDate().toEpochMilli()) >= 60) {
            return WarStatus.EXPIRED;
        } else if (war.getAtt_peace()) {
            return WarStatus.ATTACKER_OFFERED_PEACE;
        } else if (war.getDef_peace()) {
            return WarStatus.DEFENDER_OFFERED_PEACE;
        } else {
            return WarStatus.ACTIVE;
        }
    }

    public DBWar(War war) {
        this(war, true);
    }

    public DBWar(War war, boolean cities) {
         this(war.getId(), war.getAtt_id(), war.getDef_id(), war.getAtt_alliance_id(), war.getDef_alliance_id(), WarType.fromV3(war.getWar_type()), getStatus(war), war.getDate().toEpochMilli(), cities ? getCities(war.getAtt_id()) : 0, cities ? getCities(war.getDef_id()) : 0);
    }

    private static int getAA(String aaStr) {
        DBAlliance aa = Locutus.imp().getNationDB().getAllianceByName(aaStr);
        return aa == null ? 0 : aa.getAlliance_id();
    }

    public DBWar(SWarContainer c) {
        this(c.getWarID(), c.getAttackerID(), c.getDefenderID(), getAA(c.getAttackerAA()), getAA(c.getDefenderAA()), WarType.parse(c.getWarType()), WarStatus.parse(c.getStatus()), TimeUtil.parseDate(TimeUtil.WAR_FORMAT, c.getDate()), getCities(c.getAttackerID()), getCities(c.getDefenderID()));
    }

    public DBWar(DBWar other) {
        this.warId = other.warId;
        this.nationIdPair = other.nationIdPair;
        this.allianceIdPair = other.allianceIdPair;
        this.warStatusType = other.warStatusType;
        this.date = other.getDate();
        this.attDefCities = other.attDefCities;
    }

    private static int getCities(int nationId) {
        Locutus lc = Locutus.imp();
        if (lc == null) return 0;
        NationDB natDb = lc.getNationDB();
        if (natDb == null) return 0;
        DBNation nation = natDb.getNation(nationId);
        return nation == null ? 0 : nation.getCities();
    }

    public String getWarInfoEmbed(boolean isAttacker, boolean loot) {
        return getWarInfoEmbed(isAttacker, loot, true);
    }

    public String getWarInfoEmbed(boolean isAttacker, boolean loot, boolean title) {
        StringBuilder body = new StringBuilder();

        DBNation enemy = getNation(!isAttacker);
        if (enemy == null) return body.toString();
        WarCard card = new WarCard(this, false);

        if (title) {
            String typeStr = isAttacker ? "\uD83D\uDD2A" : "\uD83D\uDEE1";
            body.append(typeStr);
            body.append("`" + enemy.getNation() + "`")
                    .append(" | ").append(enemy.getAllianceName()).append(":");
        }
        if (loot && isAttacker) {
            double lootValue = enemy.lootTotal();
            body.append("$" + MathMan.format((int) lootValue));
        }
        body.append(enemy.toCityMilMarkdown());

        String attStr = card.condensedSubInfo(isAttacker);
        String defStr = card.condensedSubInfo(!isAttacker);
        body.append("```" + attStr + "|" + defStr + "``` ");
        body.append(StringMan.repeat("\u2501", 10) + "\n");
        return body.toString();
    }

    public List<AbstractCursor> getAttacks2() {
        return getAttacks2(true);
    }

    public List<AbstractCursor> getAttacks2(boolean loadInactive) {
        return Locutus.imp().getWarDb().getAttacksByWarId2(this, loadInactive);
    }

    public List<AbstractCursor> getAttacks(Collection<AbstractCursor> attacks) {
        List<AbstractCursor> result = new ArrayList<>();
        for (AbstractCursor attack : attacks) {
            if (attack.getWar_id() == warId) result.add(attack);
        }
        return result;
    }

    /**
     * Resistance
     * @param attacks
     * @return [attacker, defender]
     */
    public Map.Entry<Integer, Integer> getResistance(List<AbstractCursor> attacks) {
        int[] result = {100, 100};
        for (AbstractCursor attack : attacks) {
            int resI = attack.getAttacker_id() == getAttacker_id() ? 1 : 0;
            int damage = attack.getResistance();
            result[resI] = Math.max(0, result[resI] - damage);
        }
        return new AbstractMap.SimpleEntry<>(result[0], result[1]);
    }

    public Map.Entry<Integer, Integer> getMap(List<AbstractCursor> attacks) {
        DBNation attacker = Locutus.imp().getNationDB().getNation(getAttacker_id());
        DBNation defender = Locutus.imp().getNationDB().getNation(getDefender_id());

        if (attacker == null || defender == null) {
            return new AbstractMap.SimpleEntry<>(0, 0);
        }

        long turnStart = TimeUtil.getTurn(getDate());
        long turnEnd = TimeUtil.getTurn();
        long[] attTurnMap = {turnStart, 6};
        long[] defTurnMap = {turnStart, 6};
        switch (defender.getWarPolicy()) {
            case FORTRESS:
                attTurnMap[1]--;
                defTurnMap[1]--;
                break;
            case BLITZKRIEG:
                attTurnMap[1]++;
        }

        int wastedMap = 0;
        boolean fortified = false;

        outer:
        for (AbstractCursor attack : attacks) {
            long[] turnMap;
            if (attack.getAttacker_id() == getAttacker_id()) {
                turnMap = attTurnMap;
            } else {
                turnMap = defTurnMap;
            }
            long lastTurn = turnMap[0];
            long turn = TimeUtil.getTurn(attack.getDate());
            int mapUsed;
            switch (attack.getAttack_type()) {
                case FORTIFY:
                    if (attack.getAttacker_id() == getAttacker_id()) {
                        fortified = true;
                    }
                case GROUND:
                    mapUsed = 3;
                    break;

                case AIRSTRIKE_INFRA:
                case AIRSTRIKE_SOLDIER:
                case AIRSTRIKE_TANK:
                case AIRSTRIKE_MONEY:
                case AIRSTRIKE_SHIP:
                case AIRSTRIKE_AIRCRAFT:
                case NAVAL:
                    mapUsed = 4;
                    break;
                case MISSILE:
                    mapUsed = 8;
                    break;
                case NUKE:
                    mapUsed = 12;
                    break;
                case PEACE:
                    continue;
                default:
                    break outer;
            }
            turnMap[1] += (turn - lastTurn);
            if (turnMap[1] > 12) {
                wastedMap += (int) (turnMap[1] - 12);
                turnMap[1] = 12;
            }
            turnMap[1] -= mapUsed;
            turnMap[0] = turn;
        }
        attTurnMap[1] = Math.min(12, attTurnMap[1] + turnEnd - attTurnMap[0]);
        defTurnMap[1] = Math.min(12, defTurnMap[1] + turnEnd - defTurnMap[0]);
        return new AbstractMap.SimpleEntry<>((int) attTurnMap[1], (int) defTurnMap[1]);
    }

    public boolean isActive() {
        switch (getStatus()) {
            case ACTIVE:
            case DEFENDER_OFFERED_PEACE:
            case ATTACKER_OFFERED_PEACE:
                return true;
            default:
            case DEFENDER_VICTORY:
            case ATTACKER_VICTORY:
            case PEACE:
            case EXPIRED:
                return false;
        }
    }

    public int getWarId() {
        return warId;
    }

    public long getDate() {
        return date;
    }

    public String toUrl() {
        return Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war=" + warId;
    }

    @Override
    public String toString() {
        return "{" +
                "warId=" + warId +
                ", attacker_id=" + getAttacker_id() +
                ", defender_id=" + getDefender_id() +
                ", attacker_aa=" + getAttacker_aa() +
                ", defender_aa=" + getDefender_aa() +
                ", warType=" + getWarType() +
                ", status=" + getStatus() +
                ", date=" + getDate() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o instanceof ArrayUtil.IntKey key) {
            return key.key == warId;
        }
        if (getClass() != o.getClass()) return false;

        DBWar dbWar = (DBWar) o;
        return dbWar.warId == warId;
    }

    @Override
    public int hashCode() {
        return warId;
    }

    public Boolean isAttacker(DBNation nation) {
        if (nation.getNation_id() == getAttacker_id()) return true;
        if (nation.getNation_id() == getDefender_id()) return false;
        return null;
    }

    public DBNation getNation(Boolean attacker) {
        if (attacker == null) return null;
        return Locutus.imp().getNationDB().getNation(attacker ? getAttacker_id() : getDefender_id());
    }

    public CounterStat getCounterStat() {
        return Locutus.imp().getWarDb().getCounterStat(this);
    }

    public WarAttackParser toParser(boolean primary) {
        return new WarAttackParser(this, primary);
    }

    public AttackCost toCost() {
        return toCost(getAttacks2(), true, true, true, true, true);
    }

    public AttackCost toCost(List<AbstractCursor> attacks, boolean buildings, boolean ids, boolean victories, boolean wars, boolean inclAttacks) {
        String nameA = PW.getName(getAttacker_id(), false);
        String nameB = PW.getName(getDefender_id(), false);
        Function<AbstractCursor, Boolean> isPrimary = a -> a.getAttacker_id() == getAttacker_id();
        Function<AbstractCursor, Boolean> isSecondary = b -> b.getAttacker_id() == getDefender_id();
        AttackCost cost = new AttackCost(nameA, nameB, buildings, ids, victories, wars, inclAttacks);
        cost.addCost(attacks, isPrimary, isSecondary);
        return cost;
    }

    public String getNationHtmlUrl(boolean attacker) {
        int id = attacker ? getAttacker_id() : getDefender_id();
        return MarkupUtil.htmlUrl(PW.getName(id, false), PW.getNationUrl(id));
    }

    public String getAllianceHtmlUrl(boolean attacker) {
        int id = attacker ? getAttacker_aa() : getDefender_aa();
        return MarkupUtil.htmlUrl(PW.getName(id, true), PW.getAllianceUrl(id));
    }

    public int getNationId(int allianceId) {
        if (getAttacker_aa() == allianceId) return getAttacker_id();
        if (getDefender_aa() == allianceId) return getDefender_id();
        return 0;
    }
    public boolean isAttacker(int nation_id) {
        return this.getAttacker_id() == nation_id;
    }

    public int getAllianceId(int attacker_nation_id) {
        return attacker_nation_id == this.getAttacker_id() ? this.getAttacker_aa() : (attacker_nation_id == this.getDefender_id() ? this.getDefender_aa() : 0);
    }

    public long possibleEndDate() {
        return TimeUtil.getTimeFromTurn(TimeUtil.getTurn(getDate()) + 60);
    }

    public int getControl(Predicate<AttackType> attackType, MilitaryUnit... units) {
        long acDate = 0;
        int acNation = 0;
        for (AbstractCursor attack : getAttacks2(false)) {
            if (attackType.test(attack.getAttack_type())) {
                switch (attack.getSuccess()) {
                    case PYRRHIC_VICTORY, MODERATE_SUCCESS -> {
                        if (acNation != attack.getAttacker_id()) {
                            acNation = 0;
                            acDate = 0;
                        }
                    }
                    case IMMENSE_TRIUMPH -> {
                        acNation = attack.getAttacker_id();
                        acDate = attack.getDate();
                    }
                }
            }
        }
        if (acNation != 0) {
            for (AbstractCursor attack : Locutus.imp().getWarDb().getAttacks(acNation, acDate)) {
                if (attackType.test(attack.getAttack_type()) &&
                        attack.getSuccess() == SuccessType.IMMENSE_TRIUMPH &&
                        attack.getDefender_id() == acNation &&
                        attack.getDate() > acDate) {
                    return 0;
                }

            }
        }
        DBNation nation = DBNation.getById(acNation);
        if (nation != null) {
            boolean hasUnits = false;
            for (MilitaryUnit unit : units) {
                if (nation.getUnits(unit) > 0) {
                    hasUnits = true;
                    break;
                }
            }
            if (!hasUnits) return 0;
        }
        return acNation;
    }

    public int getGroundControl() {
        return getControl(f -> f == AttackType.GROUND, MilitaryUnit.SOLDIER, MilitaryUnit.TANK);
    }

    public int getBlockader() {
        return getControl(f -> f == AttackType.NAVAL, MilitaryUnit.SHIP);
    }

    public int getAirControl() {
        return getControl(f -> switch (f) {
            case AIRSTRIKE_INFRA -> true;
            case AIRSTRIKE_SOLDIER -> true;
            case AIRSTRIKE_TANK -> true;
            case AIRSTRIKE_MONEY -> true;
            case AIRSTRIKE_SHIP -> true;
            case AIRSTRIKE_AIRCRAFT -> true;
            default -> false;
        }, MilitaryUnit.AIRCRAFT);
    }

    public void setAttCities(int attCities) {
        this.attDefCities = (char) (attCities | (getDefCities() << 8));
    }

    public void setDefCities(int defCities) {
        this.attDefCities = (char) (getAttCities() | (defCities << 8));
    }
}