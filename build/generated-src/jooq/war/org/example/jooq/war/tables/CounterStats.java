/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.war.tables;


import java.util.function.Function;

import org.example.jooq.war.DefaultSchema;
import org.example.jooq.war.Keys;
import org.example.jooq.war.tables.records.CounterStatsRecord;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function3;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row3;
import org.jooq.Schema;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class CounterStats extends TableImpl<CounterStatsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>COUNTER_STATS</code>
     */
    public static final CounterStats COUNTER_STATS = new CounterStats();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<CounterStatsRecord> getRecordType() {
        return CounterStatsRecord.class;
    }

    /**
     * The column <code>COUNTER_STATS.id</code>.
     */
    public final TableField<CounterStatsRecord, Integer> ID = createField(DSL.name("id"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>COUNTER_STATS.type</code>.
     */
    public final TableField<CounterStatsRecord, Integer> TYPE = createField(DSL.name("type"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>COUNTER_STATS.active</code>.
     */
    public final TableField<CounterStatsRecord, Integer> ACTIVE = createField(DSL.name("active"), SQLDataType.INTEGER.nullable(false), this, "");

    private CounterStats(Name alias, Table<CounterStatsRecord> aliased) {
        this(alias, aliased, null);
    }

    private CounterStats(Name alias, Table<CounterStatsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>COUNTER_STATS</code> table reference
     */
    public CounterStats(String alias) {
        this(DSL.name(alias), COUNTER_STATS);
    }

    /**
     * Create an aliased <code>COUNTER_STATS</code> table reference
     */
    public CounterStats(Name alias) {
        this(alias, COUNTER_STATS);
    }

    /**
     * Create a <code>COUNTER_STATS</code> table reference
     */
    public CounterStats() {
        this(DSL.name("COUNTER_STATS"), null);
    }

    public <O extends Record> CounterStats(Table<O> child, ForeignKey<O, CounterStatsRecord> key) {
        super(child, key, COUNTER_STATS);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public UniqueKey<CounterStatsRecord> getPrimaryKey() {
        return Keys.COUNTER_STATS__PK_COUNTER_STATS;
    }

    @Override
    public CounterStats as(String alias) {
        return new CounterStats(DSL.name(alias), this);
    }

    @Override
    public CounterStats as(Name alias) {
        return new CounterStats(alias, this);
    }

    @Override
    public CounterStats as(Table<?> alias) {
        return new CounterStats(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public CounterStats rename(String name) {
        return new CounterStats(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public CounterStats rename(Name name) {
        return new CounterStats(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public CounterStats rename(Table<?> name) {
        return new CounterStats(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row3 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row3<Integer, Integer, Integer> fieldsRow() {
        return (Row3) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function3<? super Integer, ? super Integer, ? super Integer, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function3<? super Integer, ? super Integer, ? super Integer, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
