package com.wgzhao.addax.admin.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

@RestController
@RequestMapping("/fsapi")
public class FsController {

    private static final Logger logger = LoggerFactory.getLogger(FsController.class);

    private String dsExec;

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

    @RequestMapping("/get")
    public String get(@RequestParam String fname) {
        logger.info("get file: {}", fname);
        return getFileContent(fname);
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
