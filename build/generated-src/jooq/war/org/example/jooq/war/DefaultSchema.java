/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.war;


import java.util.Arrays;
import java.util.List;

import org.example.jooq.war.tables.AttackSubcategoryCache;
import org.example.jooq.war.tables.Attacks2;
import org.example.jooq.war.tables.Blockaded;
import org.example.jooq.war.tables.BountiesV3;
import org.example.jooq.war.tables.CounterStats;
import org.example.jooq.war.tables.Wars;
import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DefaultSchema extends SchemaImpl {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>DEFAULT_SCHEMA</code>
     */
    public static final DefaultSchema DEFAULT_SCHEMA = new DefaultSchema();

    /**
     * The table <code>ATTACK_SUBCATEGORY_CACHE</code>.
     */
    public final AttackSubcategoryCache ATTACK_SUBCATEGORY_CACHE = AttackSubcategoryCache.ATTACK_SUBCATEGORY_CACHE;

    /**
     * The table <code>attacks2</code>.
     */
    public final Attacks2 ATTACKS2 = Attacks2.ATTACKS2;

    /**
     * The table <code>BLOCKADED</code>.
     */
    public final Blockaded BLOCKADED = Blockaded.BLOCKADED;

    /**
     * The table <code>BOUNTIES_V3</code>.
     */
    public final BountiesV3 BOUNTIES_V3 = BountiesV3.BOUNTIES_V3;

    /**
     * The table <code>COUNTER_STATS</code>.
     */
    public final CounterStats COUNTER_STATS = CounterStats.COUNTER_STATS;

    /**
     * The table <code>WARS</code>.
     */
    public final Wars WARS = Wars.WARS;

    /**
     * No further instances allowed
     */
    private DefaultSchema() {
        super("", null);
    }


    @Override
    public Catalog getCatalog() {
        return DefaultCatalog.DEFAULT_CATALOG;
    }

    @Override
    public final List<Table<?>> getTables() {
        return Arrays.asList(
            AttackSubcategoryCache.ATTACK_SUBCATEGORY_CACHE,
            Attacks2.ATTACKS2,
            Blockaded.BLOCKADED,
            BountiesV3.BOUNTIES_V3,
            CounterStats.COUNTER_STATS,
            Wars.WARS
        );
    }
}
