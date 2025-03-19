package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.utils.FuncHelper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Oracle 存储过程的 Java 实现
 */
@Component
public class ProcedureHelper {
    private static final Logger logger = LoggerFactory.getLogger(ProcedureHelper.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Resource
    private FuncHelper funcHelper;

    /**
     * 实现 sp_sms 存储过程
     * 发送短信通知
     *
     * @param iMsg      消息内容
     * @param iMobile   手机号码，默认为"1"
     * @param iSendType 发送类型，默认为"010"
     */
    public void spSms(String iMsg, String iMobile, String iSendType) {
        if (iMobile == null || iMobile.isEmpty()) {
            iMobile = "1";
        }
        if (iSendType == null || iSendType.isEmpty()) {
            iSendType = "010";
        }

        try {
            // 处理手机号码，类似于原始存储过程中的 with t_in as 部分
            List<String> mobileList = new ArrayList<>();
            String[] groups = iMobile.split(",");
            for (String group : groups) {
                if (group.isEmpty()) continue;

                // 检查是否是组ID，如果是则查询对应的手机号
                List<String> groupMobiles = jdbcTemplate.queryForList(
                        "SELECT mobile FROM vw_mobile_group WHERE groupid = ?",
                        String.class, group);

                if (!groupMobiles.isEmpty()) {
                    mobileList.addAll(groupMobiles);
                } else {
                    mobileList.add(group);
                }
            }

            // 过滤有效的手机号
            List<String> validMobiles = new ArrayList<>();
            for (String mobile : mobileList) {
                if (jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM vw_mobile_group WHERE mobile = ?",
                        Integer.class, mobile) > 0) {
                    validMobiles.add(mobile);
                }
            }

            // 合并手机号
            String vMobile = String.join(",", validMobiles);

            // 获取当前时间
            Date now = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(now);

            // 调用 gettd() 函数获取交易日期
            String tradeDate = funcHelper.getTd().toString();

            // 构建消息内容
            String msgContent = tradeDate + ":" + iMsg.replaceAll(";$", "") + ";\n" + currentTime;
            if (msgContent.length() > 450) {
                msgContent = msgContent.substring(0, 450);
            }

            // 检查短信发送频率限制
            boolean canSendSms = true;
            if (iSendType.charAt(0) == '1') {
                int smsCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM tb_msg WHERE bsms<>'N' AND dw_clt_date>=?",
                        Integer.class, new Timestamp(now.getTime() - 60 * 1000)); // 最近1分钟

                if (smsCount >= 30) {
                    canSendSms = false;
                }
            }

            // 检查电话呼叫时间限制
            boolean canCall = true;
            SimpleDateFormat timeFormat = new SimpleDateFormat("HHmm");
            String currentHourMinute = timeFormat.format(new Date(now.getTime() + 60 * 60 * 1000)); // 当前时间+1小时

            if (currentHourMinute.compareTo("0000") >= 0 && currentHourMinute.compareTo("0800") <= 0) {
                canCall = false;
            }

            // 设置发送标志
            String bsms = (canSendSms && iSendType.charAt(0) == '1') ? "Y" : "N";
            String bkk = (iSendType.charAt(1) == '1') ? "Y" : "N";
            String bcall = (canCall && iSendType.charAt(2) == '1') ? "Y" : "N";

            // 插入消息记录
            jdbcTemplate.update(
                    "INSERT INTO tb_msg(phone, msg, bsms, bkk, bcall) VALUES(?, ?, ?, ?, ?)",
                    vMobile, msgContent, bsms, bkk, bcall);

            logger.info("Message sent: {}, to: {}, type: {}", iMsg, vMobile, iSendType);
        } catch (Exception e) {
            logger.error("Error in spSms: " + e.getMessage(), e);
        }
    }

    /**
     * 实现 sp_imp_status 存储过程
     * 更新任务状态
     *
     * @param iKind  状态类型
     * @param iSpId  任务ID
     */
    public void spImpStatus(String iKind, String iSpId) {
        try {
            Date vCurtime = new Date();

            // 获取基础信息
            Map<String, Object> info = getTaskInfo(iKind, iSpId);
            if (info.isEmpty()) {
                logger.warn("No task found with ID: {}", iSpId);
                return;
            }

            String vRemark = (String) info.get("remark");
            int vErr = (int) info.get("error_count");
            String vSou = (String) info.get("source");

            // 处理状态变更
            String vKind = iKind;
            if (iKind.equals("Y") && vErr > 0) {
                vKind = "E";
            }

            if (vKind.length() == 1) {
                // 主表状态变更
                updateMainTableStatus(iSpId, vSou, vKind, vCurtime);

                if (vKind.equals("R")) {
                    // 主表开始执行，附属表状态置为N
                    resetSubTaskStatus(iSpId, vSou);
                } else if (vKind.equals("E")) {
                    // 任务执行结束，报错提醒
                    sendErrorNotification(iSpId, vSou);
                }
            } else {
                // 附属表状态变更
                updateSubTaskStatus(iSpId, vSou, vKind.substring(1, 2), vCurtime);
            }

            // 记录操作流水
            recordOperationLog(vSou, vKind, iSpId, vRemark, vCurtime);

            logger.info("Status updated: {}, for task: {}, source: {}", vKind, iSpId, vSou);
        } catch (Exception e) {
            logger.error("Error in spImpStatus: " + e.getMessage(), e);
            // 发送错误通知
            spSms("sp_imp_status执行报错,i_kind=[" + iKind + "],i_sp_id=[" + iSpId + "],错误说明=[" + e.getMessage() + "]", "18692206867", "110");
        }
    }

    /**
     * 实现 sp_imp_param 存储过程
     * 处理系统参数
     *
     * @param iCurrDate 当前日期，默认为1
     */
    public void spImpParam(int iCurrDate) {
        try {
            int vCurrDate;
            if (iCurrDate / 10000000 != 2) {
                // 如果不是8位日期格式，则使用当前日期
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                vCurrDate = Integer.parseInt(sdf.format(new Date()));
            } else {
                vCurrDate = iCurrDate;
            }

            // 获取交易日期
            Integer vTradeDate = jdbcTemplate.queryForObject(
                    "SELECT MAX(init_date) FROM vw_trade_date WHERE init_date <= ?",
                    Integer.class, vCurrDate);

            if (vTradeDate == null) {
                logger.error("Failed to get trade date for current date: {}", vCurrDate);
                return;
            }

            // 确定参数来源
            String vParamSou;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String today = sdf.format(new Date());
            String yesterday = sdf.format(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
            String tomorrow = sdf.format(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));

            if (String.valueOf(vCurrDate).equals(today)) {
                vParamSou = "C";
            } else if (String.valueOf(vCurrDate).equals(yesterday)) {
                vParamSou = "L";
            } else if (String.valueOf(vCurrDate).equals(tomorrow)) {
                vParamSou = "N";
            } else {
                vParamSou = "T";
            }

            // 检查是否需要跳过周
            int vJumpWeek = 0;
            Integer weekCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM vw_trade_date WHERE init_date BETWEEN ? AND ?",
                    Integer.class,
                    getDateOffset(vTradeDate, -7, "week_start"),
                    getDateOffset(vTradeDate, -1, "week_start"));

            if (weekCount == null || weekCount == 0) {
                vJumpWeek = 7;
            }

            // 检查是否在允许的时间范围内更新参数
            SimpleDateFormat timeFormat = new SimpleDateFormat("HHmm");
            String currentTime = timeFormat.format(new Date());
            boolean shouldUpdateParams = (vParamSou.equals("T") ||
                    (vParamSou.matches("[CLN]") &&
                            currentTime.compareTo("1625") >= 0 &&
                            currentTime.compareTo("1635") <= 0));

            if (shouldUpdateParams) {
                // 清除旧参数
                jdbcTemplate.update("DELETE FROM tb_imp_param0 WHERE param_sou = ?", vParamSou);

                // 插入交易日期相关参数
                insertTradeDateParams(vTradeDate, vParamSou);

                // 插入其他日期相关参数
                insertOtherDateParams(vTradeDate, vParamSou, vJumpWeek);

                // 如果是当前日期参数，创建视图
                if (vParamSou.equals("C")) {
                    createParamView();
                }

                logger.info("Parameters updated for date: {}, source: {}", vCurrDate, vParamSou);
            } else {
                spSms("参数更新不在允许的时间范围内!!!", "1", "111");
            }
        } catch (Exception e) {
            logger.error("Error in spImpParam: " + e.getMessage(), e);
        }
    }

    /**
     * 实现 sp_imp_flag 存储过程
     * 处理标志
     *
     * @param iKind  操作类型
     * @param iGroup 标志组
     * @param iFid   标志ID
     * @param iFval  标志值，默认为0
     */
    public void spImpFlag(String iKind, String iGroup, String iFid, int iFval) {
        try {
            // 获取交易日期
            int vTradedate = funcHelper.getTd();

            if ("add".equals(iKind)) {
                // 新增标志
                jdbcTemplate.update(
                        "INSERT INTO tb_imp_flag(tradedate, kind, fid, fval) VALUES(?, ?, ?, ?)",
                        vTradedate, iGroup, iFid, iFval);

                logger.info("Flag added: {}, group: {}, id: {}, value: {}", vTradedate, iGroup, iFid, iFval);
            } else if ("del".equals(iKind)) {
                // 删除标志
                jdbcTemplate.update(
                        "DELETE FROM tb_imp_flag WHERE tradedate = ? AND INSTR(?,?,?) > 0 AND fid = ?",
                        vTradedate, "," + iGroup + ",", "," + "kind" + ",", 1, iFid);

                logger.info("Flag deleted: {}, group: {}, id: {}", vTradedate, iGroup, iFid);
            }
        } catch (Exception e) {
            logger.error("Error in spImpFlag: " + e.getMessage(), e);
            // 发送错误通知
            spSms("sp_imp_flag执行报错,kind=[" + iKind + "],group=[" + iGroup + "],fid=[" + iFid +
                    "],fval=" + iFval + "],错误说明=[" + e.getMessage() + "]", "18692206867", "110");
        }
    }

    /**
     * 实现 sp_imp_deal 存储过程
     * 处理导入任务
     *
     * @param iKind 操作类型
     * @param iKey  键值，默认为空字符串
     */
    public void spImpDeal(String iKind, String iKey) {
        if (iKey == null) {
            iKey = "";
        }

        try {
            Date vCurtime = new Date();

            // 获取SQL引擎列表
            String sqlengine = getSqlEngineList();

            // 处理git部署
            if ("git_deploy".equals(iKind) && iKey.length() == 32) {
                // 处理部署相关操作
                processCiDeploy(iKey, sqlengine);

                // 发送提醒信息
                sendDeployNotification(iKey);
            }

            // 记录操作流水
            recordOperationJournal("public", funcHelper.getTd(), iKind, iKey,
                    "开始时间：" + formatDateTime(vCurtime) +
                            ",执行耗时：" + calculateExecutionTime(vCurtime) +
                            "秒==>传入参数：{i_kind=[" + iKind + "],i_key=[" + iKey + "]}<==");

            logger.info("Import deal processed: {}, key: {}", iKind, iKey);
        } catch (Exception e) {
            logger.error("Error in spImpDeal: " + e.getMessage(), e);
            // 发送错误通知
            spSms("sp_imp_deal执行报错,kind=[" + iKind + "],key=[" + iKey +
                            "],错误说明=[" + e.getMessage().substring(0, Math.min(200, e.getMessage().length())) + "]",
                    "18692206867", "110");
        }
    }

    public boolean spImpAlone(String iKind)
    {
        return spImpAlone(iKind, "", "");
    }

    public boolean spImpAlone(String iKind, String iSpId) {
        return spImpAlone(iKind, iSpId, "");
    }
        /**
         * 实现 sp_imp_alone 存储过程
         * 独立处理任务
         *
         * @param iKind    操作类型
         * @param iSpId    任务ID，默认为空字符串
         * @param iValue1  参数值，默认为空字符串
         */
    public boolean spImpAlone(String iKind, String iSpId, String iValue1) {
        if (iSpId == null) {
            iSpId = "";
        }
        if (iValue1 == null) {
            iValue1 = "";
        }

        try {
            String vSpId = iSpId;
            String vRemark = "";
            int vTradedate = funcHelper.getTd();
            Date vCurtime = new Date();
            int vErr = 0;

            // 根据操作类型处理不同的任务
            if ("plan_start".equals(iKind)) {
                // 处理计划开始任务
                vSpId = handlePlanStart(iSpId, vTradedate, vCurtime, vRemark);
            } else if ("sp_start".equals(iKind)) {
                // 处理SP开始任务
                vSpId = handleSpStart(vTradedate);
            } else if ("etl_end".equals(iKind)) {
                // 处理ETL结束任务
                Map<String, Object> result = handleEtlEnd(iSpId);
                vRemark = (String) result.get("remark");
                vErr = (int) result.get("error_count");
            } else if ("real_after".equals(iKind)) {
                // 处理实时与盘后任务
                handleRealAfter(iSpId);
            } else if ("bupdate".equals(iKind)) {
                // 处理更新与建表任务
                handleBupdate(iSpId, iValue1);
            } else if ("syschk".equals(iKind)) {
                // 处理系统检测任务
                handleSysCheck();
            } else if ("get_hdptbls".equals(iKind)) {
                // 获取最新的hadoop表结构
                handleGetHdpTables();
            } else if ("colexch_updt".equals(iKind)) {
                // 刷新ODS采集表的源和目标结构
                vErr = handleColExchUpdate();
            }

            // 记录操作流水
            recordOperationJournal("public", vTradedate, iKind, vSpId,
                    vRemark + "\n开始时间：" + formatDateTime(vCurtime) +
                            ",执行耗时：" + calculateExecutionTime(vCurtime) +
                            "秒==>传入参数：{i_kind=[" + iKind + "],i_sp_id=[" + iSpId +
                            "],i_value1=[" + iValue1 + "],v_err=[" + vErr + "]}<==");

            logger.info("Alone task processed: {}, sp_id: {}, value1: {}", iKind, iSpId, iValue1);
            return true;
        } catch (Exception e) {
            logger.error("Error in spImpAlone: " + e.getMessage(), e);
            // 发送错误通知
            spSms("sp_imp_alone执行报错,kind=[" + iKind + "],sp_id=[" + iSpId +
                            "],i_value1=" + iValue1 + "],错误说明=[" + e.getMessage() + "]",
                    "18692206867", "110");
            return false;
        }
    }

    // 辅助方法

    /**
     * 获取任务信息
     */
    private Map<String, Object> getTaskInfo(String kind, String spId) {
        Date vCurtime = new Date();

        // Query basic information using a complex join similar to the SQL procedure
        String infoQuery =
                "WITH t_sp AS (" +
                        "SELECT sp_id, 'sp' AS sou, " +
                        "'SP主表信息：{名称=[' || spname || '],主表状态=[' || flag || '],前置源=[' || need_sou || " +
                        "'],剩余次数=[' || retry_cnt || '],运行耗时=[' || runtime || '],任务组=[' || task_group || '],参数组=[' || param_sou || ']}' AS remark " +
                        "FROM vw_imp_sp WHERE bvalid = 1 " +
                        "UNION ALL " +
                        "SELECT tid, 'etl', " +
                        "'ETL主表信息：{名称=[' || spname || '],源表=[' || sou_db_conn || ':' || sou_owner || '.' || sou_tablename || " +
                        "'],主表状态=[' || flag || '],剩余次数=[' || retry_cnt || '],运行耗时=[' || runtime || '],参数组=[' || param_sou || ']}' " +
                        "FROM vw_imp_etl WHERE bvalid = 1 " +
                        "UNION ALL " +
                        "SELECT pn_id, 'plan', " +
                        "'PLAN主表信息：{名称=[' || spname || '],主表状态=[' || flag || '],运行耗时=[' || runtime || ']}' " +
                        "FROM vw_imp_plan " +
                        "UNION ALL " +
                        "SELECT ds_id, 'ds', " +
                        "'DS主表信息：{名称=[' || ds_name || '],主表状态=[' || flag || '],剩余次数=[' || retry_cnt || '],运行耗时=[' || runtime || '],参数组=[' || param_sou || ']}' " +
                        "FROM vw_imp_ds2" +
                        "), t_com AS (" +
                        "SELECT sp_id, com_id, flag, '子表信息：{命令类型=[' || com_kind || '],命令顺序=[' || com_idx || '],命令状态=[' || flag || ']}' AS remark " +
                        "FROM tb_imp_sp_com " +
                        "UNION ALL " +
                        "SELECT ds_id, tbl_id, flag, '子表信息：{状态=[' || flag || '],目标表=[' || dest_tablename || ']}' " +
                        "FROM tb_imp_ds2_tbls" +
                        ") " +
                        "SELECT MAX(t.remark || CASE WHEN LENGTH(?) = 2 THEN CHR(10) || b.remark ELSE '' END) AS remark, " +
                        "coalesce(SUM(CASE WHEN coalesce(b.flag,'N') = 'Y' THEN 0 ELSE 1 END), -1) AS err_count, " +
                        "MAX(sou) AS sou " +
                        "FROM t_sp t " +
                        "INNER JOIN t_com b ON b.sp_id = t.sp_id AND ? IN (b.sp_id, b.com_id) AND coalesce(b.flag,'N') <> 'X' " +
                        "WHERE LENGTH(?) = 32";

        return jdbcTemplate.queryForMap(infoQuery, kind, spId, spId);
    }

    /**
     * 更新主表状态
     */
    private void updateMainTableStatus(String spId, String source, String status, Date currentTime) {
        // 根据不同的source类型更新不同的表
        if ("sp".equals(source)) {
            // 更新SP表
            jdbcTemplate.update(
                    "UPDATE tb_imp_sp SET flag = ?, " +
                            "start_time = CASE WHEN ? = 'R' THEN ? ELSE start_time END, " +
                            "end_time = CASE WHEN ? IN ('Y', 'E') THEN ? ELSE end_time END, " +
                            "runtime = CASE WHEN ? = 'Y' THEN (? - start_time) * 24 * 60 * 60 " +
                            "WHEN ? = 'E' THEN runtime / 2 ELSE runtime END, " +
                            "retry_cnt = retry_cnt - CASE WHEN ? = 'E' THEN 1 ELSE 0 END " +
                            "WHERE sp_id = ?",
                    status, status, new Timestamp(currentTime.getTime()),
                    status, new Timestamp(currentTime.getTime()),
                    status, new Timestamp(currentTime.getTime()),
                    status, status, spId);
        } else if ("etl".equals(source)) {
            // 更新ETL表
            // 类似的更新逻辑
        } else if ("plan".equals(source)) {
            // 更新计划任务表
            // 类似的更新逻辑
        } else if ("ds".equals(source)) {
            // 更新数据服务表
            // 类似的更新逻辑
        }
    }

    /**
     * 重置子任务状态
     */
    private void resetSubTaskStatus(String spId, String source) {
        // 根据不同的source类型重置不同表中的子任务状态
        if (source.equals("sp") || source.equals("etl") || source.equals("plan")) {
            jdbcTemplate.update(
                    "UPDATE tb_imp_sp_com SET flag = 'N' " +
                            "WHERE flag <> 'X' AND sp_id = ?",
                    spId);
        } else if (source.equals("ds")) {
            // 数据服务的子任务重置逻辑
            // 这里需要实现更复杂的条件判断
        }
    }

    /**
     * 发送错误通知
     */
    private void sendErrorNotification(String spId, String source) {
        // 查询需要发送通知的任务信息
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT source || ':' || name || ' 执行失败!!' || CHR(10) || " +
                        "'[' || TO_CHAR(start_time, 'YYYY-MM-DD HH24:MI:SS') || '=>' || " +
                        "TO_CHAR(end_time, 'HH24:MI:SS') || ']' || " +
                        "CASE WHEN source IN ('ds', 'plan') THEN error_msg END AS msg, " +
                        "mobile " +
                        "FROM your_view_or_query_here " +
                        "WHERE id = ? AND source = ?",
                spId, source);

        // 发送通知
        for (Map<String, Object> task : tasks) {
            spSms((String) task.get("msg"), (String) task.get("mobile"), "110");
        }
    }

    /**
     * 更新子任务状态
     */
    private void updateSubTaskStatus(String comId, String source, String status, Date currentTime) {
        if (source.equals("sp") || source.equals("etl") || source.equals("plan")) {
            jdbcTemplate.update(
                    "UPDATE tb_imp_sp_com SET flag = ?, " +
                            "start_time = CASE WHEN ? = 'R' THEN ? ELSE start_time END, " +
                            "end_time = CASE WHEN ? IN ('Y', 'E') THEN ? ELSE end_time END " +
                            "WHERE com_id = ? AND flag <> 'X'",
                    status, "cR", new Timestamp(currentTime.getTime()),
                    "cY", new Timestamp(currentTime.getTime()),
                    comId);
        } else if (source.equals("ds")) {
            // 数据服务的子任务状态更新
            // 类似的更新逻辑
        }
    }

    /**
     * 记录操作日志
     */
    private void recordOperationLog(String source, String status, String keyId, String remark, Date startTime) {
        jdbcTemplate.update(
                "INSERT INTO tb_imp_jour(kind, trade_date, status, key_id, remark) " +
                        "VALUES(?, ?, ?, ?, ?)",
                source, funcHelper.getTd(), status, keyId,
                remark + "\n开始时间：" + formatDateTime(startTime) +
                        ",执行耗时：" + calculateExecutionTime(startTime) +
                        "秒==>传入参数：{i_kind=[" + status + "],i_sp_id=[" + keyId + "]}<==");
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        return sdf.format(date);
    }

    /**
     * 计算执行时间（秒）
     */
    private long calculateExecutionTime(Date startTime) {
        return (System.currentTimeMillis() - startTime.getTime()) / 1000;
    }

    /**
     * 获取SQL引擎列表
     */
    private String getSqlEngineList() {
        // 实现获取SQL引擎列表的逻辑
        return "presto|shell|hive|clickhouse|allsql|crmdb|ora_(1,2,3)|my_(1,2,3)";
    }

    /**
     * 处理CI部署
     */
    private void processCiDeploy(String depId, String sqlengine) {
        // 实现处理CI部署的逻辑
        // 这里需要实现复杂的部署流程
    }

    /**
     * 发送部署通知
     */
    private void sendDeployNotification(String depId) {
        // 实现发送部署通知的逻辑
    }

    /**
     * 记录操作流水
     */
    private void recordOperationJournal(String kind, int tradeDate, String status, String keyId, String remark) {
        jdbcTemplate.update(
                "INSERT INTO tb_imp_jour(kind, trade_date, status, key_id, remark) " +
                        "VALUES(?, ?, ?, ?, ?)",
                kind, tradeDate, status, keyId, remark);
    }

    /**
     * 处理计划开始任务
     */
    private String handlePlanStart(String spId, int tradedate, Date curtime, String remark) {
        StringBuilder spIdBuilder = new StringBuilder(spId == null ? "" : spId);

        // 实现处理计划开始任务的逻辑
        // 返回更新后的spId

        return spIdBuilder.toString();
    }

    /**
     * 处理SP开始任务
     */
    private String handleSpStart(int tradedate) {
        StringBuilder spIdBuilder = new StringBuilder();

        // 实现处理SP开始任务的逻辑
        // 返回更新后的spId

        return spIdBuilder.toString();
    }

    /**
     * 处理ETL结束任务
     */
    private Map<String, Object> handleEtlEnd(String spId) {
        Map<String, Object> result = new HashMap<>();

        // 实现处理ETL结束任务的逻辑
        // 设置remark和error_count

        return result;
    }

    /**
     * 处理实时与盘后任务
     */
    private void handleRealAfter(String spId) {
        // 实现处理实时与盘后任务的逻辑
    }

    /**
     * 处理更新与建表任务
     */
    private void handleBupdate(String spId, String value1) {
        // 实现处理更新与建表任务的逻辑
    }

    /**
     * 处理系统检查任务
     */
    private void handleSysCheck() {
        // 实现系统检查逻辑
        try {
            // 检查数据库连接状态
            List<Map<String, Object>> systems = jdbcTemplate.queryForList(
                    "SELECT sysid, sys_name, db_conn, db_user, db_pwd FROM vw_imp_system WHERE bvalid = 1");

            for (Map<String, Object> system : systems) {
                String sysId = (String) system.get("sysid");
                String sysName = (String) system.get("sys_name");
                String dbConn = (String) system.get("db_conn");
                String dbUser = (String) system.get("db_user");
                String dbPwd = (String) system.get("db_pwd");

                boolean isConnected = checkDatabaseConnection(dbConn, dbUser, dbPwd);

                if (!isConnected) {
                    // 发送连接失败通知
                    spSms("系统检查: " + sysName + "(" + sysId + ") 数据库连接失败!", "1", "110");

                    // 更新系统状态
                    jdbcTemplate.update(
                            "UPDATE tb_imp_system SET bvalid = 0 WHERE sysid = ?",
                            sysId);
                }
            }

            // 检查任务执行状态
            checkTaskExecutionStatus();

            logger.info("System check completed");
        } catch (Exception e) {
            logger.error("Error in handleSysCheck: " + e.getMessage(), e);
            spSms("系统检查执行失败: " + e.getMessage(), "1", "110");
        }
    }

    /**
     * 检查数据库连接
     */
    private boolean checkDatabaseConnection(String dbConn, String dbUser, String dbPwd) {
        Connection conn = null;
        try {
            // 根据连接字符串类型创建不同的连接
            if (dbConn.startsWith("jdbc:oracle")) {
                Class.forName("oracle.jdbc.driver.OracleDriver");
            } else if (dbConn.startsWith("jdbc:mysql")) {
                Class.forName("com.mysql.jdbc.Driver");
            } else if (dbConn.startsWith("jdbc:postgresql")) {
                Class.forName("org.postgresql.Driver");
            }

            conn = java.sql.DriverManager.getConnection(dbConn, dbUser, dbPwd);
            return true;
        } catch (Exception e) {
            logger.error("Database connection failed: " + dbConn, e);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * 检查任务执行状态
     */
    private void checkTaskExecutionStatus() {
        // 检查长时间运行的任务
        List<Map<String, Object>> longRunningTasks = jdbcTemplate.queryForList(
                "SELECT sp_id, sp_owner, sp_name, runtime FROM tb_imp_sp " +
                        "WHERE flag = 'R' AND runtime > 3600"); // 运行超过1小时的任务

        if (!longRunningTasks.isEmpty()) {
            StringBuilder msg = new StringBuilder("以下任务运行时间过长:\n");
            for (Map<String, Object> task : longRunningTasks) {
                msg.append(task.get("sp_owner")).append(".").append(task.get("sp_name"))
                        .append(" (").append(task.get("runtime")).append("秒)\n");
            }
            spSms(msg.toString(), "1", "110");
        }

        // 检查失败的任务
        List<Map<String, Object>> failedTasks = jdbcTemplate.queryForList(
                "SELECT sp_id, sp_owner, sp_name FROM tb_imp_sp " +
                        "WHERE flag = 'E' AND retry_cnt = 0"); // 重试次数用完仍失败的任务

        if (!failedTasks.isEmpty()) {
            StringBuilder msg = new StringBuilder("以下任务执行失败且无法重试:\n");
            for (Map<String, Object> task : failedTasks) {
                msg.append(task.get("sp_owner")).append(".").append(task.get("sp_name")).append("\n");
            }
            spSms(msg.toString(), "1", "110");
        }
    }

    /**
     * 获取Hadoop表结构
     */
    private void handleGetHdpTables() {
        try {
            // 清空临时表
            jdbcTemplate.update("TRUNCATE TABLE tmp_imp_hdp_tbls");

            // 获取Hadoop表结构信息
            // 这里需要通过外部命令或API调用获取Hadoop表结构
            // 例如使用JDBC连接Hive或调用shell命令

            // 示例：通过执行shell命令获取表结构
            String command = "hive -e \"show tables in default\" > /tmp/hive_tables.txt";
            Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", command});
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                // 读取输出文件并解析表结构
                parseHadoopTables("/tmp/hive_tables.txt");

                // 更新表结构信息
                updateHadoopTableStructure();

                logger.info("Hadoop table structure updated successfully");
            } else {
                logger.error("Failed to execute Hadoop command, exit code: " + exitCode);
                spSms("获取Hadoop表结构失败，命令执行错误", "1", "110");
            }
        } catch (Exception e) {
            logger.error("Error in handleGetHdpTables: " + e.getMessage(), e);
            spSms("获取Hadoop表结构失败: " + e.getMessage(), "1", "110");
        }
    }

    /**
     * 解析Hadoop表结构
     */
    private void parseHadoopTables(String filePath) throws Exception {
        // 读取文件内容并解析表结构
        java.nio.file.Path path = java.nio.file.Paths.get(filePath);
        List<String> lines = java.nio.file.Files.readAllLines(path);

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            // 插入表信息到临时表
            jdbcTemplate.update(
                    "INSERT INTO tmp_imp_hdp_tbls(db_name, tbl_name) VALUES(?, ?)",
                    "default", line.trim());
        }
    }

    /**
     * 更新Hadoop表结构信息
     */
    private void updateHadoopTableStructure() {
        // 从临时表更新到正式表
        jdbcTemplate.update(
                "MERGE INTO tb_imp_tbl_hdp t " +
                        "USING tmp_imp_hdp_tbls s " +
                        "ON (t.db_name = s.db_name AND t.tbl_name = s.tbl_name) " +
                        "WHEN MATCHED THEN " +
                        "  UPDATE SET t.last_update = SYSDATE " +
                        "WHEN NOT MATCHED THEN " +
                        "  INSERT (db_name, tbl_name, create_time, last_update) " +
                        "  VALUES (s.db_name, s.tbl_name, SYSDATE, SYSDATE)");
    }

    /**
     * 处理列交换更新
     */
    private int handleColExchUpdate() {
        try {
            // 更新ODS采集表的源和目标结构
            int updatedCount = jdbcTemplate.update(
                    "UPDATE tb_imp_etl SET bupdate = 'Y' " +
                            "WHERE tid IN (SELECT tid FROM vw_imp_tbl_diff_hive UNION " +
                            "SELECT tid FROM vw_imp_tbl_diff_mysql)");

            if (updatedCount > 0) {
                // 发送通知
                spSms("已更新" + updatedCount + "个表的结构信息", "1", "000");
            }

            return updatedCount;
        } catch (Exception e) {
            logger.error("Error in handleColExchUpdate: " + e.getMessage(), e);
            spSms("更新表结构信息失败: " + e.getMessage(), "1", "110");
            return 0;
        }
    }

    /**
     * 获取日期偏移
     */
    private String getDateOffset(int baseDate, int offset, String offsetType) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            Date date = sdf.parse(String.valueOf(baseDate));
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            if ("week_start".equals(offsetType)) {
                // 获取周的开始日期
                cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                cal.add(Calendar.DATE, offset);
            } else if ("month_start".equals(offsetType)) {
                // 获取月的开始日期
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MONTH, offset);
            } else {
                // 普通日期偏移
                cal.add(Calendar.DATE, offset);
            }

            return sdf.format(cal.getTime());
        } catch (Exception e) {
            logger.error("Error calculating date offset: " + e.getMessage(), e);
            return String.valueOf(baseDate);
        }
    }

    /**
     * 插入交易日期相关参数
     */
    private void insertTradeDateParams(int tradeDate, String paramSou) {
        // 查询交易日期相关参数并插入
        List<Map<String, Object>> tradeDateParams = jdbcTemplate.queryForList(
                "SELECT param_kind, param_value FROM (" +
                        "  SELECT 'trade_date' AS param_kind, ? AS param_value FROM dual " +
                        "  UNION ALL " +
                        "  SELECT 'last_trade_date', " +
                        "    (SELECT MAX(init_date) FROM vw_trade_date WHERE init_date < ?) FROM dual " +
                        "  UNION ALL " +
                        "  SELECT 'next_trade_date', " +
                        "    (SELECT MIN(init_date) FROM vw_trade_date WHERE init_date > ?) FROM dual " +
                        "  /* 其他交易日期相关参数 */" +
                        ")",
                tradeDate, tradeDate, tradeDate);

        for (Map<String, Object> param : tradeDateParams) {
            jdbcTemplate.update(
                    "INSERT INTO tb_imp_param0(param_name, param_value, param_sou) " +
                            "VALUES(?, ?, ?)",
                    "$$" + param.get("param_kind"), param.get("param_value"), paramSou);
        }
    }

    /**
     * 插入其他日期相关参数
     */
    private void insertOtherDateParams(int tradeDate, String paramSou, int jumpWeek) {
        // 插入其他日期相关参数
        // 这里需要实现复杂的日期计算和参数插入逻辑
    }

    /**
     * 创建参数视图
     */
    private void createParamView() {
        try {
            // 获取所有参数名
            List<String> paramNames = jdbcTemplate.queryForList(
                    "SELECT param_kind_0 FROM tb_imp_param0 " +
                            "WHERE param_sou = 'C' AND param_kind_0 IS NOT NULL " +
                            "ORDER BY param_kind_0",
                    String.class);

            if (paramNames.isEmpty()) {
                logger.warn("No parameters found for creating view");
                return;
            }

            // 构建视图创建语句
            StringBuilder viewSql = new StringBuilder();
            viewSql.append("CREATE OR REPLACE VIEW vw_imp_param_all AS ")
                    .append("SELECT * FROM (SELECT param_kind_0, param_value FROM tb_imp_param0 WHERE param_sou = 'C') ")
                    .append("PIVOT (MAX(param_value) FOR param_kind_0 IN (");

            for (String paramName : paramNames) {
                viewSql.append("'").append(paramName).append("' AS ").append(paramName).append(",");
            }

            // 移除最后一个逗号并添加结束括号
            String sql = viewSql.substring(0, viewSql.length() - 1) + "))";

            // 执行视图创建
            jdbcTemplate.execute(sql);

            logger.info("Parameter view created successfully");
        } catch (Exception e) {
            logger.error("Error creating parameter view: " + e.getMessage(), e);
        }
    }
}
