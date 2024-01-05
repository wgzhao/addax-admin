package com.wgzhao.fsbrowser.controller.maintable;

import com.wgzhao.fsbrowser.model.oracle.TbImpDb;
import com.wgzhao.fsbrowser.repository.oracle.TbImpDBRepo;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping("/list")
    public List<TbImpDb> getAllImpDB() {
        return tbImpDBRepo.findAll();
    }

    @GetMapping("/detail/{id}")
    public Optional<TbImpDb> getImpDBById(Model model, @PathVariable(value="id") String id) {
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
}
