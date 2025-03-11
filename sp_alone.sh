#!/bin/bash
export PATH=/home/hive/bin:/sbin:/usr/sbin:$PATH
##本脚本为小任务集中的脚本，避免一堆小sh的出现
##参数1：类别（小sh的名字）
##参数2：小sh的参数1，以此类推

##通用的变量
rootdir=$(rds "get path.infalog")
curpath=$(rds "get path.bin")
v_rmlog=N

v_today=$(date +%Y%m%d)
v_yest=$(date -d "yesterday" +'%Y%m%d')
c_ckh=$(rds "get com.ckhcmd")
c_presto=$(rds "get com.prestocmd")
c_allsql=$(rds "get com.prestoall")
c_sql=$(rds "get com.inicmd")
c_sql3=$(rds "get com.iniout")

##日志文件名
if [ "x${1}" == "xds_etl" -o "x${1}" == "xsp_etl" ]; then
	logprex="${1}_${2}"
else
	logprex=${1}
fi
logfile=$(rds "get path.runlog")/tuna_${logprex}_$(date +"%Y%m%d_%H%M%S")_$(hostname |awk -F '.' '{print $1}')_$(echo $$).log

wait_idx()
{
	##循环判断队列中是否还有可用的，模拟阻塞
	##返回可以执行的队列编号，从1开始
	if [ "x$2" == "x" ]; then exit 0 ; fi ;
	while true
	do
		for idx in $(seq 1 $2)
		do
			if [ "x$(rfg add ${1}_${idx})" == "x1" ]; then
				echo "${idx}"
				return ${idx}
			fi
		done
		sleep 1
	done
}

runsql()
{
	##执行指定的SQL文件,适用于针对某一个库执行多条SQL语句
	##参数1：需要执行的SQL文件(文件前三行分别为：账号、密码、数据库连接串)
	##参数2：并发数，非必填，默认1
    paral_num=1
    paral_name="runsql_"$(cat /proc/sys/kernel/random/uuid |tr -d '-')

    if [ "x$2" != "x" ]; then paral_num=$2; fi;
    echo -e "\n$(date +'%F %T'):并发数：${paral_num},并发任务名称：${paral_name},设置rds："$(rds "set ${paral_name} 0")

	echo -e "cotent=[$(cat ${1})]\n"
	if [ $(wc -l ${1} |awk '{print $1}') -ge 4 ]; then
        ##获取原始SQL文件中的前三列重要信息
		db_user=$(sed -n '1p' ${1})
		db_pass=$(sed -n '2p' ${1})
		db_url=$(sed -n '3p' ${1})

        ##按顺序读取原始SQL文件实际执行代码(不能用管道读取，否则wait失效)
		tmpfile=/tmp/${paral_name}.txt
		sed -n '4,10000p' ${1} >${tmpfile}
		while read line
		do
            if [ "x${line}" == "x" ]; then continue ; fi ;
			rc=$(wait_idx ${paral_name} ${paral_num})
			fname=${paral_name}_${rc}
            
			##有空闲的队列，开始执行
			(
				echo "$(date +'%F %T'):[${fname}]开始执行[${line}]"
				${curpath}/jdbc2console.sh -U "${db_url}" -u "${db_user}" -p "${db_pass}" "${line}" 2>&1
				rc=$?
				if [ "x${rc}" != "x0" ] ; then rds "set ${paral_name} ${rc}" ; fi ;
				echo "$(date +'%F %T'):[${fname}]执行结束[${line}]，执行结果[${rc}]，删除标志：$(rfg rem ${fname})"
			) &
		done <${tmpfile}
		wait
		rm -f ${tmpfile}
        echo "$(date +'%F %T'):全部任务执行完毕,执行结果："$(rds "get ${paral_name}")
	else
		echo "原始文件${1}没有需要执行的代码"
	fi
    rc=$(rds "get ${paral_name}")
    rds "del ${paral_name}"
    return $rc
}

sdb()
{
	##在数据库源库执行语句
	${curpath}/jdbc2console.sh -f MySQL $(${c_sql} "select db_conn from stg01.vw_imp_system where sysid='$1'") "$2"
}

syslog()
{
	##记录运行日志(时间|机器名|进程编号|日志内容)
	echo "$(date +'%F_%T')|$(hostname)|$(echo $$)|$1" >>$(rds "get path.runlog")/tuna_syslog_${v_today}_00.log
}

tip()
{
	##该脚本为通用脚本，为所有脚本提供统一可控的日志提示
	##参数1：shell脚本名称,参数值1061配置
	##参数2：1或者2
	##参数3：需要加入提示的字符串，可以是任何东西

	##获取SHELL的名字和状态
	shname=$(rds "get shname.$1")
	shsts=$(rds "get com.shsts_$2")

	##入参错误时,添加提示
	if [ "x${shname}" == "x" ];then shname="未知名称:$1"; fi;
	if [ "x${shsts}" == "x" ];then shsts="未知状态:$2";	fi;

	#输出文本
	echo -e "--------[<b>${shname}</b>]***${shsts}***[当前时间:`date +"%F %T"`][服务器:$(hostname){$(ifconfig |grep "188\.175\." |awk '{print $2}')}][$3]--------\n"
}

