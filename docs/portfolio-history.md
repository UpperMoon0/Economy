# Portfolio history and operation coverage

Portfolio is an account net-worth history, not a trading-profit report. A change in net worth can come from cash flows, prices, goods entering/leaving owned storage, or team storage succession. Cash and goods use separate typed financial/storage owners; selecting TEAM never merges members' personal accounts.

## Committed operations

The shared account implementation reports successful credit, debit, direct set, transfer, starting balance and account deletion through `BalanceStore.recordMutation`. Save/load and equal-value sets do not report operations. Each committed operation has a fresh ID independent of a caller's reusable transaction context. Transfers share an ID across both accounts; history deduplication is per typed account.

Trade execution groups payment, delivery and compensation until its synchronous operation scope closes. A fully refunded attempt adds no money event. A partial fill records the final accepted cash delta once on each account. Failed compensation retains actual cash changes and existing recovery claims. Trade movements have zero external cash flow; grants, starting balances, generic API causes and transfers are external flows for the individual account. This does not create taxes, fees or rewards that were not previously implemented.

Independent nested orders and addon callback operations retain their own operation IDs, reasons and cash-flow deltas. Reusing a transaction context never turns an unrelated credit/debit into a transfer leg; only actual transfer legs are grouped. A transparent external account wrapper can forward one exact target/context/amount credit as the counterpart leg, under the existing external-account contract. Journal delivery waits for the enclosing settlement to finish so provisional payments that are subsequently refunded cannot contaminate another operation's history. The internal cash path is reconstructed in scope-start order from committed deltas. When more than one independent operation shares that settlement, every point explicitly states that its balance, assets and net worth share the final grouped valuation; its cash delta and external flow remain per-operation. Intermediate asset chronology cannot safely be reconstructed from provider callbacks, so it is not fabricated. Single surviving operations and ordinary immediate mutations retain their normal individual marks.

The journal observes committed changes; it does not retry or roll back money. Reporter failures are logged and cannot turn a successful payment into a reported failure. Saved account balances and attributed history use the existing account saved-data file. The existing world-save lifecycle remains the durability boundary; this is not a separate transactional database or a crash-recovery replacement.

## Coverage matrix

| Entry point | Shared boundary | Coverage |
| --- | --- | --- |
| API credit/debit, namespaced addon causes | `BankAccount.credit/debit` | Successful nonzero operations; cancellation/insufficient funds excluded |
| Admin give/take/set, personal and typed commands | Same methods and `BankAccount.setBalance` | Immediate attributed cash events |
| Starting balance | `AccountManager.getOrCreatePlayerAccount` | One player inflow; no team/server starting grants |
| UI/command/API payments | `BankAccount.transferTo` | Both typed legs, common operation ID |
| Team deposits/withdrawals/spending | Same transfer boundary | Team and player isolated, opposing cash flows |
| Disband payouts/retry | Same transfer boundary and `TeamWalletLifecycle.settle` | Successful shares only; settled members not paid twice; storage successor refreshed |
| Account deletion | `AccountManager.deleteAccount` | Nonzero removed cash reported; retired handles reject new mutations while pending committed operations survive deletion/recreation |
| Market buy/sell, built-in/provider, partial delivery | `Order.executeAmount` | Final trade cash changes, no external inflow classification |
| Refund/full compensation/payment veto | Same synchronous trade scope | Net-zero refunded attempts and rejected payments emit no money event |
| Reserve/edit/cancel/expiry/recovery | `OrderManager`, shared valuation | Owned escrow retained exactly once; final storage snapshots when needed |
| Failed refund | Existing compensation metadata | Typed receivable/liability conserved in asset value |
| Price/storage changes | Shared valuation and visible refresh | Valuation observations carry zero cash-flow delta |
| Restart/legacy history | Three saved-data adapters | Exact decimals and attribution persist; legacy points remain explicitly unattributed |

## Refresh and retention

The visible Portfolio tab polls every 40 client ticks. LIVE requests a fresh response. The server admits at most one portfolio request per player per second, with no per-tick account scan. Wallet changes clear old displayed data; replies include the authoritative selected wallet after permission fallback. Holdings list both total owned units and unreserved stored units so sell-order escrow cannot become a New Order stock hint. The packet displays at most the highest-value 512 holdings and explicitly labels truncation; the total asset valuation includes all holdings. Responses also enforce a conservative 768 KiB encoded-byte budget: the latest history points receive half the budget and holdings use the remainder. Omitted rows are explicitly counted in the visible status; saved history and total valuation remain complete. Operation source/reason strings are bounded to 256/1,024 characters with an explicit truncation suffix.

Each account retains at most 1,024 points: recent individual events plus an explicit older-history rollup checkpoint. Rollup preserves summed cash deltas/external flows and its final balance/assets; it cannot recover the chronology of older individual events. Legacy portions remain unattributed. Callback ID deduplication covers retained individual operations across saves/restarts; rolled-up operation IDs are not an indefinite replay registry. Production account callbacks are synchronous and scopes close idempotently; replaying arbitrary historical callbacks is not an API for replaying money.

Valuation boundaries, provider opt-in, unloaded storage, unknown prices and quarantined claims are documented in [portfolio-valuation.md](portfolio-valuation.md). Asset transfers and storage additions must not be inferred to be trading profit from the cash-flow fields alone.
