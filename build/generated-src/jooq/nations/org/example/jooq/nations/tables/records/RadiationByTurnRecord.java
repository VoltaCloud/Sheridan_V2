/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.nations.tables.records;


import org.example.jooq.nations.tables.RadiationByTurn;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Row3;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class RadiationByTurnRecord extends UpdatableRecordImpl<RadiationByTurnRecord> implements Record3<Integer, Integer, Long> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>RADIATION_BY_TURN.continent</code>.
     */
    public RadiationByTurnRecord setContinent(Integer value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>RADIATION_BY_TURN.continent</code>.
     */
    public Integer getContinent() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>RADIATION_BY_TURN.radiation</code>.
     */
    public RadiationByTurnRecord setRadiation(Integer value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>RADIATION_BY_TURN.radiation</code>.
     */
    public Integer getRadiation() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>RADIATION_BY_TURN.turn</code>.
     */
    public RadiationByTurnRecord setTurn(Long value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>RADIATION_BY_TURN.turn</code>.
     */
    public Long getTurn() {
        return (Long) get(2);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record2<Integer, Long> key() {
        return (Record2) super.key();
    }

    // -------------------------------------------------------------------------
    // Record3 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row3<Integer, Integer, Long> fieldsRow() {
        return (Row3) super.fieldsRow();
    }

    @Override
    public Row3<Integer, Integer, Long> valuesRow() {
        return (Row3) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return RadiationByTurn.RADIATION_BY_TURN.CONTINENT;
    }

    @Override
    public Field<Integer> field2() {
        return RadiationByTurn.RADIATION_BY_TURN.RADIATION;
    }

    @Override
    public Field<Long> field3() {
        return RadiationByTurn.RADIATION_BY_TURN.TURN;
    }

    @Override
    public Integer component1() {
        return getContinent();
    }

    @Override
    public Integer component2() {
        return getRadiation();
    }

    @Override
    public Long component3() {
        return getTurn();
    }

    @Override
    public Integer value1() {
        return getContinent();
    }

    @Override
    public Integer value2() {
        return getRadiation();
    }

    @Override
    public Long value3() {
        return getTurn();
    }

    @Override
    public RadiationByTurnRecord value1(Integer value) {
        setContinent(value);
        return this;
    }

    @Override
    public RadiationByTurnRecord value2(Integer value) {
        setRadiation(value);
        return this;
    }

    @Override
    public RadiationByTurnRecord value3(Long value) {
        setTurn(value);
        return this;
    }

    @Override
    public RadiationByTurnRecord values(Integer value1, Integer value2, Long value3) {
        value1(value1);
        value2(value2);
        value3(value3);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached RadiationByTurnRecord
     */
    public RadiationByTurnRecord() {
        super(RadiationByTurn.RADIATION_BY_TURN);
    }

    /**
     * Create a detached, initialised RadiationByTurnRecord
     */
    public RadiationByTurnRecord(Integer continent, Integer radiation, Long turn) {
        super(RadiationByTurn.RADIATION_BY_TURN);

        setContinent(continent);
        setRadiation(radiation);
        setTurn(turn);
    }

    /**
     * Create a detached, initialised RadiationByTurnRecord
     */
    public RadiationByTurnRecord(org.example.jooq.nations.tables.pojos.RadiationByTurn value) {
        super(RadiationByTurn.RADIATION_BY_TURN);

        if (value != null) {
            setContinent(value.getContinent());
            setRadiation(value.getRadiation());
            setTurn(value.getTurn());
        }
    }
}
