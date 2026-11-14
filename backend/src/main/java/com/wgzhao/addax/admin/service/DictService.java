package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.CalendarType;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.repository.SysItemRepo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.wgzhao.addax.admin.common.Constants.DEFAULT_SWITCH_TIME;
import static com.wgzhao.addax.admin.common.Constants.shortSdf;

/**
 * 字典服务类，负责系统参数字典及字典项的相关业务操作。
 * 包含参数获取、类型转换、业务日期计算、Hive 类型映射等功能。
 */
@Service
@AllArgsConstructor
@Slf4j
public class DictService
{
    // Built-in fallbacks so RDBMS targets work on existing deployments that predate the
    // wR / R2R dict rows. Fresh installs seed the same content via scripts/data.sql.
    private static final String DEFAULT_RDBMS_WRITER_TEMPLATE = """
        {
            "name": "${name}",
            "parameter": {
                "username": "${username}",
                "password": "${password}",
                "column": [${column}],
                "writeMode": "${writeMode}",
                "batchSize": 1024,
                "preSql": [],
                "postSql": [],
                "connection": {
                    "table": [ "${table}" ],
                    "jdbcUrl": "${jdbcUrl}"
                }
            }
        }
        """;

    private static final String DEFAULT_RDBMS2RDBMS_JOB_TEMPLATE = """
        {
          "job": {
            "content": {
                "reader": ${reader},
                 "writer": ${writer},
              "setting": {
                "speed": {
                  "batchSize": 20480,
                  "bytes": -1,
                  "channel": 1
                }
              }
            }
          }
        }
        """;

    private static final String DEFAULT_RDBMS_READER_TEMPLATE = """
        {
            "name": "${name}",
            "parameter": {
                "username": "${username}",
                "password": "${password}",
                "column": [${column}],
                "where": "${where}",
                "autoPk": "${autoPk}",
                "splitPk": "${splitPk}",
                "connection": {
                    "jdbcUrl": [ "${jdbcUrl}" ],
                    "table": [ "${table}" ]
                },
                "fetchSize": ${fetchSize}
            }
        }
        """;

    private static final String DEFAULT_HDFS_WRITER_TEMPLATE = """
        {
            "name": "hdfswriter",
            "parameter": {
              "defaultFS": "hdfs://yytz",
              "fileType": "${fileType}",
              "compress": "${compress}",
              "path": "${path}",
              "fileName": "addax",
              "writeMode": "overwrite",
              "column": ${column},
              "createPath": true,
              "haveKerberos": false,
              "hdfsSitePath": "/etc/hadoop/conf/hdfs-site.xml",
              "hadoopConfig": {}
            }
        }
        """;

    private static final String DEFAULT_RDBMS2HDFS_JOB_TEMPLATE = """
        {
          "job": {
            "content": {
                "reader": ${reader},
                 "writer": ${writer},
              "setting": {
                "speed": {
                  "batchSize": 20480,
                  "bytes": -1,
                  "channel": 1
                }
              }
            }
          }
        }
        """;

    // The placeholder contract: each ${token} is a name the job generator substitutes at render
    // time. Validation compares a submitted template's token set against its built-in default,
    // so the contract and the default can never drift apart.
    private static final Map<String, String> DEFAULT_TEMPLATES = Map.of(
        "R2H", DEFAULT_RDBMS2HDFS_JOB_TEMPLATE,
        "R2R", DEFAULT_RDBMS2RDBMS_JOB_TEMPLATE,
        "rR", DEFAULT_RDBMS_READER_TEMPLATE,
        "wH", DEFAULT_HDFS_WRITER_TEMPLATE,
        "wR", DEFAULT_RDBMS_WRITER_TEMPLATE);

    private static final java.util.regex.Pattern PLACEHOLDER =
        java.util.regex.Pattern.compile("\\$\\{([^{}]+)}");

    private final SysItemRepo sysItemRepo;
    private final ObjectMapper objectMapper;

    /**
     * 获取切日时间（如未配置则返回默认值）
     *
     * @return 切日时间字符串，格式 HH:mm
     */
    public String getSwitchTime()
    {
        String res = getItemValue(1000, "SWITCH_TIME", String.class);
        return res == null ? DEFAULT_SWITCH_TIME : res;
    }

