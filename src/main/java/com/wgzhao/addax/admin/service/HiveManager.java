package com.wgzhao.addax.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Service
public class HiveManager
{
    @Autowired
    @Qualifier("hiveDataSource")
    private DataSource hiveDataSource;

    public void createTable()
            throws SQLException
    {
    }
}
