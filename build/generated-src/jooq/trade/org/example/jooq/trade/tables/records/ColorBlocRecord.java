/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.trade.tables.records;


import org.example.jooq.trade.tables.ColorBloc;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.Row3;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ColorBlocRecord extends UpdatableRecordImpl<ColorBlocRecord> implements Record3<Integer, String, Integer> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>COLOR_BLOC.id</code>.
     */
    public ColorBlocRecord setId(Integer value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>COLOR_BLOC.id</code>.
     */
    public Integer getId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>COLOR_BLOC.name</code>.
     */
    public ColorBlocRecord setName(String value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>COLOR_BLOC.name</code>.
     */
    public String getName() {
        return (String) get(1);
    }

    /**
     * Setter for <code>COLOR_BLOC.bonus</code>.
     */
    public ColorBlocRecord setBonus(Integer value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>COLOR_BLOC.bonus</code>.
     */
    public Integer getBonus() {
        return (Integer) get(2);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record3 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row3<Integer, String, Integer> fieldsRow() {
        return (Row3) super.fieldsRow();
    }

    @Override
    public Row3<Integer, String, Integer> valuesRow() {
        return (Row3) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return ColorBloc.COLOR_BLOC.ID;
    }

    @Override
    public Field<String> field2() {
        return ColorBloc.COLOR_BLOC.NAME;
    }

    @Override
    public Field<Integer> field3() {
        return ColorBloc.COLOR_BLOC.BONUS;
    }

    @Override
    public Integer component1() {
        return getId();
    }

    @Override
    public String component2() {
        return getName();
    }

    @Override
    public Integer component3() {
        return getBonus();
    }

    @Override
    public Integer value1() {
        return getId();
    }

    @Override
    public String value2() {
        return getName();
    }

    @Override
    public Integer value3() {
        return getBonus();
    }

    @Override
    public ColorBlocRecord value1(Integer value) {
        setId(value);
        return this;
    }

    @Override
    public ColorBlocRecord value2(String value) {
        setName(value);
        return this;
    }

    @Override
    public ColorBlocRecord value3(Integer value) {
        setBonus(value);
        return this;
    }

    @Override
    public ColorBlocRecord values(Integer value1, String value2, Integer value3) {
        value1(value1);
        value2(value2);
        value3(value3);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached ColorBlocRecord
     */
    public ColorBlocRecord() {
        super(ColorBloc.COLOR_BLOC);
    }

    /**
     * Create a detached, initialised ColorBlocRecord
     */
    public ColorBlocRecord(Integer id, String name, Integer bonus) {
        super(ColorBloc.COLOR_BLOC);

        setId(id);
        setName(name);
        setBonus(bonus);
    }

    /**
     * Create a detached, initialised ColorBlocRecord
     */
    public ColorBlocRecord(org.example.jooq.trade.tables.pojos.ColorBloc value) {
        super(ColorBloc.COLOR_BLOC);

        if (value != null) {
            setId(value.getId());
            setName(value.getName());
            setBonus(value.getBonus());
        }
    }
}
