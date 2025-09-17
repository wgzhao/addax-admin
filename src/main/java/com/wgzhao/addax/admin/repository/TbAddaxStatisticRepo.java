package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbAddaxStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TbAddaxStatisticRepo
        extends JpaRepository<TbAddaxStatistic, Long>
{

    Optional<TbAddaxStatistic> findByTidAndRunDate(String tid, LocalDate runDate);


    @Query(value = """
            select t
             from
             (select b,
             row_number() over(partition by b.tid order by b.runDate desc) as rn
             from TbAddaxStatistic b
             ) as t
             where t.rn = 1
             and t.totalErrors > 0
            """)
    List<TbAddaxStatistic> findErrorTask();
}
