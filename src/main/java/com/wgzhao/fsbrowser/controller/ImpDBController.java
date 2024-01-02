package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.TbImpDb;
import com.wgzhao.fsbrowser.repository.oracle.TbImpDBRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/impdb")
@CrossOrigin
public class ImpDBController {


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
