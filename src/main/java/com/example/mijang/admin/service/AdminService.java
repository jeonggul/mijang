package com.example.mijang.admin.service;

import com.example.mijang.admin.dto.AdminLogResponse;
import com.example.mijang.admin.dto.BatchLogResponse;
import com.example.mijang.admin.mapper.AdminLogMapper;
import com.example.mijang.admin.mapper.BatchLogMapper;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.dto.StockSearchResponse;
import com.example.mijang.stock.mapper.StockMapper;
import com.example.mijang.stock.service.StockSyncService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 기능 서비스 — 종목 조회·토글·수동 동기화, 배치 상태, 운영 로그를 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    // admin_logs.action 은 ENUM 이다 — 스키마에 없는 값을 넣으면 기록이 조용히 사라진다
    private static final String ACTION_STOCK_DEACTIVATE = "STOCK_DEACTIVATE";
    private static final String ACTION_STOCK_RESTORE = "STOCK_RESTORE";
    private static final String ACTION_BATCH_RUN = "BATCH_RUN";
    private static final String TARGET_STOCK = "STOCK";
    private static final String TARGET_BATCH = "BATCH";
    private static final String RESULT_SUCCESS = "SUCCESS";

    private final StockMapper stockMapper;
    private final StockSyncService stockSyncService;
    private final AdminLogMapper adminLogMapper;
    private final BatchLogMapper batchLogMapper;

    /** 관리자용 종목 목록을 조회한다 — 활성·비활성을 함께 본다. status 가 그 밖의 값이면 ALL 로 본다. */
    @Transactional(readOnly = true)
    public List<StockSearchResponse> stocks(String status, String assetClass, String q,
                                            int limit, int offset) {
        Boolean active = "ACTIVE".equals(status) ? Boolean.TRUE
                       : "INACTIVE".equals(status) ? Boolean.FALSE
                       : null;
        return stockMapper.findForAdmin(active, blankToNull(assetClass), blankToNull(q),
                                        limit, Math.max(0, offset));
    }

    /** 같은 조건의 종목 전체 건수를 돌려준다. */
    @Transactional(readOnly = true)
    public int stockCount(String status, String assetClass, String q) {
        Boolean active = "ACTIVE".equals(status) ? Boolean.TRUE
                       : "INACTIVE".equals(status) ? Boolean.FALSE
                       : null;
        return stockMapper.countForAdmin(active, blankToNull(assetClass), blankToNull(q));
    }

    /** 빈 문자열을 null 로 바꾼다 — XML 의 if 는 null 만 "조건 없음" 으로 읽는다. */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    /** 종목을 활성·비활성으로 전환한다 — 지우지 않고, 기록은 바꾼 뒤에 남긴다. 없는 종목이면 404 다. */
    @Transactional
    public void toggleStock(Long adminId, String symbol, boolean active, String reason) {
        String key = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        Stock stock = stockMapper.findBySymbol(key);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }
        stockMapper.setActive(key, active, active ? null : reason);

        // 원본이 사라져도 남도록 종목 이름을 함께 적는다
        writeLog(adminId, active ? ACTION_STOCK_RESTORE : ACTION_STOCK_DEACTIVATE,
                TARGET_STOCK, key, stock.name(),
                (active ? "활성화" : "비활성화") + (reason == null ? "" : " — " + reason));
    }

    /** 종목 마스터를 동기로 수동 동기화하고 반영된 종목 수를 돌려준다. */
    @Transactional
    public int syncStockMaster(Long adminId) {
        int count = stockSyncService.syncAll();
        writeLog(adminId, ACTION_BATCH_RUN, TARGET_BATCH, "SYNC_STOCKS", "종목 마스터 동기화",
                count + "건 반영");
        return count;
    }

    /** 배치 상태를 조회한다 — 잡별 최근 실행 한 건씩. */
    @Transactional(readOnly = true)
    public List<BatchLogResponse> batchStatus() {
        return batchLogMapper.findLatestPerJob();
    }

    /** 화면의 종류 필터 한 버튼이 뜻하는 target_type 목록. */
    private static final Map<String, List<String>> LOG_TYPE_GROUPS = Map.of(
            "CONTENT", List.of("POST", "COMMENT", "REPORT", "NOTICE"),
            "USER", List.of("USER"),
            "STOCK", List.of("STOCK"),
            "BATCH", List.of("BATCH"));

    /** 최근 운영 로그를 조회한다 — 모르는 종류가 오면 거르지 않고 전체를 준다. */
    @Transactional(readOnly = true)
    public List<AdminLogResponse> recentLogs(int limit, String q, String type, int days) {
        List<String> types = type == null ? null
                : LOG_TYPE_GROUPS.get(type.toUpperCase(Locale.ROOT));
        LocalDateTime since = days > 0
                ? LocalDateTime.now(TradingClock.SERVICE_ZONE).minusDays(days) : null;
        String keyword = (q == null || q.isBlank()) ? null : q.trim();
        return adminLogMapper.findRecent(limit, keyword, types, since);
    }

    /** 운영 로그를 남긴다. 실패해도 삼킨다 — 기록 때문에 운영이 막히면 안 된다. */
    private void writeLog(Long adminId, String action, String targetType,
                          String targetId, String targetLabel, String detail) {
        try {
            adminLogMapper.insert(adminId, action, targetType, targetId,
                    targetLabel, detail, RESULT_SUCCESS);
        } catch (RuntimeException e) {
            log.warn("[운영로그] 기록 실패 — {} {}", action, targetId, e);
        }
    }
}
