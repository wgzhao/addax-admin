package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.TbDictionary;
import com.wgzhao.addax.admin.model.TbImpDb;
import com.wgzhao.addax.admin.model.TbImpEtl;
import com.wgzhao.addax.admin.model.TbImpEtlSoutab;
import com.wgzhao.addax.admin.model.TbImpTblHdp;
import com.wgzhao.addax.admin.model.VwImpEtl;
import com.wgzhao.addax.admin.repository.TbDictionaryRepo;
import com.wgzhao.addax.admin.repository.TbImpDBRepo;
import com.wgzhao.addax.admin.repository.TbImpEtlHdpRepo;
import com.wgzhao.addax.admin.repository.TbImpEtlRepo;
import com.wgzhao.addax.admin.repository.TbImpEtlSoutabRepo;
import com.wgzhao.addax.admin.repository.VwImpEtlRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import com.wgzhao.addax.admin.utils.QueryUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImpEtlService
{

    @Autowired
    private TbImpEtlRepo tbImpEtlRepo;

    @Autowired
    private TbImpDBRepo tbImpDBRepo;

    @Autowired
    private TbDictionaryRepo tbDictionaryRepo;

    @Autowired
    private TbImpEtlHdpRepo tbImpEtlHdpRepo;

    @Autowired
    private TbImpEtlSoutabRepo tbImpEtlSoutabRepo;

    @Autowired
    private EtlTaskEntryService etlTaskEntryService;

    public Page<TbImpEtl> fetchEtlInfo(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        return tbImpEtlRepo.findAll(pageable);
    }

    /**
     * ODS 采集信息
     *
     */
    public Page<TbImpEtl> getOdsInfo(int page, int pageSize, String q, String sortField, String sortOrder) {


        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        if (q != null && !q.isEmpty()) {
            System.out.println("search " + q.toUpperCase());
            return tbImpEtlRepo.findByFilterColumnContaining(q.toUpperCase(), pageable);
        } else {
            return tbImpEtlRepo.findAll(pageable);
        }
    }

    public Page<TbImpEtl> getOdsByFlag(int page, int pageSize, String q, String flag, String sortField, String sortOrder) {
        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        return tbImpEtlRepo.findByFlagAndFilterColumnContaining(flag, q.toUpperCase(), pageable);
    }

    public TbImpEtl findOneODSInfo(String tid) {
        return tbImpEtlRepo.findById(tid).orElse(null);
    }

    /**
     * 添加表的字段信息到 tb_imp_etl_soutab 表，这里是源表的信息
     * 同时把表字段信息添加到 tb_imp_tbl_hdp 表中，这是用于 Hive 的表字段信息
     * @param tbImpEtl tb_imp_etl 表记录
     * @return true 成功， false 失败
     */
    public boolean addTableInfo(TbImpEtl tbImpEtl)
    {
        if (tbImpEtl == null) {
            return false;
        }
        // 获取数据库连接信息
        TbImpDb dbInfo = tbImpDBRepo.findByDbIdEtl(tbImpEtl.getSouSysid());
        // 获取源表的字段信息
        Connection connection = DbUtil.getConnect(dbInfo.getDbConstr(), dbInfo.getDbUserEtl(), dbInfo.getDbPassEtl());
        if (connection == null) {
            return false;
        }
        List<TbDictionary> t = tbDictionaryRepo.findByEntryCode(2011);
        Map<String, String> hiveTypeMapping = new HashMap<>();
        for (TbDictionary dict : t) {
            hiveTypeMapping.put(dict.getEntryValue(), dict.getEntryContent());
        }

        String sql  = "select * from " + tbImpEtl.getSouOwner() + "." + tbImpEtl.getSouTablename() + " where 1=0";
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)){
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            TbImpEtlSoutab tbImpEtlSoutab = new TbImpEtlSoutab();
            tbImpEtlSoutab.setTid(tbImpEtl.getTid());
            tbImpEtlSoutab.setSouDbConn("DB_" + tbImpEtl.getSouSysid());
            tbImpEtlSoutab.setOwner(tbImpEtl.getSouOwner());
            tbImpEtlSoutab.setTableName(tbImpEtl.getSouTablename());

            TbImpTblHdp tbImpTblHdp = new TbImpTblHdp();
            tbImpTblHdp.setTid(tbImpEtl.getTid());
            tbImpTblHdp.setHiveOwner("ods" + tbImpEtl.getSouSysid().toLowerCase());
            tbImpTblHdp.setHiveTablename(tbImpEtl.getDestTablename());

            for (int i = 1; i <= columnCount; i++) {
                tbImpEtlSoutab.setColumnId(i);
                tbImpEtlSoutab.setColumnName(metaData.getColumnName(i));
                tbImpEtlSoutab.setDataType(metaData.getColumnTypeName(i));
                tbImpEtlSoutab.setDataLength(metaData.getColumnDisplaySize(i));
                tbImpEtlSoutab.setDataPrecision(metaData.getPrecision(i));
                tbImpEtlSoutab.setDataScale(metaData.getScale(i));
//                tbImpEtlSoutab.setColComment(metaData.getColumnClassName(i));
                // current timestamp
                tbImpEtlSoutab.setDwCltDate(new Timestamp(System.currentTimeMillis()));

                tbImpTblHdp.setColIdx(i);
                tbImpTblHdp.setColName(metaData.getColumnName(i));
                // map SQL type to Hive type
                String hiveType = hiveTypeMapping.getOrDefault(metaData.getColumnTypeName(i), "string");
                tbImpTblHdp.setColTypeFull(hiveType);
                tbImpTblHdp.setColType(hiveType);
                tbImpTblHdp.setColPrecision(metaData.getPrecision(i));
                tbImpTblHdp.setColScale(metaData.getScale(i));
                tbImpTblHdp.setUpdtDate(new Timestamp(System.currentTimeMillis()));
//                tbImpTblHdp.setColComment(metaData.getColumnLabel(i));
                // save them
                tbImpEtlSoutabRepo.save(tbImpEtlSoutab);
                tbImpEtlHdpRepo.save(tbImpTblHdp);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public boolean addTableInfo() {
        List<TbImpEtl> etlList = tbImpEtlRepo.findByBupdateOrBcreateIsY();
        List<String> tids = etlList.stream().map(TbImpEtl::getTid).toList();
        for (TbImpEtl etl : etlList) {
            if (!addTableInfo(etl)) {
                return false;
            }
            etl.setBcreate("N");
            etl.setBupdate("N");
            etl.setFlag("N");
            tbImpEtlRepo.save(etl);
        }
        // update job table
        etlTaskEntryService.updateJob(tids);
        return true;
    }
}
