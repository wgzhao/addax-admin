package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.ImpEtlOverprec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImpEtlOverprecRepo extends JpaRepository<ImpEtlOverprec, String> {

    List<ImpEtlOverprec> findAllByOrderByDbStartAsc();
}
