package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.dto.AddaxCheckItem;
import com.wgzhao.addax.admin.dto.AddaxValidationResult;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.repository.EtlStatisticRepo;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 校验「Addax 程序目录」配置是否正确。
 *
 * <p>校验在后端执行，因为目录位于后端进程所在文件系统。依次做：目录存在与权限、
 * bin/addax.sh 存在且可执行、conf/core.json 的 core.server.address 指向本后端 /api/v1/log/job-report
 * 且端口一致，最后以 stream→stream 冒烟任务跑通「执行 + 上报」闭环。冒烟统计写入 etl_statistic
 * 后立即删除，不污染真实统计与仪表盘。</p>
 */
@Service
@AllArgsConstructor
@Slf4j
public class AddaxValidationService
{
    private static final long SMOKE_TIMEOUT_SECONDS = 60;
    private static final String REPORT_PATH_SUFFIX = "/log/job-report";
    private static final String SMOKE_LOG_NAME = "addax-smoke-test.log";
    private static final String DEFAULT_STREAM_JOB = """
        {
          "job": {
            "setting": {
              "speed": { "byte": -1, "channel": 1 },
              "errorLimit": { "record": 0, "percentage": 0.02 }
            },
            "content": {
              "reader": {
                "name": "streamreader",
                "parameter": {
                  "column": [
                    { "value": "addax", "type": "string" },
                    { "value": 19890604, "type": "long" },
                    { "value": "1989-06-04 00:00:00", "type": "date" },
                    { "value": true, "type": "bool" },
                    { "value": "test", "type": "bytes" }
                  ],
                  "sliceRecordCount": 10
                }
              },
              "writer": {
                "name": "streamwriter",
                "parameter": { "print": true, "column": ["col1"], "encoding": "UTF-8" }
              }
            }
          }
        }
        """;

    private final DictService dictService;
    private final EtlStatisticRepo etlStatisticRepo;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final JobReportTokenService jobReportTokenService;

    /**
     * @param addaxHomeInput 前端输入框当前值；为空时回退到已保存的 ADDAX 配置
     */
    public AddaxValidationResult validate(String addaxHomeInput)
    {
        String home = (addaxHomeInput == null || addaxHomeInput.isBlank())
            ? dictService.getAddaxHome() : addaxHomeInput.trim();

        List<AddaxCheckItem> checks = new ArrayList<>();

        // 1. 目录存在且有权限
        Path dir = Path.of(home);
        boolean dirOk = Files.isDirectory(dir) && Files.isReadable(dir) && Files.isExecutable(dir);
        checks.add(new AddaxCheckItem("directory", "Addax 程序目录", status(dirOk),
            dirOk ? "目录存在且可读、可遍历" : "目录不存在，或缺少读取/执行权限",
            dirOk ? null : "确认路径正确，并执行 chmod 755 " + home));
        if (!dirOk) {
            return new AddaxValidationResult(false, checks);
        }

        // 2. bin/addax.sh 存在且有权限、可执行
        Path script = dir.resolve("bin").resolve("addax.sh");
        boolean scriptOk = Files.isRegularFile(script) && Files.isReadable(script) && Files.isExecutable(script);
        checks.add(new AddaxCheckItem("addaxScript", "bin/addax.sh", status(scriptOk),
            scriptOk ? "脚本存在且可执行" : "脚本缺失或缺少可执行权限",
            scriptOk ? null : "确认文件存在，并执行 chmod +x " + script));
        if (!scriptOk) {
            return new AddaxValidationResult(false, checks);
        }

        // 3. conf/core.json 存在、可解析，且 core.server.address 已配置
        Path coreJson = dir.resolve("conf").resolve("core.json");
        String address = null;
        if (!Files.isRegularFile(coreJson) || !Files.isReadable(coreJson)) {
            checks.add(new AddaxCheckItem("coreJson", "conf/core.json", "fail",
                "conf/core.json 不存在或不可读",
                "确认文件存在，并保证后端进程可读 " + coreJson));
            return new AddaxValidationResult(false, checks);
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(coreJson, StandardCharsets.UTF_8));
            address = root.path("core").path("server").path("address").asText(null);
        }
        catch (Exception e) {
            checks.add(new AddaxCheckItem("coreJson", "conf/core.json", "fail",
                "conf/core.json 解析失败: " + e.getMessage(),
                "检查 JSON 语法是否正确"));
            return new AddaxValidationResult(false, checks);
        }
        if (address == null || address.isBlank()) {
            checks.add(new AddaxCheckItem("coreJson", "conf/core.json", "fail",
                "core.server.address 未配置（为空）",
                "在 conf/core.json 中把 core.server.address 配置为后端上报地址"));
            return new AddaxValidationResult(false, checks);
        }
        checks.add(new AddaxCheckItem("coreJson", "conf/core.json", "ok",
            "core.json 可解析，core.server.address 已配置", null));

        // 4. core.server.address 指向本后端 /api/v1/log/job-report 且端口一致
        String expectedPath = normalizeContextPath() + REPORT_PATH_SUFFIX;
        int runningPort = Integer.parseInt(environment.getProperty("server.port", "50601"));
        String addressError = validateServerAddress(address, expectedPath, runningPort);
        checks.add(new AddaxCheckItem("serverAddress", "上报地址与端口", status(addressError == null),
            addressError == null
                ? "core.server.address 指向本后端上报接口"
                : addressError,
            addressError == null ? null
                : "期望 core.server.address = http://<本机地址>:" + runningPort + expectedPath));
        if (addressError != null) {
            return new AddaxValidationResult(false, checks);
        }

