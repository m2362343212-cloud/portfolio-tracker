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
java -jar target/portfolio-tracker.jar --optimize                   # also suggests long-only min-variance weights, sample data
java -jar target/portfolio-tracker.jar --bayesian                   # Black-Litterman posterior + optimizer, sample data
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

Historical-price CSV format, used only by `--optimize` (header row required, ignored; rows may be in any order and cover any subset of symbols per date):

```
date,symbol,price
2026-01-02,AAPL,182.10
2026-01-02,MSFT,365.00
2026-01-03,AAPL,183.40
```

## Web dashboard and JSON API (`--web`)

The dashboard at `http://localhost:8080/` is a static page (`src/main/resources/web/index.html`) whose JavaScript calls a small JSON API on the same JDK HTTP server:

| Route | What it does |
|---|---|
| `GET /` | The JavaScript dashboard: summary cards, sortable open and closed positions, trade-CSV upload |
| `GET /report` | The server-rendered HTML report, including any `--optimize` / `--bayesian` results |
| `GET /api/portfolio` | Current portfolio as JSON |
| `POST /api/trades` | Body = trade CSV text. Parses and validates it, rebuilds the portfolio, returns the new JSON |

Design choices:

- **Money travels as strings** (`"1435.00"`), not JSON numbers, so the exact `BigDecimal` value reaches the browser; JavaScript converts to a number only to display and sort.
- **All-or-nothing uploads.** A new portfolio is built from the uploaded CSV and swapped in only if every step succeeds. A malformed row, a sell larger than the position, or a symbol with no market price returns `400` with the reason, and the previous portfolio stays on screen.
- **Basic hardening.** Uploads are capped at 1 MB (`413`), wrong HTTP methods get `405`, table cells are written with `textContent` rather than `innerHTML`, and the server listens on `localhost` only.

`PortfolioWebServerTest` starts the server on a free port and checks these responses with real HTTP requests.

## Portfolio optimization (`--optimize`)

Given a historical price series, `--optimize` suggests a long-only,
fully-invested rebalancing of the open positions that minimizes portfolio
variance -- the classic Markowitz mean-variance problem, solved without
any external optimization or linear-algebra library:

- **`optimize.ReturnStatistics`** turns aligned per-symbol return series
  into a sample mean-return vector and covariance matrix (with Bessel's
  correction), the two inputs mean-variance optimization needs.
- **`optimize.PortfolioOptimizer`** solves for the minimum-variance weights
  via **projected gradient descent**: each iteration takes a gradient step
  against `w^T * Σ * w`, then projects the result back onto the
  probability simplex (weights sum to 1, none negative) with
  `optimize.SimplexProjection`. This is deliberate: the textbook
  closed-form solution (`Σ⁻¹ * 1`, via matrix inversion) routinely wants to
  short an instrument, and clipping negative weights to zero afterward
  doesn't solve the constrained problem, it just damages the unconstrained
  answer. Working directly in the constrained space means every
  intermediate iterate -- not just the final one -- is already a valid
  long-only portfolio, and the step size is chosen automatically from a
  Gershgorin-circle bound on the covariance matrix's spectral radius, so
  it stays numerically stable regardless of the data's scale.
- A second mode, `targetReturn(target, penalty)`, finds the
  (approximately) minimum-variance portfolio that clears a target expected
  return, using a quadratic penalty on the shortfall -- a standard way to
  fold an inequality constraint into an objective a projected-gradient
  solver can handle directly.
- **`io.HistoricalPriceCsvReader`** turns a long-format `date,symbol,price`
  CSV into aligned price series per symbol (inner-joined on common dates,
  so a gap in one symbol can't silently misalign another's), and
  **`optimize.Returns`** turns a price series into simple period returns.

This part of the codebase intentionally uses `double` rather than
`BigDecimal` -- unlike the exact-decimal accounting in `model`, mean and
covariance are statistical *estimates* from a finite sample to begin with,
so exact decimal arithmetic wouldn't buy any real precision, and `double`
is what every practical numerical optimization routine uses.

## Bayesian portfolio construction (`--bayesian`)

`--optimize` estimates expected returns as the raw historical sample
mean, which is a famously noisy input to mean-variance optimization: a
small change in the sample mean can flip which portfolio comes out
"optimal" (Michaud, 1989, calls this "error maximization," since the
optimizer amplifies exactly the input errors that are largest).
`--bayesian` replaces that raw sample mean with a proper Bayesian
posterior, via the **Black-Litterman model** (Black & Litterman, 1992;
He & Litterman, 1999):

