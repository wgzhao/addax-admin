package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.LastEtlTaketime;
import com.wgzhao.fsbrowser.model.oracle.ViewPseudo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

/**
 * Define a pseudo repository for querying views
 */
public interface ViewPseudoRepo extends JpaRepository<ViewPseudo, Long> {

    // 特殊任务提醒
    @Query(value="select spname, flag,retry_cnt,runtime," +
            "to_char(start_time,'yyyy-MM-dd HH:mm:ss') as start_time," +
            "to_char(end_time,'yyyy-MM-dd HH:mm:ss') as end_time " +
            "from vw_imp_etl " +
            "where (flag='E' or runtime>=1200 or retry_cnt<3) and bvalid=1 " +
            "order by flag asc,runtime desc", nativeQuery = true)
    List<Map> findAllSepcialTask();

    // 任务拒绝行
    @Query(value="select jobname spname, total_err kvalue,start_ts start_time, end_ts end_time " +
            "from tb_addax_sta " +
            "where total_err<>0", nativeQuery = true)
    List<Map> findTaskReject();

    // 日间实时采集任务
    @Query(value = "select LAST_TIMES, NEXT_TIMES, SPNAME, " +
            "to_char(START_TIME,'yyyy-MM-dd HH:mm:ss') as START_TIME, " +
            "to_char(END_TIME, 'yyyy-MM-dd HH:mm:ss') as END_TIME " +
            " from vw_imp_realtimes_etl", nativeQuery = true)
    List<Map> findRealtimeTask();

    @Query(value = "select tradedate as trade_date, string_agg(fid, ',' order by px)  as fids, string_agg(cast(runtime as varchar),',' order by px) as take_times\n" +
            "from (\n" +
            "select tradedate,fid,\n" +
            "       cast(extract(epoch from\n" +
            "         max(case when fval='4' then dw_clt_date end) -\n" +
            "         max(case when fval='3' then dw_clt_date end)\n" +
            "       ) as int) as runtime,\n" +
            "       row_number()over(partition by fid order by tradedate) px\n" +
            "from tb_imp_flag\n" +
            "where kind in('ETL_END','ETL_START') and tradedate>=:l5td and fval in('3','4')\n" +
            "group by tradedate, fid\n" +
            "having max(case when fval='3' then 1 else 0 end)=max(case when fval='4' then 1 else 0 end)\n" +
            ") x group by tradedate", nativeQuery = true)
    List<LastEtlTaketime> findLast5LtdTaketimes(@Param("l5td") int l5td);

    // SP 整体执行情况
    @Query(value = "select sp_owner,flag,count(1) cnt,min(start_time) start_time,max(end_time) end_time,\n" +
            "       trunc((max(end_time)-min(start_time))*24*60*60) runtime\n" +
            "from vw_imp_sp t\n" +
            "where bvalid=1 and bfreq=1\n" +
            "group by sp_owner,flag\n" +
            "order by 1,2", nativeQuery = true)
    List<Map<String, Object>> findSpExecInfo();


    // 各数据源采集完成率
    @Query(value = """
            SELECT SYSNAME, round(OVER_PREC,2)*100 AS OVER_PREC,
            CASE
            	WHEN OVER_PREC = 1
            	THEN 'bg-success'
            	WHEN OVER_PREC <= 0.4
            	THEN 'bg-danger'
            	WHEN OVER_PREC <=0.6
            	THEN 'bg-warning'
            	ELSE 'bg-info'
            END AS BG_COLOR
            FROM VW_IMP_ETL_OVERPREC
            """, nativeQuery = true)
    List<Map<String, Float>> accompListRatio();

    // SP 监控 -- 特殊任务：报错、重跑
    @Query(value = "select spname,flag,retry_cnt," +
            "to_char(start_time,'yyyy-MM-dd HH:mm:ss') as start_time," +
            "to_char(end_time,'yyyy-MM-dd HH:mm:ss') as end_time,need_sou,run_freq " +
            "from vw_imp_sp " +
            "where bvalid=1 and (flag='E' or retry_cnt<>3)\n" +
            "order by flag,retry_cnt DESC", nativeQuery = true)
    List<Map<String, Object>> findErrorTasks();

    // 任务组 -- 数据服务执行情况

