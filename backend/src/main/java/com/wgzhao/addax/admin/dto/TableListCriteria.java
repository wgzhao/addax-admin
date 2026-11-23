package com.wgzhao.addax.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Normalized filter criteria of the collection-table list.
 *
 * <p>All values are already trimmed, de-duplicated and case-normalized by the controller; the
 * service turns them into predicates without re-interpreting user input.</p>
 */
public record TableListCriteria(
    int page,
    int pageSize,
    String q,
    List<String> statuses,
    String scope,
    List<Integer> sourceIds,
    List<String> dbTypes,
    Boolean sourceEnabled,
    List<Integer> targetIds,
    List<String> targetTypes,
    String targetDbContains,
    Boolean targetEnabled,
    List<String> writeModes,
    List<String> schemaModes,
    String tblCommentContains,
    LocalDate endTimeFrom,
    LocalDate endTimeTo,
    LocalDate createdFrom,
    LocalDate createdTo,
    LocalDate updatedFrom,
    LocalDate updatedTo,
    String sortField,
    String sortOrder)
{
}
