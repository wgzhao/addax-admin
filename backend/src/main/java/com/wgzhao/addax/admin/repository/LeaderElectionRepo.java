package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.LeaderElection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface LeaderElectionRepo extends JpaRepository<LeaderElection, Long> {

//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Query("select l from LeaderElection l where l.id = 1")
//    Optional<LeaderElection> findForUpdate();

    @Query("select l from LeaderElection l where l.id = 1")
    Optional<LeaderElection> findLockRow();
}

