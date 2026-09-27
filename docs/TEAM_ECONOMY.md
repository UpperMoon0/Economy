# Team economy

Economy can expose a shared wallet for an external party/team while keeping membership and permissions owned by the team mod. The built-in integration targets FTB Teams and is optional: Economy still starts and behaves normally when FTB Teams is absent.

## Account identity

Accounts are keyed by `AccountRef(kind, id)`, not by a bare UUID.

- `PLAYER:<uuid>` — personal wallet
- `TEAM:<uuid>` — shared party wallet
- `SERVER:<uuid>` — Economy server principal
- `TAX:<uuid>` — Economy tax principal

A player and a team may therefore use the same raw UUID without sharing a balance. Existing UUID-only saves migrate to `PLAYER` accounts. Only newly created player accounts receive the configured starting balance; team accounts always start at zero.

## FTB Teams mapping

When FTB Teams is installed, Economy registers an optional provider against the FTB Teams public API. Economy does not store money in FTB Teams data.

Only **party teams** become shared Economy principals. FTB personal teams are ignored so solo players do not gain a duplicate wallet, and FTB server teams are not made spendable automatically.

Joining or leaving a party never copies, merges, splits, refunds, or otherwise changes either balance. The party UUID maps to one persistent Economy `TEAM` account.

## Configuration

Team economy is enabled by default. With FTB Teams installed, party members can use a shared wallet while Personal remains their initial Market selection. Without FTB Teams, Economy continues with personal accounts and hides team controls.

Set `enabled=false` in `config/economy-team.properties` to disable team economy, then restart the server (or the game for singleplayer). The default file is:

```properties
enabled=true
viewRole=MEMBER
depositRole=MEMBER
spendRole=OFFICER
payoutRole=OWNER
adminRole=OWNER
```

Legacy `mode` entries are removed on loading and replaced with `enabled=true` unless an explicit `enabled` value already exists. This includes the previous `PERSONAL_ONLY` default. Administrators who want to keep team economy disabled must set `enabled=false`. Legacy `withdrawRole` is migrated to `payoutRole`; files without either key use OWNER. Role thresholds must be MEMBER, OFFICER, or OWNER; NONE is rejected.

The Java `TeamEconomyMode` API remains compatible for integrations: enabled configuration maps to `HYBRID`, disabled maps to `PERSONAL_ONLY`, and `TEAM_PRIMARY` remains available programmatically. Clients and servers must use matching Economy builds because the Market protocol includes typed storage and treasury permissions (`market_v3`).

## Permissions

Default minimum roles are:

| Action | Minimum role |
| --- | --- |
| View team balance / treasury | MEMBER |
| Deposit personal funds to team | MEMBER |
| Place team market orders | OFFICER |
| Direct Team payout to another player | OWNER |
| Reassign Team Vault/Tank ownership | OWNER |
| Change Vault/Tank market I/O mode | OWNER |
| Team-economy administration | OWNER |

The thresholds are configurable through `TeamEconomyRegistry`. Authorization is never cached: Economy resolves the player's current team and rank again for every check. A leave, kick, or demotion therefore takes effect before the next protected action.

Example:

```java
TeamEconomyRegistry teams = EconomyApi.teamEconomy();
Optional<TeamRef> team = teams.resolveTeam(playerId);

if (team.isPresent() && teams.canSpend(playerId, team.get().id())) {
    IBankAccount wallet = EconomyApi.accounts()
            .getOrCreateTeamAccount(team.get().id());
    // perform the server-authorized action
}
```

Do not trust a team ID or permission decision supplied by a client. Market spending and direct Team payouts are deliberately different permissions: `canSpend(...)` authorizes team market activity (OFFICER by default), while `canPayout(...)` authorizes Team payments to other players (OWNER by default). Ordinary Team -> Personal withdrawal is not supported; Team cash reaches members only through lifecycle settlement when the Team closes. Prefer the registry's mutation helpers when Team money moves:

```java
TeamEconomyRegistry teams = EconomyApi.teamEconomy();
teams.depositFromPlayer(
        EconomyApi.accounts(), playerId, amount,
        TransactionContext.transfer("team deposit", teamId));


teams.spendFromTeam(
        EconomyApi.accounts(), playerId, AccountRef.player(recipientId), amount,
        TransactionContext.transfer("team payment", recipientId)); // requires payoutRole; recipient cannot be the actor
```

