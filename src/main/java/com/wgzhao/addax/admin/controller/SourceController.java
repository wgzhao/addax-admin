package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.TbImpDb;
import com.wgzhao.addax.admin.repository.TbImpDBRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据源配置接口
 */
@Api(value = "数据源配置接口")
@RestController
@RequestMapping("/source")
public class SourceController
{
    @Autowired
    private TbImpDBRepo tbImpDBRepo;

    @GetMapping
    public ApiResponse<List<TbImpDb>> list()
    {
        return ApiResponse.success(tbImpDBRepo.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Optional<TbImpDb>> get(@PathVariable(value = "id") String id)
    {
        return ApiResponse.success(tbImpDBRepo.findById(id));
    }

    @PostMapping
    public ApiResponse<TbImpDb> saveImpDB(@RequestBody TbImpDb tbImpDb)
    {
        tbImpDBRepo.save(tbImpDb);
        return ApiResponse.success(tbImpDb);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Integer> delete(@PathVariable("id") String id)
    {
        if (tbImpDBRepo.existsById(id)) {
            tbImpDBRepo.deleteById(id);
            return ApiResponse.success(1);
        }
        else {
            return ApiResponse.success(0);
        }
    }

    @PutMapping
    public ApiResponse<Integer> bulkSave(@RequestBody List<TbImpDb> imps)
    {
        tbImpDBRepo.saveAll(imps);
        return ApiResponse.success(imps.size());
    }

    @PostMapping("/testConnect")
    public ApiResponse<Boolean> testConnect(@RequestBody Map<String, String> payload)
    {
        boolean isconn = DbUtil.testConnection(payload.get("url"), payload.get("username"), payload.get("password"));
        return ApiResponse.success(isconn);
    }
}
