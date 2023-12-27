package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.TbImpFlagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbImpFlagRepo extends JpaRepository<TbImpFlagEntity, String> {
    List<TbImpFlagEntity> findByTradedateAndKind(Integer date, String taskGroup);

    List<TbImpFlagEntity> findByTradedateAndKindOrderByDwCltDateDesc(Integer i, String taskGroup);
}
