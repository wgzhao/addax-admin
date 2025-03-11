package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.HiveTableColumn;
import com.wgzhao.addax.admin.model.oracle.TbImpEtlTblsTmp;
import com.wgzhao.addax.admin.repository.hive.HiveTableColumnRepository;
import com.wgzhao.addax.admin.repository.oracle.TbImpEtlTblsTmpRepo;
import com.wgzhao.addax.admin.utils.CacheUtil;
import jakarta.annotation.Resource;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import oracle.ucp.util.Pair;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.util.List;
import java.util.Map;

@Service
public class TaskService
{
    @Resource
    CacheUtil cacheUtil;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private HiveTableColumnRepository hiveRepo;

    @Autowired
    private TbImpEtlTblsTmpRepo tbImpEtlTblsTmpRepo;

    @Autowired
    @Qualifier("oracleJdbcTemplate")
    private JdbcTemplate oracleJdbcTemplate;
    /**
     * 表字段更新
     */
    private Pair<Boolean, String> soutabStart() {
        // step 1. set redis flag
        if (cacheUtil.sAdd("soutab", 1) != 1) {
            return new Pair<>(false, "soutab is running");
        }
        if (cacheUtil.sAdd("soutab.hadoop", 1) != 1) {
            return new Pair<>(false, "soutab.hadoop is running");
        }

        // step 2. 获取源库及hadoop的表结构信息
        copyTableStruct();
        return new Pair<>(true, "success");
    }

    @Transactional
    private boolean copyTableStruct() {
        int batchSize = 1000;
        // truncate table
        tbImpEtlTblsTmpRepo.deleteAllInBatch();
        List<HiveTableColumn> allHiveTableColumn = hiveRepo.findAllHiveTableColumn();
        int size = allHiveTableColumn.size();
        for (int i =0 ; i< size; i++ ){
            List<TbImpEtlTblsTmp> tmpBatchList = allHiveTableColumn.subList(i, Math.min(size, i + batchSize))
                    .stream()
                    .map(this::convertToTbImpEtlTblsTmp)  // 映射转换
                    .toList();
            tbImpEtlTblsTmpRepo.saveAll(tmpBatchList);
            tbImpEtlTblsTmpRepo.flush();
        }
        // call procedure
        String callSql = "{call proc_imp_etl_tbls_tmp(?, ?)}"；

        // 调用存储过程，传递参数
        oracleJdbcTemplate.call(con -> {
            CallableStatement cs = con.prepareCall(callSql);
            cs.setString(1, "soutab");
            cs.setString(2, "hadoop");
            return cs;
        }); // 可扩展结果集映射

    }

    // 转换方法：从 HiveTableColumn 转换为 TbImpEtlTblsTmp
    private TbImpEtlTblsTmp convertToTbImpEtlTblsTmp(HiveTableColumn hiveColumn) {
        TbImpEtlTblsTmp tbImpEtlTblsTmp = new TbImpEtlTblsTmp();
        BeanUtils.copyProperties(hiveColumn, tbImpEtlTblsTmp);

        return tbImpEtlTblsTmp;
    }
}
