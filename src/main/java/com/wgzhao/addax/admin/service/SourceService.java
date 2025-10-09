package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 数据源服务类，负责数据源的增删改查及相关元数据操作。
 */
@Service
@AllArgsConstructor
@Slf4j
public class SourceService
{
    /**
     * 数据源仓库
     */
    private final EtlSourceRepo etlSourceRepo;
    /**
     * 采集任务调度服务
     */
    private final CollectionSchedulingService collectionSchedulingService;

    /**
     * 获取有效数据源数量
     *
     * @return 有效数据源数量
     */
    public Integer getValidSources()
    {
        return etlSourceRepo.countByEnabled(true);
    }

    /**
     * 根据ID获取数据源对象
     *
     * @param sid 数据源ID
     * @return 数据源对象或null
     */
    public EtlSource getSource(Integer sid)
    {
        return etlSourceRepo.findById(sid).orElse(null);
    }

    /**
     * 检查数据源编号是否存在
     *
     * @param code 数据源编号
     * @return 是否存在
     */
    public boolean checkCode(String code)
    {
        return etlSourceRepo.existsByCode(code);
    }

    /**
     * 查询所有数据源
     *
     * @return 数据源列表
     */
    public List<EtlSource> findAll()
    {
        return etlSourceRepo.findAll();
    }

    /**
     * 根据ID查询数据源
     *
     * @param id 数据源ID
     * @return 可选数据源对象
     */
    public Optional<EtlSource> findById(int id)
    {
        return etlSourceRepo.findById(id);
    }

    /**
     * 保存数据源对象，并根据需要更新调度任务
     *
     * @param etlSource 数据源对象
     * @param updateSchedule 是否更新调度
     * @return 保存后的数据源对象
     */
    public EtlSource save(EtlSource etlSource, boolean updateSchedule)
    {
        etlSourceRepo.save(etlSource);
        if (updateSchedule) {
            // 先取消原有调度任务，再重新调度
            log.warn("The scheduling of source {}({}) is being updated", etlSource.getName(), etlSource.getCode());
            collectionSchedulingService.cancelTask(etlSource.getCode());
            collectionSchedulingService.scheduleOrUpdateTask(etlSource);
        }
        return etlSource;
    }

    /**
     * 根据ID删除数据源
     *
     * @param id 数据源ID
     */
    public void deleteById(int id)
    {
        etlSourceRepo.deleteById(id);
    }

    /**
     * 检查数据源ID是否存在
     *
     * @param id 数据源ID
     * @return 是否存在
     */
    public boolean existsById(int id)
    {
        return etlSourceRepo.existsById(id);
    }

    /**
     * 批量保存数据源对象
     *
     * @param sources 数据源列表
     */
    public void saveAll(List<EtlSource> sources)
    {
        etlSourceRepo.saveAll(sources);
    }

    /**
     * 新建数据源对象，并自动创建调度任务
     *
     * @param etlSource 数据源对象
     * @return 保存后的数据源对象
     */
    public EtlSource create(EtlSource etlSource)
    {
        EtlSource save = etlSourceRepo.save(etlSource);
        // 新采集源创建时，默认创建一个同步任务
        collectionSchedulingService.scheduleOrUpdateTask(etlSource);
        return save;
    }

    /**
     * 获取指定数据库下未采集的表元数据（含表注释）
     *
     * @param source 数据源对象
     * @param dbName 数据库名
     * @param existsSet 已采集表集合
     * @return 未采集表元数据列表
     */
    public List<TableMetaDto> getUncollectedTables(EtlSource source, String dbName, Set<String> existsSet)
    {
        List<TableMetaDto> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(source.getUrl(), source.getUsername(), source.getPass())) {
            // 按元数据读取所有表
            ResultSet tables = connection.getMetaData().getTables(dbName, null, "%", new String[] {"TABLE"});
            while (tables.next()) {
                String tblName = tables.getString("TABLE_NAME");
                // 已采集表跳过
                if (existsSet.contains(tblName) || existsSet.contains(tblName.toLowerCase())) {
                    continue;
                }
                String remarks = Optional.ofNullable(tables.getString("REMARKS")).orElse("");
                // 优先使用元数据中的注释，否则回退到 commentFallback
                if (remarks.isEmpty()) {
                    remarks = DbUtil.getTableComment(connection, dbName, tblName);
                }
                result.add(new TableMetaDto(tblName, remarks));
            }
        }
        catch (SQLException e) {
            log.warn("Failed to get uncollected tables for source {}: {}", source.getId(), e.getMessage());
            return null;
        }
        return result;
    }
}
