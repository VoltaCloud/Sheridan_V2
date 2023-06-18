package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;

import java.util.function.Supplier;

public abstract class AirstrikeUnit extends AbstractAttack{
    protected AirstrikeUnit(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    public static AirstrikeUnit create(int id, long date, boolean isAttackerIdGreater,
                                       AttackType type,
                                       SuccessType success,
                                       Supplier<Integer> attcas1_s,
                                       int defcas1,
                                       Supplier<Integer> defCas2_s,
                                       Supplier<Double> city_infra_before_s,
                                       Supplier<Double> infra_destroyed_s,
                                       Supplier<Integer> improvements_destroyed_s,
                                       double att_gas_used,
                                       double att_mun_used,
                                       double def_gas_used,
                                       Supplier<Double> def_mun_used_s) {
        int improvements_destroyed = success == SuccessType.UTTER_FAILURE ? 0 : improvements_destroyed_s.get();
        if (improvements_destroyed == 0) {
            if (success == SuccessType.IMMENSE_TRIUMPH && def_gas_used == 0 && defcas1 == 0) {
                return new AirstrikeUnitIt_0_NoImp(id, date, isAttackerIdGreater, city_infra_before_s.get(), infra_destroyed_s.get(), att_gas_used, att_mun_used, defCas2_s.get(), type);
            }
            double def_mun_used = def_gas_used == 0 ? 0 : def_mun_used_s.get();
            double city_infra_before = success == SuccessType.UTTER_FAILURE ? 0 : city_infra_before_s.get();
            double infra_destroyed = success == SuccessType.UTTER_FAILURE ? 0 : infra_destroyed_s.get();
            int attcas1 = def_gas_used == 0 ? 0 : attcas1_s.get();
            int defCas2 = success == SuccessType.UTTER_FAILURE ? 0 : defCas2_s.get();
            return new AirstrikeUnitAny_Any_NoImp(id, date, isAttackerIdGreater, success, attcas1, defcas1, city_infra_before, infra_destroyed, att_gas_used, att_mun_used, def_gas_used, def_mun_used, defCas2, type);
        } else {
            if (success == SuccessType.IMMENSE_TRIUMPH && def_gas_used == 0 && defcas1 == 0) {
                return new AirstrikeUnitIt_0_Imp(id, date, isAttackerIdGreater, city_infra_before_s.get(), infra_destroyed_s.get(), att_gas_used, att_mun_used, defCas2_s.get(), type);
            }
            double def_mun_used = def_gas_used == 0 ? 0 : def_mun_used_s.get();
            int attcas1 = def_gas_used == 0 ? 0 : attcas1_s.get();
            return new AirstrikeUnitAny_Any_Imp(id, date, isAttackerIdGreater, success, attcas1, defcas1, city_infra_before_s.get(), infra_destroyed_s.get(), att_gas_used, att_mun_used, def_gas_used, def_mun_used, defCas2_s.get(), type);
        }
    }

    public static class AirstrikeUnitIt_0_NoImp extends AirstrikeUnit {
        private final long data;
        private final long data2;

        public AirstrikeUnitIt_0_NoImp(int id, long date, boolean isAttackerIdGreater, double cityInfraBefore, double infraDestroyed, double att_gas_used, double att_mun_used, int defCas2, AttackType type) {
            super(id, date, isAttackerIdGreater);
            // defCas2 18 (5)
            // att_gas_used 17
            // att_mun_used 17
            // cityInfraBefore 21
            // type 4
            // --
            // infraDestroyed 18
            // 8,950,820
            // 24

            this.data = (long) type.ordinal() | (long) (cityInfraBefore * 100) << 4 | (long) (att_mun_used * 100) << (25) | (long) (att_gas_used * 100) << (42);
            this.data2 = (long) (infraDestroyed * 100) | (long) (defCas2) << 18;
        }

        @Override
        public AttackType getAttack_type() {
            return AttackType.values()[(int) (data & 0xF)];
        }

        @Override
        public int getDefcas2() {
            return (int) ((data2 >> 18) & 70368744177663L);
        }

        @Override
        public double getCity_infra_before() {
            return ((data >> 4) & 2097151) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            return ((data2) & 262143) * 0.01;
        }

        @Override
        public double getAtt_gas_used() {
            return ((data >> 42) & 131071) * 0.01;
        }

        @Override
        public double getAtt_mun_used() {
            return ((data >> 25) & 131071) * 0.01;
        }

        @Override
        public int getImprovements_destroyed() {
            return 0;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getAttcas1() {
            return 0;
        }

        @Override
        public int getDefcas1() {
            return 0;
        }

        @Override
        public double getDef_gas_used() {
            return 0;
        }

        @Override
        public double getDef_mun_used() {
            return 0;
        }
    }

    public static class AirstrikeUnitIt_0_Imp extends AirstrikeUnitIt_0_NoImp {
        public AirstrikeUnitIt_0_Imp(int id, long date, boolean isAttackerIdGreater, double cityInfraBefore, double infraDestroyed, double att_gas_used, double att_mun_used, int defCas2, AttackType type) {
            super(id, date, isAttackerIdGreater, cityInfraBefore, infraDestroyed, att_gas_used, att_mun_used, defCas2, type);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }

    public static class AirstrikeUnitAny_Any_NoImp extends AirstrikeUnit {
        private final long data4;
        private final long data5;

        public AirstrikeUnitAny_Any_NoImp(int id, long date, boolean isAttackerIdGreater, SuccessType success,
                                     int attcas1, int defcas1,
                                     double city_infra_before, double infra_destroyed,
                                     double att_gas_used, double att_mun_used, double def_gas_used, double def_mun_used, int defCas2, AttackType type) {
            super(id, date, isAttackerIdGreater);
            this.data4 = (long) (city_infra_before * 100) << 40 | (long) (infra_destroyed * 100) << 22 | (long) (attcas1) << 12 | (long) (defcas1) << 2 | (long) success.ordinal();
            this.data5 = (long) (att_mun_used * 100) << 45 | (long) (defCas2) << 21 | (long) (def_mun_used * 100) << 4 | (long) type.ordinal();
        }
        @Override
        public double getAtt_mun_used() {
            return ((data5 >> 45) & 524287) * 0.01;
        }
        @Override
        public double getDef_mun_used() {
            return ((data5 >> 4) & 131071) * 0.01;
        }

        @Override
        public double getCity_infra_before() {
            return ((data4 >> 40) & 16777215) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            return ((data4 >> 22) & 262143) * 0.01;
        }
        @Override
        public AttackType getAttack_type() {
            return AttackType.values()[(int) (data5 & 0xF)];
        }

        @Override
        public int getDefcas2() {
            return (int) ((data5 >> 21) & 16777215L);
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.values()[(int) (data4) & 0x3];
        }

        @Override
        public int getAttcas1() {
            return (int) (data4 >> 12) & 1023;
        }

        @Override
        public int getDefcas1() {
            return (int) (data4 >> 2) & 1023;
        }

        @Override
        public double getAtt_gas_used() {
            return getAtt_mun_used();
        }

        @Override
        public double getDef_gas_used() {
            return getDef_mun_used();
        }


        @Override
        public int getImprovements_destroyed() {
            return 0;
        }
    }

    public static class AirstrikeUnitAny_Any_Imp extends AirstrikeUnitAny_Any_NoImp {

        public AirstrikeUnitAny_Any_Imp(int id, long date, boolean isAttackerIdGreater, SuccessType success, int attcas1, int defcas1, double city_infra_before, double infra_destroyed, double att_gas_used, double att_mun_used, double def_gas_used, double def_mun_used, int defCas2, AttackType type) {
            super(id, date, isAttackerIdGreater, success, attcas1, defcas1, city_infra_before, infra_destroyed, att_gas_used, att_mun_used, def_gas_used, def_mun_used, defCas2, type);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }

    @Override
    public int getAttcas2() {
        return 0;
    }

    @Override
    public int getDefcas3() {
        return 0;
    }

    @Override
    public double getMoney_looted() {
        return 0;
    }

    @Override
    public double[] getLoot() {
        return null;
    }

    @Override
    public double getLootPercent() {
        return 0;
    }
}
