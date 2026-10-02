package com.example.mijang.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.stock.mapper.StockMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 종목 동기화의 기준 시각은 <b>DB 시계</b>여야 한다.
 *
 * <p>{@code synced_at} 은 DB 가 {@code CURRENT_TIMESTAMP(3)} 으로 찍는다. 그것과 견줄
 * 기준을 자바에서 만들면 서로 다른 시계를 비교하게 된다. DB 가 UTC, 서버가 KST 로 도는
 * 흔한 배치에서는 방금 넣은 행이 9시간 뒤처져 보여 <b>전 종목이 비활성으로 내려간다.</b>
 *
 * <p>이 시험은 두 시계가 같은 환경에서도 의미가 있다 —
 * {@code now()} 가 DB 를 거쳐 오는지를 보기 때문이다.
 */
@SpringBootTest
class StockSyncClockTest {

    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("기준 시각은 DB 에서 온다")
    void DB시계를읽는다() throws Exception {
        LocalDateTime fromMapper = stockMapper.now();
        assertThat(fromMapper).isNotNull();

        LocalDateTime fromDb;
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT CURRENT_TIMESTAMP(3)")) {
            rs.next();
            /* getObject 로 읽어야 한다. getTimestamp 는 접속 설정의 표준시로 한 번
               변환하는데, 우리 URL 은 serverTimezone=UTC 라고 적어 두었지만 실제 서버는
               그렇지 않아 아홉 시간이 밀린다(실측). synced_at 을 넣고 빼는 길과
               같은 길로 읽어야 견줄 수 있다 */
            fromDb = rs.getObject(1, LocalDateTime.class);
        }

        // 같은 시계에서 왔다면 몇 밀리초 차이뿐이다
        Duration gap = Duration.between(fromMapper, fromDb).abs();
        assertThat(gap).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("불릴 때마다 앞으로만 간다")
    void 되돌아가지않는다() {
        LocalDateTime first = stockMapper.now();
        LocalDateTime second = stockMapper.now();
        assertThat(second).isAfterOrEqualTo(first);
    }
}
