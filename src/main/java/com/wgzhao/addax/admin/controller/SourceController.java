package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
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
    private EtlSourceRepo etlSourceRepo;

    @GetMapping
    public ApiResponse<List<EtlSource>> list()
    {
        return ApiResponse.success(etlSourceRepo.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Optional<EtlSource>> get(@PathVariable(value = "id") int id)
    {
        return ApiResponse.success(etlSourceRepo.findById(id));
    }

    @PostMapping
    public ApiResponse<EtlSource> saveImpDB(@RequestBody EtlSource etlSource)
    {
        etlSourceRepo.save(etlSource);
        return ApiResponse.success(etlSource);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Integer> delete(@PathVariable("id") int id)
    {
        if (etlSourceRepo.existsById(id)) {
            etlSourceRepo.deleteById(id);
            return ApiResponse.success(1);
        }
        else {
            return ApiResponse.success(0);
        }
    }

    @PutMapping
    public ApiResponse<Integer> bulkSave(@RequestBody List<EtlSource> imps)
    {
        etlSourceRepo.saveAll(imps);
        return ApiResponse.success(imps.size());
    }

    @PostMapping("/testConnect")
    public ApiResponse<Boolean> testConnect(@RequestBody Map<String, String> payload)
    {
        boolean isconn = DbUtil.testConnection(payload.get("url"), payload.get("username"), payload.get("password"));
        return ApiResponse.success(isconn);
    }
}
