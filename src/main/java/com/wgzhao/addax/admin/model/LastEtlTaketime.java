package com.wgzhao.addax.admin.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity
@Getter
public class LastEtlTaketime {
    @Id
    private String fids;
    private String tradeDate;
    private String takeTimes;
}
