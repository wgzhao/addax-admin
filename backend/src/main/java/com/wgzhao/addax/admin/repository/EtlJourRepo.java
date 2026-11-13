package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlJour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EtlJourRepo
    extends JpaRepository<EtlJour, Long>
{
    void deleteAllByTid(long tableId);

    Optional<EtlJour> findFirstByTidAndKindOrderByIdDesc(long tableId, String kind);
}
