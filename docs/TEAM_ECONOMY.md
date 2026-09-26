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

## Modes

The default is intentionally backward-compatible:

- `PERSONAL_ONLY` — no team wallet is exposed.
- `HYBRID` — personal remains the default, but a shared team wallet is available.
- `TEAM_PRIMARY` — the current party wallet is the default principal when the player still has the required role.

The mode is exposed through `EconomyApi.teamEconomy()`:

```java
EconomyApi.teamEconomy().setMode(TeamEconomyMode.HYBRID);
```

Pack/bootstrap code can also configure the matching values on `EconomyConfig` before Economy initialization.

## Permissions

Default minimum roles are:

| Action | Minimum role |
| --- | --- |
| View team balance | MEMBER |
| Deposit to team | MEMBER |
| Spend team funds | OFFICER |
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

Do not trust a team ID or permission decision supplied by a client. Prefer the registry's mutation helpers when money moves:

```java
TeamEconomyRegistry teams = EconomyApi.teamEconomy();
teams.depositFromPlayer(
        EconomyApi.accounts(), playerId, amount,
        TransactionContext.transfer("team deposit", teamId));

teams.spendFromTeam(
        EconomyApi.accounts(), playerId, AccountRef.player(recipientId), amount,
        TransactionContext.transfer("team payment", recipientId));
```

Those helpers resolve the actor's current party and role again immediately before the account transfer, avoiding a stale client-side or cached authorization decision.

## Market wallet UI

The Market sync builds a fresh server-authoritative wallet snapshot for the viewing player. `Personal` and `Team` balances are separate values; when a viewable party wallet exists, the UI also shows the FTB team display name, current role, and the configured minimum spend role. A leave, kick, demotion below the view threshold, `PERSONAL_ONLY`, missing provider, or incompatible provider removes the team wallet from the next sync instead of leaving stale client state visible.

The Market principal is also shown explicitly. In `HYBRID`, Personal is the default until the player runs `/economy team use team`. In `TEAM_PRIMARY`, the default is resolved from current FTB membership/rank each time, so joining/leaving/changing teams cannot leave a stale implicit default. An explicit Personal or Team override is retained until `/economy team use default`; a stale explicit Team selection fails closed instead of silently charging Personal.

## Market orders

Orders persist three identities independently:

```text
principal    = AccountRef   // who pays / receives money
actor        = UUID         // player who placed/performed the action
storageOwner = UUID         // player whose Vault/Tank/provider storage is used
```

For current built-in player placement, `actor == storageOwner`. A team-funded order uses `TEAM:<ftb-team-id>` as its principal while keeping the placing player's UUID for physical storage. Team UUIDs are never passed to Vault/Tank/provider owner lookup. Legacy orders migrate to `PLAYER:<old-owner>` with the old owner as both actor and storage owner.

Matching compares economic principals, so two different members cannot make the same team trade with itself. Every team order revalidates current membership/rank before execution, edit, or cancellation. Leave/kick/demotion invalidates the order lazily even if an FTB lifecycle event is missed; Economy attempts a lossless cancellation and keeps any unrecoverable escrow/compensation record persisted until recovery succeeds.

When an FTB party is deleted, its wallet is tombstoned/closed, new team actions are rejected, outstanding team orders are cancelled and escrow is restored to their recorded storage owners, and remaining cash is transferred once to the last recorded FTB owner only after no recovery references remain. Replayed deletion events or restart reconciliation therefore cannot duplicate funds.

## Development dependencies and compatibility checks

All five loader development environments include FTB Teams and FTB Library on their local runtime classpath (client, server, live-join, and GameTest runs). They remain optional production integrations and are not bundled or published as Economy dependencies. Pins live in `gradle/ftb-teams.gradle`:

| Target | FTB Teams | FTB Library |
| --- | --- | --- |
| Forge / Fabric 1.20.1 | 2001.3.2 | 2001.2.13 |
| Fabric / NeoForge 1.21.1 | 2101.1.11 | 2101.1.36 |
| NeoForge 26.1.2 | 26.1.2.4 | 26.1.2.8 |

`./gradlew :common:ftbContractTest` resolves the five published Teams jars and their matching FTB Library jars from [FTB Maven](https://maven.ftb.dev/releases/dev/ftb/mods/). It checks every reflected provider method plus both lifecycle-event generations directly from class files: Architectury `TeamEvent` fields on 1.20.1/1.21.1, and NeoForge 26.x event wrappers/data records together with FTB Library's `BaseEventWithData#getEventData`. It uses a separate source set without local FTB doubles or Minecraft class loading. The same task runs automatically with `:common:test` / `check`, including the shared CI lane. Each target resolves separately so Gradle cannot collapse different Minecraft versions into one jar.

The existing reflection-adapter unit tests use behavioral doubles for membership changes, party filtering, and failures; they do not claim artifact compatibility. When upgrading a development pin, run the artifact contract and the corresponding live-join lane.
