package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpEtlOverprec;
import com.wgzhao.fsbrowser.service.ImpEtlOverprecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/etl")
@CrossOrigin
public class ETLController {

        @Autowired
        private ImpEtlOverprecService impEtlOverprecService;
//        @RequestMapping("/index")
//        public String index(Model model)
//        {
//            model.addAttribute("impEtlOverprec", impEtlOverprecService.getAllImpEtlOverprec());
//            return "etl/index";
//        }

        @RequestMapping("/list")
        public List<ImpEtlOverprec> getAll() {
            return impEtlOverprecService.getAllImpEtlOverprec();
        }

        // 各数据源采集完成率，用于图表展示
        @RequestMapping("/accomplishRatio")
        public List<Map<String, Float>> accompListRatio() {
                return impEtlOverprecService.accompListRatio();
        }
}
