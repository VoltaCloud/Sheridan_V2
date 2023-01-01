/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.war.tables.pojos;


import java.io.Serializable;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Wars implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Integer id;
    private final Integer attackerId;
    private final Integer defenderId;
    private final Integer attackerAa;
    private final Integer defenderAa;
    private final Integer warType;
    private final Integer status;
    private final Long date;

    public Wars(Wars value) {
        this.id = value.id;
        this.attackerId = value.attackerId;
        this.defenderId = value.defenderId;
        this.attackerAa = value.attackerAa;
        this.defenderAa = value.defenderAa;
        this.warType = value.warType;
        this.status = value.status;
        this.date = value.date;
    }

    public Wars(
        Integer id,
        Integer attackerId,
        Integer defenderId,
        Integer attackerAa,
        Integer defenderAa,
        Integer warType,
        Integer status,
        Long date
    ) {
        this.id = id;
        this.attackerId = attackerId;
        this.defenderId = defenderId;
        this.attackerAa = attackerAa;
        this.defenderAa = defenderAa;
        this.warType = warType;
        this.status = status;
        this.date = date;
    }

    /**
     * Getter for <code>WARS.id</code>.
     */
    public Integer getId() {
        return this.id;
    }

    /**
     * Getter for <code>WARS.attacker_id</code>.
     */
    public Integer getAttackerId() {
        return this.attackerId;
    }

    /**
     * Getter for <code>WARS.defender_id</code>.
     */
    public Integer getDefenderId() {
        return this.defenderId;
    }

    /**
     * Getter for <code>WARS.attacker_aa</code>.
     */
    public Integer getAttackerAa() {
        return this.attackerAa;
    }

    /**
     * Getter for <code>WARS.defender_aa</code>.
     */
    public Integer getDefenderAa() {
        return this.defenderAa;
    }

    /**
     * Getter for <code>WARS.war_type</code>.
     */
    public Integer getWarType() {
        return this.warType;
    }

    /**
     * Getter for <code>WARS.status</code>.
     */
    public Integer getStatus() {
        return this.status;
    }

    /**
     * Getter for <code>WARS.date</code>.
     */
    public Long getDate() {
        return this.date;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Wars other = (Wars) obj;
        if (this.id == null) {
            if (other.id != null)
                return false;
        }
        else if (!this.id.equals(other.id))
            return false;
        if (this.attackerId == null) {
            if (other.attackerId != null)
                return false;
        }
        else if (!this.attackerId.equals(other.attackerId))
            return false;
        if (this.defenderId == null) {
            if (other.defenderId != null)
                return false;
        }
        else if (!this.defenderId.equals(other.defenderId))
            return false;
        if (this.attackerAa == null) {
            if (other.attackerAa != null)
                return false;
        }
        else if (!this.attackerAa.equals(other.attackerAa))
            return false;
        if (this.defenderAa == null) {
            if (other.defenderAa != null)
                return false;
        }
        else if (!this.defenderAa.equals(other.defenderAa))
            return false;
        if (this.warType == null) {
            if (other.warType != null)
                return false;
        }
        else if (!this.warType.equals(other.warType))
            return false;
        if (this.status == null) {
            if (other.status != null)
                return false;
        }
        else if (!this.status.equals(other.status))
            return false;
        if (this.date == null) {
            if (other.date != null)
                return false;
        }
        else if (!this.date.equals(other.date))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.id == null) ? 0 : this.id.hashCode());
        result = prime * result + ((this.attackerId == null) ? 0 : this.attackerId.hashCode());
        result = prime * result + ((this.defenderId == null) ? 0 : this.defenderId.hashCode());
        result = prime * result + ((this.attackerAa == null) ? 0 : this.attackerAa.hashCode());
        result = prime * result + ((this.defenderAa == null) ? 0 : this.defenderAa.hashCode());
        result = prime * result + ((this.warType == null) ? 0 : this.warType.hashCode());
        result = prime * result + ((this.status == null) ? 0 : this.status.hashCode());
        result = prime * result + ((this.date == null) ? 0 : this.date.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Wars (");

        sb.append(id);
        sb.append(", ").append(attackerId);
        sb.append(", ").append(defenderId);
        sb.append(", ").append(attackerAa);
        sb.append(", ").append(defenderAa);
        sb.append(", ").append(warType);
        sb.append(", ").append(status);
        sb.append(", ").append(date);

        sb.append(")");
        return sb.toString();
    }
}