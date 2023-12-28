package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.ImpDB;
import com.wgzhao.fsbrowser.repository.oracle.ImpDBRepo;
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
    private ImpDBRepo impDBRepo;

    @GetMapping("/list")
    public List<ImpDB> getAllImpDB() {
        return impDBRepo.findAll();
    }

    @GetMapping("/detail/{id}")
    public Optional<ImpDB> getImpDBById(Model model, @PathVariable(value="id") String id) {
        return impDBRepo.findById(id);
    }

    @PostMapping("/save")
    public ImpDB saveImpDB(@ModelAttribute("impDB") ImpDB impDB) {
        if (impDB.getId() == null || Objects.equals(impDB.getId(), "")) {
            impDB.setId(UUID.randomUUID().toString());
        }
        impDBRepo.save(impDB);
        return impDB;
    }
}