autobak()
{
	bakdir="${rootdir}/autobak"
	
	echo -e "</p>\n$(date +'%F %T'):=====bin备份=====<p style='background-color:#A9A9A9'>"
	bakfile=${bakdir}/autobak_bin_${v_today}.tar
	rm -f ${bakfile}
	tar -cvf ${bakfile} ${rootdir}/bin

	echo -e "</p>\n\n$(date +'%F %T'):=====日志文件归档=====<p style='background-color:#A9A9A9'>"
	logdir=$(rds "get path.runlog")
	mkdir -p ${logdir}/${v_yest}
	find ${logdir}/ -path "${logdir}/${v_yest}" -a -prune -o -name "*_${v_yest}_*" -type f -exec mv -f {} ${logdir}/${v_yest}/ \;

	echo -e "</p>\n\n$(date +'%F %T'):=====备份文件压缩存档=====<p style='background-color:#A9A9A9'>"
	bakfile=${bakdir}/autobak_${v_today}.tar.gz
	rm -f ${bakfile}
	tar -zcvf ${bakfile} ${bakdir}/autobak_*_${v_today}.*
	rm -f ${bakdir}/autobak_*_${v_today}.*
	cp ${bakfile} /mnt/dfs/user/hive/autobak/

	echo -e "</p>\n\n$(date +'%F %T'):=====删除历史数据=====<p style='background-color:#A9A9A9'>"
	find ${bakdir}/ -type f -ctime +12 -exec rm {} \;

	echo -e "</p>\n\n$(date +'%F %T'):=====其他处理事项=====<p style='background-color:#A9A9A9'>"
	ln ${rootdir}/nohup.out ${rootdir}/log/tuna_nohup_${v_today}_0000.log

	echo "</p>"
}

plan_start()
{
	##计划任务主控制
	syslog "计划任务主控制开始执行"
	if [ $(date +%S) -ge 57 ]; then
		syslog "计划任务主控制在整点前几秒开始"
		sleep 3
	fi
	if [ $(date +%S) -gt 30 ]; then
		syslog "计划任务主控制在不合适的时间点启动,本次计划任务退出"
		return
	fi
	if [ "x$(rfg add plan_start)" == "x1" ] ; then
		strsql="";
		shfile="$(rds 'get path.oths')/plan_start.sh"
		>${shfile}
		##执行计划前的准备工作
		${c_sql} "begin stg01.sp_imp_alone('plan_start');end;" 2>&1
		##获取需要调起的任务
		for line in $(${c_sql3} "select stg01.fn_imp_value('plan_run') from dual")
		do
			if [ "x${line}" != "x" ]; then
				IFS='|'; linev=(${line}); IFS=' ';
				strsql="${strsql}stg01.sp_imp_status('R','${linev[1]}');"
				echo "$(rds 'get com.sp_alone') start_wkf ${linev[0]} ${linev[1]}" |tee -a ${shfile}
			fi
		done
		if [ "x${strsql}" != "x" ]; then
			echo "$(date +'%F %T'):执行SQL=[${strsql}]"
			${c_sql} "begin ${strsql} end;" 2>&1
			bash ${shfile} 2>&1
		fi
		##判断是否有sp_start的任务
		if [ "x"$(${c_sql} "select count(1) from dual where stg01.fn_imp_value('sp_run') is not null") == "x1" ]; then
			start_wkf sp_start
		fi
		rfg rem plan_start >/dev/null
	else
		berr=1
		for host in {etl01}
		do
			if [ $(ssh ${host} "ps -ef|grep bin/sp_alone.sh" |grep "plan_start" |wc -l) -ge 1 ]; then
				berr=0
				break
			fi
		done
		if [ "x${berr}" == "x1" ]; then
			${c_sql} "begin stg01.sp_sms('没有计划任务在执行，但是占用了标志!!$(date +"%F %T")','1','110');end;"
		fi
	fi
	syslog "计划任务主控制执行完毕"
}

sp_start()
{
	##启动任务的并发入口
	if [ "x$(rfg add auto)" == "x1" ]; then
		echo -e "$(date +'%F %T'):当前redis标志情况:\n"$(rfg all 1)
        rfg wtout sp_init
		start_wkf sp_init
		rfg rem auto
		echo -e "\n$(date +'%F %T'):当前redis标志情况:\n"$(rfg all 1)
	fi
}

sp_init()
{
    ##采集,SP,数据服务的总入口
    if [ "x$(rfg add sp_init)" == "x1" ]; then
		shfile="$(rds 'get path.oths')/sp_init.sh"
		>${shfile}
		strsql=""
        echo "#####$(date +'%F %T'):数据源采集完毕######"
        for sysid in $(${c_sql3} "select stg01.fn_imp_value('etl_end') from dual")
        do
            if [ "x${sysid}" != "x" ]; then
				##置数据源采集结束
				strsql="${strsql}stg01.sp_imp_alone('etl_end','${sysid}');"
				##数据源采集完后的操作
				echo "$(rds 'get com.sp_alone') start_wkf etl_end ${sysid}" |tee -a ${shfile}
			fi
        done
		if [ "x${strsql}" != "x" ]; then
			echo "$(date +'%F %T'):执行SQL=[${strsql}]"
			${c_sql} "begin ${strsql} end;" 2>&1
			bash ${shfile} 2>&1
		fi
		
		echo -e "\n\n#####$(date +'%F %T'):需要执行的采集、SP计算、数据服务######"
		strsql=""
		>${shfile}
        ${c_sql} "begin stg01.sp_imp_alone('sp_start');end;" 2>&1
        if [ $? -eq 0 ]; then
            for line in $(${c_sql3} "select stg01.fn_imp_value('sp_run') from dual")
			do
				if [ "x${line}" != "x" ]; then
					##置状态为R
					IFS='|'; linev=(${line}); IFS=' ';
					strsql="${strsql}stg01.sp_imp_status('R','${linev[1]}');"
					echo "$(rds 'get com.sp_alone') start_wkf ${linev[0]} ${linev[1]}" |tee -a ${shfile}
				fi
			done
			if [ "x${strsql}" != "x" ]; then
				echo "$(date +'%F %T'):执行SQL=[${strsql}]"
				${c_sql} "begin ${strsql} end;"
				bash ${shfile} 2>&1
			fi
        fi
		#如果没有日志输出，不保留日志文件
        if [ $(wc -l ${logfile} |awk '{print $1}') -le 8 ]; then
            v_rmlog=Y
        fi
        rfg rem sp_init >/dev/null
    fi
}

