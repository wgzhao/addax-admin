package com.wgzhao.addax.admin.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
     * Java implementation of fn_imp_comment_replace
     * Remove newline, chr(19), single quote, double quote, backslash
     * @param input input string
     * @return processed string
     */
    public static String impCommentReplace(String input) {
        if (input == null) {
            return null;
        }
        String result = input;
        result = result.replace("\n", "");
        result = result.replace(String.valueOf((char)19), "");
        result = result.replace("'", "");
        result = result.replace("\"", "");
        result = result.replace("\\", "");
        return result;
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

        return switch (kind.toUpperCase()) {
            case "PLAN_RUN" -> handlePlanRun();
            case "SP_RUN" -> handleSpRun();
            case "COM_TEXT" -> handleComText(spId);
            // 其他case分支...
            default -> throw new IllegalArgumentException("Unsupported i_kind: " + kind);
        };

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

    private String handlePlanRun() {
        String sql = """
                 WITH t_sp AS (
                  SELECT 'plan|' || pn_id::text AS sp_id FROM vw_imp_plan WHERE brun = 1 AND bpntype = 1
                  UNION ALL
                  SELECT 'judge|' || CASE bstart WHEN -1 THEN 'status_' WHEN 0 THEN 'start_' END || sysid::text
                  FROM vw_imp_etl_judge WHERE bstart IN (-1, 0) AND px = 1
                )
                SELECT string_agg(sp_id, chr(10) ORDER BY sp_id) FROM t_sp
                """;
        return jdbcTemplate.queryForObject(sql,String.class);
    }

    private String handleSpRun()  {
        String sql = """
                with t_sp as (
                         select 'sp' as kind, sp_id, 'sp'||sp_owner as dest_sys, runtime, brun
                         from vw_imp_sp
                         where brun = 1 or flag = 'R'
                         union all
                         select 'etl' as kind, tid as sp_id, sysid as dest_sys, runtime + runtime_add as runtime, brun
                         from vw_imp_etl
                         where brun = 1 or flag = 'R'
                         union all
                         select 'ds' as kind, ds_id as sp_id, 'ds'||dest_sysid as dest_sys, coalesce(runtime, 999) as runtime, brun
                         from vw_imp_ds2
                         where brun = 1 or flag = 'R'
                     ),
                     t_ranked as (
                         select kind, sp_id, dest_sys, brun, runtime,
                                row_number() over (partition by kind, dest_sys order by brun, runtime desc) as sys_px
                         from t_sp
                     ),
                     t_px as (
                         select
                             (case when kind = 'etl' then 'sp' else kind end) || '|' || sp_id as sp_id,
                             brun * row_number() over (order by brun, runtime + 20000.0 / nullif(sys_px, 0) desc) as px
                         from t_ranked
                         where sys_px <= coalesce(
                             (select db_paral from vw_imp_system where sysid = dest_sys and sys_kind = 'etl'),
                             8
                         )
                     )
                     select string_agg(sp_id, chr(10) order by px)
                     from t_px
                     where px between 1 and 100
                """;
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private String handleComText(String spId)  {
        String sql = "SELECT com_text FROM tb_imp_sp_com WHERE com_id = ?";
        Map<String, Object> stringObjectMap = jdbcTemplate.queryForMap(sql, spId);
        String tpl = stringObjectMap.getOrDefault("com_text", "").toString();
        if (tpl.isEmpty()) {
            return null;
        }
        sql = """
               select coalesce(a.param_sou,b.param_sou,'C') AS sou,b.tid
               from tb_imp_sp_com t
               left join tb_imp_sp a on a.sp_id = t.sp_id
               left join tb_imp_etl b on b.tid = t.sp_id
               where t.com_id = ?
               """;
        Map<String, Object> stringObjectMap1 = jdbcTemplate.queryForMap(sql, spId);
        String sou = stringObjectMap1.get("sou").toString();
        String tid = stringObjectMap1.getOrDefault("tid", "").toString();
        if (tid.isEmpty() ) {
            return  null;
        }

        sql = """
               select sou_db_constr, sou_db_user, sou_db_pass,
               case when t.sou_owner like '%-%' then '`'||t.sou_owner||'`' else t.sou_owner end||case when sou_db_kind='sqlserver' and sou_db_conf like '%[soutab_owner:table_catalog]%' then '..' else '.' end||t.sou_tablename as sou_tablename,
               sou_filter,
               '/ods/'||lower(replace(dest,'.','/'))||'/logdate=${dest_part}') as tag_tblname,

                """;
        return null;

    }

    private void handleJobfile(String spId) {
        String sql = """
                select a.jobfile,a.jobkind
                     from vw_imp_etl t
                     inner join vw_imp_jobfile a on a.jobkind=t.jobkind
                     where t.tid = ?
                """;
        Map<String, Object> map = jdbcTemplate.queryForMap(sql, spId);
        String jobfile = map.get("jobfile").toString();
        String jobkind = map.get("jobkind").toString();
        sql = """
            select jobkind,data_type,column_name,bquota,col_name,col_type,col_idx
            from vw_imp_etl_cols
            where tid = ?
            order by col_idx
            """;
        List<Map> columns = jdbcTemplate.queryForList(sql, Map.class, spId);
    }

    private String handleDestPart(String spId)
    {
        String sql = """
                      select case
                               when etl_kind = 'R' then
                                '0'
                               else
                                case
                                  when dest_part_kind in ('D', 'M', 'Q') then
                                   '${T' || dest_part_kind || '}'
                                  else
                                   '1'
                                end
                             end
                        from vw_imp_etl
                       where tid = ?
                """;
        return jdbcTemplate.queryForObject(sql, String.class, spId);
    }

    private String handleDestPartValue(String spId) {
        String  sql = """
                select COALESCE (a.param_value, '1')
                       from vw_imp_etl t
                       left join vw_imp_param a
                         on a.param_sou = t.param_sou
                        and a.param_kind_0 like 'T%'
                        and a.param_kind_0 = 'T' || dest_part_kind
                      where t.tid = ?
                """;
        return jdbcTemplate.queryForObject(sql,  String.class, spId);
    }

    private String handleSysname(String spId, String v1) {
        String sql = """
                     select case when ?='short' then sys_name else sysid||'_'||sys_name end
                      from vw_imp_system
                      where sysid = ? ;
                """;
        return jdbcTemplate.queryForObject(sql, String.class, v1, spId);
    }

    private String handleTaskname(String spId) {
        String sql = """
               select spname
                   from (select spname from vw_imp_etl where tid = :spId
                         union all
                         select spname from vw_imp_sp where sp_id = :spId
                         union all
                         select spname from vw_imp_plan where pn_id = :spId
                         union all
                         select ds_name from vw_imp_ds2_mid where :spId in(ds_id,tbl_id) limit 1
                         union all
                         select sysid||'_'||sys_name from vw_imp_system where sysid = :spId
                        ) t
               """;
        return jdbcTemplate.queryForObject(sql, String.class, spId);
    }

    private String getPnTypeList() {
         String sql = """
              select listagg(entry_content||'['||entry_value||']='||fn_imp_pntype(entry_value)||chr(10))within group(order by entry_value)
                     from tb_dictionary
                    where entry_code='1064' and entry_value<='3
              """;
         return jdbcTemplate.queryForObject(sql, String.class);
    }

    private String getHadoopSchema() {
        String sql = """
              SELECT 'ODS[A-Z0-9]{2}' || string_agg('|' || upper(db_name), '' ORDER BY length(db_name) DESC)
              FROM (SELECT db_name
                    FROM tb_imp_etl_tbls
                    WHERE col_idx = 1000
                      AND db_name NOT IN ('edwuf', 'edwuftp', 'kpiuf', 'kpiuftp', 'tmp', 'default')
                      AND db_name !~ '^ods'
                    GROUP BY db_name
                    UNION
                    SELECT sp_owner FROM vw_imp_sp WHERE bvalid=1 GROUP BY sp_owner) t
              """;
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private List<String> handleUpdtHive() {
        String sql = """
                 select replace(wm_concat(create_db), ',', chr(10)) || chr(10)
                        from vw_imp_system t
                       inner join (select sysid from vw_imp_etl where bcreate = 'Y' and bupdate = 'n' and bvalid = 1 group by sysid) a
                          on a.sysid = t.sysid
                       where t.sys_kind = 'etl' and t.bvalid = 1
                         and t.sysid not in (select fid from tb_imp_flag where tradedate = ? and fval = 4 and kind = 'ETL_END')
                """;
        List<String> result = jdbcTemplate.queryForList(sql, String.class, getTd());

        sql = """
                select tid, lower(dest) as tblname
                from vw_imp_etl
                where bcreate = 'Y' and bupdate = 'n' and bvalid = 1 and tid in(select tid from tb_imp_tbl_sou group by tid)
                """;
        List<Map> tables = jdbcTemplate.queryForList(sql, Map.class);
        for (Map table : tables) {
            String tid = table.get("tid").toString();
            String tblname = table.get("tblname").toString();
            String createSql = "create external table if not exists `" + tblname.replace(".", "`.`") + "`(";
            String columnsSql = """
                    select column_name, dest_type_full as column_type, col_comment as column_comment, column_id
                                        from tb_imp_tbl_sou t
                                        where tid = ?
                                        union all
                                        select entry_value,entry_content,remark,10000+row_number()over(order by entry_value)
                                        from tb_dictionary where entry_code='2015'
                                        order by column_id
                    """;
            List<Map> columns = jdbcTemplate.queryForList(columnsSql, Map.class);
            boolean fistColumn = true;
            for (Map column: columns) {
                if (fistColumn) {
                    createSql += "`" + column.get("column_name") + "` " + column.get("column_type");
                    fistColumn = false;
                } else {
                    createSql += ", `" + column.get("column_name") + "` " + column.get("column_type");
                }
                if (column.get("column_comment") != null) {
                    createSql += " COMMENT '" + column.get("column_comment") + "'";
                }
            }
            createSql += " )";
            // get the table comment
            String comment = jdbcTemplate.queryForObject("select max(tbl_comment) into strtmp1 from tb_imp_tbl_sou where tid = ? and tbl_comment is not null", String.class, tid);
            if (!comment.isEmpty()) {
                createSql += " COMMENT '" + comment + "'";
            }
            // storage format and location
            createSql += """
            partitioned by (logdate string)
            stored as orc
            location '/ods/""" + tblname.replace(".", "/") + "'"
            + " tblproperties ('orc.compress'='lz4');";

            result.add(createSql);
        }

        // alter table
        sql = """
                select t.alter_sql from vw_imp_tbl_diff_hive t
                inner join vw_imp_etl a on a.bcreate = 'N' and a.bupdate = 'n' and a.tid=t.tid
                """;
        jdbcTemplate.queryForList(sql, String.class).addAll(result);
        return result;
    }

    private List<String> handleUpdtMysql() {
        return jdbcTemplate.queryForList("select alter_sql from vw_imp_tbl_diff_mysql", String.class);
    }
}

