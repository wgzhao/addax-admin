package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.VwImpEtl;
import com.wgzhao.fsbrowser.repository.oracle.VwImpEtlRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
            return vwImpEtlRepo.findAll(Sort.by(orders));
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
        return null;
//        return vwImpEtlRepo.findAll(spec, 0, 100, Sort.by(orders));
    }
}