- **`optimize.BlackLittermanModel`** starts from a prior that's much
  better-behaved than the sample mean: the expected returns implied by
  treating the market-cap-weighted portfolio as itself mean-variance
  optimal (a "reverse optimization" of CAPM logic, `pi = delta * Sigma *
  w_market`). It then updates that prior with the investor's own views --
  each expressed as a pick vector, a view return, and a confidence
  variance (smaller = more confident) -- to produce a posterior mean and
  covariance. This is Bayesian updating in the textbook sense: a Gaussian
  prior, a Gaussian likelihood (the views), and a closed-form Gaussian
  posterior. Both **absolute** views ("asset X will return 8%") and
  **relative** views ("X will outperform Y by 2%") are supported, plus a
  default-confidence helper (`tau * Var(symbol)`, the He & Litterman
  convention) for when a caller doesn't have a better estimate of their
  own confidence.
- **`optimize.Matrix`** is a small, from-scratch, package-private
  linear-algebra type (multiply, transpose, inverse via Gauss-Jordan
  elimination with partial pivoting) that exists solely to support this:
  unlike `PortfolioOptimizer`, which deliberately avoids ever needing a
  matrix inverse (see its own Javadoc), computing a Bayesian posterior is
  inherently a matrix-inversion problem, so there's no way around one
  here.
- **`BlackLittermanResult.toReturnStatistics()`** packages the posterior
  as a `ReturnStatistics` (via a new `ReturnStatistics.of(...)` factory
  that accepts an externally-estimated mean/covariance rather than
  computing one from raw returns), so it plugs directly into the
  already-existing `PortfolioOptimizer` -- no separate code path needed
  to actually act on the posterior.

Worth noting from the CLI demo: `minimumVariance()` ignores expected
returns entirely by construction, so running it on the Black-Litterman
posterior produces identical weights to running it on the raw sample
mean -- that's correct, not a bug, and `--bayesian` prints both the
minimum-variance baseline and a `targetReturn(...)` result specifically
so the view's effect is actually visible. It's also worth noting that a
view on two symbols can shift a *third*, uninvolved symbol's posterior
too, whenever that symbol is correlated with the ones the view names --
Black-Litterman propagates information through the whole covariance
structure, not just along the pick vector's literal entries.

## Multi-asset backtest (`--backtest`)

The optimizer above answers "how should I split my money today?". The backtest answers the harder question: **if I had followed each rule every month for the past 13 years, what would have happened?**

```
pip install yfinance
python scripts/fetch_prices.py XIC.TO XSP.TO XBB.TO CGL.TO --start 2010-01-01
java -jar target/portfolio-tracker.jar --backtest data/etf-prices.csv \
     --benchmark XIC=0.3,XSP=0.3,XBB=0.4 --risk-free 0.02 --max-weight 0.4
```

**Universe.** Four Toronto-listed ETFs, one per asset class: Canadian equities (XIC), US equities (XSP, CAD-hedged), Canadian bonds (XBB) and gold (CGL). Prices are adjusted for distributions and converted to month-ends. Cash is deliberately not an asset: it barely moves, so a minimum-variance rule would simply hold cash. It enters only as the risk-free rate in the Sharpe ratio.

**Method.** Walk-forward: at each month-end a rule sees only the previous 36 months of returns, sets target weights, and the portfolio is held for one month. Weights drift with prices between rebalances, and every trade back to target costs 10 bps. `BacktestTest` checks that a rule never sees the return it is about to earn.

**Rules compared.** Equal weight; a fixed 60/40 stock/bond mix; inverse volatility; long-only minimum variance; and minimum variance with a 40% ceiling on any single asset class.

**Results, Nov 2013 to Oct 2026 (156 months), after costs:**

| Rule | Return / yr | Volatility / yr | Sharpe | Max drawdown | 2022 | Turnover / yr |
|---|---|---|---|---|---|---|
| Equal weight | 8.67% | 8.66% | 0.77 | 14.4% | -9.3% | 12% |
| 60/40 (XIC/XSP/XBB = 30/30/40) | 7.93% | 8.73% | 0.68 | 15.6% | -12.1% | 9% |
| Inverse volatility | 7.05% | 7.13% | 0.71 | 13.7% | -9.8% | 15% |
| Minimum variance | 2.69% | 5.29% | 0.13 | 15.6% | -11.5% | 19% |
| Minimum variance, 40% cap | 7.64% | 7.60% | 0.74 | 11.4% | -6.6% | 33% |

**What I found.**