sp_etl()
{
	##采集,SP,计划任务的具体执行
	##参数1：任务ID
	##参数2：默认不传，代表执行采集或者SP；如果传入plan，代表计划任务；如果传入manual,表示前台单独执行
	if [ "x$1" == "x" ]; then return 1; fi;
	if [ "x"$(rfg add sp.$1) == "x1" ]; then
		spname=$(${c_sql3} "select nvl(stg01.fn_imp_value('taskname','$1'),'$1') from dual")
		echo "<b>$(date +'%F %T'):${1}[${spname}]开始执行...</b>"
		if [ "x$2" == "xmanual" ]; then ${c_sql} "update tb_imp_sp_com set flag='N' where sp_id='$1' and flag!='X'"; fi;

		for comt in $(${c_sql3} "select com_id||','||com_kind||','||com_idx from stg01.tb_imp_sp_com where sp_id='$1' and flag='N' order by com_idx")
		do
			com_id=$(echo ${comt} |awk -F ',' '{print $1}')
			com_kind=$(echo ${comt} |awk -F ',' '{print $2}')
			com_idx=$(echo ${comt} |awk -F ',' '{print $3}')
			if [ "x${com_id}" == "x" -o "x${com_kind}" == "x" -o "x${com_idx}" == "x" ]; then continue ; fi ;
			##获取脚本内容
			com_file=$(rds "get path.coms")/${spname}_${com_idx}.txt
			echo -e "\n<b>$(date +'%F %T'):生成文件${com_file}...</b>"
			${c_sql3} "select stg01.fn_imp_value('com_text','${com_id}') from dual" >${com_file}
			if [ $? -eq 0 ]; then
				echo "生成成功，置命令状态为R"
				${c_sql} "begin stg01.sp_imp_status('cR','${com_id}');end;" 2>&1
			else
				echo "生成失败，跳过"
				${c_sql} "begin stg01.sp_imp_status('cE','${com_id}');end;" 2>&1
				if [ "x$2" == "xplan" ]; then
                    ##计划需要继续执行,只是跳过报错的一条计划
					continue
				else
                    ##采集或者SP计算，命令报错，中止主任务；计划任务不能终止
					break
				fi
			fi
			##开始执行脚本
			echo -e "\n<b>$(date +'%F %T'):执行文件${com_file}...</b><p style='background-color:#A9A9A9'>"
			${curpath}/tuna.py -t 36000 -m ${com_kind} -f ${com_file} 2>&1
			if [ $? -eq 0 ]; then
				${c_sql} "begin stg01.sp_imp_status('cY','${com_id}');end;" 2>&1
			else
				${c_sql} "begin stg01.sp_imp_status('cE','${com_id}');end;" 2>&1
				if [ "x$2" == "xplan" ]; then
                    ##计划需要继续执行,只是跳过报错的一条计划
					continue
				else
                    ##采集或者SP计算，命令报错，中止主任务；计划任务不能终止
					break
				fi
			fi
			echo "</p>"
		done
		echo -e "\n<b>$(date +'%F %T'):${1}[${spname}]执行结束...</b>"$(rfg rem sp.$1)
        if [ "x$2" != "xmanual" ]; then
            ${c_sql} "begin stg01.sp_imp_status('Y','$1');end;" 2>&1
            ##计划任务执行无需重新调起sp_start
            if [ "x$2" == "x" ]; then sp_start ; fi;
		fi
	else
        if [ "x$2" != "xmanual" ]; then
            ${c_sql} "begin stg01.sp_imp_status('E','$1');end;" 2>&1
        fi
	fi
}