Those helpers resolve the actor's current party and role again immediately before the account transfer, avoiding a stale client-side or cached authorization decision.

## Market wallet UI

The Market sync builds a fresh server-authoritative wallet snapshot for the viewing player. The balance badge is an account switcher: selecting **Personal** or **Team <party>** changes the principal and storage account used by new orders. The server revalidates the selection; a stale or unauthorized Team selection fails closed rather than silently charging Personal funds.

In `HYBRID`, Personal remains the policy default. In `TEAM_PRIMARY`, a spend-authorized party is the policy default. The UI switcher creates an explicit per-player override without changing the server policy mode. `/economy team use <personal|team|default>` remains a command fallback for the same selection state.

The Market principal is centralized and server-authoritative, not local state owned by the balance badge. `MarketWalletSelection` is the server source of truth and the synchronized `MarketClientStore.marketPrincipal` drives New Order, My/Team Orders, Portfolio, Containers, and Pay Player together. Principal-sensitive server responses are filtered before they are sent: Personal mode returns only Personal orders/storage/history/portfolio data, while Team mode returns only the current Team principal. If Team market-spend authorization is lost, the selected Market principal falls back to Personal on the next authoritative refresh.

Command paths that operate on money also expose explicit principals where relevant: `/economy balance personal|team`, `/economy pay personal|team`, and OP `/economy give|take|set personal|team`. Legacy unqualified balance/pay/admin forms remain Personal-only shorthands for compatibility.

The **Team Treasury** sidebar tab is available whenever the current party is viewable. It shows Personal and Team balances, party name, current role, and permission requirements. Deposit is MEMBER+ by default. There is no normal withdrawal control. Direct Team-to-player payouts live in **Pay Player** and require `payoutRole` (OWNER by default), independently of the OFFICER+ role used for team market orders. Self-payment from Team mode is rejected.

The **Containers** tab lists both Personal and current-Team Vaults/Tanks with their owner. Storage ownership reassignment and the Vault/Tank BOTH / INPUT / OUTPUT market mode are storage-administration operations governed by adminRole (OWNER by default), not by the market spendRole. The server revalidates every change and reports denied or stale actions back to the player instead of failing silently. Storage ownership changes are server-authorized; the client never supplies a trusted team ID.

A leave, kick, demotion below the relevant threshold, disabled team economy, missing provider, or incompatible provider is reflected on the next sync. Existing orders retain the persisted account/storage identity they were created with.

## Team closure and settlement

Team closure is a distinct lifecycle operation, not a withdrawal permission. Economy snapshots the final Team membership before the Team disappears, marks the Team wallet closing, rejects new protected Team activity, and first cancels/recovers outstanding Team orders and compensation references. Only after those obligations are clear does cash settlement begin.

Remaining Team cash is split equally across the persisted member snapshot in deterministic UUID order. Settlement progress is persisted after each successful payout, so a restart or vetoed transfer retries only the unpaid recipients instead of duplicating money. Any final rounding remainder goes to the last unpaid recipient. After cash reaches zero, Team-owned Vault/Tank blocks are reassigned to the last recorded owner for physical custody so unloaded storage cannot become orphaned. The closing tombstone remains durable.

Custom `TeamEconomyProvider` integrations must provide a complete `getMembers(teamId)` enumeration if they want Team closure settlement. The default provider method reports enumeration as unsupported by returning an empty collection. If no authoritative member snapshot has ever been captured, Economy refuses to collapse the payout to the owner and leaves the Team wallet preserved until membership can be resolved.

## Market orders

Orders persist three identities independently:

```text
principal       = AccountRef   // who pays / receives money
actor           = UUID         // human player who placed/performed the action
storageOwner    = UUID         // persisted UUID projection of physical storage
storageAccount()= AccountRef   // typed Vault/Tank/provider owner used for goods
```