1. **The lowest-volatility rule had the worst risk-adjusted return.** Minimum variance put about 90% of the portfolio in bonds, because bonds had been the calmest asset class over the trailing 36 months. When rates rose in 2022 that concentration became the main risk: the portfolio fell 15.6% from its July 2020 peak, as far as the 60/40 mix, while earning far less in normal years. Low past volatility is not the same as low future risk.
2. **A per-asset-class cap fixed most of it.** Limiting any asset class to 40%, the way institutional investment policies set ranges, forced the remainder into Canadian equities and gold. Sharpe rose from 0.13 to 0.74, the maximum drawdown became the lowest of all rules (11.4%), and 2022 was the least bad year (-6.6%). The cost is higher turnover, because the cap binds often.
3. **Equal weight was still hardest to beat on return.** With only four asset classes, the error in estimating a covariance matrix from 36 months of data seems to cost about as much as optimizing gains. Its edge over 60/40 comes mostly from holding gold, which behaved differently from stocks and bonds in 2022.

**Limitations.** The risk-free rate is a flat 2% assumption rather than the actual T-bill series. CGL only starts in October 2010, which sets the start date. The last month in the run above is partial. Four assets and one 13-year window are a small sample, so these are observations about this period, not general laws.

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
The optimizer is long-only for the same reason the accounting is: the
target-return mode is a soft (penalty-method) constraint, not an exact
one; and there's no transaction-cost or turnover penalty, so a suggested
rebalancing is a target weight vector, not a trade list.

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
- `SimplexProjectionTest` — projections that are hand-verifiable via the
  KKT conditions (a spike, a uniform point, an already-valid point), plus
  that every result sums to 1 with no negative entries.
- `ReturnStatisticsTest` — mean and covariance checked against hand
  computation, symbol-order preservation, defensive copying, and the
  empty/too-short/mismatched-length error cases.
- `ReturnsTest` — simple-return math and its edge cases (too few prices, a
  zero price).
- `PortfolioOptimizerTest` — an equal-variance two-asset case that must
  split 50/50 (provably, via the KKT conditions, regardless of
  covariance), that the lower-variance asset gets more weight in a mixed
  case, that every result is a valid long-only fully-invested portfolio,
  that optimizing never leaves the portfolio worse off than naive equal
  weighting, that it converges within the iteration budget, and that
  targeting a higher return shifts weight toward the higher-return asset.
- `HistoricalPriceCsvReaderTest` — aligned parsing, the inner-join
  behavior when a symbol is missing a date, and malformed-row errors.
- `MatrixTest` — multiply, transpose, add, scale, and inverse checked
  against hand computation (a diagonal matrix, a general 2x2 via the
  closed-form formula, and `M * M^-1 == I` for a 3x3), plus the singular-
  and non-square-matrix error cases.
- `BlackLittermanModelTest` — every number checked by hand: the implied
  equilibrium return formula, a single-view posterior verified against an
  independently-computed precision-weighted average, that an uncorrelated
  asset's posterior is provably untouched by a view that doesn't name it,
  and the resulting posterior variance's exact value -- plus the view
  constructors, the BL-to-`PortfolioOptimizer` bridge, and the error cases.

Run them with `mvn test`.

## Suggested resume bullets

> **Portfolio Tracker — Personal Project (Java)**
> - Designed and built a Java trade-blotter and P&L tracker from scratch, applying OOP principles (immutable value objects, encapsulated domain logic, custom exception types) across a 6-package Maven project with unit tests
> - Implemented an average-cost-basis accounting engine using `BigDecimal` for exact decimal precision, correctly handling weighted-average cost blending, realized P&L on partial/full sells, and edge cases like fully-closed positions
> - Implemented a mean-variance portfolio optimizer from scratch (no external optimization library) using projected gradient descent with a from-scratch simplex projection to enforce long-only, fully-invested constraints, plus a step size derived from a Gershgorin-circle bound on the covariance matrix's spectral radius for numerical stability
> - Implemented Bayesian portfolio construction (Black-Litterman) from scratch, including a from-scratch matrix-inversion routine (Gauss-Jordan with partial pivoting), to combine market-implied equilibrium returns with investor views into a posterior return/covariance estimate for the optimizer
> - Built a live local web dashboard on Java's built-in HTTP server (`com.sun.net.httpserver`), rendering portfolio state as a styled, dark-mode-aware HTML report with zero external dependencies
> - Wrote a CSV ingestion layer with line-level error reporting and 82 JUnit 5 unit tests covering core accounting logic, the optimizer's and Black-Litterman model's correctness (numbers verified by hand, not just plausibility checks), parsing edge cases, and HTML rendering (including output escaping)
