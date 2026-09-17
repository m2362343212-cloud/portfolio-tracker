# Portfolio Tracker

A small Java application that ingests a trade blotter (CSV), maintains
per-symbol positions using the **average-cost-basis** method, and reports
realized/unrealized P&L — either as a plain-text console report or as a
live local web dashboard. Built as a self-contained, resume-worthy example
of Java OOP design — no frameworks, no database, no build-time dependency
beyond JUnit for tests; the web dashboard runs on `com.sun.net.httpserver`,
which ships inside the JDK itself.

## What it does

1. Reads a list of trades (date, symbol, side, quantity, price) from a CSV file.
2. Applies them in order to a `Portfolio`, which routes each trade to the right
   `Position` (creating one the first time a symbol is traded).
3. Each `Position` tracks its own running quantity, weighted-average cost, and
   realized P&L as trades come in.
4. Given a set of current market prices, renders a report of every open
   position's market value and unrealized P&L, plus portfolio-wide totals —
   as a console table (default) or a styled HTML dashboard (`--web`).

## Running it

```
mvn package
java -jar target/portfolio-tracker.jar                              # console report, bundled sample data
java -jar target/portfolio-tracker.jar trades.csv prices.csv        # console report, your own files
java -jar target/portfolio-tracker.jar --web                        # web dashboard at http://localhost:8080/, sample data
java -jar target/portfolio-tracker.jar --web trades.csv prices.csv  # web dashboard, your own files
```

The web dashboard shows three summary cards (total realized P&L, total
unrealized P&L, total market value), a table of open positions with
color-coded gains/losses, and a table of closed positions with their
realized P&L — and follows your system's light/dark mode automatically.

Trade CSV format (header row required, ignored):

```
date,symbol,side,quantity,price
2026-01-05,AAPL,BUY,10,185.20
2026-02-15,AAPL,SELL,8,201.50
```

Price CSV format (header row required, ignored):

```
symbol,price
AAPL,205.00
```

## Design notes

- **`Trade`** is an immutable value object — a booked trade is a historical
  fact, not something you mutate. Validated at construction (positive
  quantity/price, non-blank symbol), with `equals`/`hashCode` defined on
  value rather than identity.
- **`Position`** owns the average-cost-basis math for a single symbol: every
  BUY blends into one weighted-average cost; every SELL realizes P&L against
  that average without disturbing it. When a position fully closes, its
  average cost resets to zero rather than being left stale.
- **`Portfolio`** aggregates many `Position` objects by symbol and exposes
  portfolio-level rollups (total realized P&L, total unrealized P&L, total
  market value) without callers ever touching a raw map.
- **`InsufficientPositionException`** is a deliberate design choice: the
  tracker is intentionally long-only (no short selling) for a first version,
  and this is documented in the exception's own Javadoc rather than left as
  an implicit assumption.
- **`BigDecimal`** is used throughout instead of `double`/`float`, because
  binary floating point cannot represent most decimal currency values
  exactly — the classic `0.1 + 0.2 != 0.3` problem, which is unacceptable
  for anything that touches money.
- The `io`, `report`, and `web` packages are kept separate from `model`:
  parsing and presentation are concerns that shouldn't leak into the domain
  classes that do the actual accounting. `report.PortfolioReportGenerator`
  (text) and `web.HtmlReportRenderer` (HTML) are two interchangeable
  "views" over the exact same `Portfolio`/`Position` objects — neither the
  accounting logic nor the CSV parser has any idea a web dashboard exists.
- **`web.PortfolioWebServer`** is deliberately built on
  `com.sun.net.httpserver.HttpServer` — the JDK's own built-in HTTP server
  — rather than a framework like Spring Boot. For a project this size that
  keeps the whole thing dependency-free and lets you explain exactly how a
  request becomes an HTTP response, with nothing hidden behind
  auto-configuration.

## Scope / what's deliberately left out

This is a portfolio piece, not a production system, so a few things are
intentionally out of scope for v1 (and noted in the code where relevant):
short selling, FIFO/LIFO lot-level cost basis (average cost only), multi-
currency support, and live market data (prices are supplied by the caller).

## Testing

Unit tests live under `src/test/java` and cover:

- `PositionTest` — weighted-average cost across multiple buys, realized P&L
  on a partial sell, average-cost reset on a full close, the
  `InsufficientPositionException` case, and unrealized P&L against a
  supplied market price.
- `TradeTest` — construction validation and value-based `equals`/`hashCode`.
- `PortfolioTest` — trade routing to the correct position, aggregation across
  symbols, and the open-vs-all-positions distinction.
- `TradeCsvReaderTest` — valid parsing, header skipping, blank-line handling,
  and malformed-row errors that report the offending line number.
- `HtmlReportRendererTest` — correct values in the rendered markup,
  positive/negative P&L getting the right CSS class, closed positions not
  requiring a market price, and HTML-escaping of symbol names.

Run them with `mvn test`.

## Suggested resume bullets

> **Portfolio Tracker — Personal Project (Java)**
> - Designed and built a Java trade-blotter and P&L tracker from scratch, applying OOP principles (immutable value objects, encapsulated domain logic, custom exception types) across a 5-package Maven project with unit tests
> - Implemented an average-cost-basis accounting engine using `BigDecimal` for exact decimal precision, correctly handling weighted-average cost blending, realized P&L on partial/full sells, and edge cases like fully-closed positions
> - Built a live local web dashboard on Java's built-in HTTP server (`com.sun.net.httpserver`), rendering portfolio state as a styled, dark-mode-aware HTML report with zero external dependencies
> - Wrote a CSV ingestion layer with line-level error reporting and 25+ JUnit 5 unit tests covering core accounting logic, parsing edge cases, and HTML rendering (including output escaping)
