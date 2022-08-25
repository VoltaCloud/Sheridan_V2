/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.war;


import org.example.jooq.war.tables.Attacks2;
import org.example.jooq.war.tables.Wars;
import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;


/**
 * A class modelling indexes of tables in the default schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index INDEX_ATTACK_ATTACKER_NATION_ID = Internal.createIndex(DSL.name("index_attack_attacker_nation_id"), Attacks2.ATTACKS2, new OrderField[] { Attacks2.ATTACKS2.ATTACKER_NATION_ID }, false);
    public static final Index INDEX_ATTACK_DATE = Internal.createIndex(DSL.name("index_attack_date"), Attacks2.ATTACKS2, new OrderField[] { Attacks2.ATTACKS2.DATE }, false);
    public static final Index INDEX_ATTACK_DEFENDER_NATION_ID = Internal.createIndex(DSL.name("index_attack_defender_nation_id"), Attacks2.ATTACKS2, new OrderField[] { Attacks2.ATTACKS2.DEFENDER_NATION_ID }, false);
    public static final Index INDEX_ATTACK_WARID = Internal.createIndex(DSL.name("index_attack_warid"), Attacks2.ATTACKS2, new OrderField[] { Attacks2.ATTACKS2.WAR_ID }, false);
    public static final Index INDEX_WARS_ATTACKER = Internal.createIndex(DSL.name("index_WARS_attacker"), Wars.WARS, new OrderField[] { Wars.WARS.ATTACKER_ID }, false);
    public static final Index INDEX_WARS_DATE = Internal.createIndex(DSL.name("index_WARS_date"), Wars.WARS, new OrderField[] { Wars.WARS.DATE }, false);
    public static final Index INDEX_WARS_DEFENDER = Internal.createIndex(DSL.name("index_WARS_defender"), Wars.WARS, new OrderField[] { Wars.WARS.DEFENDER_ID }, false);
    public static final Index INDEX_WARS_STATUS = Internal.createIndex(DSL.name("index_WARS_status"), Wars.WARS, new OrderField[] { Wars.WARS.STATUS }, false);
}
