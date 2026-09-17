package com.iaaops.ingestion.persistence;

import com.iaaops.ingestion.ParsedRow;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 事实表的写入侧。
 *
 * 按天整体替换：先删掉这批日期在该媒体下的全部行，再写入新行——重复导入同一天不会累加，
 * 也不需要在行级别做去重键。读取侧在 reporting 模块，两边各有各的 SQL，共用同一张表。
 */
@Repository
public class FactWriteRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;

    FactWriteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 删掉这批日期在该媒体下的既有行，返回删除条数。 */
    public int deleteDays(String tenantId, String media, Collection<LocalDate> dates) {
        if (dates.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(dates.size(), "?"));
        Object[] params = new Object[dates.size() + 2];
        params[0] = tenantId;
        params[1] = media;
        int index = 2;
        for (LocalDate date : dates) {
            params[index++] = date;
        }
        return jdbc.update("delete from ad_facts where tenant_id = ? and media = ? and stat_date in ("
                + placeholders + ")", params);
    }

    public void insert(String tenantId, String media, List<ParsedRow> rows) {
        String sql = """
                insert into ad_facts (tenant_id, stat_date, stat_hour, media, account, campaign, cost, revenue,
                                      impressions, clicks, launches, callbacks, conversions)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<ParsedRow> batch = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbc.batchUpdate(sql, batch, batch.size(), (statement, row) -> {
                statement.setString(1, tenantId);
                statement.setObject(2, row.statDate());
                if (row.statHour() == null) {
                    statement.setNull(3, Types.SMALLINT);
                } else {
                    statement.setShort(3, row.statHour());
                }
                statement.setString(4, media);
                statement.setString(5, row.account());
                statement.setString(6, row.campaign());
                statement.setBigDecimal(7, row.cost());
                statement.setBigDecimal(8, row.revenue());
                statement.setLong(9, row.impressions());
                statement.setLong(10, row.clicks());
                statement.setLong(11, row.launches());
                statement.setLong(12, row.callbacks());
                statement.setLong(13, row.conversions());
            });
        }
    }
}
