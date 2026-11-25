package com.wgzhao.addax.admin.service.collection;

import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keys rows that predate the stored route keys.
 *
 * <p>The normalization depends on the live source/target identifier policy, which the startup schema
 * migration cannot resolve without opening connections from inside its transaction. The backfill
 * therefore runs after startup, computes the keys through the same {@link CollectionScopeService}
 * the API uses, and skips a row whose key would collide with another active definition instead of
 * failing the launch. Rows left unkeyed are outside the partial unique indexes until an operator
 * resolves the duplicate or an edit recomputes them.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TableRouteKeyBackfillService
    implements ApplicationRunner
{
    private final EtlTableRepo tableRepo;
    private final CollectionScopeService collectionScopeService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args)
    {
        try
        {
            List<Long> ids = tableRepo.findIdsMissingRouteKeys();
            if (ids.isEmpty())
            {
                return;
            }
            int keyed = 0;
            int conflicts = 0;
            for (Long id : ids)
            {
                EtlTable row = tableRepo.findById(id).orElse(null);
                if (row == null)
                {
                    continue;
                }
                try
                {
                    collectionScopeService.applyRouteKeys(row);
                }
                catch (RuntimeException e)
                {
                    log.warn("Route key backfill skipped table {}: {}", id, e.getMessage());
                    continue;
                }
                if (row.getRetiredAt() == null && conflictsWithActiveRow(row))
                {
                    conflicts++;
                    log.error("Route key backfill conflict for table {} (sid={}, target={}, source route key={}); "
                            + "leaving it unkeyed until the duplicate definition is resolved",
                        id, row.getSid(), row.getTargetId(), row.getSourceRouteKey());
                    continue;
                }
                try
                {
                    tableRepo.updateRouteKeys(id, row.getSourceRouteKey(), row.getTargetSlotKey());
                    keyed++;
                }
                catch (DataIntegrityViolationException e)
                {
                    conflicts++;
                    log.error("Route key backfill conflict for table {} while writing the keys", id);
                }
            }
            log.info("Route key backfill: keyed {} table(s), {} conflict(s)", keyed, conflicts);
        }
        catch (Exception e)
        {
            // New writes are keyed regardless; the remainder is retried on the next start.
            log.error("Route key backfill failed; it will be retried on the next start", e);
        }
    }

    private boolean conflictsWithActiveRow(EtlTable row)
    {
        Integer count = jdbcTemplate.queryForObject("""
            select count(*) from public.etl_table
             where sid = ? and target_id = ? and retired_at is null and id <> ?
               and (source_route_key = ? or target_slot_key = ?)
            """, Integer.class, row.getSid(), row.getTargetId(), row.getId(),
            row.getSourceRouteKey(), row.getTargetSlotKey());
        return count != null && count > 0;
    }
}
