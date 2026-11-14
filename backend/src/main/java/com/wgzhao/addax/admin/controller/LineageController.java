package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ColumnLineageDto;
import com.wgzhao.addax.admin.service.LineageService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Field-level lineage: global field search across collections.
 */
@RestController
@RequestMapping("/lineage")
@AllArgsConstructor
public class LineageController
{
    private final LineageService lineageService;

    @GetMapping("/columns")
    public List<ColumnLineageDto> searchColumns(@RequestParam("q") String q)
    {
        return lineageService.searchColumns(q);
    }
}
