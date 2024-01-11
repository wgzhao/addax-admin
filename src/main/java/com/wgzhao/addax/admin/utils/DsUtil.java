package com.wgzhao.addax.admin.utils;

import oracle.ucp.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 调度工具命令行类
 */
@Component
public class DsUtil {
    private static final Logger logger = LoggerFactory.getLogger(DsUtil.class);

    // 调度工具命令脚本
    @Value("${addax.ds.path}")
    private static String dsExec;

    private final Map<String, String> ctypeMap = Map.of(
            "source", "soutab_start",
            "sp", "sp_start",
            "spcom", "spcom");
    /**
     * 执行调度工具命令
     * @param ctype 任务类型
     * @param sp_id 任务ID
     *
     * @return 调度工具命令执行结果
     */
    public Pair<Boolean, String>  execDs(String ctype, String sp_id ) {
        logger.info("exec ds: ctype: {}, sp_id: {}", ctype, sp_id);
        StringBuilder sb = new StringBuilder(" ");
        sb.append(dsExec);
        if (! ctypeMap.containsKey(ctype)) {
            logger.error("bad ctype: {}", ctype);
            return new Pair<>(false, "bad ctype");
        }
        sb.append(" ").append(ctypeMap.get(ctype));

        try {
            Process process = Runtime.getRuntime().exec(sb.toString());
            process.waitFor();
            return new Pair<>(true, new String(process.getInputStream().readAllBytes()));
        } catch (IOException | InterruptedException e) {
            logger.error("exec ds error: {}", e.getMessage());
            return new Pair<>(false,  e.getMessage());
        }
    }

    public String getFileContent(String path) {
        try {
            return Files.readString(Paths.get(path));
        } catch (IOException e) {
            logger.error("read file error: {}", e.getMessage());
            return null;
        }
    }
}
