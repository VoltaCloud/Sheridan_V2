/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.nations.tables;


import java.util.function.Function;

import org.example.jooq.nations.DefaultSchema;
import org.example.jooq.nations.Keys;
import org.example.jooq.nations.tables.records.CityBuildsRecord;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function9;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row9;
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
public class CityBuilds extends TableImpl<CityBuildsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>CITY_BUILDS</code>
     */
    public static final CityBuilds CITY_BUILDS = new CityBuilds();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<CityBuildsRecord> getRecordType() {
        return CityBuildsRecord.class;
    }

    /**
     * The column <code>CITY_BUILDS.id</code>.
     */
    public final TableField<CityBuildsRecord, Integer> ID = createField(DSL.name("id"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>CITY_BUILDS.nation</code>.
     */
    public final TableField<CityBuildsRecord, Integer> NATION = createField(DSL.name("nation"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>CITY_BUILDS.created</code>.
     */
    public final TableField<CityBuildsRecord, Long> CREATED = createField(DSL.name("created"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>CITY_BUILDS.infra</code>.
     */
    public final TableField<CityBuildsRecord, Integer> INFRA = createField(DSL.name("infra"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>CITY_BUILDS.land</code>.
     */
    public final TableField<CityBuildsRecord, Integer> LAND = createField(DSL.name("land"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>CITY_BUILDS.powered</code>.
     */
    public final TableField<CityBuildsRecord, Boolean> POWERED = createField(DSL.name("powered"), SQLDataType.BOOLEAN.nullable(false), this, "");

    /**
     * The column <code>CITY_BUILDS.improvements</code>.
     */
    public final TableField<CityBuildsRecord, byte[]> IMPROVEMENTS = createField(DSL.name("improvements"), SQLDataType.BLOB.nullable(false), this, "");

    /**
     * The column <code>CITY_BUILDS.update_flag</code>.
     */
    public final TableField<CityBuildsRecord, Long> UPDATE_FLAG = createField(DSL.name("update_flag"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>CITY_BUILDS.nuke_date</code>.
     */
    public final TableField<CityBuildsRecord, Long> NUKE_DATE = createField(DSL.name("nuke_date"), SQLDataType.BIGINT.nullable(false), this, "");

    private CityBuilds(Name alias, Table<CityBuildsRecord> aliased) {
        this(alias, aliased, null);
    }

    private CityBuilds(Name alias, Table<CityBuildsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>CITY_BUILDS</code> table reference
     */
    public CityBuilds(String alias) {
        this(DSL.name(alias), CITY_BUILDS);
    }

    /**
     * Create an aliased <code>CITY_BUILDS</code> table reference
     */
    public CityBuilds(Name alias) {
        this(alias, CITY_BUILDS);
    }

    /**
     * Create a <code>CITY_BUILDS</code> table reference
     */
    public CityBuilds() {
        this(DSL.name("CITY_BUILDS"), null);
    }

    public <O extends Record> CityBuilds(Table<O> child, ForeignKey<O, CityBuildsRecord> key) {
        super(child, key, CITY_BUILDS);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public UniqueKey<CityBuildsRecord> getPrimaryKey() {
        return Keys.CITY_BUILDS__PK_CITY_BUILDS;
    }

    @Override
    public CityBuilds as(String alias) {
        return new CityBuilds(DSL.name(alias), this);
    }

    @Override
    public CityBuilds as(Name alias) {
        return new CityBuilds(alias, this);
    }

    @Override
    public CityBuilds as(Table<?> alias) {
        return new CityBuilds(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public CityBuilds rename(String name) {
        return new CityBuilds(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public CityBuilds rename(Name name) {
        return new CityBuilds(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public CityBuilds rename(Table<?> name) {
        return new CityBuilds(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row9 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row9<Integer, Integer, Long, Integer, Integer, Boolean, byte[], Long, Long> fieldsRow() {
        return (Row9) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function9<? super Integer, ? super Integer, ? super Long, ? super Integer, ? super Integer, ? super Boolean, ? super byte[], ? super Long, ? super Long, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function9<? super Integer, ? super Integer, ? super Long, ? super Integer, ? super Integer, ? super Boolean, ? super byte[], ? super Long, ? super Long, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
