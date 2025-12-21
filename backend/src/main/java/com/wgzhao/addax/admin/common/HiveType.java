package com.wgzhao.addax.admin.common;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wgzhao.addax.admin.common.Constants.HIVE_DECIMAL_MAX_PRECISION;
import static com.wgzhao.addax.admin.common.Constants.HIVE_DECIMAL_MAX_SCALE;

/**
 * Lightweight Hive type struct
 */
public class HiveType
{
    final HiveType.Base base;
    final Integer param1; // e.g., length or precision
    final Integer param2; // e.g., scale
    final String raw;
    HiveType(HiveType.Base base, Integer p1, Integer p2, String raw)
    {
        this.base = base;
        this.param1 = p1;
        this.param2 = p2;
        this.raw = raw;
    }

    static HiveType.Base normalizeBase(HiveType.Base b)
    {
        // treat STRING/VARCHAR/CHAR distinct for equality, but compatibility handles relaxation
        return b;
    }

    public static HiveType parse(String raw)
    {
        String s = raw.trim().toLowerCase();
        // strip extra spaces
        s = s.replaceAll("\\s+", " ");
        // patterns
        Pattern decimal = Pattern.compile("decimal\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
        Pattern varchar = Pattern.compile("varchar\\s*\\(\\s*(\\d+)\\s*\\)");
        Pattern charp = Pattern.compile("char\\s*\\(\\s*(\\d+)\\s*\\)");

        Matcher m;
        if ((m = decimal.matcher(s)).matches()) {
            int p = Integer.parseInt(m.group(1));
            int sc = Integer.parseInt(m.group(2));
            // Clamp parsed DECIMAL to Hive max to avoid out-of-range comparisons later
            p = Math.min(p, HIVE_DECIMAL_MAX_PRECISION);
            sc = Math.min(sc, HIVE_DECIMAL_MAX_SCALE);
            return new HiveType(HiveType.Base.DECIMAL, p, sc, raw);
        }
        if ((m = varchar.matcher(s)).matches()) {
            return new HiveType(HiveType.Base.VARCHAR, Integer.parseInt(m.group(1)), null, raw);
        }
        if ((m = charp.matcher(s)).matches()) {
            return new HiveType(HiveType.Base.CHAR, Integer.parseInt(m.group(1)), null, raw);
        }
        // simple types
        return switch (s) {
            case "string" -> new HiveType(HiveType.Base.STRING, null, null, raw);
            case "binary" -> new HiveType(HiveType.Base.BINARY, null, null, raw);
            case "boolean" -> new HiveType(HiveType.Base.BOOLEAN, null, null, raw);
            case "tinyint" -> new HiveType(HiveType.Base.TINYINT, null, null, raw);
            case "smallint" -> new HiveType(HiveType.Base.SMALLINT, null, null, raw);
            case "int", "integer" -> new HiveType(HiveType.Base.INT, null, null, raw);
            case "bigint" -> new HiveType(HiveType.Base.BIGINT, null, null, raw);
            case "float" -> new HiveType(HiveType.Base.FLOAT, null, null, raw);
            case "double" -> new HiveType(HiveType.Base.DOUBLE, null, null, raw);
            case "date" -> new HiveType(HiveType.Base.DATE, null, null, raw);
            case "timestamp" -> new HiveType(HiveType.Base.TIMESTAMP, null, null, raw);
            default -> {
                // detect complex types roughly
                if (s.startsWith("array<")) {
                    yield new HiveType(HiveType.Base.ARRAY, null, null, raw);
                }
                if (s.startsWith("map<")) {
                    yield new HiveType(HiveType.Base.MAP, null, null, raw);
                }
                if (s.startsWith("struct<")) {
                    yield new HiveType(HiveType.Base.STRUCT, null, null, raw);
                }
                if (s.startsWith("uniontype<")) {
                    yield new HiveType(HiveType.Base.UNION, null, null, raw);
                }
                yield new HiveType(HiveType.Base.UNKNOWN, null, null, raw);
            }
        };
    }

    /**
     * Determine whether current Hive column type is compatible (equal or more relaxed) than desired type.
     * If compatible, we skip changing the column type.
     */
    public static boolean isHiveTypeCompatible(String currentTypeRaw, String desiredTypeRaw)
    {
        if (currentTypeRaw == null || desiredTypeRaw == null) {
            return false;
        }
        HiveType current = HiveType.parse(currentTypeRaw);
        HiveType desired = HiveType.parse(desiredTypeRaw);

        // Exact match
        if (current.equals(desired)) {
            return true;
        }

        // Special rule: if desired is DECIMAL, only compatible when current is DECIMAL or STRING
        if (desired.base == HiveType.Base.DECIMAL) {
            return current.base == HiveType.Base.DECIMAL || current.base == HiveType.Base.STRING;
        }

        // Strings are most relaxed and can hold anything
        if (current.base == HiveType.Base.STRING) {
            return true;
        }
        // Binary is also very relaxed for arbitrary bytes
        if (current.base == HiveType.Base.BINARY && desired.isPrimitive()) {
            return true;
        }

        // Numeric hierarchy: DOUBLE > FLOAT > DECIMAL(p,s) > BIGINT > INT > SMALLINT > TINYINT
        if (current.base.isNumeric() && desired.base.isNumeric()) {
            return isNumericWiderOrEqual(current, desired);
        }

        // CHAR/VARCHAR compatibility
        if (current.base == HiveType.Base.VARCHAR || current.base == HiveType.Base.CHAR) {
            // string desired always compatible
            if (desired.base == HiveType.Base.STRING) {
                return true;
            }
            if (desired.base == HiveType.Base.VARCHAR || desired.base == HiveType.Base.CHAR) {
                int curLen = current.param1OrDefault(65535); // assume large defaults
                int desLen = desired.param1OrDefault(0);
                return curLen >= desLen;
            }
            // numeric to varchar/char shouldn't be forced; treat current varchar/char as relaxed
            if (desired.base.isNumeric()) {
                return true;
            }
        }

        // DATE/TIMESTAMP: timestamp can represent date
        if (current.base == HiveType.Base.TIMESTAMP && desired.base == HiveType.Base.DATE) {
            return true;
        }
        if (current.base == HiveType.Base.TIMESTAMP && desired.base == HiveType.Base.TIMESTAMP) {
            return true;
        }
        if (current.base == HiveType.Base.DATE && desired.base == HiveType.Base.DATE) {
            return true;
        }

        // Boolean: only compatible with boolean
        if (current.base == HiveType.Base.BOOLEAN) {
            return desired.base == HiveType.Base.BOOLEAN;
        }

        // For complex types or mismatched primitives, default to not compatible
        return false;
    }

    private static boolean isNumericWiderOrEqual(HiveType cur, HiveType des)
    {
        // Clamp DECIMAL precision/scale to Hive's max supported before any comparisons
        // This ensures comparisons are made within Hive capabilities (max (38,10))
        if (cur.base == HiveType.Base.DECIMAL) {
            int cp = cur.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int cs = cur.param2OrDefault(HIVE_DECIMAL_MAX_SCALE);
            cp = Math.min(cp, HIVE_DECIMAL_MAX_PRECISION);
            cs = Math.min(cs, HIVE_DECIMAL_MAX_SCALE);
            cur = new HiveType(HiveType.Base.DECIMAL, cp, cs, cur.raw);
        }
        if (des.base == HiveType.Base.DECIMAL) {
            int dp = des.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int ds = des.param2OrDefault(HIVE_DECIMAL_MAX_SCALE);
            dp = Math.min(dp, HIVE_DECIMAL_MAX_PRECISION);
            ds = Math.min(ds, HIVE_DECIMAL_MAX_SCALE);
            des = new HiveType(HiveType.Base.DECIMAL, dp, ds, des.raw);
        }

        // Handle decimal precision/scale
        if (cur.base == HiveType.Base.DECIMAL && des.base == HiveType.Base.DECIMAL) {
            int cp = cur.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int cs = cur.param2OrDefault(HIVE_DECIMAL_MAX_SCALE);
            int dp = des.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int ds = des.param2OrDefault(HIVE_DECIMAL_MAX_SCALE);
            return cp >= dp && cs >= ds;
        }
        // Decimal can hold integers/floats depending on precision/scale; treat decimal as between float and bigint depending on scale
        if (cur.base == HiveType.Base.DECIMAL && (des.base == HiveType.Base.BIGINT || des.base == HiveType.Base.INT || des.base == HiveType.Base.SMALLINT || des.base == HiveType.Base.TINYINT)) {
            // integer desired: decimal with scale 0 and precision high enough is compatible; if unknown, assume compatible
            int cs = cur.param2OrDefault(0);
            int cp = cur.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int needPrecision = switch (des.base) {
                case BIGINT -> 19; // up to 9223372036854775807
                case INT -> 10;
                case SMALLINT -> 5;
                case TINYINT -> 3;
                default -> 0;
            };
            return cs == 0 && cp >= needPrecision;
        }
        if (cur.base == HiveType.Base.DECIMAL && (des.base == HiveType.Base.FLOAT || des.base == HiveType.Base.DOUBLE)) {
            // decimal to float/double: float/double are wider; decimal is not wider for arbitrary floats
            return false;
        }
        if ((cur.base == HiveType.Base.FLOAT || cur.base == HiveType.Base.DOUBLE) && des.base == HiveType.Base.DECIMAL) {
            // float/double are wider than decimal
            return true;
        }

        int curRank = cur.base.numericRank();
        int desRank = des.base.numericRank();
        return curRank >= desRank; // higher rank means wider
    }

    public boolean isPrimitive()
    {
        return base != HiveType.Base.ARRAY && base != HiveType.Base.MAP && base != HiveType.Base.STRUCT && base != HiveType.Base.UNION && base != HiveType.Base.UNKNOWN;
    }

    public int param1OrDefault(int def) {return param1 == null ? def : param1;}

    public int param2OrDefault(int def) {return param2 == null ? def : param2;}

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HiveType other)) {
            return false;
        }
        return Objects.equals(normalizeBase(this.base), normalizeBase(other.base)) &&
            Objects.equals(this.param1, other.param1) &&
            Objects.equals(this.param2, other.param2);
    }

    enum Base
    {
        STRING, VARCHAR, CHAR, BINARY,
        BOOLEAN,
        TINYINT, SMALLINT, INT, BIGINT,
        FLOAT, DOUBLE,
        DECIMAL,
        DATE, TIMESTAMP,
        // others: complex
        ARRAY, MAP, STRUCT, UNION, UNKNOWN;

        public boolean isNumeric()
        {
            return this == TINYINT || this == SMALLINT || this == INT || this == BIGINT || this == FLOAT || this == DOUBLE || this == DECIMAL;
        }

        public int numericRank()
        {
            // smaller -> lower rank; wider -> higher rank
            return switch (this) {
                case TINYINT -> 1;
                case SMALLINT -> 2;
                case INT -> 3;
                case BIGINT -> 4;
                case DECIMAL -> 5; // treat decimal as wider than BIGINT for integers
                case FLOAT -> 6;
                case DOUBLE -> 7;
                default -> -1;
            };
        }
    }
}
