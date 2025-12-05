package com.wgzhao.addax.admin.dto;

/**
 * 表元信息：注释与近似行数。
 */
public record TableMeta(String comment, Long approxRowCount) {}