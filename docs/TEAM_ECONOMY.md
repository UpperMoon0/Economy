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

## Market orders

This foundation deliberately does **not** reinterpret the existing `IOrder.owner` UUID as a team UUID. Team-funded market orders require separate economic principal, acting player, and physical storage owner identities; that migration is tracked by issue #29.

Until #29 is implemented, normal market orders continue to use personal player principals and player-owned Vault/Tank storage.