ds_etl()
{
	##数据服务的具体执行
	ds_id=$1
	if [ "x${ds_id}" == "x" -o "x"$(rfg add ds.${ds_id}) != "x1" ]; then return 1; fi;
	dest_dir=$(rds "get path.oths")

	##初始化redis
	echo -e "\n<b>$(date +'%F %T'):初始化redis配置信息...</b>"$(rds "set ds.${ds_id} 0")
	${c_sql3} "select init_rds from stg01.vw_imp_ds2 where ds_id='${ds_id}'" |tee ${dest_dir}/${ds_id}.initrds
	bash ${dest_dir}/${ds_id}.initrds 2>&1
	if [ $? -ne 0 ]; then rds "set ds.${ds_id} 1"; fi ;

	##刷新ds视图
	if [ "x"$(rds "get ds.${ds_id}") == "x0" -a "x"$(rds "get ds.${ds_id}.dsview") == "x1" ]; then
		echo -e "\n<b>$(date +'%F %T'):自定义查询转为presto视图...</b><p style='background-color:#A9A9A9'>"
		for kind in {presto,allsql}
		do
			echo "利用${kind}刷新视图..."
			${c_sql3} "select stg01.fn_imp_value('ds_sql_${kind}','${ds_id}') from dual" |tee ${dest_dir}/${ds_id}.dsview
			if [ $(wc -c ${dest_dir}/${ds_id}.dsview |awk '{print $1}') -gt 3 ]; then
				$(eval echo \${c_$kind} -f ${dest_dir}/${ds_id}.dsview 2>&1)
				if [ $? -ne 0 ]; then
					echo "利用${kind}刷新视图失败!!!设置redis："$(rds "set ds.${ds_id} 1")
				fi
			fi
		done
		echo "</p>"
	fi

	##获取目标表字段及ds下视图字段,更新涉及表
	if [ "x"$(rds "get ds.${ds_id}") == "x0" -a "x"$(rds "get ds.${ds_id}.bupdate") == "x1" ]; then
		echo -e "\n<b>$(date +'%F %T'):获取目标表字段及ds下视图字段...</b><p style='background-color:#A9A9A9'>"
		for db_conn in $(${c_sql} "select sou_db_conn from vw_imp_ds2_mid where ds_id='${ds_id}' group by sou_db_conn union all select '${ds_id}' from dual union all select 'hadoop' from dual")
		do
		(
			soutab_etl "${db_conn}"
			if [ $? -ne 0 ]; then rds "set ds.${ds_id} 1"; fi;
		) &
		done
		wait
		##更新数据服务涉及源表，用于计算是否完整配置了前置条件
		${c_sql} "begin stg01.sp_imp_alone('bupdate','D','${ds_id}');end;" 2>&1

		##更新完后，修改状态
		if [ "x"$(rds "get ds.${ds_id}") == "x0" ]; then
			echo "</p>表结构获取完成，置更新状态为N"
			${c_sql} "update stg01.tb_imp_ds2 set bupdate='N' where ds_id='${ds_id}'" 2>&1
		fi
	fi

	##前置SQL
	if [ "x"$(rds "get ds.${ds_id}") == "x0" -a "x"$(rds "get ds.${ds_id}.pre_sql") == "x1" ]; then
		echo -e "\n<b>$(date +'%F %T'):执行前置语句pre_sql...</b>"
		${c_sql3} "select pre_sql from stg01.vw_imp_ds2 where ds_id='${ds_id}'" |tee ${dest_dir}/${ds_id}.presql
		runsql ${dest_dir}/${ds_id}.presql
		if [ $? -ne 0 ]; then rds "set ds.${ds_id} 1"; fi ;
	fi

	##前置SH
	if [ "x"$(rds "get ds.${ds_id}") == "x0" -a "x"$(rds "get ds.${ds_id}.pre_sh") == "x1" ]; then
		echo -e "\n<b>$(date +'%F %T'):执行前置脚本pre_sh...</b>"
		${c_sql3} "select pre_sh from stg01.vw_imp_ds2 where ds_id='${ds_id}'" |tee ${dest_dir}/${ds_id}.presh
		bash ${dest_dir}/${ds_id}.presh
		if [ $? -ne 0 ]; then rds "set ds.${ds_id} 1"; fi ;
	fi

	##开始数据推送
	if [ "x"$(rds "get ds.${ds_id}") == "x0" ]; then
		echo -e "\n<b>$(date +'%F %T'):开始数据服务推送...</b>"
		if [ $? -ne 0 ]; then rds "set ds.${ds_id} 1"; fi ;
		${c_sql3} "select tbl_id from stg01.tb_imp_ds2_tbls where ds_id='${ds_id}' and flag='N' order by end_time-start_time desc" |tee ${dest_dir}/${ds_id}.txt

		for tbl_id in $(cat ${dest_dir}/${ds_id}.txt)
		do
			if [ "x${tbl_id}" == "x" ]; then continue; fi ;
			rc=$(wait_idx ds_${ds_id} $(rds "get ds.${ds_id}.paral_num"))
			(
				echo -e "======$(date +'%F %T'):[${tbl_id}][并发队列号:${rc}]开始执行======"
                ${c_sql} "begin stg01.sp_imp_status('cR','${tbl_id}');end;"

				##获取服务JSON及具体执行命令（文件及关系型数据库，通过cmd区分）
				${c_sql3} "select stg01.fn_imp_value('ds_json','${tbl_id}') from dual" >${dest_dir}/${tbl_id}.json
				${c_sql3} "select stg01.fn_imp_value('ds_cmd','${tbl_id}') from dual" >${dest_dir}/${tbl_id}.sh
				
				#命令具体执行
				if [ $(rds "get ds.${ds_id}.paral_num") -eq 1 ]; then
					echo "<p style='background-color:#A9A9A9'>"
					bash ${dest_dir}/${tbl_id}.sh ${dest_dir}/${tbl_id}.json 2>&1
				else
					bash ${dest_dir}/${tbl_id}.sh ${dest_dir}/${tbl_id}.json 2>/dev/null 1>/dev/null
				fi
				if [ $? -eq 0 ]; then
                    ${c_sql} "begin stg01.sp_imp_status('cY','${tbl_id}');end;"
				else
					rds "set ds.${ds_id} 1"
                    ${c_sql} "begin stg01.sp_imp_status('cE','${tbl_id}');end;"
					echo "${tbl_id}执行失败，等待10秒后继续"
					sleep 10
				fi
				echo -e "</p>======$(date +'%F %T'):[${tbl_id}][并发队列号:${rc}]执行结束======"$(rfg rem ds_${ds_id}_${rc})
			) &
		done
		wait
	fi

	##后置SQL
	if [ "x"$(rds "get ds.${ds_id}") == "x0" -a "x"$(rds "get ds.${ds_id}.post_sql") == "x1" ]; then
		echo -e "\n<b>$(date +'%F %T'):执行后置语句post_sql...</b>"
		${c_sql3} "select post_sql from stg01.vw_imp_ds2 where ds_id='${ds_id}'" |tee ${dest_dir}/${ds_id}.postsql
		runsql ${dest_dir}/${ds_id}.postsql
		if [ $? -ne 0 ]; then rds "set ds.${ds_id} 1"; fi ;
	fi

	##后置SH
	if [ "x"$(rds "get ds.${ds_id}") == "x0" -a "x"$(rds "get ds.${ds_id}.post_sh") == "x1" ]; then
		echo -e "\n<b>$(date +'%F %T'):执行后置脚本post_sh...</b>"
		${c_sql3} "select post_sh from stg01.vw_imp_ds2 where ds_id='${ds_id}'" |tee ${dest_dir}/${ds_id}.postsh
		bash ${dest_dir}/${ds_id}.postsh
		if [ $? -ne 0 ]; then rds "set ds.${ds_id} 1"; fi ;
	fi

    ##执行完毕
    echo "数据服务执行结果："$(rds "get ds.${ds_id}")
    if [ "x"$(rds "get ds.${ds_id}") == "x0" ]; then
        ${c_sql} "begin stg01.sp_imp_status('Y','${ds_id}');end;"
    else
        ${c_sql} "begin stg01.sp_imp_status('E','${ds_id}');end;"
    fi
	
    ##清理redis
	echo -e "\n<b>$(date +'%F %T'):清理redis配置信息...</b>"
	for rs in $(rds "keys ds.${ds_id}*")
	do
		echo "删除[${rs}]:"$(rds "del $rs")
	done
	rfg rem ds.${ds_id}
}

