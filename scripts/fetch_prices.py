"""
Download daily adjusted prices from Yahoo Finance and save them in the
date,symbol,price format the backtest reads.

    pip install yfinance
    python scripts/fetch_prices.py XIC.TO XSP.TO XBB.TO CGL.TO --start 2010-01-01 --out data/etf-prices.csv

Adjusted prices include reinvested dividends and distributions, which matters
a lot for bond funds. The script prints each ticker's first date so you can
check that every series covers the period you want.
"""
import argparse
import pathlib

import yfinance as yf


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("tickers", nargs="+")
    ap.add_argument("--start", default="2010-01-01")
    ap.add_argument("--out", default="data/etf-prices.csv")
    args = ap.parse_args()

    rows = []
    for ticker in args.tickers:
        df = yf.download(ticker, start=args.start, auto_adjust=True, progress=False)
        if df.empty:
            raise SystemExit(f"No data for {ticker}. Check the symbol on finance.yahoo.com.")
        close = df["Close"].squeeze().dropna()
        name = ticker.split(".")[0]                      # XIC.TO -> XIC
        print(f"{name}: {len(close)} days, {close.index[0].date()} to {close.index[-1].date()}")
        rows += [f"{d.date()},{name},{p:.6f}" for d, p in close.items()]

    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("date,symbol,price\n" + "\n".join(rows) + "\n")
    print(f"Wrote {len(rows)} rows to {out}")


if __name__ == "__main__":
    main()
