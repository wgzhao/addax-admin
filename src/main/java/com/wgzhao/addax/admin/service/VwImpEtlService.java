package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.oracle.VwImpEtl;
import com.wgzhao.addax.admin.repository.oracle.VwImpEtlRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;

@Service
public class VwImpEtlService {

    @Autowired
    private VwImpEtlRepo vwImpEtlRepo;

    /**
     * implement the following sql
     *         select sysname,sou_owner,sou_tablename,sou_filter,dest_owner,dest_tablename,start_time,end_time
     *         from vw_imp_etl
     *         where lower(sysname||sou_owner||sou_tablename||dest_owner||dest_tablename)
     *         		like lower('%' || ?1 || '%')
     *         	and rownum<=100 order by 1,2,3
     */

    public Page<VwImpEtl> fetchEtlInfo(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        return vwImpEtlRepo.findAll(pageable);
    }

    /**
     * ODS 采集信息
     * 实现如下的 SQL 逻辑
     * select dest_owner,sou_owner,dest_tablename,sys_name,retry_cnt,tid,spname,flag,runtime
     * from stg01.vw_imp_etl
     * where (flag='${etl_flag}' or '${etl_flag}' is null) and
     * 	(
     * 	regexp_like(upper(dest_owner||'.'||dest_tablename||','||sou_owner||'.'||sou_tablename||','||realtime_taskgroup) ,'${etl_filter}') ) or
     * 	instr(upper(';${etl_filter};') , upper(';'||dest_owner||'.'||dest_tablename||';'))>0 or
     * 	spname='${etl_filter}' or
     * 	'${etl_filter}' is null
     * 	) and rownum<=100 order by 1,2,3
     *
     */
    public Page<VwImpEtl> getOdsInfo(int page, int pageSize, String q) {
        Pageable pageable = PageRequest.of(page, pageSize);
        if (! q.isEmpty()) {
            System.out.println("search with " + q.toUpperCase());
            return vwImpEtlRepo.findByFilterColumnContaining(q.toUpperCase(), pageable);
        }
        else {
            return vwImpEtlRepo.findAll(pageable);
        }
    }

    public VwImpEtl findOneODSInfo(String tid) {
        return vwImpEtlRepo.findById(tid).orElse(null);
    }
}
