package com.wgzhao.addax.admin.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.List;
import java.util.Map;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class HdfsWriterTemplate{

    private String defaultFS;
    private String fileType;
    private String path;
    private List<Map<String, String>> column;
    private String compress;
    private boolean haveKerberos;
    private String kerberosKeytabFilePath;
    private String kerberosPrincipal;
    private boolean createPath=true;
    private boolean enableHA=true;
    private String hdfsSitePath;
    private String hadoopConfig;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson() {

        String columnJson;
        try {
            columnJson = objectMapper.writeValueAsString(column);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("column 转换为 JSON 失败", e);
        }

        String result = """
                {
                  "name": "hdfswriter",
                  "parameter": {
                    "defaultFS": "%s",
                    "fileType": "%s",
                    "compress": "%s",
                    "path": "%s",
                    "fileName": "addax",
                    "writeMode": "overwrite",
                    "column": %s,
                    "createPath": %b,
                """.formatted(
                defaultFS,
                fileType,
                compress,
                path,
                columnJson,
                createPath
        );
        if (haveKerberos) {
            if (kerberosKeytabFilePath == null || kerberosKeytabFilePath.isEmpty()) {
                throw new IllegalArgumentException("Kerberos keytab file path must be provided when Kerberos is enabled.");
            }
            if (kerberosPrincipal == null || kerberosPrincipal.isEmpty()) {
                throw new IllegalArgumentException("Kerberos principal must be provided when Kerberos is enabled.");
            }
            result += """
                    "haveKerberos": true,
                    "kerberosKeytabFilePath": "%s",
                    "kerberosPrincipal": "%s"
                    """.formatted(
                    kerberosKeytabFilePath,
                    kerberosPrincipal
            );
        }

        if (enableHA) {
            if (Strings.isBlank(hdfsSitePath) && (hadoopConfig == null || hadoopConfig.isEmpty())) {
                throw new IllegalArgumentException("Either hdfsSitePath or hadoopConfig must be provided when HA is enabled.");
            }
            if (Strings.isNotBlank(hdfsSitePath)) {
                result += """
                        "hdfsSitePath": "%s",
                        """.formatted(
                        hdfsSitePath
                );
            } else {
                result += """
                        "hadoopConfig": %s,
                        """.formatted(
                        hadoopConfig
                );
            }
        }
       // remove the last comma and close the JSON object
        result = result.replaceAll(",\\s*$", "") + "\n  }\n}";
        return result;
    }
}