judge_init()
{
	##采集前置任务:备份采集表
	if [ "x$1" != "x" -a "x"$(rfg add $1) == "x1" ]; then
		#获取基础变量信息
		begdate=$(date +%s) #开始时间，用于计算超时
		sysid=$(echo $1 |awk -F '_' '{print $NF}') #数据源编号
		dbcmd="$(rds 'get path.bin')/jdbc2console.sh "$(${c_sql3} "select db_conn from stg01.vw_imp_etl_judge where sysid='${sysid}' and px=1") #数据源连接串

		#采集表记录数比对
		while true
		do
			#清空记录数比对表
			${dbcmd} "begin lc_sjzx.sp_tables_etl('clear_cnt');end;"
			#获取表记录数
			${dbcmd} "begin lc_sjzx.sp_tables_etl('get_cnt');end;" 2>&1
			if [ $? -ne 0 ]; then
				${c_sql} "begin stg01.sp_sms('检测记录数报错,等待5分钟后再次检测','UF','111');end;"
				sleep 300
			fi

			#比对记录数
			cnt=$(${dbcmd} "select 1 from lc_sjzx.vw_tables_cnt having sum(bok)=(select count(1) from tables_etl where flag<>'0')")

			#反馈记录数比对结果
			msg=$(${dbcmd} "select '记录数比对结果:'||nvl(listagg(','||msg)within group(order by abs(master_cnt-slaver_cnt) desc),',完全一致') msg from lc_sjzx.vw_tables_cnt where bok=0 and rownum<=15")
			echo "${msg}"
			${c_sql} "begin stg01.sp_sms(replace('${msg}',',',chr(10)),'UF','110');end;"

			##记录数比对一致，或者启用应急处理方案
			if [ "x${cnt}" == "x1" ]; then
				msg="${sysid}采集表的记录数比对符合条件,开始备份表"
				echo "${msg}"
				${c_sql} "begin stg01.sp_sms('${msg}','UF','110');end;"
				break
			else
				msg=$(${dbcmd} "select '无记录比对结果:'||wm_concat(t.table_name) from tables_etl t left join vw_tables_cnt a on a.owner=t.owner and a.table_name=t.table_name where t.flag!='0' and a.table_name is null having count(1)>1")
				echo "${msg}"
				${c_sql} "begin stg01.sp_sms('${msg}','UF','110');end;"
				sleep 60
			fi
		done

		#备份表
		${dbcmd} "begin lc_sjzx.sp_tables_etl('bak');lc_sjzx.sp_tables_etl_qt('JG');end;" 2>&1
		if [ $? -ne 0 ]; then
			${c_sql} "begin stg01.sp_sms('备份表报错,等待20分钟后开始采集,请及时处理错误','UF','111');end;"
			sleep 1200
		else
			${c_sql} "begin stg01.sp_sms('${sysid}采集表备份完成，即将开始采集','UF','110');end;"
			${c_sql} "begin stg01.sp_imp_alone('plan_start','JG');end;"
			##股票质押开始推送
			${c_sql} "update tb_imp_ds2 set flag='N',retry_cnt=3 where ds_id in('40A0998C0CDF438D993CFA9D10DCE362','E37317F36CBE0CBAE053890A5A0A6E2D') and flag not in('R','X')"
		fi
		
		rfg rem $1 >/dev/null
	fi
}

