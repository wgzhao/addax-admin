package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.EtlJob
import org.springframework.data.jpa.repository.JpaRepository

interface EtlJobRepo : JpaRepository<EtlJob, Long>

