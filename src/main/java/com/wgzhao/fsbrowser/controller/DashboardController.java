package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.LastEtlTaketime;
import com.wgzhao.fsbrowser.repository.PesudoRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private PesudoRepo pesudoRepo;

    @GetMapping("/index")
    public String index(Model model)
    {
        List<LastEtlTaketime> lastEtlTaketimes =  pesudoRepo.findLast5LtdTaketimes(20231019);
        List<String> fids = Arrays.asList(lastEtlTaketimes.get(0).getFids().split(",") );
        List<Map<String, Object>> result = new ArrayList<>();
        for(LastEtlTaketime d: lastEtlTaketimes) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", d.getTradeDate());
            map.put("type", "bar");
            map.put("data", Arrays.asList(d.getTakeTimes().split(",")));
            result.add(map);
        }
        model.addAttribute("fids", fids);
        model.addAttribute("last5LtdTaketimes", result);
        return "home";
    }

}