    /**
     * 获取切日时间（LocalTime对象）
     *
     * @return 切日时间
     */
    public LocalTime getSwitchTimeAsTime()
    {
        String res = getSwitchTime();
        String[] split = res.split(":");
        int hour = Integer.parseInt(split[0]);
        int minute = Integer.parseInt(split[1]);
        return LocalTime.of(hour, minute);
    }

    /**
     * 获得上个业务日期，规则如下：
     * 以切日为界限，如果当前时间在切日时间之前，则业务日期不早于当前时间的最近业务日期，否则为当前时间的业务日期
     * 跨日采集时也做特殊处理
     *
     * @return 业务日期，格式为 yyyyMMdd
     */
    public String getBizDate()
    {
        LocalTime localTime = LocalTime.now();
        String curDate;
        if (localTime.isAfter(getSwitchTimeAsTime()) && localTime.isBefore(LocalTime.of(23, 59))) {
            curDate = LocalDate.now().plusDays(1).format(shortSdf);
        }
        else {
            curDate = LocalDate.now().format(shortSdf);
        }
        // The global business date keeps its historical meaning (derived from the trade-date dictionary)
        // so upgrading adds the per-source calendar without moving the anchor existing deployments,
        // dashboards and cached templates were built on.
        String res = sysItemRepo.findLastCalendarDateBefore(CalendarType.TRADE_DAY.getDictCode(), curDate);
        return res == null ? curDate : res;
    }

    /**
     * 业务日期加减运算
     * 根据传入的业务日期和天数，计算新的业务日期，日期为正数，表示比当前业务日期早，负数表示比当前业务日期晚
     *
     * @param bizDate 当前业务日期，格式为 yyyyMMdd
     * @param days 天数，正数表示加，负数表示减
     * @return 计算后的业务日期，格式为 yyyyMMdd
     */
    public String bizDateAdd(String bizDate, int days)
    {
        String res = sysItemRepo.getCalendarDateOffset(bizDate, days, CalendarType.TRADE_DAY.getDictCode());
        return res == null ? bizDate : res;
    }

    /**
     * 获取 Addax 安装路径
     *
     * @return Addax 路径
     */
    public String getAddaxHome()
    {
        String res = getItemValue(1000, "ADDAX", String.class);
        return res == null ? "/opt/app/addax" : res;
    }

    /**
     * 获取并发限制参数
     *
     * @return 并发限制
     */
    public int getConcurrentLimit()
    {
        Integer res = getItemValue(1000, "CONCURRENT_LIMIT", Integer.class);
        return res == null ? 20 : res;
    }

    /**
     * 获取队列大小参数
     *
     * @return 队列大小
     */
    public int getQueueSize()
    {
        Integer res = getItemValue(1000, "QUEUE_SIZE", Integer.class);
        return res == null ? 100 : res;
    }

    public String getRdbms2HdfsJobTemplate()
    {
        String res = getItemValue(5000, "R2H", String.class);
        return (res == null || res.isBlank()) ? DEFAULT_RDBMS2HDFS_JOB_TEMPLATE : res;
    }

    public String getRdbms2RdbmsJobTemplate()
    {
        String res = getItemValue(5000, "R2R", String.class);
        return (res == null || res.isBlank()) ? DEFAULT_RDBMS2RDBMS_JOB_TEMPLATE : res;
    }

    // 读取 RDBMS Reader 模板
    public String getRdbmsReaderTemplate()
    {
        String res = getItemValue(5001, "rR", String.class);
        return (res == null || res.isBlank()) ? DEFAULT_RDBMS_READER_TEMPLATE : res;
    }

    // 获取 HDFS Writer 模板
    public String getHdfsWriterTemplate()
    {
        String res = getItemValue(5001, "wH", String.class);
        return (res == null || res.isBlank()) ? DEFAULT_HDFS_WRITER_TEMPLATE : res;
    }

    // 获取 RDBMS Writer 模板
    public String getRdbmsWriterTemplate()
    {
        String res = getItemValue(5001, "wR", String.class);
        return (res == null || res.isBlank()) ? DEFAULT_RDBMS_WRITER_TEMPLATE : res;
    }