        // 5. 冒烟测试：stream→stream 执行完成且上报落库，随后清理
        AddaxCheckItem smoke = runSmokeTest(dir, script);
        checks.add(smoke);

        boolean success = checks.stream().allMatch(item -> "ok".equals(item.status()));
        return new AddaxValidationResult(success, checks);
    }

    private String normalizeContextPath()
    {
        String contextPath = environment.getProperty("server.servlet.context-path", "/api/v1");
        if (contextPath == null || contextPath.isBlank()) {
            return "";
        }
        if (contextPath.endsWith("/")) {
            return contextPath.substring(0, contextPath.length() - 1);
        }
        return contextPath;
    }

    private String validateServerAddress(String address, String expectedPath, int runningPort)
    {
        URI uri;
        try {
            uri = URI.create(address);
        }
        catch (IllegalArgumentException e) {
            return "core.server.address 不是合法 URL: " + address;
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return "core.server.address 必须使用 http/https 协议，当前为: " + (scheme == null ? "(空)" : scheme);
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!expectedPath.equals(path)) {
            return "上报路径不匹配：期望 " + expectedPath + "，实际 " + path;
        }
        int port = uri.getPort();
        if (port != runningPort) {
            return "上报端口不匹配：当前程序运行端口 " + runningPort
                + "，实际 " + (port == -1 ? "(未指定，默认 80/443)" : port);
        }
        return null;
    }

    private AddaxCheckItem runSmokeTest(Path dir, Path script)
    {
        // Establish a clean baseline so any existing tid=-1 row cannot be mistaken for this run's report
        etlStatisticRepo.deleteByTid(Constants.SMOKE_TEST_TID);

        String reportToken;
        try {
            reportToken = jobReportTokenService.issueSmokeToken(Constants.SMOKE_TEST_TID);
        }
        catch (Exception e) {
            return new AddaxCheckItem("smokeTest", "冒烟上报", "fail",
                "无法登记上报令牌: " + e.getMessage(), "检查 Redis 连接后重试");
        }

        Path jobFile = resolveJobFile(dir);
        List<String> cmdArgs = List.of(
            script.toString(),
            "-p", "-DjobName=" + Constants.SMOKE_TEST_TID + ":" + reportToken
                + " -Dlog.file.name=" + SMOKE_LOG_NAME,
            jobFile.toString());
        // Never persist or print the one-time report credential.
        String cmd = String.join(" ", cmdArgs).replace(reportToken, "***");

        TaskResultDto result;
        try {
            Process process = CommandExecutor.startProcess(cmdArgs);
            result = CommandExecutor.waitForProcessWithResult(process, SMOKE_TIMEOUT_SECONDS, cmd);
        }
        catch (IOException e) {
            result = TaskResultDto.failure(e.getMessage(), 0);
        }

        // findSmokeReport() polls: the report POST can still be in flight when the process exits, so
        // the token must stay valid until the poll gives up. It is one-shot, and the TTL bounds the
        // window even if this revoke is skipped.
        Optional<EtlStatistic> reported;
        try {
            reported = findSmokeReport();
        }
        finally {
            jobReportTokenService.revokeSmokeToken(reportToken);
        }
        etlStatisticRepo.deleteByTid(Constants.SMOKE_TEST_TID);

        if (jobFile.toAbsolutePath().toString().contains("addax-smoke")) {
            try {
                Files.deleteIfExists(jobFile);
            }
            catch (IOException ignored) {
                // best-effort cleanup of the generated temp job
            }
        }

        if (!result.success()) {
            return new AddaxCheckItem("smokeTest", "冒烟任务执行", "fail",
                "Addax 冒烟任务执行失败: " + result.message(),
                "根据输出修正问题后重试（检查 java 版本、log 目录可写、插件是否完整）");
        }
        if (reported.isEmpty()) {
            return new AddaxCheckItem("smokeTest", "冒烟上报", "fail",
                "冒烟任务执行成功，但统计结果未上报到本后端",
                "检查 core.server.address 是否指向当前后端且端口可达，并确认后端 /api/v1/log/job-report 可访问");
        }
        long records = reported.get().getTotalRecs() == null ? 0 : reported.get().getTotalRecs();
        return new AddaxCheckItem("smokeTest", "冒烟任务与上报", "ok",
            "冒烟任务执行完成并成功上报（读取 " + records + " 行）", null);
    }

    private Path resolveJobFile(Path dir)
    {
        Path bundled = dir.resolve("job").resolve("job.json");
        if (Files.isRegularFile(bundled) && Files.isReadable(bundled)) {
            return bundled;
        }
        // Fall back to a generated stream→stream job so the smoke test still exercises the full
        // execute + report loop when the install ships without job/job.json.
        try {
            Path temp = Files.createTempFile("addax-smoke", ".json");
            Files.writeString(temp, DEFAULT_STREAM_JOB, StandardCharsets.UTF_8);
            return temp;
        }
        catch (IOException e) {
            log.warn("Failed to create default smoke job: {}", e.getMessage());
            return bundled;
        }
    }

    private Optional<EtlStatistic> findSmokeReport()
    {
        // Addax posts its report synchronously before exiting, so the row is normally already
        // committed. Poll briefly to tolerate any scheduling delay between the POST and process exit.
        for (int i = 0; i < 10; i++) {
            Optional<EtlStatistic> stat = etlStatisticRepo.findTop1ByTidOrderByIdDesc(Constants.SMOKE_TEST_TID);
            if (stat.isPresent()) {
                return stat;
            }
            try {
                Thread.sleep(500);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Optional.empty();
    }

    private static String status(boolean ok)
    {
        return ok ? "ok" : "fail";
    }
}
