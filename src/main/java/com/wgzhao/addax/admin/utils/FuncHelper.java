package com.wgzhao.addax.admin.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Helper class that implements the functionality of Oracle functions
 */
@Component
public class FuncHelper {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Implementation of getparam
     * Returns parameter value from vw_imp_param table
     *
     * @param dateKind  Parameter kind
     * @param paramSou  Parameter source
     * @return          Parameter value as string
     */
    public String getParam(String dateKind, String paramSou) {
        String sql = "SELECT param_value FROM vw_imp_param WHERE param_kind_0 = ? AND param_sou = ?";
        return jdbcTemplate.queryForObject(sql, String.class, dateKind, paramSou);
    }

    /**
     * Implementation of getparam with default parameter source
     *
     * @param dateKind  Parameter kind
     * @return          Parameter value as string
     */
    public String getParam(String dateKind) {
        return getParam(dateKind, "C");
    }

    /**
     * Implementation of gettd
     * Returns the current trade date with format yyyyMMdd
     *
     * @return  Trade date as integer
     */
    public Integer getTd() {
        String paramValue = getParam("TD", "C");
        return Integer.parseInt(paramValue);
    }

    /**
     * Implementation of getntd
     * Returns the next trade date
     *
     * @return  Next trade date as integer
     */
    public Integer getNtd() {
        String paramValue = getParam("NTD", "C");
        return Integer.parseInt(paramValue);
    }

    /**
     * Implementation of getltd
     * Returns the last trade date
     *
     * @return  Last trade date as integer
     */
    public Integer getLtd() {
        String paramValue = getParam("LTD", "C");
        return Integer.parseInt(paramValue);
    }

    /**
     * Implementation of fn_imp_comment_replace
     * Replaces special characters in text
     *
     * @param text  Input text
     * @return      Text with replaced characters
     */
    public String fnImpCommentReplace(String text) {
        if (text == null) return null;

        return text.replace("\n", "")
                  .replace("\u0013", "") // chr(19)
                  .replace("'", "")
                  .replace("\"", "")
                  .replace("\\", "");
    }

    /**
     * Implementation of fn_imp_freqchk
     * Checks if the current date matches the frequency condition
     *
     * @param freq  Frequency string
     * @return      1 if matches, 0 if not
     */
    public int fnImpFreqChk(String freq) {
        if (freq == null || freq.isEmpty()) {
            return 0;
        }

        String sql = "WITH t_param AS " +
                     "(SELECT CASE WHEN SUBSTR(param_kind_0, -1) = '0' THEN LOWER(SUBSTR(param_kind_0, 2, 1)) " +
                     "ELSE SUBSTR(param_kind_0, 2, 1) END kind, param_value " +
                     "FROM vw_imp_param WHERE param_sou = 'C' AND param_kind_0 IN " +
                     "('TD', 'CW1', 'CM1', 'CQ1', 'CY1', 'CW0', 'CM0', 'CQ0', 'CY0')), " +
                     "t_nextdate AS (SELECT init_date, " +
                     "LEAD(init_date, ?) OVER(ORDER BY init_date) next_date " +
                     "FROM vw_trade_date), " +
                     "t_range AS (SELECT param_value start_dt, a.next_date end_dt " +
                     "FROM t_param t INNER JOIN t_nextdate a ON a.init_date = t.param_value " +
                     "WHERE kind = ?) " +
                     "SELECT COUNT(1) FROM t_range WHERE " +
                     "(SELECT param_value FROM t_param WHERE kind = 'D') BETWEEN start_dt AND end_dt";

        String strfreq = freq.substring(0, 1);
        int noffset = 0;
        if (freq.length() > 1) {
            try {
                noffset = Integer.parseInt(freq.substring(1, Math.min(freq.length(), 3))) - 1;
            } catch (NumberFormatException ignored) {
            }
        }

        return jdbcTemplate.queryForObject(sql, Integer.class, noffset, strfreq);
    }

    /**
     * Implementation of fn_imp_param_replace
     * Replaces parameters in text
     *
     * @param comText   Input text
     * @param paramSou  Parameter source
     * @return          Text with replaced parameters
     */
    public String fnImpParamReplace(String comText, String paramSou) {
        if (comText == null) return null;

        String result = comText;

        // Replace parameters from vw_imp_param
        String sql = "SELECT param_kind, param_value FROM vw_imp_param WHERE param_sou = ? AND param_kind IS NOT NULL";
        jdbcTemplate.query(sql, (rs) -> {
            String paramKind = rs.getString("param_kind");
            String paramValue = rs.getString("param_value");
            result.replace(paramKind, paramValue);
        }, paramSou);

        // Replace special parameters
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat shortDateFormat = new SimpleDateFormat("yyyyMMdd");
        Date now = new Date();

        return result
            .replace("${NOW}", fullDateFormat.format(now))
            .replace("${NO}", shortDateFormat.format(now))
            .replace("${UUID}", UUID.randomUUID().toString().replace("-", "").toUpperCase());
    }

    /**
     * Implementation of fn_imp_param_replace with default parameter source
     *
     * @param comText   Input text
     * @return          Text with replaced parameters
     */
    public String fnImpParamReplace(String comText) {
        return fnImpParamReplace(comText, "C");
    }

