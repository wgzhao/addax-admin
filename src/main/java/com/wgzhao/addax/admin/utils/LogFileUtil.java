package com.wgzhao.addax.admin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志文件工具类
 * 主要是处理 Trino 以及 Addax 的日志文件
 */
@Component
public class  LogFileUtil {

    private static final Logger logger = LoggerFactory.getLogger(LogFileUtil.class);

    // 采集日志目录
    @Value("${log.dir}")
    private String logDir;

    /**
     * 获取日志文件
     *
     * @param cdate 要获取的日志的日期范围，格式为 yyyyMMdd,yyyyMMdd
     * @param job   任务名称
     * @return 日志文件列表
     */
    public List<String> getFs(String cdate, String job) {
        logger.info("query params: cdate: {}, job: {}", cdate, job);
        List<String> result = new ArrayList<>();
        // split cdate
        String[] dateList = cdate.split(",");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Date bTime;
        Date eTime;
        Pattern pattern;
        long today;
        try {
            bTime = sdf.parse(dateList[0]);
            eTime = sdf.parse(dateList[1]);
            today = sdf.parse(sdf.format(new Date())).getTime();
        } catch (ParseException e) {
            logger.error("parse date error: {}", e.getMessage());
            return result;
        }
        for (Date d = bTime; d.compareTo(eTime) <= 0; d.setTime(d.getTime() + 86400000)) {
            String curDir = String.format(sdf.format(d));
            if (job.startsWith("tuna")) {
                pattern = Pattern.compile(job + "_" + curDir + "_.*.log");
            } else {
                pattern = Pattern.compile("tuna_" + ".*?_" + job + "_\\d+_" + curDir + "_.*.log");
            }
            if (d.getTime() >= today) {
                result.addAll(findFiles("./", pattern));
            } else {
                result.addAll(findFiles(curDir, pattern));
            }
        }
        return result;
    }

    /**
     * Find file in special directory with regex pattern
     */
    private List<String> findFiles(String dir, Pattern pattern) {
        List<String> result = new ArrayList<>();
        File file = new File(logDir + File.separator + dir);
        if (!file.exists() || !file.isDirectory()) {
            return result;
        }
        for (File f : Objects.requireNonNull(file.listFiles())) {
            if (pattern.matcher(f.getName()).find()) {
                result.add(new File(logDir).toURI().relativize(f.toURI()).getPath());
            }
        }
        return result;
    }


    Comparator<String> comparator = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            Pattern p = Pattern.compile(".*(\\d{8})_(\\d{6}).*.log");
            String s1 = "";
            String s2 = "";
            Matcher matcher = p.matcher(o1);
            if (matcher.find()) {
                s1 = matcher.group(1) + matcher.group(2);
            }
            matcher = p.matcher(o2);
            if (matcher.find()) {
                s2 = matcher.group(1) + matcher.group(2);
            }
            return s2.compareTo(s1);
        }
    };

    /**
     * create html link for files
     */
    private String createLink(String path, List<String> files) {
        // sort files by special sort alg
        StringBuilder sb = new StringBuilder();
        // get the context prefix path from server.servlet.context-path

        String urlTemplate = "<a href=\"%s/get?fname=%s\">%s</a><br/>";
        String[] fileArray = new String[files.size()];
        // convert list to array
        files.toArray(fileArray);
        Arrays.sort(fileArray, comparator);
        for (String f : fileArray) {
            sb.append((String.format(urlTemplate, path, f, new File(f).getName())));
        }
        return sb.toString();
    }

    public String getFileContent(String fname) {
        String path = logDir + File.separator + fname;
        try {
            return Files.readString(Path.of(path));
        } catch ( IOException e) {
            logger.error("read file error: {}", e.getMessage());
            return "reading file error";
        }
    }


    public static void main(String[] args) {
        String spName = "tuna_addax_22FB1A5B424846878D9A882DE1598C7E";
        String tradeRange = "20240102,20240109";
        List<String> files = new LogFileUtil().getFs(tradeRange, spName);
        System.out.printf("files: %s\n", files);

    }
}
