package com.wgzhao.fsbrowser.repository.pg;

import com.wgzhao.fsbrowser.model.pg.TbAddaxStaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddaxStaRepo extends JpaRepository<TbAddaxStaEntity, Long> {
    List<TbAddaxStaEntity> findByTotalErrNot(int i);
}