    /**
     * 按 writer 模板键读取模板，缺失的内置键回退到代码内置默认值，保证 RDBMS 目标端开箱可用。
     *
     * @param key writer 模板键（如 wH / wR）
     * @return 模板内容，未命中返回 null
     */
    public String getWriterTemplate(String key)
    {
        if (key == null || key.isBlank())
        {
            return null;
        }
        String res = getItemValue(5001, key, String.class);
        if (res != null && !res.isBlank())
        {
            return res;
        }
        if ("wR".equalsIgnoreCase(key))
        {
            return DEFAULT_RDBMS_WRITER_TEMPLATE;
        }
        return res;
    }

    /**
     * Idempotently seed the built-in RDBMS templates. The Java constants are the single source of truth;
     * seeding the dict rows (instead of duplicating them in scripts/data.sql) keeps fresh and existing
     * installs identical and lets operators edit the templates afterwards.
     */
    public void ensureRdbmsTemplates()
    {
        ensureItem(5000, "R2H", DEFAULT_RDBMS2HDFS_JOB_TEMPLATE, "关系型数据库到HDFS的模板");
        ensureItem(5000, "R2R", DEFAULT_RDBMS2RDBMS_JOB_TEMPLATE, "关系型数据库到关系型数据库的模板");
        ensureItem(5001, "rR", DEFAULT_RDBMS_READER_TEMPLATE, "通用关系型数据库读取模板");
        ensureItem(5001, "wH", DEFAULT_HDFS_WRITER_TEMPLATE, "HDFS写入模板");
        ensureItem(5001, "wR", DEFAULT_RDBMS_WRITER_TEMPLATE, "通用关系型数据库写入模板");
    }

    private void ensureItem(int dictCode, String itemKey, String itemValue, String remark)
    {
        try
        {
            if (sysItemRepo.findByDictCodeAndItemKey(dictCode, itemKey).isPresent())
            {
                return;
            }
            SysItem item = new SysItem();
            item.setDictCode(dictCode);
            item.setItemKey(itemKey);
            item.setItemValue(itemValue);
            item.setRemark(remark);
            sysItemRepo.save(item);
            log.info("Seeded dict item {}.{}", dictCode, itemKey);
        }
        catch (Exception e)
        {
            log.warn("Failed to seed dict item {}.{}: {}", dictCode, itemKey, e.getMessage());
        }
    }

    /**
     * 获取 Addax 任务模板内容
     *
     * @param kind 模板类型标识
     * @return 任务模板内容
     */
    public String getAddaxJobTemplate(String kind)
    {
        return getItemValue(5000, kind, String.class);
    }

    /**
     * 获取字典项的值并自动类型转换
     *
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @param clazz 返回类型
     * @return 字典项值
     */
    public <T> T getItemValue(int dictCode, String itemKey, Class<T> clazz)
    {
        Optional<SysItem> res = sysItemRepo.findByDictCodeAndItemKey(dictCode, itemKey);
        if (res.isEmpty()) {
            return null;
        }
        String value = res.get().getItemValue();
        if (value == null) {
            return null;
        }
        if (clazz == Integer.class) {
            return clazz.cast(Integer.parseInt(value));
        }
        else if (clazz == Long.class) {
            return clazz.cast(Long.parseLong(value));
        }
        else if (clazz == Boolean.class) {
            return clazz.cast(Boolean.parseBoolean(value));
        }
        else {
            // 默认 String 类型
            return clazz.cast(value);
        }
    }

    /**
     * 获取 Hive 类型映射表
     *
     * @return Hive类型映射表
     */
    public Map<String, String> getHiveTypeMapping()
    {
        return sysItemRepo.getHiveTypeItems().stream()
            .collect(java.util.stream.Collectors.toMap(
                SysItem::getItemKey,
                SysItem::getItemValue));
    }

    // SysItem CRUD

    public Optional<SysItem> findSystemItem(int dictCode, String itemKey)
    {
        return sysItemRepo.findByDictCodeAndItemKey(dictCode, itemKey);
    }

