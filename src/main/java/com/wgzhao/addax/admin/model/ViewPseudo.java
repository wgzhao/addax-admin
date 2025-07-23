package com.wgzhao.addax.admin.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Data
public class ViewPseudo {

    @Id
    private Long id;
    private String column;
}
