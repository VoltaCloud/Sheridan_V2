/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.nations.tables;


import java.util.function.Function;

import org.example.jooq.nations.DefaultSchema;
import org.example.jooq.nations.Keys;
import org.example.jooq.nations.tables.records.AlliancesRecord;
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
public class Alliances extends TableImpl<AlliancesRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>ALLIANCES</code>
     */
    public static final Alliances ALLIANCES = new Alliances();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<AlliancesRecord> getRecordType() {
        return AlliancesRecord.class;
    }

    /**
     * The column <code>ALLIANCES.id</code>.
     */
    public final TableField<AlliancesRecord, Integer> ID = createField(DSL.name("id"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>ALLIANCES.name</code>.
     */
    public final TableField<AlliancesRecord, String> NAME = createField(DSL.name("name"), SQLDataType.VARCHAR(32).nullable(false), this, "");

    /**
     * The column <code>ALLIANCES.acronym</code>.
     */
    public final TableField<AlliancesRecord, String> ACRONYM = createField(DSL.name("acronym"), SQLDataType.VARCHAR(32).nullable(false), this, "");

    /**
     * The column <code>ALLIANCES.flag</code>.
     */
    public final TableField<AlliancesRecord, String> FLAG = createField(DSL.name("flag"), SQLDataType.VARCHAR(256).nullable(false), this, "");

    /**
     * The column <code>ALLIANCES.forum_link</code>.
     */
    public final TableField<AlliancesRecord, String> FORUM_LINK = createField(DSL.name("forum_link"), SQLDataType.VARCHAR(256).nullable(false), this, "");

    /**
     * The column <code>ALLIANCES.discord_link</code>.
     */
    public final TableField<AlliancesRecord, String> DISCORD_LINK = createField(DSL.name("discord_link"), SQLDataType.VARCHAR(256).nullable(false), this, "");

    /**
     * The column <code>ALLIANCES.wiki_link</code>.
     */
    public final TableField<AlliancesRecord, String> WIKI_LINK = createField(DSL.name("wiki_link"), SQLDataType.VARCHAR(256).nullable(false), this, "");

    /**
     * The column <code>ALLIANCES.dateCreated</code>.
     */
    public final TableField<AlliancesRecord, Long> DATECREATED = createField(DSL.name("dateCreated"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>ALLIANCES.color</code>.
     */
    public final TableField<AlliancesRecord, Integer> COLOR = createField(DSL.name("color"), SQLDataType.INTEGER.nullable(false), this, "");

    private Alliances(Name alias, Table<AlliancesRecord> aliased) {
        this(alias, aliased, null);
    }

    private Alliances(Name alias, Table<AlliancesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>ALLIANCES</code> table reference
     */
    public Alliances(String alias) {
        this(DSL.name(alias), ALLIANCES);
    }

    /**
     * Create an aliased <code>ALLIANCES</code> table reference
     */
    public Alliances(Name alias) {
        this(alias, ALLIANCES);
    }

    /**
     * Create a <code>ALLIANCES</code> table reference
     */
    public Alliances() {
        this(DSL.name("ALLIANCES"), null);
    }

    public <O extends Record> Alliances(Table<O> child, ForeignKey<O, AlliancesRecord> key) {
        super(child, key, ALLIANCES);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public UniqueKey<AlliancesRecord> getPrimaryKey() {
        return Keys.ALLIANCES__PK_ALLIANCES;
    }

    @Override
    public Alliances as(String alias) {
        return new Alliances(DSL.name(alias), this);
    }

    @Override
    public Alliances as(Name alias) {
        return new Alliances(alias, this);
    }

    @Override
    public Alliances as(Table<?> alias) {
        return new Alliances(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public Alliances rename(String name) {
        return new Alliances(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Alliances rename(Name name) {
        return new Alliances(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public Alliances rename(Table<?> name) {
        return new Alliances(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row9 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row9<Integer, String, String, String, String, String, String, Long, Integer> fieldsRow() {
        return (Row9) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function9<? super Integer, ? super String, ? super String, ? super String, ? super String, ? super String, ? super String, ? super Long, ? super Integer, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function9<? super Integer, ? super String, ? super String, ? super String, ? super String, ? super String, ? super String, ? super Long, ? super Integer, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
