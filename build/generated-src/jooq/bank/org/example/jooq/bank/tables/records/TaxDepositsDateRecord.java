/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.bank.tables.records;


import org.example.jooq.bank.tables.TaxDepositsDate;
import org.jooq.Field;
import org.jooq.Record3;
import org.jooq.Record9;
import org.jooq.Row9;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class TaxDepositsDateRecord extends UpdatableRecordImpl<TaxDepositsDateRecord> implements Record9<Integer, Integer, Long, Integer, Integer, Integer, Integer, byte[], Integer> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>TAX_DEPOSITS_DATE.tax_id</code>.
     */
    public TaxDepositsDateRecord setTaxId(Integer value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>TAX_DEPOSITS_DATE.tax_id</code>.
     */
    public Integer getTaxId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>TAX_DEPOSITS_DATE.alliance</code>.
     */
    public TaxDepositsDateRecord setAlliance(Integer value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>TAX_DEPOSITS_DATE.alliance</code>.
     */
    public Integer getAlliance() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>TAX_DEPOSITS_DATE.date</code>.
     */
    public TaxDepositsDateRecord setDate(Long value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>TAX_DEPOSITS_DATE.date</code>.
     */
    public Long getDate() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>TAX_DEPOSITS_DATE.id</code>.
     */
    public TaxDepositsDateRecord setId(Integer value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>TAX_DEPOSITS_DATE.id</code>.
     */
    public Integer getId() {
        return (Integer) get(3);
    }

    /**
     * Setter for <code>TAX_DEPOSITS_DATE.nation</code>.
     */
    public TaxDepositsDateRecord setNation(Integer value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>TAX_DEPOSITS_DATE.nation</code>.
     */
    public Integer getNation() {
        return (Integer) get(4);
    }

    /**
     * Setter for <code>TAX_DEPOSITS_DATE.moneyrate</code>.
     */
    public TaxDepositsDateRecord setMoneyrate(Integer value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>TAX_DEPOSITS_DATE.moneyrate</code>.
     */
    public Integer getMoneyrate() {
        return (Integer) get(5);
    }

    /**
     * Setter for <code>TAX_DEPOSITS_DATE.resoucerate</code>.
     */
    public TaxDepositsDateRecord setResoucerate(Integer value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>TAX_DEPOSITS_DATE.resoucerate</code>.
     */
    public Integer getResoucerate() {
        return (Integer) get(6);
    }

    /**
     * Setter for <code>TAX_DEPOSITS_DATE.resources</code>.
     */
    public TaxDepositsDateRecord setResources(byte[] value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>TAX_DEPOSITS_DATE.resources</code>.
     */
    public byte[] getResources() {
        return (byte[]) get(7);
    }

    /**
     * Setter for <code>TAX_DEPOSITS_DATE.internal_taxrate</code>.
     */
    public TaxDepositsDateRecord setInternalTaxrate(Integer value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for <code>TAX_DEPOSITS_DATE.internal_taxrate</code>.
     */
    public Integer getInternalTaxrate() {
        return (Integer) get(8);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record3<Integer, Long, Integer> key() {
        return (Record3) super.key();
    }

    // -------------------------------------------------------------------------
    // Record9 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row9<Integer, Integer, Long, Integer, Integer, Integer, Integer, byte[], Integer> fieldsRow() {
        return (Row9) super.fieldsRow();
    }

    @Override
    public Row9<Integer, Integer, Long, Integer, Integer, Integer, Integer, byte[], Integer> valuesRow() {
        return (Row9) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return TaxDepositsDate.TAX_DEPOSITS_DATE.TAX_ID;
    }

    @Override
    public Field<Integer> field2() {
        return TaxDepositsDate.TAX_DEPOSITS_DATE.ALLIANCE;
    }

    @Override
    public Field<Long> field3() {
        return TaxDepositsDate.TAX_DEPOSITS_DATE.DATE;
    }

    @Override
    public Field<Integer> field4() {
        return TaxDepositsDate.TAX_DEPOSITS_DATE.ID;
    }

    @Override
    public Field<Integer> field5() {
        return TaxDepositsDate.TAX_DEPOSITS_DATE.NATION;
    }

    @Override
    public Field<Integer> field6() {
        return TaxDepositsDate.TAX_DEPOSITS_DATE.MONEYRATE;
    }

    @Override
    public Field<Integer> field7() {
        return TaxDepositsDate.TAX_DEPOSITS_DATE.RESOUCERATE;
    }

    @Override
    public Field<byte[]> field8() {
        return TaxDepositsDate.TAX_DEPOSITS_DATE.RESOURCES;
    }

    @Override
    public Field<Integer> field9() {
        return TaxDepositsDate.TAX_DEPOSITS_DATE.INTERNAL_TAXRATE;
    }

    @Override
    public Integer component1() {
        return getTaxId();
    }

    @Override
    public Integer component2() {
        return getAlliance();
    }

    @Override
    public Long component3() {
        return getDate();
    }

    @Override
    public Integer component4() {
        return getId();
    }

    @Override
    public Integer component5() {
        return getNation();
    }

    @Override
    public Integer component6() {
        return getMoneyrate();
    }

    @Override
    public Integer component7() {
        return getResoucerate();
    }

    @Override
    public byte[] component8() {
        return getResources();
    }

    @Override
    public Integer component9() {
        return getInternalTaxrate();
    }

    @Override
    public Integer value1() {
        return getTaxId();
    }

    @Override
    public Integer value2() {
        return getAlliance();
    }

    @Override
    public Long value3() {
        return getDate();
    }

    @Override
    public Integer value4() {
        return getId();
    }

    @Override
    public Integer value5() {
        return getNation();
    }

    @Override
    public Integer value6() {
        return getMoneyrate();
    }

    @Override
    public Integer value7() {
        return getResoucerate();
    }

    @Override
    public byte[] value8() {
        return getResources();
    }

    @Override
    public Integer value9() {
        return getInternalTaxrate();
    }

    @Override
    public TaxDepositsDateRecord value1(Integer value) {
        setTaxId(value);
        return this;
    }

    @Override
    public TaxDepositsDateRecord value2(Integer value) {
        setAlliance(value);
        return this;
    }

    @Override
    public TaxDepositsDateRecord value3(Long value) {
        setDate(value);
        return this;
    }

    @Override
    public TaxDepositsDateRecord value4(Integer value) {
        setId(value);
        return this;
    }

    @Override
    public TaxDepositsDateRecord value5(Integer value) {
        setNation(value);
        return this;
    }

    @Override
    public TaxDepositsDateRecord value6(Integer value) {
        setMoneyrate(value);
        return this;
    }

    @Override
    public TaxDepositsDateRecord value7(Integer value) {
        setResoucerate(value);
        return this;
    }

    @Override
    public TaxDepositsDateRecord value8(byte[] value) {
        setResources(value);
        return this;
    }

    @Override
    public TaxDepositsDateRecord value9(Integer value) {
        setInternalTaxrate(value);
        return this;
    }

    @Override
    public TaxDepositsDateRecord values(Integer value1, Integer value2, Long value3, Integer value4, Integer value5, Integer value6, Integer value7, byte[] value8, Integer value9) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        value9(value9);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached TaxDepositsDateRecord
     */
    public TaxDepositsDateRecord() {
        super(TaxDepositsDate.TAX_DEPOSITS_DATE);
    }

    /**
     * Create a detached, initialised TaxDepositsDateRecord
     */
    public TaxDepositsDateRecord(Integer taxId, Integer alliance, Long date, Integer id, Integer nation, Integer moneyrate, Integer resoucerate, byte[] resources, Integer internalTaxrate) {
        super(TaxDepositsDate.TAX_DEPOSITS_DATE);

        setTaxId(taxId);
        setAlliance(alliance);
        setDate(date);
        setId(id);
        setNation(nation);
        setMoneyrate(moneyrate);
        setResoucerate(resoucerate);
        setResources(resources);
        setInternalTaxrate(internalTaxrate);
    }

    /**
     * Create a detached, initialised TaxDepositsDateRecord
     */
    public TaxDepositsDateRecord(org.example.jooq.bank.tables.pojos.TaxDepositsDate value) {
        super(TaxDepositsDate.TAX_DEPOSITS_DATE);

        if (value != null) {
            setTaxId(value.getTaxId());
            setAlliance(value.getAlliance());
            setDate(value.getDate());
            setId(value.getId());
            setNation(value.getNation());
            setMoneyrate(value.getMoneyrate());
            setResoucerate(value.getResoucerate());
            setResources(value.getResources());
            setInternalTaxrate(value.getInternalTaxrate());
        }
    }
}
