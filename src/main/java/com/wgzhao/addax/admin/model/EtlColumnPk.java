package com.wgzhao.addax.admin.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class EtlColumnPk
        implements Serializable
{
    private long tid;
    private String columnName;

}