    /**
     * Implementation of fn_imp_pnname
     * Returns plan name based on plan type and parameters
     *
     * @param pnType    Plan type
     * @param fixed     Fixed time
     * @param interval  Time interval
     * @param range     Time range
     * @return          Plan name
     */
    public String fnImpPnname(String pnType, String fixed, Integer interval, String range) {
        if (pnType == null) return null;

        String sql = "SELECT entry_content FROM tb_dictionary WHERE entry_code = '1064' AND entry_value = ?";
        String planName = jdbcTemplate.queryForObject(sql, String.class, pnType);

        if (fixed != null || interval != null || range != null) {
            if (fixed != null) {
                planName += "定时" + fixed;
            } else {
                planName += range + "内_间隔" + interval + "分钟";
            }
        }

        return planName;
    }

    /**
     * Implementation of fn_imp_pntype
     * Checks if the plan type matches current conditions
     *
     * @param pnType    Plan type
     * @return          1 if matches, 0 if not
     */
    public int fnImpPntype(String pnType) {
        if (pnType == null) return 0;

        if ("0".equals(pnType)) {
            return 1;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String currentDate = dateFormat.format(new Date());
        Integer td = getTd();
        Integer ntd = getNtd();

        if (("2".equals(pnType) || "3".equals(pnType)) &&
            (currentDate.equals(td.toString()) || currentDate.equals(ntd.toString()))) {
            return 1;
        }

        if (("1".equals(pnType) || "3".equals(pnType)) && "Y".equals(getParam("TF"))) {
            return 1;
        }

        return 0;
    }

    /**
     * Implementation of fn_imp_timechk
     * Checks if current time matches the time condition
     *
     * @param currTime  Current time
     * @param fixed     Fixed time
     * @param interval  Time interval
     * @param range     Time range
     * @param exit      Exit flag
     * @return          1 if matches, 0 if not
     */
    public int fnImpTimechk(Date currTime, String fixed, Integer interval, String range, String exit) {
        if (currTime == null) return 0;

        String sql1 = "SELECT dt_full FROM vw_imp_date WHERE dt = ?";
        String sql2 = "SELECT dt_full FROM vw_imp_date WHERE dt = ?";

        int range1;
        int range2;

        if (range != null) {
            String[] ranges = range.split("-");
            String startRange = ranges.length > 0 ? ranges[0] : "0";
            String endRange = ranges.length > 1 ? ranges[1] : "2359";

            range1 = jdbcTemplate.queryForObject(sql1, Integer.class, startRange);
            range2 = jdbcTemplate.queryForObject(sql2, Integer.class, endRange);
        } else {
            range1 = 0;
            range2 = 2359;
        }

        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmm");
        int currentTime = Integer.parseInt(timeFormat.format(currTime));

        // Check interval-based condition
        if (interval != null && interval > 0) {
            // This is a simplified version - the full implementation would need complex time calculations
            if ((range1 < range2 && currentTime >= range1 && currentTime <= range2) ||
                (range1 > range2 && (currentTime >= range1 || currentTime <= range2))) {

                // Check if current minutes are divisible by interval
                SimpleDateFormat minutesFormat = new SimpleDateFormat("HH:mm");
                String[] timeParts = minutesFormat.format(currTime).split(":");
                int totalMinutes = Integer.parseInt(timeParts[0]) * 60 + Integer.parseInt(timeParts[1]);

                if (totalMinutes % interval == 0) {
                    return 1;
                }
            }
        }

        // Check fixed time condition
        if (fixed != null && !fixed.isEmpty() && (currentTime == 0 || currentTime > 23)) {
            String formattedTime = String.valueOf(currentTime);
            formattedTime = formattedTime.replaceAll("^0+|00$", "0");

            if (("," + fixed + ",").contains("," + formattedTime + ",")) {
                return 1;
            }
        }

        return 0;
    }

    /**
     * Implementation of fn_imp_timechk with default exit parameter
     */
    public int fnImpTimechk(Date currTime, String fixed, Integer interval, String range) {
        return fnImpTimechk(currTime, fixed, interval, range, "Y");
    }

    /**
     * Implementation of fn_imp_value
     * This is a complex function with many cases - implementing a simplified version
     * that handles some common cases
     *
     * @param kind     Value kind
     * @param spId     SP ID
     * @param value1   Additional value
     * @return         Result value
     */
    public String fnImpValue(String kind, String spId, String value1) {
        if (kind == null) return "";

        // Handle specific cases as needed
        switch (kind) {
            case "com_text":
                String sql = "SELECT com_text FROM tb_imp_sp_com WHERE com_id = ?";
                String comText = jdbcTemplate.queryForObject(sql, String.class, spId);

                // This is simplified - the full implementation would need to handle more replacements
                if (comText != null) {
                    return fnImpParamReplace(comText);
                }
                return "";

            case "dest_part":
                sql = "SELECT CASE WHEN etl_kind = 'R' THEN '0' ELSE " +
                      "CASE WHEN dest_part_kind IN ('D', 'M', 'Q') THEN '${T' || dest_part_kind || '}' " +
                      "ELSE '1' END END FROM vw_imp_etl WHERE tid = ?";
                return jdbcTemplate.queryForObject(sql, String.class, spId);

            case "sysname":
                String shortFlag = "short".equals(value1) ? "" : "sysid||'_'||";
                sql = "SELECT " + shortFlag + "sys_name FROM vw_imp_system WHERE sysid = ?";
                return jdbcTemplate.queryForObject(sql, String.class, spId);

            // Add more cases as needed

            default:
                return "";
        }
    }

    /**
     * Implementation of fn_imp_value with default parameters
     */
    public String fnImpValue(String kind) {
        return fnImpValue(kind, "", "");
    }

    public String fnImpValue(String kind, String spId) {
        return fnImpValue(kind, spId, "");
    }
}