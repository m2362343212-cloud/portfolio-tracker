# Portfolio Tracker

A small Java command-line application that ingests a trade blotter (CSV),
maintains per-symbol positions using the **average-cost-basis** method, and
prints a realized/unrealized P&L report. Built as a self-contained,
resume-worthy example of Java OOP design — no frameworks, no database, just
the language and the standard library.

## What it does

1. Reads a list of trades (date, symbol, side, quantity, price) from a CSV file.
2. Applies them in order to a `Portfolio`, which routes each trade to the right
   `Position` (creating one the first time a symbol is traded).
3. Each `Position` tracks its own running quantity, weighted-average cost, and
   realized P&L as trades come in.
4. Given a set of current market prices, prints a report of every open
   position's market value and unrealized P&L, plus portfolio-wide totals.

## Running it

```
mvn package
java -jar target/portfolio-tracker.jar                       # uses the bundled sample data
java -jar target/portfolio-tracker.jar trades.csv prices.csv # uses your own files
```

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
- The `io` and `report` packages are kept separate from `model`: parsing and
  formatting are concerns that shouldn't leak into the domain classes that
  do the actual accounting.

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

Run them with `mvn test`.

## Suggested resume bullets

> **Portfolio Tracker — Personal Project (Java)**
> - Designed and built a Java command-line trade-blotter and P&L tracker from scratch, applying OOP principles (immutable value objects, encapsulated domain logic, custom exception types) across a 4-package Maven project with unit tests
> - Implemented an average-cost-basis accounting engine using `BigDecimal` for exact decimal precision, correctly handling weighted-average cost blending, realized P&L on partial/full sells, and edge cases like fully-closed positions
> - Wrote a CSV ingestion layer with line-level error reporting and 20+ JUnit 5 unit tests covering core accounting logic and parsing edge cases
