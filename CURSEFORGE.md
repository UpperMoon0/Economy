# NsTut Economy

**NsTut Economy** adds a player-driven economy, market, connected storage, portfolio tracking, and optional shared Team wallets across supported Fabric, Forge, and NeoForge targets.

Current source targets include Minecraft 1.20.1 (Fabric/Forge), 1.21.1 (Fabric/NeoForge), and 26.1.2 (NeoForge). Install the Economy file that matches your exact Minecraft version and loader.

## Required dependency

Economy requires **OpenUI MC** on the client. Install the matching OpenUI MC version required by your Economy build.

---

## Market Terminal

The Market Terminal brings trading and account management together in one screen:

- Browse active items and fluids with current market prices.
- Create, edit, cancel, and accept Buy/Sell Orders.
- Review trade history and price charts.
- Track cash, stored assets, and historical portfolio value.
- Filter orders/products and switch compact Grid/Row layouts.
- Use the account switcher to choose the **Personal** or authorized **Team** principal used by new orders, Containers, Portfolio, and Pay Player.
- See server-side action results through consistent toasts instead of silent failures.

Open the Market Terminal through a Market block or `/economy balance`.

## Player-Driven Trading

Players choose the commodity, quantity, and price per unit. Compatible Buy and Sell Orders match automatically. Sell Orders reserve goods from the selected principal's connected storage; purchases are delivered back into storage with free capacity.

Exact item variants with NBT/data components are distinct market commodities when their metadata differs, so pricing, holdings, and portfolio history use the same variant identity.

Server administrators can also create server-managed orders to provide market demand or supply.

---

## Vault Item Storage

Vaults connect stored items directly to the market.

- Each Vault stores up to 54 stacks.
- Multiple Vaults owned by the same principal work together as market storage.
- Sell Orders can reserve items from Vaults; purchases can be delivered into Vaults.
- Vaults can be owned by either a Personal account or a Team account.
- Market I/O mode can be configured per Vault.

## Fluid Tank Storage

Fluid Tanks provide market-connected fluid storage.

- `tankCapacity` in `config/economy-storage.properties` controls the capacity of newly created Tanks; the default is `128,000 mB` and persisted Tanks keep their saved capacity.
- A Tank holds one compatible fluid at a time.
- Tanks have distinct **Input** and **Output** container slots. Compatible filled/empty containers are consumed from Input and their resulting containers accumulate in Output.
- Compatible pipes/automation can use the loader's standard fluid API when external automation is enabled.
- Fluid Sell Orders draw from Tanks and bought fluids can be delivered into Tanks with free capacity.
- Tanks can be Personal- or Team-owned and support the same market I/O modes as Vaults.

Fluid quantities are shown in millibuckets (`mB`), with compact values such as `1k mB` and `128k mB`.

## Storage Modes

Every Vault and Tank has a market mode:

- `BOTH` - supplies Sell Orders and receives purchases.
- `INPUT ONLY` - supplies Sell Orders but does not receive purchases.
- `OUTPUT ONLY` - receives purchases but does not supply Sell Orders.

These modes affect market participation only. Direct compatible inventory/fluid automation is independent of market mode.

## Containers

The **Containers** tab follows the currently selected Personal/Team Market principal and lists that principal's registered Vaults and Tanks. Cards show location, owner, market mode, status, and capacity/content information.

Authorized Team administrators can reassign storage ownership between themselves and the current Team and can change Team storage I/O mode. Permission is revalidated server-side for every change.

---

## Optional Team Wallets

Economy supports shared Team principals. With **FTB Teams** installed, party teams are supported automatically; Personal and server teams are not turned into duplicate Economy wallets. Addons may register a custom `TeamEconomyProvider`; a custom provider takes precedence over the built-in FTB Teams fallback.

Team economy is enabled by default when a usable provider exists and can be disabled with `enabled=false` in `config/economy-team.properties`.

