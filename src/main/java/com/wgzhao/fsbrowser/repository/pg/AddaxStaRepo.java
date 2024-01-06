package com.wgzhao.fsbrowser.repository.pg;

import com.wgzhao.fsbrowser.model.pg.TbAddaxSta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddaxStaRepo extends JpaRepository<TbAddaxSta, Long> {
    List<TbAddaxSta> findByTotalErrNot(int i);
}
