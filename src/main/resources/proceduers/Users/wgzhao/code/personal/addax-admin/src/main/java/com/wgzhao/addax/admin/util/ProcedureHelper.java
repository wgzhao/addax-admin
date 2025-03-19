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
            recordOperationJournal("public", getTdAsInt(), iKind, iKey, 
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

    /**
     * 实现 sp_imp_alone 存储过程
     * 独立处理任务
     *
     * @param iKind    操作类型
     * @param iSpId    任务ID，默认为空字符串
     * @param iValue1  参数值，默认为空字符串
     */
    public void spImpAlone(String iKind, String iSpId, String iValue1) {
        if (iSpId == null) {
            iSpId = "";
        }
        if (iValue1 == null) {
            iValue1 = "";
        }
        
        try {
            String vSpId = iSpId;
            String vRemark = "";
            int vTradedate = getTdAsInt();
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
                // 处理系统检查任务
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
        } catch (Exception e) {
            logger.error("Error in spImpAlone: " + e.getMessage(), e);
            // 发送错误通知
            spSms("sp_imp_alone执行报错,kind=[" + iKind + "],sp_id=[" + iSpId + 
                  "],i_value1=" + iValue1 + "],错误说明=[" + e.getMessage() + "]", 
                  "18692206867", "110");
        }
    }

    // 辅助方法

    /**
     * 获取交易日期
     */
    private String getTd() {
        return jdbcTemplate.queryForObject("SELECT stg01.gettd() FROM dual", String.class);
    }

    /**
     * 获取交易日期（整数形式）
     */
    private int getTdAsInt() {
        return jdbcTemplate.queryForObject("SELECT stg01.gettd() FROM dual", Integer.class);
    }

    /**
     * 获取任务信息
     */
    private Map<String, Object> getTaskInfo(String spId) {
        // 实现获取任务基础信息的逻辑
        // 这里需要根据实际情况实现复杂的SQL查询
        // 返回包含remark, error_count, source等信息的Map
        return null; // 实际实现时需要返回真实数据
    }

    /**
     * 更新主表状态
     */
    private void updateMainTableStatus(String spId, String source, String status, Date currentTime) {
        // 根据不同的source类型更新不同的表
        if ("sp".equals(source)) {
            // 更新SP表
            jdbcTemplate.update(
                    "UPDATE stg01.tb_imp_sp SET flag = ?, " +
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
                    "UPDATE stg01.tb_imp_sp_com SET flag = 'N' " +
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
                    "UPDATE stg01.tb_imp_sp_com SET flag = ?, " +
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
                "INSERT INTO stg01.tb_imp_jour(kind, trade_date, status, key_id, remark) " +
                "VALUES(?, ?, ?, ?, ?)",
                source, getTdAsInt(), status, keyId,
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
                "INSERT INTO stg01.tb_imp_jour(kind, trade_date, status, key_id, remark) " +
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
                    "SELECT sysid, sys_name, db_conn, db_user, db_pwd FROM stg01.vw_imp_system WHERE bvalid = 1");
            
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
                            "UPDATE stg01.tb_imp_system SET bvalid = 0 WHERE sysid = ?", 
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
                "SELECT sp_id, sp_owner, sp_name, runtime FROM stg01.tb_imp_sp " +
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
                "SELECT sp_id, sp_owner, sp_name FROM stg01.tb_imp_sp " +
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
            jdbcTemplate.update("TRUNCATE TABLE stg01.tmp_imp_hdp_tbls");
            
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
                    "INSERT INTO stg01.tmp_imp_hdp_tbls(db_name, tbl_name) VALUES(?, ?)",
                    "default", line.trim());
        }
    }
    
    /**
     * 更新Hadoop表结构信息
     */
    private void updateHadoopTableStructure() {
        // 从临时表更新到正式表
        jdbcTemplate.update(
                "MERGE INTO stg01.tb_imp_tbl_hdp t " +
                "USING stg01.tmp_imp_hdp_tbls s " +
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
                    "UPDATE stg01.tb_imp_etl SET bupdate = 'Y' " +
                    "WHERE tid IN (SELECT tid FROM stg01.vw_imp_tbl_diff_hive UNION " +
                    "SELECT tid FROM stg01.vw_imp_tbl_diff_mysql)");
            
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
                "    (SELECT MAX(init_date) FROM stg01.vw_trade_date WHERE init_date < ?) FROM dual " +
                "  UNION ALL " +
                "  SELECT 'next_trade_date', " +
                "    (SELECT MIN(init_date) FROM stg01.vw_trade_date WHERE init_date > ?) FROM dual " +
                "  /* 其他交易日期相关参数 */" +
                ")",
                tradeDate, tradeDate, tradeDate);
        
        for (Map<String, Object> param : tradeDateParams) {
            jdbcTemplate.update(
                    "INSERT INTO stg01.tb_imp_param0(param_name, param_value, param_sou) " +
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
                    "SELECT param_kind_0 FROM stg01.tb_imp_param0 " +
                    "WHERE param_sou = 'C' AND param_kind_0 IS NOT NULL " +
                    "ORDER BY param_kind_0",
                    String.class);
            
            if (paramNames.isEmpty()) {
                logger.warn("No parameters found for creating view");
                return;
            }
            
            // 构建视图创建语句
            StringBuilder viewSql = new StringBuilder();
            viewSql.append("CREATE OR REPLACE VIEW stg01.vw_imp_param_all AS ")
                  .append("SELECT * FROM (SELECT param_kind_0, param_value FROM stg01.tb_imp_param0 WHERE param_sou = 'C') ")
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