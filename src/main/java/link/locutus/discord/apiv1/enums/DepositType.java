package link.locutus.discord.apiv1.enums;

import link.locutus.discord.util.MathMan;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public enum DepositType {
    DEPOSIT("For funds directly deposited or withdrawn"),
    TAX("For city raw consumption or taxes"),
    LOAN("For funds members are expected to repay at some date in the future"),
    GRANT("Can be excluded from withdrawal limit, considered a loan if no time is specified e.g. `#expire=60d` or `#decay=3w`"),
    IGNORE("Excluded from deposits"),
    TRADE("Sub type of deposits, earmarked as trading funds"),

    CITY(GRANT, "Go to <https://politicsandwar.com/city/create/> and purchase a new city", true),
    PROJECT(GRANT, "Go to <https://politicsandwar.com/nation/projects/> and purchase the desired project", true),
    INFRA(GRANT, "Go to your city <https://politicsandwar.com/cities/> and purchase the desired infrastructure", true),
    LAND(GRANT, "Go to your city <https://politicsandwar.com/cities/> and purchase the desired land", true),
    BUILD(GRANT, "Go to <https://politicsandwar.com/city/improvements/bulk-import/> and import the desired build", true),
    WARCHEST(GRANT, "Go to <https://politicsandwar.com/nation/military/> and purchase the desired units", true),
    RAWS(GRANT, "Raw resources for city consumption", true),

    EXPIRE(GRANT, "Will be excluded from deposits after the specified time e.g. `#expire=60d`", false),
    DECAY(GRANT, "Expires by reducing linearly over time until 0 e.g. `#decay=60d`", false),

    ;

    private final String description;
    private DepositType parent;
    private boolean isClassifier;

    DepositType(String description) {
        this(null, description, false);
    }

    DepositType(DepositType parent, String description, boolean isClassifier) {
        this.parent = parent;
        this.description = description;
        this.isClassifier = isClassifier;
    }

    public boolean isClassifier() {
        return isClassifier;
    }

    public String getDescription() {
        return description;
    }

    public DepositType getParent() {
        return parent;
    }

    public DepositTypeInfo withValue() {
        return withValue(0, 0);
    }

    public DepositTypeInfo withAmount(long amount) {
        return withValue(amount, 0);
    }

    public DepositTypeInfo withCity(long city) {
        return withValue(0, city);
    }

    public DepositTypeInfo withValue(long amount, long city) {
        return new DepositTypeInfo(this, amount, city, false);
    }

    public DepositTypeInfo withValue(long amount, long city, boolean ignore) {
        return new DepositTypeInfo(this, amount, city, ignore);
    }


    public static class DepositTypeInfo {
        public final DepositType type;
        public final long amount;
        public final long city;
        public boolean ignore;

        public DepositTypeInfo(DepositType type, long amount, long city, boolean ignore) {
            this.type = type;
            this.amount = amount;
            this.city = city;
            this.ignore = ignore;
        }

        public DepositTypeInfo clone() {
            return new DepositTypeInfo(type, amount, city, ignore);
        }

        public DepositTypeInfo(DepositType type) {
            this(type, 0, 0, false);
        }

        public DepositTypeInfo ignore(boolean value) {
            this.ignore = value;
            return this;
        }

        public DepositType getType() {
            return type;
        }

        public long getAmount() {
            return amount;
        }

        public long getCity() {
            return city;
        }

        public String toString(long accountId) {
            String result = toString();
            if (accountId != 0) {
                if (type.parent == null) {
                    if (result.contains("=")) {
                        throw new IllegalArgumentException("Deposit type " + type.name() + " already has a value");
                    }
                    if (result.contains("#ignore")) {
                        String typeName = type.name().toLowerCase(Locale.ROOT);
                        result = result.replace(typeName, typeName + "=" + accountId);
                    } else {
                        result += "=" + accountId;
                    }
                } else if (result.contains("#ignore")) {
                    result += "=" + accountId;
                } else {
                    result = "#" + type.parent.name().toLowerCase(Locale.ROOT) + "=" + accountId + " " + result;
                }
            }
            return result;
        }

        @Override
        public String toString() {
            String note = "#" + type.name().toLowerCase(Locale.ROOT);
            if (amount != 0) {
                note += "=" + amount;
            }
            if (city != 0) {
                note += " #city=" + city;
            }
            if (ignore && type != IGNORE) {
                note += " #ignore";
            }
            return note.trim();
        }

        public boolean isDeposits() {
            return (type == DEPOSIT || type == TRADE) && !isIgnored();
        }

        public boolean isIgnored() {
            return ignore || type == IGNORE;
        }

        public DepositTypeInfo applyClassifiers(Map<String, String> parsed) {
            for (Map.Entry<String, String> noteEntry : parsed.entrySet()) {
                String noteStr = noteEntry.getKey().substring(1);
                String valueStr = noteEntry.getValue();
                boolean ignore = isIgnored();
                try {
                    DepositType type = DepositType.valueOf(noteStr.toUpperCase(Locale.ROOT));
                    if (!type.isClassifier()) {
                        throw new IllegalArgumentException();
                    }
                    long amount = getAmount();
                    if (valueStr != null && !valueStr.isEmpty() && MathMan.isInteger(valueStr)) {
                        amount = Long.parseLong(valueStr);
                    }
                    return new DepositTypeInfo(type, amount, 0, ignore);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Cannot apply modifier: `" + noteStr + "`, only " +
                            Arrays.stream(DepositType.values()).filter(DepositType::isClassifier).map(DepositType::name).toList());
                }
            }
            return this;
        }
    }
}