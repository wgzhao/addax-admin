package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.VwImpEtl;
import com.wgzhao.fsbrowser.repository.oracle.VwImpEtlRepo;
import org.springframework.beans.factory.annotation.Autowired;
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

    public List<VwImpEtl> fetchEtlInfo(String filter) {

        List<Sort.Order> orders = new ArrayList<>();
        orders.add(new Sort.Order(Sort.Direction.ASC, "sysname"));
        orders.add(new Sort.Order(Sort.Direction.ASC, "souOwner"));
        orders.add(new Sort.Order(Sort.Direction.ASC, "souTablename"));
        if (filter == null || filter.isEmpty()) {
            return vwImpEtlRepo.findAll(null, 0, 100,Sort.by(orders));
        }
        // create custom Specification
        Specification<VwImpEtl> spec = (root, query, cb) -> {
            // query clause set
            // where lower(sysname||sou_owner||sou_tablename||dest_owner||dest_tablename) like lower('%' || ?1 || '%')
            String likeFilter = "%" + filter.toLowerCase() + "%";
            return cb.like(cb.lower(
                    cb.concat(
                            cb.concat(
                                    cb.concat(
                                            cb.concat(
                                                    root.get("sysname"),
                                                    root.get("souOwner")
                                            ),
                                            root.get("souTablename")
                                    ),
                                    root.get("destOwner")
                            ),
                            root.get("destTablename")
                    )
            ), likeFilter);
        };
        return vwImpEtlRepo.findAll(spec, 0, 100, Sort.by(orders));
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
    public List<VwImpEtl> getOdsInfo(String etlFlag, String etlFilter) {
        List<Sort.Order> orders = new ArrayList<>();
        orders.add(new Sort.Order(Sort.Direction.ASC, "destOwner"));
        orders.add(new Sort.Order(Sort.Direction.ASC, "souOwner"));
        orders.add(new Sort.Order(Sort.Direction.ASC, "destTablename"));
        if (etlFlag.isEmpty() && etlFilter.isEmpty()) {
            return vwImpEtlRepo.findAll(null, 0, 100);
        }
        // create custom Specification
        Specification<VwImpEtl> spec = (root, query, cb) -> {
            Predicate flag = null;
            Predicate filter = null;
            if (! etlFlag.isEmpty()) {
                flag = cb.equal(root.get("flag"), etlFlag);
            }
            if (! etlFilter.isEmpty()) {
                filter = cb.like(root.get("filterColumn"), "%" + etlFilter.toUpperCase() + "%");
            }
            if (flag == null) {
                return filter;
            } else if (filter == null) {
                return flag;
            } else {
                return cb.and(flag, filter);
            }
        };
        return vwImpEtlRepo.findAll(spec, 0, 100);
    }
}
