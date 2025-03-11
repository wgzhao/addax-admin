package com.wgzhao.addax.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@AllArgsConstructor
@Setter
@Getter
public class SortBy
{
    private String key;
    private String order;
}
