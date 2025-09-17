package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.ImpSpCom;
import com.wgzhao.addax.admin.model.TbImpDb;
import com.wgzhao.addax.admin.repository.ImpSpComRepo;
import com.wgzhao.addax.admin.repository.TbImpDBRepo;
import com.wgzhao.addax.admin.service.ImpSpService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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


    @GetMapping(value = "/list")
    public ApiResponse<List<TbImpSp>> getList() {
        return ApiResponse.success(impSpService.findAll());
    }
    @GetMapping("/detail/{id}")
    public ApiResponse<Optional<TbImpSp>> getImpDBById(@PathVariable(value="id") String id) {
        return ApiResponse.success(impSpService.findById(id));
    }

    @PostMapping("/save")
    public ApiResponse<TbImpDb> saveImpDB(@ModelAttribute("impDB") TbImpDb tbImpDb) {
        if (tbImpDb.getId() == null || Objects.equals(tbImpDb.getId(), "")) {
            tbImpDb.setId(UUID.randomUUID().toString());
        }
        tbImpDBRepo.save(tbImpDb);
        return ApiResponse.success(tbImpDb);
    }

    // 命令列表
    @GetMapping("/cmdlist/{id}")
    public ApiResponse<List<ImpSpCom>> getCmdsBySpId(@PathVariable("id") String spId) {
        return ApiResponse.success(impSpComRepo.findAllBySpId(spId));
    }

    //前置情况
    @GetMapping("/prequires/{id}")
    public ApiResponse<List<Map<String, String>>> getPrequires(@PathVariable("id") String spId) {
        return ApiResponse.success(impSpService.findRequires(spId));
    }

    // 使用场景
    @GetMapping("/scene")
    public ApiResponse<List<TbImpSpNeedtab>> getScene(@RequestParam("tbl") String tbl, @RequestParam(value = "sysId", required = false) String sysId) {
        return ApiResponse.success(tbImpSpNeedtabRepo.findDistinctByTableNameIgnoreCase(tbl));
    }

    // 主表详情
    @GetMapping("/through/{id}")
    public ApiResponse<Map<String, String>> getThrough(@PathVariable("id") String spId) {
        return ApiResponse.success(impSpService.findThrough(spId));
    }

    /**
     * 生成指定 sp_id 的溯源数据
     */
    @GetMapping(value="/lineage/{id}")
    public ApiResponse<List<Map<String, Object>>> getLineage(@PathVariable("id") String spId) {
        return ApiResponse.success(impSpService.findLineage(spId));
    }

    @PutMapping("/spDetail/{id}")
    public ApiResponse<TbImpSp> updateImpDB(@PathVariable(value="id") String id, @RequestBody TbImpSp tbImpSp) {
       if (impSpService.exists(id)) {
           // exists, update
           impSpService.save(tbImpSp);
       } else {
           return ApiResponse.error(400, "id not found");
       }
        return ApiResponse.success(tbImpSp);
    }
}
