package codeit.ootd.infra.weather;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OwmOneCallUsageLimiter {

    private static final int DAILY_LIMIT = 950;

    private static final String RESERVE_SQL = """
        INSERT INTO weather_one_call_usage (usage_date, request_count)
        VALUES ((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date, 1)
        ON CONFLICT (usage_date)
        DO UPDATE SET request_count = weather_one_call_usage.request_count + 1
        WHERE weather_one_call_usage.request_count < ?
        RETURNING request_count
        """;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryReserveRequest() {
        List<Integer> counts = jdbcTemplate.query(
            RESERVE_SQL,
            (rs, rowNum) -> rs.getInt("request_count"),
            DAILY_LIMIT
        );

        return !counts.isEmpty();
    }
}
