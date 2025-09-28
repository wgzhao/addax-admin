package com.wgzhao.addax.admin.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter: EtlTableStatus <-> Integer
 */
@Converter(autoApply = true)
public class EtlTableStatusConverter implements AttributeConverter<EtlTableStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(EtlTableStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public EtlTableStatus convertToEntityAttribute(Integer dbData) {
        return EtlTableStatus.fromCode(dbData);
    }
}
