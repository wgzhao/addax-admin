package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.TbImpFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbImpFlagRepo extends JpaRepository<TbImpFlag, String> {
    List<TbImpFlag> findByTradedateAndKind(Integer date, String taskGroup);

    List<TbImpFlag> findByTradedateAndKindOrderByDwCltDateDesc(Integer i, String taskGroup);
}
