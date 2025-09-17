package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbAddaxStatistic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface TbAddaxStatisticRepo
        extends JpaRepository<TbAddaxStatistic, Long>
{

    Optional<TbAddaxStatistic> findByTidAndRunDate(String tid, LocalDate runDate);
}
