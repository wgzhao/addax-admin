package com.wgzhao.addax.admin.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="tb_imp_etl_job")
@Setter
@Getter
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TbImpEtlJob
{
    @Id
    String tid;

    String job;
}
