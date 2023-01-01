/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.locutus.tables;


import java.util.function.Function;

import org.example.jooq.locutus.DefaultSchema;
import org.example.jooq.locutus.Keys;
import org.example.jooq.locutus.tables.records.UuidsRecord;
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
public class Uuids extends TableImpl<UuidsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>UUIDS</code>
     */
    public static final Uuids UUIDS = new Uuids();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<UuidsRecord> getRecordType() {
        return UuidsRecord.class;
    }

    /**
     * The column <code>UUIDS.nation_id</code>.
     */
    public final TableField<UuidsRecord, Integer> NATION_ID = createField(DSL.name("nation_id"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>UUIDS.uuid</code>.
     */
    public final TableField<UuidsRecord, byte[]> UUID = createField(DSL.name("uuid"), SQLDataType.BLOB.nullable(false), this, "");

    /**
     * The column <code>UUIDS.date</code>.
     */
    public final TableField<UuidsRecord, Long> DATE = createField(DSL.name("date"), SQLDataType.BIGINT.nullable(false), this, "");

    private Uuids(Name alias, Table<UuidsRecord> aliased) {
        this(alias, aliased, null);
    }

    private Uuids(Name alias, Table<UuidsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>UUIDS</code> table reference
     */
    public Uuids(String alias) {
        this(DSL.name(alias), UUIDS);
    }

    /**
     * Create an aliased <code>UUIDS</code> table reference
     */
    public Uuids(Name alias) {
        this(alias, UUIDS);
    }

    /**
     * Create a <code>UUIDS</code> table reference
     */
    public Uuids() {
        this(DSL.name("UUIDS"), null);
    }

    public <O extends Record> Uuids(Table<O> child, ForeignKey<O, UuidsRecord> key) {
        super(child, key, UUIDS);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public UniqueKey<UuidsRecord> getPrimaryKey() {
        return Keys.UUIDS__PK_UUIDS;
    }

    @Override
    public Uuids as(String alias) {
        return new Uuids(DSL.name(alias), this);
    }

    @Override
    public Uuids as(Name alias) {
        return new Uuids(alias, this);
    }

    @Override
    public Uuids as(Table<?> alias) {
        return new Uuids(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public Uuids rename(String name) {
        return new Uuids(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Uuids rename(Name name) {
        return new Uuids(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public Uuids rename(Table<?> name) {
        return new Uuids(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row3 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row3<Integer, byte[], Long> fieldsRow() {
        return (Row3) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function3<? super Integer, ? super byte[], ? super Long, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function3<? super Integer, ? super byte[], ? super Long, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}