- The Market account switcher selects Personal or Team for new market activity.
- **Team Treasury** shows Personal/Team balances and accepts Personal -> Team deposits. There is no ordinary Team -> Personal withdrawal.
- **Pay Player** can send Team funds to another player when the actor has payout permission; Team self-payment is rejected.
- Default roles: MEMBER can view/deposit, OFFICER can perform Team market spending, OWNER can make direct Team payouts and administer Team storage.
- Team membership/rank is rechecked before protected actions.
- On Team deletion, Economy closes/reconciles outstanding Team orders first, then splits remaining Team cash equally across the last authoritative member snapshot. Physical Team Vault/Tank custody moves to the last recorded owner only after cash settlement completes.

See `docs/TEAM_ECONOMY.md` in the repository for provider, permission, migration, and lifecycle details.

---

## Portfolio and Price History

- View current cash, stored assets, and total net worth for the selected principal.
- See item/fluid holdings from connected Vaults and Tanks.
- Follow historical price and portfolio charts.
- Review completed purchases and sales in Trade History.
- Exact item variants retain their own identity and historical valuation rather than collapsing to the base item.

## Coins and Player Payments

- Send Personal coins directly to another player.
- When Team mode is selected and payout permission is available, Pay Player can send from the Team wallet instead.
- Payments and completed trades provide user-facing notifications.
- Large values use compact formatting such as `1k`, `2.5m`, `1b`, and `1t`.

---

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/economy balance` | Player | Legacy shorthand for your Personal balance / Market entry |
| `/economy balance <personal|team> [player]` | Player / OP for another player | View an explicit Personal or current-Team balance |
| `/economy pay <player> <amount>` | Player | Legacy Personal-payment shorthand |
| `/economy pay personal <player> <amount>` | Player | Pay from your Personal account |
| `/economy pay team <player> <amount>` | Player | Pay another player from your Team account; requires payout permission and rejects self-payment |
| `/economy team [balance]` | Player | View Personal/Team balances, current role, and Market wallet selection |
| `/economy team deposit <amount>` | Player | Deposit Personal funds into the current Team wallet |
| `/economy team pay <player> <amount>` | Player | Direct Team payout to another player; OWNER by default |
| `/economy team use <personal|team|default>` | Player | Select the wallet used by new market actions or restore policy default |
| `/economy serverorder buy <commodity> <qty> <price>` | OP Level 2 | Create a server Buy Order |
| `/economy serverorder sell <commodity> <qty> <price>` | OP Level 2 | Create a server Sell Order |
| `/economy serverorder list` | OP Level 2 | List active server orders and IDs |
| `/economy serverorder remove <order-id>` | OP Level 2 | Remove a server order |
| `/economy give <player> <amount>` | OP Level 2 | Legacy Personal admin shorthand |
| `/economy give <personal|team> <player> <amount>` | OP Level 2 | Add funds to the explicit principal |
| `/economy take <player> <amount>` | OP Level 2 | Legacy Personal admin shorthand |
| `/economy take <personal|team> <player> <amount>` | OP Level 2 | Remove funds from the explicit principal |
| `/economy set <player> <amount>` | OP Level 2 | Legacy Personal admin shorthand |
| `/economy set <personal|team> <player> <amount>` | OP Level 2 | Set the explicit principal balance |

For fluid server orders, quantity is entered in `mB`; for example, `16000` represents `16k mB`.

---

## Addon developers

Economy exposes a supported addon API for typed Personal/Team accounts, atomic transfers, Team providers and authorization, orders, market analytics, events, custom commodity codecs, and pluggable storage providers.

The compatibility boundary is the top-level `com.nstut.economy.api` package. `com.nstut.economy.api.internal` and other implementation packages are not supported addon dependencies. Developer documentation is maintained in `docs/GETTING_STARTED.md`, `docs/EXTENDING_ECONOMY.md`, `docs/API_REFERENCE.md`, and `docs/TEAM_ECONOMY.md`.
