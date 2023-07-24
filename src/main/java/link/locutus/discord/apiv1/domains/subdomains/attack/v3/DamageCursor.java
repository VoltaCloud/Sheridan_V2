package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import it.unimi.dsi.fastutil.bytes.Byte2ByteArrayMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Map;

public abstract class DamageCursor extends AbstractCursor{

    protected SuccessType success;
    private int city_id;
    private int city_infra_before_cents;
    private int infra_destroyed_cents;
    private Map<Byte, Byte> buildingsDestroyed = new Byte2ByteArrayMap();
    private int num_improvements;


    @Override
    public final SuccessType getSuccess() {
        return success;
    }

    @Override
    public double getInfra_destroyed() {
        return infra_destroyed_cents * 0.01;
    }

    @Override
    public int getImprovements_destroyed() {
        return num_improvements;
    }

    public abstract MilitaryUnit[] getUnits();
    public abstract int getUnitLosses(MilitaryUnit unit, boolean attacker);

    @Override
    public int getAttcas1() {
        MilitaryUnit[] units = getUnits();
        return getUnitLosses(units[0], true);
    }

    @Override
    public int getDefcas1() {
        MilitaryUnit[] units = getUnits();
        return getUnitLosses(units[0], false);
    }

    @Override
    public int getAttcas2() {
        MilitaryUnit[] units = getUnits();
        return units.length > 1 ? getUnitLosses(units[1], true) : 0;
    }

    @Override
    public int getDefcas2() {
        MilitaryUnit[] units = getUnits();
        return units.length > 1 ? getUnitLosses(units[1], false) : 0;
    }

    @Override
    public int getDefcas3() {
        MilitaryUnit[] units = getUnits();
        return units.length > 2 ? getUnitLosses(units[2], false) : 0;
    }

    @Override
    public void addBuildingsDestroyed(int[] destroyedBuffer) {
        if (num_improvements > 0) {
            for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                byte typeId = entry.getKey();
                byte amt = entry.getValue();
                destroyedBuffer[typeId] += amt;
            }
        }
    }

    @Override
    public double getCity_infra_before() {
        return city_infra_before_cents * 0.01;
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
        output.writeBits(success.ordinal(), 2);

        if (success != SuccessType.UTTER_FAILURE) {
            output.writeInt(city_id);
            output.writeVarInt(city_infra_before_cents);
            output.writeVarInt(infra_destroyed_cents);
            output.writeBits(num_improvements, 4);

            // 26 types of buildings (2^5)
            for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                byte typeId = entry.getKey();
                byte amt = entry.getValue();
                for (int i = 0; i < amt; i++) {
                    output.writeBits(typeId, 5);
                }
            }
        }
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        success = SuccessType.values[(int) input.readBits(2)];

        if (success != SuccessType.UTTER_FAILURE) {
            city_id = input.readInt();
            city_infra_before_cents = (int) input.readVarInt();
            infra_destroyed_cents = (int) input.readVarInt();
            num_improvements = (int) input.readBits(4);

            buildingsDestroyed.clear();
            for (int i = 0; i < num_improvements; i++) {
                byte typeId = (byte) input.readBits(5);
                buildingsDestroyed.compute(typeId, (k, v) -> v == null ? (byte) 1 : (byte) (v + 1));
            }
        } else {
            city_id = 0;
            city_infra_before_cents = 0;
            infra_destroyed_cents = 0;
            num_improvements = 0;
            buildingsDestroyed.clear();
        }
    }


    @Override
    public void load(WarAttack attack) {
        super.load(attack);
        success = SuccessType.values[attack.getSuccess()];
        if (getSuccess() != SuccessType.UTTER_FAILURE) {
            city_id = attack.getCity_id();
            city_infra_before_cents = (int) (attack.getCity_infra_before() * 100);
            infra_destroyed_cents = (int) (attack.getInfra_destroyed() * 100);
            num_improvements = 0;
            buildingsDestroyed.clear();
            for (String impName : attack.getImprovements_destroyed()) {
                Building building = Buildings.get(impName);
                num_improvements++;
                buildingsDestroyed.compute((byte) building.ordinal(), (k, v) -> v == null ? (byte) 1 : (byte) (v + 1));
            }
        } else {
            city_id = 0;
            city_infra_before_cents = 0;
            infra_destroyed_cents = 0;
            num_improvements = 0;
            buildingsDestroyed.clear();
        }
    }

    @Override
    public final double[] getLoot() {
        return null;
    }

    @Override
    public final double getLootPercent() {
        return 0;
    }
}