judge_etl()
{
	##判断标志的具体实现
	if [ "x$1" != "x" -a "x"$(rfg add $1) == "x1" ]; then
		params=($(echo $1 |sed 's/_/ /g'))
		sysid=${params[1]}
		#获取源库标志
		if [ "x${params[0]}" == "xstatus" ]; then
			sts=$($(rds "get path.bin")/jdbc2console.sh $(${c_sql3} "select db_conn from stg01.vw_imp_etl_judge where sysid='${sysid}' and px=1") "$(${c_sql3} "select judge_sql from stg01.vw_imp_etl_judge where sysid='${sysid}' and px=1")")
			if [ $? -eq 0 ]; then
				${c_sql} "begin stg01.sp_imp_flag('add','ETL_JUDGE','${sysid}',${sts});end;" 2>&1
				##UF未采集发送短信，从19点开始，5分钟一次
				curtime=$(date +1%H%M)
				if [ ${curtime} -ge '11900' -a $((curtime%5)) == 0 -a "x${sysid}${sts}" == "xUF0" ]; then
					msg=$($(rds "get com.prestoall") --execute "select array_join(array_agg(format('%.0f:%s%s',curr_time,step_name,remark) order by curr_time desc),chr(10)) from (select *,row_number()over(order by curr_time desc) px from ora_uf.hs_sett.businoperlog where init_date=curr_date) where px<=10")
					$(rds "get com.inicmd") "begin stg01.sp_sms('最近清算步骤:'||chr(10)||'${msg}','UF','110');end;"
				fi
			fi
		#执行采集前置任务
		elif [ "x${params[0]}" == "xstart" ]; then
			echo "$(date +'%F %T'):执行采集前置"
			##前置开始执行，生成标志1
			${c_sql} "begin stg01.sp_imp_flag('add','ETL_START','${sysid}',1);end;" 2>&1
			##执行采集前置任务
			prefile=$(rds "get path.cmds")/judge_pre_${sysid}.sh
			${c_sql3} "select judge_pre from stg01.vw_imp_etl_judge where px=1 and bstart=1 and sysid='${sysid}'" |tee ${prefile}
			bash ${prefile} 2>&1
			if [ $? -eq 0 ]; then
				##前置执行完后，生成可以采集的标志2
				${c_sql} "begin stg01.sp_imp_flag('add','ETL_START','${sysid}',2);end;" 2>&1
			else
				##前置执行失败，短信提醒(是否重跑，看以后的规划)
				${c_sql} "begin stg01.sp_sms('${sysid}的前置任务执行失败!!!','1','110');end;"
			fi
		fi
		rfg rem $1 >/dev/null
	fi
}

soutab_start()
{
	if [ "x$(rfg add soutab)" == "x1" ] ; then
		##获取源库及hadoop的表结构信息
		echo "<b>$(date +'%F %T'):获取源库及hadoop的表结构信息</b><p style='background-color:#A9A9A9'>"
		dest_dir=$(rds "get path.oths")
		for db_conn in $(${c_sql} "select sou_db_conn from stg01.vw_imp_etl_soutab where kind='etl'")
		do
		(
			echo "$(date +'%F %T'):${db_conn}...start"
			soutab_etl ${db_conn}
			echo "$(date +'%F %T'):${db_conn}...over"
		) &
		done
		wait
		##刷新对比表
		${c_sql} "begin stg01.sp_imp_alone('colexch_updt');end;" 2>&1

		##建表或者刷新hive表结构
		dest_file=${dest_dir}/updt_hive.sql
		for kind in {updt_hive,updt_mysql}
		do
			${c_sql3} "select stg01.fn_imp_value('${kind}') from dual" >${dest_file}
			if [ $? -eq 0 -a $(wc -c ${dest_file} |awk '{print $1}') -gt 5 ]; then
				echo -e "</p>\n<b>$(date +'%F %T'):${kind} $(rfg add soutab.task)</b>[${dest_file}]<p style='background-color:#A9A9A9'>"
				if [ "x${kind}" == "xupdt_hive" ]; then
					${curpath}/tuna.py -m hive -f ${dest_file} 2>&1
				else
					cat ${dest_file}
					HOME=/opt/infalog mysql -hnn01 -P3306 -Dhive -uhive -p'ZEQEJGsNP7NT' <${dest_file}
				fi
			fi
		done

		##获取更新后的hadoop表结构信息，并刷新对比表
		if [ $? -eq 0 -a "x$(rfg has soutab.task)" == "x1" ]; then
			echo -e "</p>\n<b>$(date +'%F %T'):本次hadoop有更新，获取更新后的hadoop表结构信息</b><p style='background-color:#A9A9A9'>"
			soutab_etl hadoop
			${c_sql} "begin stg01.sp_imp_alone('colexch_updt');end;" 2>&1
		fi
		rfg rem soutab.task
		
		##更新状态及采集JSON
		echo -e "</p>\n<b>$(date +'%F %T'):执行完毕，更新状态及采集JSON</b>"
		${c_sql} "begin stg01.sp_imp_alone('bupdate','N');end;" 2>&1

		rfg rem soutab >/dev/null
	fi
}

