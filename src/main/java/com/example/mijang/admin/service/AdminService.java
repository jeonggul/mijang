/*
 * AdminService — 관리자 기능의 본체
 *
 * 이 파일이 하는 일
 *   관리자 화면이 하는 일을 실제로 수행한다 —
 *   종목 목록 조회(비활성 포함), 종목 올리기·내리기, 종목 마스터 수동 동기화,
 *   배치 상태 조회, 운영 로그 조회.
 *   관리자인지는 여기서 확인하지 않는다. SecurityConfig 의 경로 규칙이 이미 막았다.
 */
package com.example.mijang.admin.service;

import com.example.mijang.admin.dto.AdminLogResponse;
import com.example.mijang.admin.dto.BatchLogResponse;
import com.example.mijang.admin.mapper.AdminLogMapper;
import com.example.mijang.admin.mapper.BatchLogMapper;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.dto.StockSearchResponse;
import com.example.mijang.stock.mapper.StockMapper;
import com.example.mijang.stock.service.StockSyncService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 기능. 개발명세서(API) ADMIN-01·02·07 · 화면 SR-013
 *
 * <p>권한 확인은 여기서 하지 않는다. {@code SecurityConfig} 의 경로 규칙이 막는다(2.1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final String ACTION_STOCK_TOGGLE = "STOCK_TOGGLE";
    private static final String ACTION_BATCH_RUN = "BATCH_RUN";
    private static final String TARGET_STOCK = "STOCK";
    private static final String TARGET_BATCH = "BATCH";
    private static final String RESULT_SUCCESS = "SUCCESS";

    private final StockMapper stockMapper;
    private final StockSyncService stockSyncService;
    private final AdminLogMapper adminLogMapper;
    private final BatchLogMapper batchLogMapper;

    /**
     * 관리자용 종목 목록. {@code ADMIN-01}
     *
     * <p>일반 검색({@code StockSearchService})을 쓰지 않는 이유가 있다. 그쪽은 활성 종목만
     * 돌려주므로 <b>내려간 종목을 찾을 수가 없다.</b> 관리자는 내려간 것을 다시 올려야 하니
     * 활성·비활성을 함께 보는 조회가 따로 필요하다.
     *
     * @param status ACTIVE·INACTIVE·ALL. 그 밖의 값은 ALL 로 본다
     */
    @Transactional(readOnly = true)
    public List<StockSearchResponse> stocks(String status, String assetClass, String q, int limit) {
        Boolean active = "ACTIVE".equals(status) ? Boolean.TRUE
                       : "INACTIVE".equals(status) ? Boolean.FALSE
                       : null;
        return stockMapper.findForAdmin(active, blankToNull(assetClass), blankToNull(q), limit);
    }

    /** 같은 조건의 전체 건수. 화면 머리말에 쓴다. */
    @Transactional(readOnly = true)
    public int stockCount(String status, String assetClass, String q) {
        Boolean active = "ACTIVE".equals(status) ? Boolean.TRUE
                       : "INACTIVE".equals(status) ? Boolean.FALSE
                       : null;
        return stockMapper.countForAdmin(active, blankToNull(assetClass), blankToNull(q));
    }

    /**
     * 빈 문자열을 null 로 바꾼다.
     *
     * <p>XML 의 {@code <if>} 는 null 만 "조건 없음" 으로 읽는다. 화면이 빈 검색어를 보내면
     * 빈 문자열이 그대로 와서 {@code LIKE '%'} 가 되므로 여기서 걸러야 한다.
     */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 종목 활성·비활성 전환. {@code ADMIN-01}
     *
     * <p>지우지 않는다(2.6). 과거 매매 기록이 참조하고 있다.
     *
     * <p>기록은 <b>바꾼 뒤에</b> 남긴다. 하지 않은 일이 기록되면 안 된다(2.3).
     *
     * @throws BusinessException 없는 종목일 때(404)
     */
    @Transactional
    public void toggleStock(Long adminId, String symbol, boolean active, String reason) {
        String key = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        Stock stock = stockMapper.findBySymbol(key);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }
        stockMapper.setActive(key, active, active ? null : reason);

        // 원본이 사라져도 무슨 종목이었는지 남도록 이름을 함께 적는다(2.2)
        writeLog(adminId, ACTION_STOCK_TOGGLE, TARGET_STOCK, key, stock.name(),
                (active ? "활성화" : "비활성화") + (reason == null ? "" : " — " + reason));
    }

    /**
     * 종목 마스터 수동 동기화. {@code ADMIN-01}
     *
     * <p>동기로 돌리고 건수를 돌려준다(2.5). 관리자 한 명이 쓰는 화면이라
     * 수십 초를 기다리게 해도 된다.
     *
     * @return 반영된 종목 수
     */
    @Transactional
    public int syncStockMaster(Long adminId) {
        int count = stockSyncService.syncAll();
        writeLog(adminId, ACTION_BATCH_RUN, TARGET_BATCH, "SYNC_STOCKS", "종목 마스터 동기화",
                count + "건 반영");
        return count;
    }

    /** 배치 상태. {@code ADMIN-02} — 잡별 최근 실행 한 건씩. */
    @Transactional(readOnly = true)
    public List<BatchLogResponse> batchStatus() {
        return batchLogMapper.findLatestPerJob();
    }

    /** 운영 로그. {@code ADMIN-07} */
    @Transactional(readOnly = true)
    public List<AdminLogResponse> recentLogs(int limit) {
        return adminLogMapper.findRecent(limit);
    }

    /**
     * 운영 로그를 남긴다.
     *
     * <p><b>실패해도 삼킨다</b>(2.3). 기록이 중요하지만 기록 때문에 운영이 막히면 안 된다.
     */
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
