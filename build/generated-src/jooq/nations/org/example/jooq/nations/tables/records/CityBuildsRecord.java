/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.nations.tables.records;


import org.example.jooq.nations.tables.CityBuilds;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record9;
import org.jooq.Row9;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class CityBuildsRecord extends UpdatableRecordImpl<CityBuildsRecord> implements Record9<Integer, Integer, Long, Integer, Integer, Boolean, byte[], Long, Long> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>CITY_BUILDS.id</code>.
     */
    public CityBuildsRecord setId(Integer value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>CITY_BUILDS.id</code>.
     */
    public Integer getId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>CITY_BUILDS.nation</code>.
     */
    public CityBuildsRecord setNation(Integer value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>CITY_BUILDS.nation</code>.
     */
    public Integer getNation() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>CITY_BUILDS.created</code>.
     */
    public CityBuildsRecord setCreated(Long value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>CITY_BUILDS.created</code>.
     */
    public Long getCreated() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>CITY_BUILDS.infra</code>.
     */
    public CityBuildsRecord setInfra(Integer value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>CITY_BUILDS.infra</code>.
     */
    public Integer getInfra() {
        return (Integer) get(3);
    }

    /**
     * Setter for <code>CITY_BUILDS.land</code>.
     */
    public CityBuildsRecord setLand(Integer value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>CITY_BUILDS.land</code>.
     */
    public Integer getLand() {
        return (Integer) get(4);
    }

    /**
     * Setter for <code>CITY_BUILDS.powered</code>.
     */
    public CityBuildsRecord setPowered(Boolean value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>CITY_BUILDS.powered</code>.
     */
    public Boolean getPowered() {
        return (Boolean) get(5);
    }

    /**
     * Setter for <code>CITY_BUILDS.improvements</code>.
     */
    public CityBuildsRecord setImprovements(byte[] value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>CITY_BUILDS.improvements</code>.
     */
    public byte[] getImprovements() {
        return (byte[]) get(6);
    }

    /**
     * Setter for <code>CITY_BUILDS.update_flag</code>.
     */
    public CityBuildsRecord setUpdateFlag(Long value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>CITY_BUILDS.update_flag</code>.
     */
    public Long getUpdateFlag() {
        return (Long) get(7);
    }

    /**
     * Setter for <code>CITY_BUILDS.nuke_date</code>.
     */
    public CityBuildsRecord setNukeDate(Long value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for <code>CITY_BUILDS.nuke_date</code>.
     */
    public Long getNukeDate() {
        return (Long) get(8);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record9 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row9<Integer, Integer, Long, Integer, Integer, Boolean, byte[], Long, Long> fieldsRow() {
        return (Row9) super.fieldsRow();
    }

    @Override
    public Row9<Integer, Integer, Long, Integer, Integer, Boolean, byte[], Long, Long> valuesRow() {
        return (Row9) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return CityBuilds.CITY_BUILDS.ID;
    }

    @Override
    public Field<Integer> field2() {
        return CityBuilds.CITY_BUILDS.NATION;
    }

    @Override
    public Field<Long> field3() {
        return CityBuilds.CITY_BUILDS.CREATED;
    }

    @Override
    public Field<Integer> field4() {
        return CityBuilds.CITY_BUILDS.INFRA;
    }

    @Override
    public Field<Integer> field5() {
        return CityBuilds.CITY_BUILDS.LAND;
    }

    @Override
    public Field<Boolean> field6() {
        return CityBuilds.CITY_BUILDS.POWERED;
    }

    @Override
    public Field<byte[]> field7() {
        return CityBuilds.CITY_BUILDS.IMPROVEMENTS;
    }

    @Override
    public Field<Long> field8() {
        return CityBuilds.CITY_BUILDS.UPDATE_FLAG;
    }

    @Override
    public Field<Long> field9() {
        return CityBuilds.CITY_BUILDS.NUKE_DATE;
    }

    @Override
    public Integer component1() {
        return getId();
    }

    @Override
    public Integer component2() {
        return getNation();
    }

    @Override
    public Long component3() {
        return getCreated();
    }

    @Override
    public Integer component4() {
        return getInfra();
    }

    @Override
    public Integer component5() {
        return getLand();
    }

    @Override
    public Boolean component6() {
        return getPowered();
    }

    @Override
    public byte[] component7() {
        return getImprovements();
    }

    @Override
    public Long component8() {
        return getUpdateFlag();
    }

    @Override
    public Long component9() {
        return getNukeDate();
    }

    @Override
    public Integer value1() {
        return getId();
    }

    @Override
    public Integer value2() {
        return getNation();
    }

    @Override
    public Long value3() {
        return getCreated();
    }

    @Override
    public Integer value4() {
        return getInfra();
    }

    @Override
    public Integer value5() {
        return getLand();
    }

    @Override
    public Boolean value6() {
        return getPowered();
    }

    @Override
    public byte[] value7() {
        return getImprovements();
    }

    @Override
    public Long value8() {
        return getUpdateFlag();
    }

    @Override
    public Long value9() {
        return getNukeDate();
    }

    @Override
    public CityBuildsRecord value1(Integer value) {
        setId(value);
        return this;
    }

    @Override
    public CityBuildsRecord value2(Integer value) {
        setNation(value);
        return this;
    }

    @Override
    public CityBuildsRecord value3(Long value) {
        setCreated(value);
        return this;
    }

    @Override
    public CityBuildsRecord value4(Integer value) {
        setInfra(value);
        return this;
    }

    @Override
    public CityBuildsRecord value5(Integer value) {
        setLand(value);
        return this;
    }

    @Override
    public CityBuildsRecord value6(Boolean value) {
        setPowered(value);
        return this;
    }

    @Override
    public CityBuildsRecord value7(byte[] value) {
        setImprovements(value);
        return this;
    }

    @Override
    public CityBuildsRecord value8(Long value) {
        setUpdateFlag(value);
        return this;
    }

    @Override
    public CityBuildsRecord value9(Long value) {
        setNukeDate(value);
        return this;
    }

    @Override
    public CityBuildsRecord values(Integer value1, Integer value2, Long value3, Integer value4, Integer value5, Boolean value6, byte[] value7, Long value8, Long value9) {
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
     * Create a detached CityBuildsRecord
     */
    public CityBuildsRecord() {
        super(CityBuilds.CITY_BUILDS);
    }

    /**
     * Create a detached, initialised CityBuildsRecord
     */
    public CityBuildsRecord(Integer id, Integer nation, Long created, Integer infra, Integer land, Boolean powered, byte[] improvements, Long updateFlag, Long nukeDate) {
        super(CityBuilds.CITY_BUILDS);

        setId(id);
        setNation(nation);
        setCreated(created);
        setInfra(infra);
        setLand(land);
        setPowered(powered);
        setImprovements(improvements);
        setUpdateFlag(updateFlag);
        setNukeDate(nukeDate);
    }

    /**
     * Create a detached, initialised CityBuildsRecord
     */
    public CityBuildsRecord(org.example.jooq.nations.tables.pojos.CityBuilds value) {
        super(CityBuilds.CITY_BUILDS);

        if (value != null) {
            setId(value.getId());
            setNation(value.getNation());
            setCreated(value.getCreated());
            setInfra(value.getInfra());
            setLand(value.getLand());
            setPowered(value.getPowered());
            setImprovements(value.getImprovements());
            setUpdateFlag(value.getUpdateFlag());
            setNukeDate(value.getNukeDate());
        }
    }
}
