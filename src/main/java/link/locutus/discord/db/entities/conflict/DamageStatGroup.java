package link.locutus.discord.db.entities.conflict;

import com.google.gson.JsonArray;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.util.PnwUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

import static link.locutus.discord.db.entities.conflict.ConflictColumn.header;
import static link.locutus.discord.db.entities.conflict.ConflictColumn.ranking;

public class DamageStatGroup {
    public char totalWars;
    public char activeWars;
    public int attacks = 0;
    public char warsWon;
    public char warsLost;
    public char warsExpired;
    public char warsPeaced;
    public final char[] attackTypes = new char[AttackType.values.length];
    public final char[] attackSubTypes = new char[AttackTypeSubCategory.values.length];
    public final char[] successTypes = new char[SuccessType.values.length];
    public final char[] warTypes = new char[WarType.values.length];

    public final double[] totalCost = ResourceType.getBuffer();
    public final double[] consumption = ResourceType.getBuffer();
    public final double[] loot = ResourceType.getBuffer();
    public final int[] units = new int[MilitaryUnit.values.length];
    public final char[] buildings = new char[Buildings.values().length];
    private long infraCents = 0;
    public static Map<ConflictColumn, Function<DamageStatGroup, Object>> createRanking() {
        Map<ConflictColumn, Function<DamageStatGroup, Object>> header = createHeader();
        header.entrySet().removeIf(e -> !e.getKey().isRanking());
        return header;
    }

    public void clear() {
        totalWars = 0;
        activeWars = 0;
        attacks = 0;
        warsWon = 0;
        warsLost = 0;
        warsExpired = 0;
        warsPeaced = 0;
        Arrays.fill(attackTypes, (char) 0);
        Arrays.fill(attackSubTypes, (char) 0);
        Arrays.fill(successTypes, (char) 0);
        Arrays.fill(warTypes, (char) 0);
        Arrays.fill(totalCost, 0);
        Arrays.fill(consumption, 0);
        Arrays.fill(loot, 0);
        Arrays.fill(units, 0);
        Arrays.fill(buildings, (char) 0);
        infraCents = 0;
    }
    public static Map<ConflictColumn, Function<DamageStatGroup, Object>> createHeader() {
        Map<ConflictColumn, Function<DamageStatGroup, Object>> map = new Object2ObjectLinkedOpenHashMap<>();
        map.put(ranking("loss_value", false), p -> (long) PnwUtil.convertedTotal(p.totalCost));
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            map.put(header("loss_" + type.name().toLowerCase(), false), p -> (long) p.totalCost[type.ordinal()]);
        }
        map.put(ranking("consume_gas", false), p -> (long) p.consumption[ResourceType.GASOLINE.ordinal()]);
        map.put(ranking("consume_mun", false), p -> (long) p.consumption[ResourceType.MUNITIONS.ordinal()]);
        map.put(ranking("consume_value", false), p -> (long) PnwUtil.convertedTotal(p.consumption));
        map.put(ranking("loot_value", false), p -> (long) PnwUtil.convertedTotal(p.loot));
        for (MilitaryUnit unit : MilitaryUnit.values) {
            if (unit == MilitaryUnit.SPIES || unit == MilitaryUnit.INFRASTRUCTURE || unit == MilitaryUnit.MONEY) continue;
            String name = unit.name().toLowerCase() + "_loss";
            ConflictColumn col = unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE ? header(name, false) : ranking(name, false);
            map.put(col, p -> p.units[unit.ordinal()]);
            map.put(ranking(unit.name().toLowerCase() + "_loss_value", false), p -> (long) (unit.getConvertedCost() * p.units[unit.ordinal()]));
        }
        map.put(ranking("unit_loss_value", false), p -> {
            double total = 0;
            for (int i = 0; i < p.units.length; i++) {
                int amt = p.units[i];
                if (amt > 0) {
                    total += MilitaryUnit.values[i].getConvertedCost() * amt;
                }
            }
            return Math.round(total);
        });
        map.put(ranking("building_loss", false), p -> {
            int total = 0;
            for (char c : p.buildings) {
                total += c;
            }
            return total;
        });
        map.put(ranking("building_loss_value", false), p -> {
            double total = 0;
            for (int i = 0; i < p.buildings.length; i++) {
                int amt = p.buildings[i];
                if (amt > 0) {
                    total += Buildings.get(i).getNMarketCost(amt);
                }
            }
            return Math.round(total);
        });
        map.put(ranking("infra_loss", false), p -> (long) (p.infraCents * 0.01));