    /**
     * 保存字典项对象
     *
     * @param item 字典项对象
     * @return 保存后的字典项对象
     */
    public SysItem saveItem(SysItem item)
    {
        return sysItemRepo.save(item);
    }

    public Map<String, Object> getSysConfig()
    {
        return sysItemRepo.findByDictCode(1000).stream()
            .collect(java.util.stream.Collectors.toMap(
                SysItem::getItemKey,
                SysItem::getItemValue));
    }

    public void saveSysConfig(Map<String, Object> payload)
    {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            findSystemItem(1000, entry.getKey()).ifPresent(item -> {
                if (item.getItemValue() != entry.getValue()) {
                    item.setItemValue(entry.getValue().toString());
                    saveItem(item);
                }
            });
        }
    }

    // 获取三个模板
    public Map<String, SysItem> getJobTemplates()
    {
        return sysItemRepo.findByDictCodeIn(List.of(5000, 5001)).stream()
            .collect(java.util.stream.Collectors.toMap(
                SysItem::getItemKey,
                item -> item));
    }

    public void updateJobTemplates(List<SysItem> templates)
    {
        for (SysItem item : templates) {
            if (item.getDictCode() != 5000 && item.getDictCode() != 5001) {
                continue;
            }
            validateTemplateContract(item);
            saveItem(item);
        }
    }

    /**
     * The {@code ${...}} names in a template are a hard contract with the job generator: renaming,
     * removing or adding one leaves an unresolved literal in the rendered job. The guard prevents
     * the placeholder set from changing relative to the template's own stored state — not to the
     * shipped default — so a deployment that already hardcoded a value (e.g. {@code writeMode})
     * keeps working while accidental placeholder edits are still refused.
     */
    private void validateTemplateContract(SysItem item)
    {
        String key = item.getItemKey();
        String defaultTemplate = DEFAULT_TEMPLATES.get(key);
        if (defaultTemplate == null)
        {
            throw new ApiException(400, "未知作业模板键: " + key);
        }
        String stored = getItemValue(item.getDictCode(), key, String.class);
        String baseline = (stored == null || stored.isBlank()) ? defaultTemplate : stored;
        Set<String> expected = placeholdersOf(baseline);
        Set<String> actual = placeholdersOf(item.getItemValue());
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(expected);
        if (!missing.isEmpty() || !extra.isEmpty())
        {
            String reason = "模板 " + key + " 的占位符与契约不一致"
                + (missing.isEmpty() ? "" : "，缺少: " + missing)
                + (extra.isEmpty() ? "" : "，多余: " + extra)
                + "。占位符是任务生成的契约，不允许增删改";
            throw new ApiException(400, reason);
        }
        validateTemplateJson(key, item.getItemValue());
    }

    private Set<String> placeholdersOf(String template)
    {
        if (template == null || template.isBlank())
        {
            return Set.of();
        }
        return PLACEHOLDER.matcher(template).results()
            .map(m -> m.group(1).trim())
            .collect(Collectors.toSet());
    }

    private void validateTemplateJson(String key, String template)
    {
        try
        {
            objectMapper.readTree(maskPlaceholders(template));
        }
        catch (Exception e)
        {
            throw new ApiException(400, "模板 " + key + " 不是合法的 JSON（占位符已按字符串处理）: " + e.getMessage());
        }
    }

    /**
     * Replace bare {@code ${...}} tokens (those outside a JSON string) with quoted string literals
     * so {@code readTree} can parse the surrounding structure; tokens inside strings are already
     * valid JSON text and are left untouched.
     */
    private String maskPlaceholders(String text)
    {
        StringBuilder out = new StringBuilder();
        boolean inString = false;
        int i = 0;
        int slot = 0;
        while (i < text.length())
        {
            char c = text.charAt(i);
            if (inString)
            {
                if (c == '\\' && i + 1 < text.length())
                {
                    out.append(c).append(text.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"')
                {
                    inString = false;
                }
                out.append(c);
                i += 1;
                continue;
            }
            if (c == '"')
            {
                inString = true;
                out.append(c);
                i += 1;
                continue;
            }
            if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{')
            {
                int end = text.indexOf('}', i + 2);
                if (end != -1)
                {
                    out.append("\"__ADDAX_SLOT_").append(slot++).append("__\"");
                    i = end + 1;
                    continue;
                }
            }
            out.append(c);
            i += 1;
        }
        return out.toString();
    }

    // 表结构刷新超时时间，单位秒
    public int getSchemaRefreshTimeout()
    {
        Integer res = getItemValue(1000, "SCHEMA_REFRESH_TIMEOUT", Integer.class);
        return res == null ? 600 : res;
    }

    /**
     * 按日期生成一份日历字典，日期格式 yyyyMMdd，item_key 与 item_value 均为日期字符串。
     *
     * 已存在的日期不会被覆盖：工作日历生成出来的是“周一至周五”这个起点，维护者随后要删掉法定节假日、
     * 补上调休上班日，重新生成时必须保留这些人工修正，否则一次误操作就会把调休信息抹掉。
     *
     * @param dictCode 目标字典编号，交易日历 1021，工作日历 1022
     * @param year 指定年份，比如 2026
     * @param includeWeekend 是否包含周末，true 包含，false 不包含
     */
    public int generateCalendar(int dictCode, int year, boolean includeWeekend)
    {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        int added = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!includeWeekend) {
                int dayOfWeek = date.getDayOfWeek().getValue();
                if (dayOfWeek == 6 || dayOfWeek == 7) {
                    continue;
                }
            }
            String dateStr = date.format(shortSdf);
            if (sysItemRepo.findByDictCodeAndItemKey(dictCode, dateStr).isPresent()) {
                continue;
            }
            SysItem item = new SysItem();
            item.setDictCode(dictCode);
            item.setItemKey(dateStr);
            item.setItemValue(dateStr);
            item.setRemark("自动生成");
            sysItemRepo.save(item);
            added++;
        }
        log.info("日历字典 {} 生成完成：{} 年新增 {} 条", dictCode, year, added);
        return added;
    }

    /**
     * 列出某个日历字典（1021/1022）下的日期，按日期升序。可选按年份过滤。
     *
     * @param dictCode 日历字典编号
     * @param year 年份，null 表示返回全部
     * @return 日期明细（item_key = item_value = yyyyMMdd）
     */
    public List<SysItem> listCalendarDates(int dictCode, Integer year)
    {
        String prefix = year == null ? null : String.valueOf(year);
        return sysItemRepo.findByDictCode(dictCode).stream()
            .filter(item -> item.getItemKey() != null && (prefix == null || item.getItemKey().startsWith(prefix)))
            .sorted(Comparator.comparing(SysItem::getItemKey))
            .collect(Collectors.toList());
    }

    /**
     * 手工新增一个日历日期（法定节假日后的调休上班日、或漏维护的交易日）。已存在则幂等跳过。
     *
     * @param dictCode 日历字典编号
     * @param dateStr yyyyMMdd 日期字符串
     * @return 是否实际新增（已存在返回 false）
     */
    public boolean addCalendarDate(int dictCode, String dateStr)
    {
        // yyyyMMdd 解析失败会抛 DateTimeParseException，由控制器转成 400
        LocalDate.parse(dateStr, shortSdf);
        if (sysItemRepo.findByDictCodeAndItemKey(dictCode, dateStr).isPresent()) {
            return false;
        }
        SysItem item = new SysItem();
        item.setDictCode(dictCode);
        item.setItemKey(dateStr);
        item.setItemValue(dateStr);
        item.setRemark("人工维护");
        sysItemRepo.save(item);
        log.info("日历字典 {} 新增单日 {}", dictCode, dateStr);
        return true;
    }

    /**
     * 删除一个日历日期（法定节假日）。不存在时静默忽略。
     *
     * @param dictCode 日历字典编号
     * @param dateStr yyyyMMdd 日期字符串
     * @return 是否实际删除（不存在返回 false）
     */
    public boolean removeCalendarDate(int dictCode, String dateStr)
    {
        Optional<SysItem> existing = sysItemRepo.findByDictCodeAndItemKey(dictCode, dateStr);
        if (existing.isEmpty()) {
            return false;
        }
        sysItemRepo.delete(existing.get());
        log.info("日历字典 {} 删除单日 {}", dictCode, dateStr);
        return true;
    }
}
