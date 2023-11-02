package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.ImpDB;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImpDBRepo extends JpaRepository<ImpDB, String> {
}