        // Counts //

        map.put(ranking("wars", true), p -> (int) p.totalWars);
        map.put(ranking("wars_active", true), p -> (int) p.activeWars);
        map.put(ranking("attacks", true), p -> (int) p.attacks);
        map.put(ranking("wars_won", true), p -> (int) p.warsWon);
        map.put(ranking("wars_lost", true), p -> (int) p.warsLost);
        map.put(ranking("wars_expired", true), p -> (int) p.warsExpired);
        map.put(ranking("wars_peaced", true), p -> (int) p.warsPeaced);
        for (AttackType type : AttackType.values) {
            ConflictColumn col;
            String name = type.name().toLowerCase() + "_attacks";
            if (type == AttackType.PEACE || type == AttackType.VICTORY) {
                continue;
            }
            if (type == AttackType.MISSILE || type == AttackType.NUKE) {
                col = ranking(name, true);
            } else if (type == AttackType.A_LOOT){
                col = header(name, true);
            } else {
                col = ranking(name, true);
            }
            map.put(col, p -> (int) p.attackTypes[type.ordinal()]);
        }
        for (AttackTypeSubCategory type : AttackTypeSubCategory.values) {
            switch (type) {
                case AIRSTRIKE_MONEY:
                case AIRSTRIKE_INFRA:
                case MISSILE:
                case NUKE:
                    continue;
            }
            map.put(header(type.name().toLowerCase() + "_attacks", true), p -> (int) p.attackSubTypes[type.ordinal()]);
        }
        for (SuccessType type : SuccessType.values) {
            map.put(header(type.name().toLowerCase() + "_attacks", true), p -> (int) p.successTypes[type.ordinal()]);
        }
        for (WarType type : WarType.values) {
            if (type == WarType.NUCLEAR) continue;
            map.put(ranking(type.name().toLowerCase() + "_wars", true), p -> (int) p.warTypes[type.ordinal()]);
        }

        return map;
    }

    public void apply(AbstractCursor attack, boolean isAttacker) {
        attack.getLosses(totalCost, isAttacker, true, true, true, true, true);
        attack.getLosses(consumption, isAttacker, false, false, true, false, false);
        attack.getLosses(loot, isAttacker, false, false, false, true, false);
        attack.getUnitLosses(units, isAttacker);
        if (!isAttacker) {
            infraCents += Math.round(attack.getInfra_destroyed_value() * 100);
            attack.addBuildingsDestroyed(buildings);
        }
    }

    public void newWar(DBWar war, boolean isAttacker) {
        totalWars++;
        if (war.isActive()) activeWars++;
        else {
            addWarStatus(war.getStatus(), isAttacker);
        }
        warTypes[war.getWarType().ordinal()]++;
    }

    private void addWarStatus(WarStatus status, boolean isAttacker) {
        switch (status) {
            case DEFENDER_VICTORY -> {
                if (isAttacker) warsLost++;
                else warsWon++;
            }
            case ATTACKER_VICTORY -> {
                if (isAttacker) warsWon++;
                else warsLost++;
            }
            case PEACE -> {
                warsPeaced++;
            }
            case EXPIRED -> {
                warsExpired++;
            }
        }
    }

    public void updateWar(DBWar previous, DBWar current, boolean isAttacker) {
        addWarStatus(current.getStatus(), isAttacker);
        if (previous.isActive() && !current.isActive()) {
            activeWars--;
        }
    }

    public void newAttack(DBWar war, AbstractCursor attack, AttackTypeSubCategory subCategory) {
        attacks++;
        attackTypes[attack.getAttack_type().ordinal()]++;
        if (subCategory != null) {
            attackSubTypes[subCategory.ordinal()]++;
        }
        successTypes[attack.getSuccess().ordinal()]++;
    }

    public double getInfra() {
        return infraCents * 0.01;
    }

    public double getTotalConverted() {
        return PnwUtil.convertedTotal(totalCost);
    }


}
