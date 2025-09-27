package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.EtlJourRepo;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class EtlJourService
{
    private final EtlJourRepo etlJourRepo;
}

