/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.nations.tables.pojos;


import java.io.Serializable;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Kicks implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Integer nation;
    private final Integer alliance;
    private final Long date;
    private final Integer type;

    public Kicks(Kicks value) {
        this.nation = value.nation;
        this.alliance = value.alliance;
        this.date = value.date;
        this.type = value.type;
    }

    public Kicks(
        Integer nation,
        Integer alliance,
        Long date,
        Integer type
    ) {
        this.nation = nation;
        this.alliance = alliance;
        this.date = date;
        this.type = type;
    }

    /**
     * Getter for <code>KICKS.nation</code>.
     */
    public Integer getNation() {
        return this.nation;
    }

    /**
     * Getter for <code>KICKS.alliance</code>.
     */
    public Integer getAlliance() {
        return this.alliance;
    }

    /**
     * Getter for <code>KICKS.date</code>.
     */
    public Long getDate() {
        return this.date;
    }

    /**
     * Getter for <code>KICKS.type</code>.
     */
    public Integer getType() {
        return this.type;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Kicks other = (Kicks) obj;
        if (this.nation == null) {
            if (other.nation != null)
                return false;
        }
        else if (!this.nation.equals(other.nation))
            return false;
        if (this.alliance == null) {
            if (other.alliance != null)
                return false;
        }
        else if (!this.alliance.equals(other.alliance))
            return false;
        if (this.date == null) {
            if (other.date != null)
                return false;
        }
        else if (!this.date.equals(other.date))
            return false;
        if (this.type == null) {
            if (other.type != null)
                return false;
        }
        else if (!this.type.equals(other.type))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.nation == null) ? 0 : this.nation.hashCode());
        result = prime * result + ((this.alliance == null) ? 0 : this.alliance.hashCode());
        result = prime * result + ((this.date == null) ? 0 : this.date.hashCode());
        result = prime * result + ((this.type == null) ? 0 : this.type.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Kicks (");

        sb.append(nation);
        sb.append(", ").append(alliance);
        sb.append(", ").append(date);
        sb.append(", ").append(type);

        sb.append(")");
        return sb.toString();
    }
}
