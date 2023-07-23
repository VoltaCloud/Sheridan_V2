package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.UnitCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Map;

public class NavalCursor extends UnitCursor {
    private SuccessType success;
    private int attcas1;
    private int defcas1;


    @Override
    public AttackType getAttackType() {
        return AttackType.NAVAL;
    }

    @Override
    public SuccessType getSuccess() {
        return success;
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);
        success = SuccessType.values[attack.getSuccess()];
        this.attcas1 = attack.getAtt_ships_lost();
        this.defcas1 = attack.getDef_ships_lost();
    }

    private static final MilitaryUnit[] UNITS = {MilitaryUnit.SHIP};

    @Override
    public MilitaryUnit[] getUnits() {
        return UNITS;
    }

    @Override
    public int getUnitLosses(MilitaryUnit unit, boolean attacker) {
        if (unit == MilitaryUnit.SHIP) {
            return attacker ? attcas1 : defcas1;
        }
        return 0;
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        success = SuccessType.values[(int) input.readBits(2)];

        if (input.readBit()) attcas1 = input.readVarInt();
        else attcas1 = 0;

        if (input.readBit()) defcas1 = input.readVarInt();
        else defcas1 = 0;
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
        // success = 0,1,2,3
        output.writeBits(success.ordinal(), 2);

        output.writeBit(attcas1 > 0);
        if (attcas1 > 0) output.writeVarInt(attcas1);

        output.writeBit(defcas1 > 0);
        if (defcas1 > 0) output.writeVarInt(defcas1);
    }
}
