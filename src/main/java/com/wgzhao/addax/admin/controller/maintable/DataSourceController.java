package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.model.oracle.TbImpDb;
import com.wgzhao.addax.admin.repository.oracle.TbImpDBRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据源配置接口
 */
@Api(value="数据源配置接口", tags = {"主表配置"})
@RestController
@RequestMapping("/maintable/datasource")
public class DataSourceController {


    @Autowired
    private TbImpDBRepo tbImpDBRepo;

    @GetMapping
    public List<TbImpDb> list() {
        return tbImpDBRepo.findAll();
    }

    @GetMapping("/{id}")
    public Optional<TbImpDb> get(@PathVariable(value="id") String id) {
        return tbImpDBRepo.findById(id);
    }

    @PostMapping
    public TbImpDb saveImpDB(@RequestBody TbImpDb tbImpDb) {
        tbImpDBRepo.save(tbImpDb);
        return tbImpDb;
    }

    @DeleteMapping("/{id}")
    public int delete(@PathVariable("id") String id) {
        if (tbImpDBRepo.existsById(id)) {
            tbImpDBRepo.deleteById(id);
            return 1;
        } else {
            return 0;
        }
    }

    @PutMapping
    public int bulkSave(@RequestBody List<TbImpDb> imps) {
        tbImpDBRepo.saveAll(imps);
        return imps.size();
    }

    @PostMapping("/testConnect")
    public boolean testConnect(@RequestBody Map<String, String> payload) {
        return DbUtil.testConnection(payload.get("url"), payload.get("username"), payload.get("password"));
    }
}
