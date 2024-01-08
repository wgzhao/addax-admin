package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.model.oracle.ImpSpCom;
import com.wgzhao.addax.admin.model.oracle.TbImpDb;
import com.wgzhao.addax.admin.model.oracle.TbImpSp;
import com.wgzhao.addax.admin.model.oracle.TbImpSpNeedtab;
import com.wgzhao.addax.admin.repository.oracle.ImpSpComRepo;
import com.wgzhao.addax.admin.repository.oracle.TbImpDBRepo;
import com.wgzhao.addax.admin.repository.oracle.TbImpSpNeedtabRepo;
import com.wgzhao.addax.admin.service.ImpSpService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SP 计算接口
 */
@Api(value = "SP 计算接口", tags = {"主表计算"})
@RestController
@RequestMapping("/maintable/sp")
public class SPController {


    @Autowired
    private TbImpDBRepo tbImpDBRepo;

    @Autowired
    private ImpSpComRepo impSpComRepo;

    @Autowired
    private ImpSpService impSpService;

    @Autowired
    private TbImpSpNeedtabRepo tbImpSpNeedtabRepo;

//    @GetMapping({"/list", "/"})
//    public List<TbImpDb> getAllImpDB() {
//        return tbImpDBRepo.findAll();
//    }

    @GetMapping(value = "/list")
    public List<TbImpSp> getList() {
        return impSpService.findAll();
    }

    @GetMapping("/detail/{id}")
    public Optional<TbImpDb> getImpDBById(@PathVariable(value="id") String id) {
        return tbImpDBRepo.findById(id);
    }

    @PostMapping("/save")
    public TbImpDb saveImpDB(@ModelAttribute("impDB") TbImpDb tbImpDb) {
        if (tbImpDb.getId() == null || Objects.equals(tbImpDb.getId(), "")) {
            tbImpDb.setId(UUID.randomUUID().toString());
        }
        tbImpDBRepo.save(tbImpDb);
        return tbImpDb;
    }

    // 命令列表
    @GetMapping("/cmdlist/{id}")
    public List<ImpSpCom> getCmdsBySpId(@PathVariable("id") String spId) {
        return impSpComRepo.findAllBySpId(spId);
    }

    //前置情况
    @GetMapping("/prequires/{id}")
    public List<Map<String, String>> getPrequires(@PathVariable("id") String spId) {
        return impSpService.findRequires(spId);
    }

    // 使用场景
    @GetMapping("/scene")
    public List<TbImpSpNeedtab> getScene(@RequestParam("tbl") String tbl, @RequestParam(value = "sysId", required = false) String sysId) {
        return tbImpSpNeedtabRepo.findDistinctByTableNameIgnoreCase(tbl);
    }

    // 主表详情
    @GetMapping("/through/{id}")
    public Map<String, String> getThrough(@PathVariable("id") String spId) {
        return impSpService.findThrough(spId);
    }

    /**
     * 生成指定 sp_id 的溯源数据
     */
    @GetMapping(value="/lineage/{id}")
    public List<Map<String, Object>> getLineage(@PathVariable("id") String spId) {
        return impSpService.findLineage(spId);
    }
}
