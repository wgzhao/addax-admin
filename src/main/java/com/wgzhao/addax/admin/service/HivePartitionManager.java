package com.wgzhao.addax.admin.service;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStore;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.Partition;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class HivePartitionManager
{

    public void addPartition(String dbName, String tableName, Map<String, String> partitionSpec) throws HiveException {
        HiveMetaStore client = null;
        try {
            // 初始化 Hive 配置
            HiveConf hiveConf = new HiveConf();
            hiveConf.set("javax.jnlp.start", "true");
            hiveConf.set("hive.metastore.uris", "thrift://nn01:8903");
            // 获取 Hive 元数据客户端
            Hive hive =  Hive.get(hiveConf);
            Table table = hive.getTable(dbName, tableName);
            // 创建分区

            // 检查分区是否已存在
            if (hive.getPartition(table, partitionSpec, false) != null) {
                System.out.println("分区已存在：" + partitionSpec);
                return;
            }

            // 创建分区并添加到表
            Partition partition = hive.createPartition(table, partitionSpec);
            System.out.println("成功添加分区：" + partitionSpec);

        } catch (Exception e) {
            throw new HiveException("Failed to add partition: " + e.getMessage(), e);
        }
    }

    /**
     * 解析分区规格字符串，支持格式如: "year=2023/month=12/day=25" 或 "year=2023,month=12,day=25"
     */
    private Map<String, String> parsePartitionSpec(String partitionSpec) {
        Map<String, String> partitionValues = new HashMap<>();

        if (partitionSpec == null || partitionSpec.trim().isEmpty()) {
            throw new IllegalArgumentException("Partition specification cannot be null or empty");
        }

        // 支持两种分隔符：'/' 和 ','
        String[] parts = partitionSpec.split("[/,]");

        for (String part : parts) {
            part = part.trim();
            if (part.contains("=")) {
                String[] keyValue = part.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    // 移除值两边的引号（如果存在）
                    if ((value.startsWith("'") && value.endsWith("'")) ||
                        (value.startsWith("\"") && value.endsWith("\""))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    partitionValues.put(key, value);
                }
            }
        }

        if (partitionValues.isEmpty()) {
            throw new IllegalArgumentException("Invalid partition specification: " + partitionSpec);
        }

        return partitionValues;
    }
}
