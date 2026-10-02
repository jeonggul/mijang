# MIJANG project memory

## Project identity

- `미장 (MIJANG)` is a personal learning/portfolio web app for Korean users who invest in US stocks.
- Its core value is recording trades and decomposing KRW profit/loss into stock-price and FX effects. It is not an investment-advice or brokerage service.
- Repository: this directory (`mijang/`). Reference documents live in the sibling directory `../docs/` and are not part of this Git repository.

## Source-of-truth order

When documents disagree, use this order:

1. Current code, `build.gradle`, and `src/main/resources/application*.properties`
2. `../docs/db/schema.sql` plus the dated files in `../docs/db/migrations/`
3. The newest domain implementation note in `../docs/mijang-vault/미장-*-구현.md`
4. API/DB specifications and `미장-구현-우선순위.md`
5. Planning documents, prototypes, PDF, and spreadsheet artifacts

The documentation contains historical and aspirational designs. In particular, architecture text mentioning React/Vite, Redis, Flyway, Spring Boot 3.5, Testcontainers, or package `com.mijang` does not describe the current code. The current implementation is the authority.

Ignore `../docs/mijang-vault/떴다-기획서.md`; it describes another project. Also ignore the parent `.claude/commands/sync-from-notion.md` unless the user explicitly asks about that separate workflow.

## Current implementation

- Java 17, Spring Boot 4.0.7, Gradle wrapper
- Spring MVC/WebMVC + Thymeleaf + vanilla JavaScript/CSS
- Spring Security with JWT access/refresh tokens in HttpOnly cookies
- MyBatis 4.0.1 + MySQL 8; SQL mappings are in `src/main/resources/mapper/`
- Outbound REST/WebSocket clients and browser-facing SSE for market data
- Package root is `com.example.mijang`
- Redis is not wired. `RedisConfig` is a placeholder; the single-instance MVP uses in-memory quote/cache/pub-sub abstractions.
- Flyway is not wired. Apply `../docs/db/schema.sql` and subsequent dated migrations manually and in date order.

Main domain packages are `user`, `security`, `stock`, `market`, `news`, `fx`, `portfolio`, `community`, `admin`, `common`, `config`, and `web`.

Layering rule:

```text
Controller -> Service -> Mapper/MyBatis -> MySQL
                |
                +-> external VendorClient
```

- Controllers validate/translate HTTP and authentication context; business rules and transaction boundaries belong in services.
- Mappers own SQL, not business decisions.
- External clients translate vendor APIs; a vendor outage should degrade the affected value, not take down unrelated screens.
- Financial arithmetic uses `BigDecimal` with explicit scale and rounding.

## Core domain rules

- `transactions` is the source ledger. `holdings` and `daily_snapshots` are derived and must be recalculable.
- Average cost uses moving average; average purchase FX uses cost-weighted average.
- The key invariant is:

```text
stockPnl = qty * (currentPrice - avgPrice) * currentFx
fxPnl    = qty * avgPrice * (currentFx - avgBuyFx)
totalPnl = stockPnl + fxPnl
         = qty * (currentPrice * currentFx - avgPrice * avgBuyFx)
```

- Live IEX quotes are display-only. P/L and snapshots use confirmed FX and stored daily/SIP closing prices.
- Store timestamps in UTC. Determine US trade dates/sessions with `America/New_York`; do not hand-code DST offsets.
- Missing price/FX data is not zero. Prefer `null`, skipped-symbol metadata, or a graceful empty state.
- Never delete referenced stock masters; mark them inactive. Account withdrawal is soft deletion.
- Never expose holding quantities through community badges/cards. A community trade card is a write-time snapshot so later transaction edits do not silently rewrite a post.

## External data and limits

- Alpaca: stock master, calendar, bars, corporate actions, REST snapshots, and WebSocket quotes. Free Basic uses IEX and a 30-symbol WebSocket subscription limit; REST budget is 200/minute.
- Finnhub: company profile, metrics, earnings calendar, and company news. Do not use it for candles or the economic calendar.
- SEC EDGAR: filings and XBRL facts. No key, but a truthful contact email in `User-Agent` is mandatory; keep requests at or below 10/second.
- BLS + the bundled FOMC JSON: economic calendar.
- Open Exchange Rates: USD/KRW quotes. Free plan is hourly and 1,000 calls/month, so collection is hourly shortly after the hour. `fx_quotes` is current history; `fx_rates` is the confirmed daily calculation input.

All credentials belong only in untracked `src/main/resources/application-secret.properties`. Never print, paste into docs, or commit real values.

## Local setup and verification

1. Copy `src/main/resources/application-secret.properties.example` to `application-secret.properties` and fill local values.
2. Create MySQL database `mijang` with `utf8mb4` and an app-specific user.
3. Apply `../docs/db/schema.sql`, then every required dated migration in chronological order.
4. Run `./gradlew test` for regression coverage and `./gradlew bootRun` to start locally.
5. Use `./scripts/check-api-keys.sh` only when an actual vendor check is intended; it performs network calls and consumes quotas.

`mijang.batch.enabled=false` is the local default. With batches off, empty `stocks`, `daily_prices`, `market_days`, and FX tables produce empty or fallback screens; that is not proof the feature is unimplemented.

For a focused change, run its targeted test first, then the full suite. Do not require live vendor calls in ordinary tests.

## Current product status (2026-08-24)

- The MVP scopes foundation, auth, stock, FX, portfolio, dashboard, report, mypage, admin, and base market streaming are implemented.
- News/company information is already connected even though broader news collection is an extension.
- Community is the active extension: free/Q&A/stock boards, post creation/detail, comments, shareholder badge, and attached trade snapshot are being connected.
- Community work deliberately leaves reports, reactions/bookmarks, post/comment edit/delete, popular-board aggregation, and UI pagination for later.
- Other extensions include retrospect, tax/dividend, notifications, social login, advanced multi-instance market infrastructure, and operations/deployment.

Before editing, inspect `git status`; the worktree may contain active user changes. Preserve unrelated modifications and do not rewrite them.

## Known documentation/configuration traps

- `application-secret.properties.example` and `scripts/check-api-keys.sh` still mention the retired Korea Eximbank key, while current FX code requires `mijang.fx.app-id` for Open Exchange Rates. Verify/fix this separately when touching setup or FX tooling.
- Some documents report old class/template/test counts. Recount from the filesystem instead of copying those numbers.
- Prototype HTML in `../docs/prototype/` is visual reference material, not the production frontend.
- The parent `../.agents/` directory is a tool/skill collection, not application source.

## Git conventions

- Use feature branches and pull requests. Commit messages follow Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
- Never commit secrets, `.env` files, IDE metadata, build output, or `application-secret.properties`.
- Do not commit or push unless the user explicitly asks.

## Codex plugins and MCP

- No plugin or MCP connection is required to build, test, or run the app. Alpaca, Finnhub, SEC, BLS, and Open Exchange Rates are application HTTP integrations configured by properties, not Codex MCP dependencies.
- Figma is useful only when the task needs the existing design frames or design-to-code comparison.
- GitHub integration is useful for PRs, issues, Actions, and remote review, but local coding does not depend on it.
- Notion is only needed for the separate parent `sync-from-notion` workflow; it is not a current MIJANG source of truth.
