package com.wgzhao.addax.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class AddaxStatAggDto
{
    private String name;
    private List<String> runDates;
    private List<Long> takeSecs;
}
