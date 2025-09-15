package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.service.EtlTaskEntryService;
import com.wgzhao.addax.admin.service.SpAloneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SP Alone Controller - 采集程序主脚本控制器
 * 将 sp_alone.sh 脚本中的函数转换为 REST API 接口
 */
@RestController
@RequestMapping("/sp-alone")
@RequiredArgsConstructor
public class SpAloneController {

    private final SpAloneService spAloneService;

    private final EtlTaskEntryService etlTaskEntryService;

    /**
     * Redis 操作接口
     */
    @PostMapping("/rds")
    public ResponseEntity<String> rds(@RequestBody String command) {
        String result = spAloneService.executeRedisCommand(command);
        return ResponseEntity.ok(result);
    }

    /**
     * Redis 标志管理接口
     */
    @PostMapping("/rfg/{action}")
    public ResponseEntity<String> rfg(@PathVariable String action, @RequestParam String flag) {
        String result = spAloneService.executeRedisFlag(action, flag);
        return ResponseEntity.ok(result);
    }

    /**
     * 等待队列索引接口
     */
    @GetMapping("/wait-idx")
    public ResponseEntity<Integer> waitIdx(@RequestParam String name, @RequestParam Integer maxCount) {
        Integer result = spAloneService.waitForIndex(name, maxCount);
        return ResponseEntity.ok(result);
    }

    /**
     * 发送企业微信机器人消息接口
     */
    @PostMapping("/send-wecom-robot")
    public ResponseEntity<String> sendToWecomRobot(@RequestBody String message) {
        String result = spAloneService.sendToWecomRobot(message);
        return ResponseEntity.ok(result);
    }

    /**
     * 执行SQL文件接口
     */
    @PostMapping("/runsql")
    public ResponseEntity<Integer> runSql(@RequestParam String sqlFile,
                                         @RequestParam(defaultValue = "1") Integer parallelNum) {
        Integer result = spAloneService.runSqlFile(sqlFile, parallelNum);
        return ResponseEntity.ok(result);
    }

    /**
     * 数据库源库执行语句接口
     */
    @PostMapping("/sdb/{sysId}")
    public ResponseEntity<String> sdb(@PathVariable String sysId, @RequestBody String sql) {
        String result = spAloneService.executeSourceDbSql(sysId, sql);
        return ResponseEntity.ok(result);
    }

    /**
     * 系统日志记录接口
     */
    @PostMapping("/syslog")
    public ResponseEntity<Void> syslog(@RequestBody String content) {
        spAloneService.recordSystemLog(content);
        return ResponseEntity.ok().build();
    }

    /**
     * 提示信息接口
     */
    @GetMapping("/tip")
    public ResponseEntity<String> tip(@RequestParam String scriptName,
                                     @RequestParam String status,
                                     @RequestParam(required = false) String message) {
        String result = spAloneService.generateTip(scriptName, status, message);
        return ResponseEntity.ok(result);
    }

    /**
     * 自动备份接口
     */
    @PostMapping("/autobak")
    public ResponseEntity<String> autobak() {
        String result = spAloneService.performAutomaticBackup();
        return ResponseEntity.ok(result);
    }

    /**
     * 计划任务主控制接口 - 定时任务每分钟执行一次
     */
//    @Scheduled(cron = "0 * * * * ?") // 每分钟的第0秒执行
    @PostMapping("/plan-start")
    public ResponseEntity<String> planStart() {
//        String result = spAloneService.executePlanStart();
        String result = etlTaskEntryService.executePlanStartWithQueue();
        return ResponseEntity.ok(result);
    }

    /**
     * 手动触发计划任务主控制接口
     */
    @PostMapping("/plan-start/manual")
    public ResponseEntity<String> planStartManual() {
        String result = spAloneService.executePlanStart();
        return ResponseEntity.ok(result);
    }

    /**
     * 启动任务的并发入口接口
     */
    @PostMapping("/sp-start")
    public ResponseEntity<String> spStart() {
        String result = spAloneService.executeSpStart();
        return ResponseEntity.ok(result);
    }

    /**
     * 采集、SP、数据服务的总入口接口
     */
    @PostMapping("/sp-init")
    public ResponseEntity<String> spInit() {
        String result = spAloneService.executeSpInit();
        return ResponseEntity.ok(result);
    }

    /**
     * 采集、SP、计划任务的具体执行接口
     */
    @PostMapping("/sp-etl/{taskId}")
    public ResponseEntity<String> spEtl(@PathVariable String taskId,
                                       @RequestParam(required = false) String mode) {
        String result = spAloneService.executeSpEtl(taskId, mode);
        return ResponseEntity.ok(result);
    }

    /**
     * 数据服务的具体执行接口
     */
    @PostMapping("/ds-etl/{dsId}")
    public ResponseEntity<String> dsEtl(@PathVariable String dsId) {
        String result = spAloneService.executeDataServiceEtl(dsId);
        return ResponseEntity.ok(result);
    }

    /**
     * 判断标志的具体实现接口
     */
    @PostMapping("/judge-etl/{flag}")
    public ResponseEntity<String> judgeEtl(@PathVariable String flag) {
        String result = spAloneService.executeJudgeEtl(flag);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取源库及hadoop的表结构信息接口
     */
    @PostMapping("/soutab-start")
    public ResponseEntity<String> soutabStart() {
        String result = spAloneService.executeSourceTableStart();
        return ResponseEntity.ok(result);
    }

    /**
     * 源表ETL处理接口
     */
    @PostMapping("/soutab-etl/{dbConn}")
    public ResponseEntity<Integer> soutabEtl(@PathVariable String dbConn) {
        Integer result = spAloneService.executeSourceTableEtl(dbConn);
        return ResponseEntity.ok(result);
    }

    /**
     * 更新参数接口
     */
    @PostMapping("/updt-param")
    public ResponseEntity<String> updtParam() {
        String result = spAloneService.updateParameters();
        return ResponseEntity.ok(result);
    }

    /**
     * 数据服务检查接口
     */
    @PostMapping("/dschk")
    public ResponseEntity<String> dschk() {
        String result = spAloneService.executeDataServiceCheck();
        return ResponseEntity.ok(result);
    }

    /**
     * 系统检查接口
     */
    @PostMapping("/syschk")
    public ResponseEntity<String> syschk() {
        String result = spAloneService.executeSystemCheck();
        return ResponseEntity.ok(result);
    }

    /**
     * 获取系统状态接口
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = spAloneService.getSystemStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 获取Redis标志状态接口
     */
    @GetMapping("/flags")
    public ResponseEntity<Map<String, Object>> getFlags() {
        Map<String, Object> flags = spAloneService.getRedisFlags();
        return ResponseEntity.ok(flags);
    }
}
