package com.wgzhao.addax.admin.service.target;

import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.common.TargetFamily;
import com.wgzhao.addax.admin.dto.AddaxCheckItem;
import com.wgzhao.addax.admin.dto.TargetHealthCheckPlan;
import com.wgzhao.addax.admin.dto.TargetHealthCheckResult;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.repository.EtlStatisticRepo;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.JobReportTokenService;
import com.wgzhao.addax.admin.service.impl.HdfsTargetAdapter;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 目标端健康检查：把"这个目标端现在能不能被生产任务使用"拆成可解释的检查项。
 *
 * <p>检查按 {@link #plan} 给出的清单逐项执行（{@link #runItem}），前端先列出全部项目再逐项回填
 * 结果，因此单项耗时差异不会让清单本身迟到。<b>清单与执行共用同一份定义</b>，两边不会漂移。</p>
 *
 * <p>HDFS 目标端有两段完全独立的配置：HiveServer2（建表、元数据）与 HDFS 写入（
 * defaultFS/hdfs-site/Kerberos/格式）。JDBC 连通只说明前者，因此这里额外跑一个
 * streamreader → hdfswriter 探针任务，使用与采集作业相同的 writer 参数渲染逻辑，经同一个
 * {@code bin/addax.sh} 启动，写一行到目标前缀下的隐藏目录。探针走 {@code overwrite}，每个
 * 目标端只保留最后一次的文件，不会随校验次数增长。</p>
 *
 * <p>探针上报沿用负 tid 约定（{@link Constants#TARGET_SMOKE_TEST_TID}），跑完删除统计行，
 * 不进入仪表盘。探针是否上报成功不作为检查项：那是 Addax 目录校验的职责，这里只关心
 * "这份 HDFS 配置能不能写进去"。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TargetHealthCheckService
{
    private static final long SMOKE_TIMEOUT_SECONDS = 180;
    private static final String SMOKE_LOG_NAME = "target-health-check.log";
    private static final Set<String> WRITER_FILE_TYPES = Set.of("orc", "parquet", "text");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^{}]+}");

    // Order is the execution order shown to the operator: cheap static checks first so an obvious
    // misconfiguration is reported before the multi-second HDFS probe starts.
    private static final List<TargetHealthCheckPlan.Item> HDFS_ITEMS = List.of(
        new TargetHealthCheckPlan.Item("addaxHome", "Addax 程序目录"),
        new TargetHealthCheckPlan.Item("writerPlugin", "hdfswriter 插件"),
        new TargetHealthCheckPlan.Item("writerTemplate", "HDFS 写入模板"),
        new TargetHealthCheckPlan.Item("hiveJdbc", "HiveServer2 连接"),
        new TargetHealthCheckPlan.Item("hdfsWrite", "HDFS 写入冒烟"));
    private static final List<TargetHealthCheckPlan.Item> RDBMS_ITEMS = List.of(
        new TargetHealthCheckPlan.Item("jdbc", "JDBC 连接"));

    private static final Map<String, String> LABELS = labels();

    private final TargetAdapterRegistry adapterRegistry;
    private final HdfsTargetAdapter hdfsTargetAdapter;
    private final DictService dictService;
    private final EtlStatisticRepo etlStatisticRepo;
    private final ObjectMapper objectMapper;
    private final JobReportTokenService jobReportTokenService;

    /** 检查清单：前端据此先渲染所有检查项，再逐项请求结果。 */
    public TargetHealthCheckPlan plan(EtlTarget target)
    {
        TargetAdapter adapter = resolveAdapter(target);
        return new TargetHealthCheckPlan(adapter.getFamily().name(), plannedItems(adapter.getFamily()));
    }

    /** 执行单个检查项；key 不在该目标族的清单内时按参数错误拒绝。 */
    public AddaxCheckItem runItem(EtlTarget target, String key)
    {
        TargetAdapter adapter = resolveAdapter(target);
        boolean planned = plannedItems(adapter.getFamily()).stream()
            .anyMatch(item -> item.key().equals(key));
        if (!planned) {
            throw new ApiException(400, "目标端 " + adapter.getFamily().name() + " 没有检查项: " + key);
        }
        return switch (key)
        {
            case "jdbc" -> checkRdbms(target, adapter);
            case "addaxHome" -> checkAddaxHome();
            case "writerPlugin" -> checkWriterPlugin();
            case "writerTemplate" -> checkWriterTemplate(target);
            case "hiveJdbc" -> checkHiveJdbc(target);
            case "hdfsWrite" -> checkHdfsWrite(target, addaxRuntimeReady());
            default -> throw new ApiException(400, "未知的检查项: " + key);
        };
    }

    /** 一次跑完整个清单，供 API/测试使用；前端逐项调用时走 {@link #runItem}。 */
    public TargetHealthCheckResult check(EtlTarget target)
    {
        TargetHealthCheckPlan plan = plan(target);
        List<AddaxCheckItem> checks = new ArrayList<>();
        for (TargetHealthCheckPlan.Item planned : plan.items()) {
            checks.add(runItem(target, planned.key()));
        }
        boolean success = checks.stream().noneMatch(item -> "fail".equals(item.status()));
        return new TargetHealthCheckResult(success, plan.family(), checks);
    }

    private TargetAdapter resolveAdapter(EtlTarget target)
    {
        if (target == null) {
            throw new ApiException(400, "Target payload is empty");
        }
        try {
            return adapterRegistry.resolveForTarget(target);
        }
        catch (IllegalStateException e) {
            throw new ApiException(400, e.getMessage());
        }
    }

    private static List<TargetHealthCheckPlan.Item> plannedItems(TargetFamily family)
    {
        return family == TargetFamily.HDFS ? HDFS_ITEMS : RDBMS_ITEMS;
    }

    private static Map<String, String> labels()
    {
        Map<String, String> labels = new HashMap<>();
        HDFS_ITEMS.forEach(item -> labels.put(item.key(), item.label()));
        RDBMS_ITEMS.forEach(item -> labels.put(item.key(), item.label()));
        return Map.copyOf(labels);
    }

    private static AddaxCheckItem item(String key, String status, String message, String hint)
    {
        return new AddaxCheckItem(key, LABELS.get(key), status, message, hint);
    }

    private AddaxCheckItem checkRdbms(EtlTarget target, TargetAdapter adapter)
    {
        try {
            adapter.testConnection(target);
            return item("jdbc", "ok", "已建立连接", null);
        }
        catch (RuntimeException e) {
            return item("jdbc", "fail", messageOf(e),
                "检查 JDBC 地址、账号密码，以及目标库网络可达性");
        }
    }

    private AddaxCheckItem checkAddaxHome()
    {
        String home = dictService.getAddaxHome();
        Path homeDir = Path.of(home);
        boolean homeOk = Files.isDirectory(homeDir) && Files.isExecutable(homeDir);
        return item("addaxHome", status(homeOk),
            homeOk ? "目录存在且可执行: " + home : "目录不存在或缺少读取/执行权限: " + home,
            homeOk ? null : "在系统配置中修正 Addax 程序目录，并确认后端进程可访问该路径");
    }

    private AddaxCheckItem checkWriterPlugin()
    {
        Path homeDir = Path.of(dictService.getAddaxHome());
        boolean homeOk = Files.isDirectory(homeDir) && Files.isExecutable(homeDir);
        Path writerPlugin = homeDir.resolve("plugin").resolve("writer").resolve("hdfswriter");
        boolean pluginOk = homeOk && Files.isDirectory(writerPlugin);
        return item("writerPlugin", pluginOk ? "ok" : (homeOk ? "fail" : "skip"),
            pluginOk ? "插件目录存在: " + writerPlugin
                : homeOk ? "未找到插件目录: " + writerPlugin : "Addax 程序目录不可用，跳过插件检查",
            pluginOk ? null : "在 Addax 程序目录下安装 hdfswriter 插件（plugin/writer/hdfswriter）");
    }

    private boolean addaxRuntimeReady()
    {
        Path homeDir = Path.of(dictService.getAddaxHome());
        return Files.isDirectory(homeDir) && Files.isExecutable(homeDir)
            && Files.isDirectory(homeDir.resolve("plugin").resolve("writer").resolve("hdfswriter"));
    }

    private AddaxCheckItem checkWriterTemplate(EtlTarget target)
    {
        String key = StringUtils.defaultIfBlank(target.getWriterTemplateKey(), "wH");
        String template = dictService.getItemValue(5001, key, String.class);
        if (StringUtils.isBlank(template)) {
            return item("writerTemplate", "fail",
                "模板 " + key + " 未配置或为空", "在系统配置的作业模板中维护 " + key);
        }
        try {
            // Placeholders stand for scalars/objects the generator substitutes, so they are masked
            // before parsing; the contract guard has already fixed their names.
            objectMapper.readTree(PLACEHOLDER.matcher(template).replaceAll("0"));
        }
        catch (Exception e) {
            return item("writerTemplate", "fail",
                "模板 " + key + " 不是合法 JSON: " + e.getMessage(), "修正模板 JSON 后重试");
        }
        return item("writerTemplate", "ok", "模板 " + key + " 可解析", null);
    }

    private AddaxCheckItem checkHiveJdbc(EtlTarget target)
    {
        try {
            hdfsTargetAdapter.testConnection(target);
            return item("hiveJdbc", "ok", "已建立连接，可执行建表与元数据操作", null);
        }
        catch (RuntimeException e) {
            return item("hiveJdbc", "fail", messageOf(e),
                "检查 jdbc:hive2 地址、账号密码与驱动（${APP_HOME}/drivers 或目标端的驱动 jar 路径）");
        }
    }

    private AddaxCheckItem checkHdfsWrite(EtlTarget target, boolean addaxReady)
    {
        if (!addaxReady) {
            return item("hdfsWrite", "skip", "Addax 运行时不可用，跳过写入检查",
                "先修正 Addax 程序目录或 hdfswriter 插件，再重新校验");
        }

        HdfsTargetSettings settings;
        try {
            settings = HdfsTargetSettings.from(target, objectMapper);
        }
        catch (RuntimeException e) {
            return item("hdfsWrite", "fail", messageOf(e), "修正目标端连接配置 JSON 后重试");
        }
        if (StringUtils.isBlank(settings.defaultFS())) {
            return item("hdfsWrite", "fail", "未配置 defaultFS",
                "填写 HDFS 地址（例如 hdfs://nameservice1）");
        }
        String fileType = HdfsTargetAdapter.writerFileType(settings.storageFormat());
        if (!WRITER_FILE_TYPES.contains(fileType)) {
            return item("hdfsWrite", "fail",
                "存储格式 " + settings.storageFormat() + " 不受 hdfswriter 支持",
                "hdfswriter 仅支持 orc / parquet / textfile，请在 HDFS / Hive 设置中调整");
        }

        Path jobFile = null;
        try {
            String job = hdfsTargetAdapter.buildSmokeWriterJob(target);
            jobFile = Files.createTempFile("addax-target-check", ".json");
            Files.writeString(jobFile, job, StandardCharsets.UTF_8);

            Path script = Path.of(dictService.getAddaxHome(), "bin", "addax.sh");
            String reportToken = jobReportTokenService.issueSmokeToken(Constants.TARGET_SMOKE_TEST_TID);
            List<String> cmdArgs = List.of(
                script.toString(),
                "-p", "-DjobName=" + Constants.TARGET_SMOKE_TEST_TID + ":" + reportToken
                    + " -Dlog.file.name=" + SMOKE_LOG_NAME,
                jobFile.toAbsolutePath().toString());

            etlStatisticRepo.deleteByTid(Constants.TARGET_SMOKE_TEST_TID);
            TaskResultDto result;
            try {
                Process process = CommandExecutor.startProcess(cmdArgs);
                result = CommandExecutor.waitForProcessWithResult(process, SMOKE_TIMEOUT_SECONDS,
                    String.join(" ", cmdArgs).replace(reportToken, "***"));
            }
            finally {
                // Do not revoke the token here: process exit does not guarantee that the report POST
                // has been processed, and this check does not read the report. The token is one-shot
                // and expires on its own, so a late report is harmless.
                etlStatisticRepo.deleteByTid(Constants.TARGET_SMOKE_TEST_TID);
            }

            if (!result.success()) {
                log.warn("HDFS health-check probe failed for target {}: {}",
                    target.getCode(), summarizeFailure(result.message()));
                return item("hdfsWrite", "fail",
                    "写入失败: " + summarizeFailure(result.message()),
                    "检查 defaultFS、hdfs-site.xml、Kerberos/hadoopConfig，以及 "
                        + settings.hdfsPrefix() + " 的写权限");
            }
            String probePath = hdfsTargetAdapter.smokeProbePath(target);
            return item("hdfsWrite", "ok",
                "探针任务写入成功，目录 " + probePath + "（覆盖写入，每个目标端只保留最后一次）",
                null);
        }
        catch (IOException e) {
            return item("hdfsWrite", "fail", "无法启动 Addax: " + messageOf(e),
                "确认 " + dictService.getAddaxHome() + "/bin/addax.sh 存在且可执行");
        }
        catch (RuntimeException e) {
            return item("hdfsWrite", "fail", messageOf(e), "检查目标端 HDFS 设置后重试");
        }
        finally {
            if (jobFile != null) {
                try {
                    Files.deleteIfExists(jobFile);
                }
                catch (IOException ignored) {
                    // best-effort cleanup of the generated probe job
                }
            }
        }
    }

    private static String status(boolean ok)
    {
        return ok ? "ok" : "fail";
    }

    private static String messageOf(Throwable e)
    {
        String message = e.getMessage();
        return StringUtils.isBlank(message) ? e.toString() : message;
    }

    /**
     * The probe's stdout is the whole addax log, stack traces included. A stack trace in a check
     * result is noise: the root cause line is what tells the operator which setting is wrong.
     */
    private static String summarizeFailure(String output)
    {
        if (StringUtils.isBlank(output)) {
            return "(无输出)";
        }
        String[] lines = output.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("Caused by:")) {
                return tail(line, 600);
            }
        }
        String lastError = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains(" ERROR ") && !trimmed.contains("StatusLogger")) {
                lastError = trimmed;
            }
        }
        return tail(lastError == null ? output : lastError, 800);
    }

    private static String tail(String text, int limit)
    {
        String value = text == null ? "" : text.trim();
        if (value.length() <= limit) {
            return value;
        }
        return "…" + value.substring(value.length() - limit);
    }
}
