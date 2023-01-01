/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.bank.tables.records;


import org.example.jooq.bank.tables.LootDiffByTaxId;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record4;
import org.jooq.Row4;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class LootDiffByTaxIdRecord extends UpdatableRecordImpl<LootDiffByTaxIdRecord> implements Record4<Integer, Integer, Integer, byte[]> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>loot_diff_by_tax_id.nation_id</code>.
     */
    public LootDiffByTaxIdRecord setNationId(Integer value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>loot_diff_by_tax_id.nation_id</code>.
     */
    public Integer getNationId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>loot_diff_by_tax_id.tax_id</code>.
     */
    public LootDiffByTaxIdRecord setTaxId(Integer value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>loot_diff_by_tax_id.tax_id</code>.
     */
    public Integer getTaxId() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>loot_diff_by_tax_id.date</code>.
     */
    public LootDiffByTaxIdRecord setDate(Integer value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>loot_diff_by_tax_id.date</code>.
     */
    public Integer getDate() {
        return (Integer) get(2);
    }

    /**
     * Setter for <code>loot_diff_by_tax_id.resources</code>.
     */
    public LootDiffByTaxIdRecord setResources(byte[] value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>loot_diff_by_tax_id.resources</code>.
     */
    public byte[] getResources() {
        return (byte[]) get(3);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record2<Integer, Integer> key() {
        return (Record2) super.key();
    }

    // -------------------------------------------------------------------------
    // Record4 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row4<Integer, Integer, Integer, byte[]> fieldsRow() {
        return (Row4) super.fieldsRow();
    }

    @Override
    public Row4<Integer, Integer, Integer, byte[]> valuesRow() {
        return (Row4) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return LootDiffByTaxId.LOOT_DIFF_BY_TAX_ID.NATION_ID;
    }

    @Override
    public Field<Integer> field2() {
        return LootDiffByTaxId.LOOT_DIFF_BY_TAX_ID.TAX_ID;
    }

    @Override
    public Field<Integer> field3() {
        return LootDiffByTaxId.LOOT_DIFF_BY_TAX_ID.DATE;
    }

    @Override
    public Field<byte[]> field4() {
        return LootDiffByTaxId.LOOT_DIFF_BY_TAX_ID.RESOURCES;
    }

    @Override
    public Integer component1() {
        return getNationId();
    }

    @Override
    public Integer component2() {
        return getTaxId();
    }

    @Override
    public Integer component3() {
        return getDate();
    }

    @Override
    public byte[] component4() {
        return getResources();
    }

    @Override
    public Integer value1() {
        return getNationId();
    }

    @Override
    public Integer value2() {
        return getTaxId();
    }

    @Override
    public Integer value3() {
        return getDate();
    }

    @Override
    public byte[] value4() {
        return getResources();
    }

    @Override
    public LootDiffByTaxIdRecord value1(Integer value) {
        setNationId(value);
        return this;
    }

    @Override
    public LootDiffByTaxIdRecord value2(Integer value) {
        setTaxId(value);
        return this;
    }

    @Override
    public LootDiffByTaxIdRecord value3(Integer value) {
        setDate(value);
        return this;
    }

    @Override
    public LootDiffByTaxIdRecord value4(byte[] value) {
        setResources(value);
        return this;
    }

    @Override
    public LootDiffByTaxIdRecord values(Integer value1, Integer value2, Integer value3, byte[] value4) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached LootDiffByTaxIdRecord
     */
    public LootDiffByTaxIdRecord() {
        super(LootDiffByTaxId.LOOT_DIFF_BY_TAX_ID);
    }

    /**
     * Create a detached, initialised LootDiffByTaxIdRecord
     */
    public LootDiffByTaxIdRecord(Integer nationId, Integer taxId, Integer date, byte[] resources) {
        super(LootDiffByTaxId.LOOT_DIFF_BY_TAX_ID);

        setNationId(nationId);
        setTaxId(taxId);
        setDate(date);
        setResources(resources);
    }

    /**
     * Create a detached, initialised LootDiffByTaxIdRecord
     */
    public LootDiffByTaxIdRecord(org.example.jooq.bank.tables.pojos.LootDiffByTaxId value) {
        super(LootDiffByTaxId.LOOT_DIFF_BY_TAX_ID);

        if (value != null) {
            setNationId(value.getNationId());
            setTaxId(value.getTaxId());
            setDate(value.getDate());
            setResources(value.getResources());
        }
    }
}