soutab_etl()
{
	##获取表的字段信息
	if [ "x$1" == "x" ]; then exit 1 ; fi;
	db_conn=$1
	if [ "x$(rfg add soutab.${db_conn})" == "x1" ] ; then
		##获取表结构
		jsonfile=$(rds "get path.oths")/soutab_${db_conn}.json
		echo -e "\n$(date +'%F %T'):获取表结构信息[${db_conn}][${jsonfile}]..."
		${c_sql3} "select col_json from stg01.vw_imp_etl_soutab where sou_db_conn='${db_conn}'" |tee ${jsonfile}
		if [ $(wc -l ${jsonfile} |awk '{print $1}') -ge 5 ]; then
			$(rds "get com.tuna") -m schema -f ${jsonfile} 2>&1
			if [ $? -eq 0 ]; then
				${c_sql} "begin stg01.sp_imp_alone('bupdate','${db_conn}','n');end;"
			else
				${c_sql} "begin stg01.sp_sms('获取${db_conn}的表结构信息失败!!!!','1','010');end;"
			fi
		fi
		rfg rem soutab.${db_conn} >/dev/null
	fi
}

updt_param()
{
	##切日工作流，整体替换WF_PARAM_FILE
	if [ "x$(date +'%H%M')" != 'x1630' ]; then
		syslog "参数更新任务不能执行,任务退出,非切日时间点"
		return 1
	fi
	${c_sql} "begin stg01.sp_sms('系统参数param_sys开始切换'||chr(10)||to_char(stg01.fn_imp_value('pntype_list'))||chr(10)||'TD=$(rds "get param.TD")'||chr(10)||'CD=$(rds "get param.CD")','1','110');end;"

	##计算日期参数
	for td in {"sysdate","sysdate-1","sysdate+1"}
	do
		${c_sql} "begin stg01.sp_imp_param(to_char(${td},'YYYYMMDD'));end;"
		if [ $? -ne 0 ]; then
			${c_sql} "begin stg01.sp_sms('系统参数param_sys生成失败!!!!','1','111');end;"
			return 1
		fi
	done

	##更新薪酬参数
	${c_sql} "begin stg01.sp_imp_alone('xc_init');end;"

	##读取配置信息更新redis，包含日期参数、常用地址，命令中文等
	${c_sql} "select rds from stg01.vw_updt_rds" |while read line
	do
		echo "更新redis：${line}===>"$(rds "$line")
	done
	${c_sql} "begin stg01.sp_sms('系统参数param_sys切换完成！'||chr(10)||to_char(stg01.fn_imp_value('pntype_list'))||chr(10)||'TD=$(rds "get param.TD")'||chr(10)||'CD=$(rds "get param.CD")','1','110');end;"

	##记录数比对表增加分区
	hive -e "alter table default.tab_cnt add if not exists partition(logdate='"$(rds "get param.TD")"')"

	syslog "参数更新任务执行完毕"
}

start_wkf()
{
	##统一调起工作流
	#参数1:类型
	#参数2:真实的参数1
	case $1 in

		plan)	##计划任务
			comt="$(rds 'get com.sp_alone') sp_etl $2 plan" ;;
		judge)	##判断标志
			comt="$(rds 'get com.sp_alone') judge_etl $2" ;;
		ds)	##数据服务
			comt="$(rds 'get com.sp_alone') ds_etl $2" ;;
		soutab)	##源表信息
			comt="$(rds 'get com.sp_alone') soutab_etl $2" ;;
		sp)	##采集及SP
			comt="$(rds 'get com.sp_alone') sp_etl $2" ;;
		spcom) ##手工执行SP，只执行命令，不修改主表状态
			comt="$(rds 'get com.sp_alone') sp_etl $2 manual" ;;
		manual) ##单独的自定义命令执行
			comt="$2" ;;
		*) ##直接调起命令
			comt="$(rds 'get com.sp_alone') $1 $2" ;;
	esac

	##真实调起命令
	curl -H 'token:de27aefdf8f0392ddab7c2144af67ab0' \
		-X POST 'http://etl01:12345/dolphinscheduler/projects/10691104512992/executors/start-process-instance' \
		-d 'failureStrategy=END&processDefinitionCode=10691166416992&processInstancePriority=MEDIUM&scheduleTime=&warningGroupId=0&warningType=NONE&startParams={"comt":"'"${comt}"'"}'
}

dschk()
{
	syslog "检测ds，$(date +'%F_%T'):由$(hostname)发起，开始"
	##检测ds调度工具是否正常
	if [ $(date +%M) -le 1 ]; then
		rfg add dschk.msg
	fi
	echo -e "$(date +'%F %T'):利用调度工具设置标志"
	start_wkf manual "export PATH=/home/hive/bin:$PATH;rds 'set dschk 1'"

	echo -e "\n\n$(date +'%F %T'):等待工作流执行完毕..."
	sleep 60

	echo -e "\n$(date +'%F %T'):获取标志"
	if [ "x"$(rds "get dschk") == "x1" ]; then
		echo "调度工具运行平稳..."$(rds "set dschk 0")
		rds "set dschk.errcnt 0"	##错误数归0
		if [ "x$(rfg has dschk.msg)" == "x1" ]; then
			${c_sql} "begin stg01.sp_sms('$(date +"%F %T"):调度工具运行平稳~~来自于$(hostname)','1','010');end;"
		fi
	else
		echo "调度工具异常，需要赶紧处理"
		rds "set dschk.errcnt $(($(rds "get dschk.errcnt")+1))"		##错误数+1
		${c_sql} "begin stg01.sp_sms('$(date +"%F %T"):调度工具连续异常$(rds 'get dschk.errcnt')次，需要赶紧处理!!来自于$(hostname)','dschk','110');end;"
	fi
	rfg rem dschk.msg
	syslog "检测ds，$(date +'%F_%T'):由$(hostname)发起，结束"
}