For a Personal order, `principal == storageAccount() == PLAYER:<actor>` and `storageOwner == actor`. For a newly created Team order, `principal == storageAccount() == TEAM:<ftb-team-id>`, `actor` remains the player who acted, and `storageOwner` stores the team UUID for persistence/wire compatibility. A separate persisted `StorageAccount` field preserves the storage kind, including when a player and team share the same UUID. Missing `StorageAccount` always migrates to player storage. Team BUYs therefore deliver into Team-owned Vaults/Tanks and Team SELLs reserve from Team-owned storage.

Legacy orders remain compatible. UUID-only orders migrate to `PLAYER:<old-owner>`. Team orders created by older Economy builds that recorded the acting player's UUID as `storageOwner` continue to resolve that storage as `PLAYER:<actor>` instead of being silently rewritten.

Matching compares economic principals, so two different members cannot make the same team trade with itself. Every team order revalidates current membership/rank before execution, edit, or cancellation. Leave/kick/demotion invalidates the order lazily even if an FTB lifecycle event is missed; Economy attempts a lossless cancellation and keeps any unrecoverable escrow/compensation record persisted until recovery succeeds.

When an FTB party is deleted, its wallet is tombstoned/closed and new Team actions are rejected. Economy freezes the last authoritative member snapshot, cancels/recovers outstanding Team orders first, and then splits remaining cash equally across that persisted snapshot. Per-recipient settlement progress is durable, so retries and restarts do not intentionally double-pay already-settled members; the final unpaid recipient receives any deterministic rounding remainder. Only after cash reaches zero are Team-owned Vault/Tank blocks reassigned to the last recorded owner for physical custody, without moving blocks or contents.

## Development dependencies and compatibility checks

All five loader development environments include FTB Teams and FTB Library on their local runtime classpath (client, server, live-join, and GameTest runs). They remain optional production integrations and are not bundled or published as Economy dependencies. Pins live in `gradle/ftb-teams.gradle`:

| Target | FTB Teams | FTB Library |
| --- | --- | --- |
| Forge / Fabric 1.20.1 | 2001.3.2 | 2001.2.13 |
| Fabric / NeoForge 1.21.1 | 2101.1.11 | 2101.1.36 |
| NeoForge 26.1.2 | 26.1.2.4 | 26.1.2.8 |

`./gradlew :common:ftbContractTest` resolves the five published Teams jars and their matching FTB Library jars from [FTB Maven](https://maven.ftb.dev/releases/dev/ftb/mods/). It checks every reflected provider method plus both lifecycle-event generations directly from class files: Architectury `TeamEvent` fields on 1.20.1/1.21.1, and NeoForge 26.x event wrappers/data records together with FTB Library's `BaseEventWithData#getEventData`. It uses a separate source set without local FTB doubles or Minecraft class loading. The same task runs automatically with `:common:test` / `check`, including the shared CI lane. Each target resolves separately so Gradle cannot collapse different Minecraft versions into one jar.

The existing reflection-adapter unit tests use behavioral doubles for membership changes, party filtering, and failures; they do not claim artifact compatibility. When upgrading a development pin, run the artifact contract and the corresponding live-join lane.

## Verification and remaining manual checks

Automated coverage includes all supported JVM/version suites, published FTB API contracts, typed order/trade/storage migration and UUID collisions, codec boundaries, treasury permission separation, provider/cancel recovery, and deletion/restart settlement. Forge GameTests exercise real Vault-backed Team BUY/SELL, membership invalidation, escrow restoration, open-menu reauthorization, Team storage break permissions, team commands, and real FTB lifecycle-hook registration.

The development-only Forge renderer captures the actual Market screen with deterministic fixtures in wide/narrow layouts and light/dark themes. Run `./gradlew :forge-1.20.1:renderUiPreviews`; the 48-case PNG gallery is generated in `forge-1.20.1/build/ui-previews/index.html`. Cases cover party absence/access loss, Personal/Team selection, treasury permissions, container ownership, order identities, and Team BUY/SELL forms. These fixtures verify presentation, not live multiplayer behavior.

Before release, repeat treasury actions, storage reassignment, and permission changes with a real party and a second player. Automated permission fixtures do not replace this multiplayer UI check. Other loaders are covered by compilation/JVM tests and matching UI adapters, not a manual visual pass.
