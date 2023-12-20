package com.wgzhao.fsbrowser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;


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

//    @ManyToOne(fetch = FetchType.LAZY, optional = false)
//    @JoinColumn(name = "entry_code", nullable = false)
//    @OnDelete(action = OnDeleteAction.CASCADE)
//    @JsonIgnore
//    private Dict dict;

}
