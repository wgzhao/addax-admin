package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.TbImpJour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface TbImpJourRepo extends JpaRepository<TbImpJour, String> {
    List<TbImpJour> findByTradeDate(Integer ltd);

    List<TbImpJour> findByUpdtDateAfter(Date d);

    List<TbImpJour> findTop50ByUpdtDateAfter(Date d);
}
