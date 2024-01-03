package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.TbImpDs2Tbls;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbImpDs2TblsRepo extends JpaRepository<TbImpDs2Tbls, String> {
    List<TbImpDs2Tbls> findByDsId(String id);
}
