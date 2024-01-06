package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.oracle.VwImpSystemRepo;
import com.wgzhao.addax.admin.model.oracle.VwImpSystem;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VwImpSystemService {

    @Autowired
    private VwImpSystemRepo vwImpSystemRepo;

    /**
     * implement the following SQL
     *         select sysKind,sysid,sysName,dbConstr,dbUser
     *         from VwImpSystem
     *         where ((sysKind='etl' and length(sysid)=2) or sysKind='ds')
     *             and lower(sysid||sysName||dbConstr||dbUser) like lower('%?1%')
     *         order by 1,2
     * @param filter the filter string
     * @return list of {@link VwImpSystem }
     */
    public List<VwImpSystem> fetchEtlDSInfo(String filter) {
        // create custom Specification
        Specification<VwImpSystem> spec = (root, query, cb) -> {
            // query clause set
            // where ((sys_kind='etl' and length(sysid)=2) or sys_kind='ds')
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(
                    cb.and(
                            cb.equal(root.get("sysKind"), "etl"),
                            cb.equal(cb.length(root.get("sysid")), 2)
                    ),
                    cb.equal(root.get("sysKind"), "ds")
            ));
            if (filter != null && !filter.isEmpty()) {
                // lower(sysid||sysName||dbConstr||dbUser) like lower('%?1%')
                String likeFilter = "%" + filter.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(
                        cb.concat(
                                cb.concat(
                                        cb.concat(
                                                cb.concat(
                                                        root.get("sysid"),
                                                        root.get("sysName")
                                                ),
                                                root.get("dbConstr")
                                        ),
                                        root.get("dbUser")
                                ),
                                root.get("sysKind")
                        )
                ), likeFilter));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        // order by sys_kind asc and sysid asc
        List<Sort.Order> orders = new ArrayList<>();
        orders.add(new Sort.Order(Sort.Direction.ASC, "sysKind"));
        orders.add(new Sort.Order(Sort.Direction.ASC, "sysid"));
        return vwImpSystemRepo.findAll(spec, 0, 100, Sort.by(orders));
    }
}
