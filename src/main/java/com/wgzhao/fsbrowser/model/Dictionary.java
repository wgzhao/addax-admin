package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;


@Table(name="tb_dictionary", uniqueConstraints = {@UniqueConstraint(columnNames = {"entryCode", "entryValue"})})
@Getter
@Setter
@Data
@Entity
public class Dictionary {

    @Id
    @Column(length = 4)
    private String entryCode;

    @Column(length = 255)
    private String entryValue;

    @Column(length = 2000)
    private String entryContent;

    @Column(length = 4000)
    private String remark;
}
