package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Data
public class ViewPseudoEntity {

    @Id
    private Long id;
    private String column;
}
