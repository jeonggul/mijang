/*
 * LedgerService — 계산기에 넣을 입력을 모으는 곳
 *
 * 이 파일이 하는 일
 *   한 종목의 거래와 분할을 읽어 HoldingCalculator 에 넣고 결과를 돌려준다.
 *   그게 전부다. 판단도 저장도 하지 않는다.
 *
 *   왜 따로 두는가
 *     같은 계산을 네 화면이 쓴다 — 보유 현황·매매 기록 목록·양도세·커뮤니티 매매 카드.
 *     각자 거래를 읽어 계산기를 부르면 한 곳이 분할을 빠뜨려도 드러나지 않는다.
 *     실제로 그랬다. 입력을 모으는 자리가 하나면 빠뜨릴 자리도 하나다.
 */
package com.example.mijang.portfolio.service;

import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.stock.mapper.StockSplitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원장을 훑은 결과. 보유 현황·실현손익·매도 건별 원가가 한 번에 나온다.
 *
 * <p><b>계산기에 넣을 입력을 모으는 자리는 여기 하나뿐이다.</b> 2026-09-03 점검 4.1 에서
 * 같은 계산을 부르는 네 곳 중 보유 현황만 분할을 넘기고 있었고, 분할을 겪은 종목은
 * 같은 매도의 실현손익이 화면마다 다르게 나왔다. 화면에는 오류가 아니라 그냥 다른
 * 숫자로 보여서 눈치채기 어렵다.
 *
 * <p>{@link HoldingCalculator} 에는 분할을 받지 않는 갈래가 아직 남아 있다. 순수 계산을
 * 검사하는 시험이 쓴다 — <b>운영 코드는 이 클래스만 거친다.</b>
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionMapper transactionMapper;
    private final StockSplitMapper splitMapper;

    /**
     * 한 종목을 처음부터 훑는다.
     *
     * <p>분할 이전 거래는 지금 기준으로 환산해서 센다. 시세는 이미 분할이 반영된 값으로
     * 들어오는데 원장은 그날 체결한 그대로라, 보정하지 않으면 4:1 분할 뒤 평가금액이
     * 4분의 1로 보이고 원가는 4배로 잡힌다.
     */
    @Transactional(readOnly = true)
    public HoldingCalculator.Calculation calculationOf(Long userId, String symbol) {
        return HoldingCalculator.calculateAll(symbol,
                transactionMapper.findForRecalc(userId, symbol),
                splitMapper.findBySymbol(symbol));
    }
}
