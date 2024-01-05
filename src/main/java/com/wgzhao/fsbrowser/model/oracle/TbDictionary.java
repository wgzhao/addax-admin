package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;


@Table(name="tb_dictionary")
@Getter
@Setter
@Data
@Entity
@IdClass(TbDictionaryPK.class)
public class TbDictionary {

    @Id
    @Column(name="ENTRY_CODE", length = 4)
    private String entryCode;

    @Id
    @Column(name="ENTRY_VALUE", length = 255)
    private String entryValue;

    @Column(name="ENTRY_CONTENT", length = 2000)
    private String entryContent;

    @Column(name="REMARK", length = 4000)
    private String remark;
}
