package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.repository.SysItemRepo;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * 源字段类型 → ODS/Hive 类型的映射（字典 2011）。
 *
 * <p>取代通用字典维护里对类型映射表的裸编辑：这里把"源类型 / 目标类型 / 说明"三列作为
 * 明确的数据模型暴露，运维只增删改映射行，接触不到 item_key / item_value 的存储原语。</p>
 */
@RestController
@RequestMapping("/type-mappings")
@AllArgsConstructor
public class TypeMappingController
{
    /**
     * 类型映射所在的字典编码。
     */
    private static final int TYPE_MAPPING_DICT_CODE = 2011;

    private final SysItemRepo sysItemRepo;
    private final AuditRecorder auditRecorder;

    /**
     * 映射行的 DTO：item_key 是源类型，item_value 是目标类型。
     */
    public record TypeMapping(String sourceType, String targetType, String remark)
    {
    }

    @GetMapping("")
    public List<TypeMapping> list()
    {
        return sysItemRepo.findByDictCode(TYPE_MAPPING_DICT_CODE).stream()
            .sorted(Comparator.comparing(SysItem::getItemKey, Comparator.nullsFirst(String::compareTo)))
            .map(item -> new TypeMapping(item.getItemKey(), item.getItemValue(), item.getRemark()))
            .toList();
    }

    @PostMapping("")
    @Transactional
    public ResponseEntity<TypeMapping> create(@RequestBody TypeMapping mapping)
    {
        String sourceType = requireSourceType(mapping);
        if (sysItemRepo.findByDictCodeAndItemKey(TYPE_MAPPING_DICT_CODE, sourceType).isPresent()) {
            throw new ApiException(409, "源类型已存在：" + sourceType);
        }
        SysItem item = new SysItem();
        item.setDictCode(TYPE_MAPPING_DICT_CODE);
        item.setItemKey(sourceType);
        item.setItemValue(requireTargetType(mapping));
        item.setRemark(mapping.remark());
        SysItem saved = sysItemRepo.save(item);
        auditRecorder.success("TYPE_MAPPING_CREATED", "CONFIG", "WARNING", "TYPE_MAPPING",
            sourceType, sourceType, null, mapping);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @PutMapping("/{sourceType}")
    @Transactional
    public ResponseEntity<TypeMapping> update(@PathVariable String sourceType, @RequestBody TypeMapping mapping)
    {
        String target = requireTargetType(mapping);
        SysItem before = sysItemRepo.findByDictCodeAndItemKey(TYPE_MAPPING_DICT_CODE, sourceType)
            .orElseThrow(() -> new ApiException(404, "源类型不存在：" + sourceType));
        before.setItemValue(target);
        before.setRemark(mapping.remark());
        SysItem saved = sysItemRepo.save(before);
        auditRecorder.success("TYPE_MAPPING_UPDATED", "CONFIG", "WARNING", "TYPE_MAPPING",
            sourceType, sourceType, toDto(before), mapping);
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{sourceType}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String sourceType)
    {
        SysItem item = sysItemRepo.findByDictCodeAndItemKey(TYPE_MAPPING_DICT_CODE, sourceType)
            .orElseThrow(() -> new ApiException(404, "源类型不存在：" + sourceType));
        sysItemRepo.delete(item);
        auditRecorder.success("TYPE_MAPPING_DELETED", "CONFIG", "WARNING", "TYPE_MAPPING",
            sourceType, sourceType, toDto(item), null);
        return ResponseEntity.noContent().build();
    }

    private static TypeMapping toDto(SysItem item)
    {
        return new TypeMapping(item.getItemKey(), item.getItemValue(), item.getRemark());
    }

    private static String requireSourceType(TypeMapping mapping)
    {
        if (mapping == null || mapping.sourceType() == null || mapping.sourceType().isBlank()) {
            throw new ApiException(400, "源类型不能为空");
        }
        return mapping.sourceType().trim();
    }

    private static String requireTargetType(TypeMapping mapping)
    {
        if (mapping == null || mapping.targetType() == null || mapping.targetType().isBlank()) {
            throw new ApiException(400, "目标类型不能为空");
        }
        return mapping.targetType().trim();
    }
}
