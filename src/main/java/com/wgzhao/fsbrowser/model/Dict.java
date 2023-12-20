package com.wgzhao.fsbrowser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "tb_dict")
@Getter
@Setter
@Data
public class Dict {
    @Id
    @Column(length = 4)
    private String dictCode;
    @Column(length = 255)
    private String dictName;
    @Column(length = 2000)
    private String dictClass;
    @Column(length = 2000)
    private String remark;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name="entryCode") // column entry_code in table tb_dictionary
    @JsonIgnore
    private List<Dictionary> dicts;
}
