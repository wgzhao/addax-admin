package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.VwImpEtlOverprec;
import com.wgzhao.fsbrowser.model.pg.TbAddaxStaEntity;
import com.wgzhao.fsbrowser.repository.oracle.ViewPseudoRepo;
import com.wgzhao.fsbrowser.repository.oracle.VwImpEtlOverprecRepo;
import com.wgzhao.fsbrowser.repository.pg.AddaxStaRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * ETL 采集接口
 */
@RestController
@RequestMapping("/etl")
@CrossOrigin
public class ETLController {

        @Autowired
        private VwImpEtlOverprecRepo impEtlOverprecRepo;

        @Autowired
        private ViewPseudoRepo viewPseudoRepo;

        @Autowired
        private AddaxStaRepo addaxStaRepo;

        // 数据源采集完成情况列表
        @RequestMapping("/accomplishList")
        public List<VwImpEtlOverprec> getAll() {
            return impEtlOverprecRepo.findAll();
        }

        // 各数据源采集完成率，用于图表展示
        @RequestMapping("/accomplishRatio")
        public List<Map<String, Float>> accompListRatio() {
                return viewPseudoRepo.accompListRatio();
        }


        // 日间实时采集任务
        @GetMapping("/realtimeTask")
        public List<Map<String, Object>> realtimeTask() {
                return viewPseudoRepo.findRealtimeTask();
        }

        // 特殊任务提醒
        @GetMapping("/specialTask")
        public List<Map<String, Object>> specialTask() {
                return viewPseudoRepo.findAllSepcialTask();
        }

        // 任务拒绝行
        @GetMapping("/taskReject")
        public List<TbAddaxStaEntity> getTaskReject() {
                return addaxStaRepo.findByTotalErrNot(0);
        }

}
