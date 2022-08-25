/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.locutus.tables.pojos;


import java.io.Serializable;
import java.util.Arrays;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DiscordMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long key;
    private final Long id;
    private final byte[] value;

    public DiscordMeta(DiscordMeta value) {
        this.key = value.key;
        this.id = value.id;
        this.value = value.value;
    }

    public DiscordMeta(
        Long key,
        Long id,
        byte[] value
    ) {
        this.key = key;
        this.id = id;
        this.value = value;
    }

    /**
     * Getter for <code>DISCORD_META.key</code>.
     */
    public Long getKey() {
        return this.key;
    }

    /**
     * Getter for <code>DISCORD_META.id</code>.
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Getter for <code>DISCORD_META.value</code>.
     */
    public byte[] getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final DiscordMeta other = (DiscordMeta) obj;
        if (this.key == null) {
            if (other.key != null)
                return false;
        }
        else if (!this.key.equals(other.key))
            return false;
        if (this.id == null) {
            if (other.id != null)
                return false;
        }
        else if (!this.id.equals(other.id))
            return false;
        if (this.value == null) {
            if (other.value != null)
                return false;
        }
        else if (!Arrays.equals(this.value, other.value))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.key == null) ? 0 : this.key.hashCode());
        result = prime * result + ((this.id == null) ? 0 : this.id.hashCode());
        result = prime * result + ((this.value == null) ? 0 : Arrays.hashCode(this.value));
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DiscordMeta (");

        sb.append(key);
        sb.append(", ").append(id);
        sb.append(", ").append("[binary...]");

        sb.append(")");
        return sb.toString();
    }
}
