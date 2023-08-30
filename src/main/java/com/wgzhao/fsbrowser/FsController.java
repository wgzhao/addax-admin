package com.wgzhao.fsbrowser;

import org.apache.logging.log4j.util.StringBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

@RestController
public class FsController {

    private static final Logger logger = LoggerFactory.getLogger(FsController.class);

    private static final String DS_EXEC = "/opt/infalog/bin/sp_alone.sh start_wkf";

    @Value("${log.dir}")
    private String logDir;

    @RequestMapping("/")
    public String index() {
        return "<h3>\n" +
                "                <pre>\n" +
                "                get specify hdfs file's content\n" +
                "        url: /fs/cdate/kind/fname\n" +
                "        e.g: /fs/20181024/hadoop_proc/sp_report_vw\n" +
                "        it convert kind and fname to upper, and take the fname as wildcase to match all filename\n" +
                "                </pre>\n" +
                "        </h3>";
    }
    /**
     * fs/{cdate}/{job}
     * @return
     */
    @RequestMapping("/fs/{cdate}/{job}")
    public String getFs(@PathVariable  String cdate, @PathVariable String  job) throws ParseException {
        logger.info("query params: cdate: {}, job: {}", cdate, job);
        List<String> result = new ArrayList<>();
        // split cdate
        String[] dateList = cdate.split(",");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Date bTime = sdf.parse(dateList[0]);
        Date eTime = sdf.parse(dateList[1]);
        // date as folder , find all files from bTime to eTime as folder
        List<String> files = new ArrayList<>();
        Pattern pattern;
        long today = sdf.parse(sdf.format(new Date())).getTime();
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
        if (result.size() == 1) {
            return getFileContent(result.get(0));
        } else {
            return createLink(result);
        }
    }

    @RequestMapping("/get")
    public String get(@RequestParam String fname) {
        logger.info("get file: {}", fname);
        return getFileContent(fname);
    }

    @RequestMapping("/ds")
    public String execDs(String ctype, String sp_id ) {
        logger.info("exec ds: ctype: {}, sp_id: {}", ctype, sp_id);
        StringBuilder sb = new StringBuilder(" ");
        sb.append(DS_EXEC);
        if (Objects.equals(ctype, "source")) {
             sb.append(" soutab_start ");
        } else if (Objects.equals(ctype, "sp")) {
            sb.append(" sp_start ");
        } else if (Objects.equals(ctype, "spcom")) {
            sb.append(" spcom ").append(sp_id);
        } else {
            // return HTTP 400 bad entity
            return "bad entity";
        }
        try {
            Process process = Runtime.getRuntime().exec(sb.toString());
            process.waitFor();
            return new String(process.getInputStream().readAllBytes());
        } catch (IOException | InterruptedException e) {
            logger.error("exec ds error: {}", e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * Find file in special directory with regex pattern
     */
    private List<String> findFiles(String dir, Pattern pattern) {
        List<String> result = new ArrayList<>();
        File file = new File(logDir + File.separator + dir);
        if (! file.exists() || ! file.isDirectory()) {
            return result;
        }
        for (File f: Objects.requireNonNull(file.listFiles())) {
            if (pattern.matcher(f.getName()).find()) {
                result.add(f.getName());
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
    private String createLink(List<String> files)
    {
        // sort files by special sort alg
        StringBuilder sb = new StringBuilder();
        String urlTemplate = "<a href=\"/get?fname=%s\">%s</a><br/>";
        String[] fileArray = new String[files.size()];
        // convert list to array
        files.toArray(fileArray);
        Arrays.sort(fileArray, comparator);
        for(String f : fileArray) {
            sb.append((String.format(urlTemplate, f, new File(f).getName())));
        }
        return sb.toString();
    }

    private String getFileContent(String fname)  {
        File file = new File(logDir + "/" + fname);
        if (! file.exists() || ! file.isFile()) {
            return "";
        }
        try {
            return "<pre>" + Files.readString(file.toPath()) + "</pre>";
        } catch (IOException e) {
            logger.error("read file error: " + e.getMessage());
            return "";
        }
    }
}
