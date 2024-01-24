package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.model.oracle.TbImpDb;
import com.wgzhao.addax.admin.repository.oracle.TbImpDBRepo;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
        if (tbImpDb.getId() == null || Objects.equals(tbImpDb.getId(), "")) {
            tbImpDb.setId(UUID.randomUUID().toString());
        }
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
}