syschk()
{
	if [ "x$(rfg add syschk)" == "x1" ]; then
		##更新检测的配置信息
		${c_sql} "truncate table stg01.tb_imp_chk_inf"
		${c_sql} "insert into stg01.tb_imp_chk_inf(engine,chk_idx,chk_sendtype,chk_mobile,bpntype,chk_kind,chk_sql) select engine,chk_idx,chk_sendtype,chk_mobile,bpntype,chk_kind,chk_sql from stg01.vw_imp_chk_inf"
		
		${c_sql} "delete from stg01.tb_imp_chk where chk_kind not in(select chk_kind from stg01.tb_imp_chk_inf)"

		${c_sql} "delete from stg01.tb_imp_chk where chk_kind in(select chk_kind from stg01.tb_imp_chk_inf where engine='allsql')"
		##1073系统检测
		echo "</p><b>$(date +'%F %T'):基于allsql的自定义检测</b><p style='background-color:#A9A9A9'>"
		for grp in 1 2 3 4 5
		do
		(
			sqlfile=$(rds "get path.oths")/syschk_${grp}.sql
			$(rds "get com.iniout") "select chk from (select 'insert into ora_in.stg01.tb_imp_chk(chk_mobile,chk_sendtype,chk_kind,chk_name,chk_content)'||chr(10)||stg01.fn_imp_param_replace(chk_sql)||';' as chk,mod(row_number()over(order by chk_idx),5)+1 px from stg01.tb_imp_chk_inf where engine='allsql') where px=${grp}" |tee ${sqlfile}
			$(rds "get com.prestoall") -f ${sqlfile} 2>&1
			if [ $? -ne 0 ]; then
				$(rds "get com.inicmd") "begin stg01.sp_sms('${grp}组的系统检测失败','1','110');end;"
			fi
		) &
		done
		wait

		##系统整体异常检测（配置了短信的，将会发送短信提醒）
		echo "</p><b>$(date +'%F %T'):系统整体异常检测</b><p style='background-color:#A9A9A9'>"
		${c_sql} "begin stg01.sp_imp_alone('syschk');end;" 2>&1
		if [ $? -ne 0 ]; then
			${c_sql} "begin stg01.sp_sms('系统检测函数报错,请及时处理!!','1','110');end;"
		fi
		rfg rem syschk 1>/dev/null
		echo "</p>"
	fi
}

etl_end()
{
	##ODS采集完后，需要执行的后续操作
	sysid=$1

	##1、更新redis中api参数,用于接口的日期参数，该参数是以数据源编号开头的，用于区分不同数据源的采集情况
	${c_sql} "select replace(rds,'set param.','set api.${sysid}') rds from stg01.vw_updt_rds where rds like 'set param.%'" |while read line
	do
  		echo "更新redis:${line}===>"$(rds "$line")
	done

	##2、ODS采集表记录数及比对
	if [ "x$2" == "xtblcnt" -o "x$2" == "x" ]; then
		tblcnt=$(rds "get path.oths")/tblcnt_${sysid}.txt
		tblsql=$(rds "get path.oths")/tblcnt_${sysid}.sql
		>${tblcnt}
		${c_sql3} "select stg01.fn_imp_value('etl_end_chk','${sysid}') from dual" >${tblsql}
		${c_presto} --output-format TSV -f ${tblsql} |tee -a ${tblcnt}
		cp ${tblcnt} /mnt/dfs/sta/stage/tab_cnt/logdate=$(rds "get param.TD")/
	fi
}

tip "${1}" 1 "$@" |tee ${logfile}
echo -e "######curpath=${curpath},shname=${1},logfile=${logfile},v_today=${v_today},v_yest=${v_yest},param=[$@]######\n" |tee -a ${logfile}

if [ "x"$(rds "get com.halt") == "xY" ] ; then
	msg="$(date +'%F %T'):系统暂停服务!!sp_alone:${1}无法执行"
	echo "${msg}" |tee -a ${logfile}
	${c_sql} "begin stg01.sp_sms('${msg}','1','110');end;"

elif [ "x$1" != "x" ]; then

	"$@" 2>&1 >>${logfile}
	if [ $? -ne 0 ]; then
		${c_sql} "begin stg01.sp_sms('sp_alone:${1}[${2}]执行失败，请速速排查','1','110');end;"
	fi
	
fi

tip "${1}" 2 "$@" |tee -a ${logfile}
echo -e "\n\n\n展示日志文件内容\n$(cat ${logfile})"

##删除空的日志文件
if [ $(wc -l ${logfile} |awk '{print $1}') -le 6 -o "x${v_rmlog}" = "xY" ] ; then
	rm -f ${logfile}
fi
