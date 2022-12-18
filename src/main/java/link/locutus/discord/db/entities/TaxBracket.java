package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;

import java.util.*;

public class TaxBracket {
    public final int taxId;
    public long dateFetched;
    private int allianceId;
    private String name;

    public int moneyRate;
    public int rssRate;

    public TaxBracket(int allianceId, Map<String, String> data) {
        name = data.get("Bracket Name");
        String[] nameSplit = name.substring(0, name.length() - 1).split("#");
        name = name.split(" \\(#")[0];

        taxId = Integer.parseInt(nameSplit[nameSplit.length - 1]);
        this.allianceId = allianceId;
        moneyRate = Integer.parseInt(data.get("Money Tax Rate").replaceAll("%", ""));
        rssRate = Integer.parseInt(data.get("Resource Tax Rate").replaceAll("%", ""));
        int nations = Integer.parseInt(data.get("Nations").split(" ")[0].replace(",", ""));

        this.dateFetched = System.currentTimeMillis();
    }

    public TaxBracket(int taxId, int allianceId, String name, int moneyRate, int rssRate, long date) {
        this.taxId = taxId;
        this.allianceId = allianceId;
        this.name = name;
        this.moneyRate = moneyRate;
        this.rssRate = rssRate;
        this.dateFetched = date;
    }

    public TaxBracket(com.politicsandwar.graphql.model.TaxBracket bracket) {
        this.taxId = bracket.getId();
        this.allianceId = bracket.getAlliance_id();
        this.name = bracket.getBracket_name();
        this.moneyRate = bracket.getTax_rate();
        this.rssRate = bracket.getResource_tax_rate();
        this.dateFetched = System.currentTimeMillis();
    }

    public int getAllianceId() {
        if (this.allianceId < 0) {
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(f -> f.getAlliance_id() == taxId);
            if (!nations.isEmpty()) {
                allianceId = nations.iterator().next().getAlliance_id();
            } else {
                allianceId = 0;
            }
        }
        return allianceId;
    }

    public Set<DBNation> getNations() {
        if (taxId == 0) return Collections.emptySet();
        if (getAllianceId() == 0) {
            DBAlliance alliance = DBAlliance.get(allianceId);
            if (alliance != null) return alliance.getNations(f -> f.getTax_id() == taxId);
            return Collections.emptySet();
        }
        return Locutus.imp().getNationDB().getNationsMatching(f -> f.getTax_id() == taxId);
    }

    public String getUrl() {
        return String.format("" + Settings.INSTANCE.PNW_URL() + "/index.php?id=15&tax_id=%s", taxId);
    }

    @Override
    public String toString() {
        return ((name != null && !name.isEmpty()) ? (name + " - ") : "") + "#" + taxId + " (" + moneyRate + "/" + rssRate + ")";
    }

    public TaxRate getTaxRate() {
        return new TaxRate(moneyRate, rssRate);
    }

    public String getName() {
        return name;
    }

    public int getAllianceId(boolean fetchIfUnknown) {
        return fetchIfUnknown ? getAllianceId() : allianceId;
    }
}