    @Query(value = "select task_group,ds_name,t.flag," +
            "t.start_time,t.end_time,runtime,bdelay,\n" +
            "case when t.flag='R' then a.prec end prec,a.dest_tablename\n" +
            "from vw_imp_ds2 t\n" +
            "left join (select ds_id,\n" +
            " wm_concat(case when flag='R' then dest_tablename end) dest_tablename,\n" +
            " sum(case when flag='Y' then 1 else 0 end)/count(1) prec\n" +
            "          from tb_imp_ds2_tbls\n" +
            "          group by ds_id) a on a.ds_id=t.ds_id\n" +
            "where t.bvalid=1 and t.bfreq=1\n" +
            "order by case when bdelay=1 then 1 " +
            "else decode(flag,'E',0,'R',2,'N',3,'Y',4,0) end asc,runtime desc", nativeQuery = true)
    List<Map<String, Object>> findDataServiceExecTime();

    @Query(value = "select '工作流整体超长' kind,ds_name,start_time,end_time,runtime\n" +
            "from vw_imp_ds2\n" +
            "where start_time is not null and runtime>=1000\n" +
            "union all\n" +
            "select '单表推送超长',ds_name||':'||dest_tablename,start_time,end_time,runtime\n" +
            "from stg01.vw_imp_ds2_mid t\n" +
            "where start_time is not null and runtime>=1000\n" +
            "order by kind,runtime desc", nativeQuery = true)
    List<Map<String, Object>> findDataServiceExecTimeout();

    @Query(value = "select * from ( " +
            "select t.dest_sysname, " +
            "\tcount(1) as total_cnt,\n" +
            "\tsum(case when a.bfreq=1 then 1 else 0 end) as td_task,\n" +
            "\tsum(case when a.bfreq=1 and t.end_time is not null then 1 else 0 end) as td_ok,\n" +
            "\tsum(case when a.bfreq=1 and t.flag='E' then 1 else 0 end) as td_err\n" +
            "from vw_imp_ds2_mid t\n" +
            "inner join vw_imp_ds2 a on a.ds_id=t.ds_id\n" +
            "where t.bvalid=1\n" +
            "group by t.dest_sysname\n" +
            ")\n" +
            "order by case when td_err>0 then 1 when td_ok<td_task then 2 else 3 end,td_task desc", nativeQuery = true)
    List<Map<String, Object>> findTargetComplete();

    // 数据中心采集及数据服务系统清单
    @Query(value = """
        select sys_kind,sysid,sys_name,db_constr,db_user
        from vw_imp_system
        where ((sys_kind='etl' and length(sysid)=2) or sys_kind='ds')
        order by 1,2
        """, nativeQuery = true)
    List<Map<String, Object>> findEtlAndDs();

    @Query(value = """
        select sys_kind,sysid,sys_name,db_constr,db_user
        from vw_imp_system
        where ((sys_kind='etl' and length(sysid)=2) or sys_kind='ds')
            and lower(sysid||sys_name||db_constr||db_user)
            like lower('%' || ?1 || '%')
        order by 1,2
        """, nativeQuery = true)
    List<Map<String, Object>> findEtlAndDs(String filter);

    // 数据中心采集表清单(显示100条)
    @Query(value = """
        select sysname,sou_owner,sou_tablename,sou_filter,dest_owner,dest_tablename,start_time,end_time
        from vw_imp_etl
        where rownum <= 100 order by 1,2,3
        """, nativeQuery = true)
    List<Map<String, Object>> findTop100EtlInfo();

    @Query(value = """
        select sysname,sou_owner,sou_tablename,sou_filter,dest_owner,dest_tablename,start_time,end_time
        from vw_imp_etl
        where lower(sysname||sou_owner||sou_tablename||dest_owner||dest_tablename)
        		like lower('%' || ?1 || '%')
        	and rownum<=100 order by 1,2,3
        """, nativeQuery = true)
    List<Map<String, Object>> findTop100EtlInfo(String filter);

    // 数据中心数据推送表清单(显示100条)
    @Query(value = """
    select ds_name,lower(dest_tablename) as tblname,
    to_char(start_time,'yyyy-MM-dd HH:mm:ss') as start_time,
    to_char(end_time, 'yyyy-MM-dd HH:mm:ss') as end_time
    from vw_imp_ds2_mid
    where rownum<=100
    order by dest_sysid,dest_tablename
    """, nativeQuery = true)
    List<Map<String, Object>> findTop100DsInfo();

    @Query(value = """
    select ds_name,lower(dest_tablename) as tblname,
    to_char(start_time,'yyyy-MM-dd HH:mm:ss') as start_time,
    to_char(end_time, 'yyyy-MM-dd HH:mm:ss') as end_time
    from vw_imp_ds2_mid
    where lower(task_group||dest_sysid||d_conn||dest_tablename||sou_table)
    		like lower('%' || ?1 || '%')
    	and rownum<=100
    order by dest_sysid,dest_tablename
    """, nativeQuery = true)
    List<Map<String, Object>> findTop100DsInfo(String filter);
}
