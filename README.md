# NsTut Economy

![Minecraft](https://img.shields.io/badge/Minecraft-multi--version-brightgreen)
![Loaders](https://img.shields.io/badge/loaders-Fabric%20%7C%20Forge%20%7C%20NeoForge-orange)
![License](https://img.shields.io/badge/License-MIT-green)

A modern multi-loader order-book economy for Minecraft, with physical item and fluid storage, automated order matching, portfolio tracking, and a full in-game market terminal.

## Dependency

The client UI requires [OpenUI MC](https://github.com/UpperMoon0/OpenUI-MC), which must be installed alongside Economy. Economy owns the market-specific screens and logic; reusable layout, widget, rendering, scrolling, and animation primitives live in OpenUI MC. Use the dependency version declared by the Economy jar or its release page.

---

## Key Features

### Unified Item and Fluid Market

- Trade both item and fluid commodities through the same market, order book, history, and server-order systems.
- Fluid quantities are consistently displayed in millibuckets (`mB`), including compact values such as `1k mB` and `128k mB`.
- Sell orders reserve their goods when created. Item orders draw from eligible Vaults, while fluid orders draw from eligible Tanks.
- Buy orders verify that the buyer has enough compatible destination space before a trade completes.
- Cancelling a sell order restores its remaining reserved items or fluids to the owner's storage network.
- Only canonical, container-compatible fluid variants are listed; flowing variants are excluded.

### Market Terminal UI

- Open the terminal through the Market block or `/economy balance`.
- Browse active and previously traded commodities without filling the catalog with products that have never had market activity.
- Filter Browse by activity and product type, and filter Active Orders and Trade History by order/trade and product type.
- Compact two-line controls keep `Activity`, `Order`/`Trade`, `Product`, and `Sort` filters on one row.
- Browse defaults to a compact grid view, with a persistent Grid/Rows toggle saved in the client configuration.
- Row view keeps the order count right-aligned; grid view places it below the price for compact cards.
- Long order-book entries use a clipped ping-pong marquee, moving the coin icon and text together without overflowing the viewport.
- Product details, charts, active orders, history, portfolio holdings, and container statistics all understand fluid commodities.
- Interactive price and portfolio charts include history scrolling, timestamp tooltips, and a `▶ LIVE` action.
- Large money, item, fluid, order, and container counts share compact `k`, `m`, `b`, and `t` formatting.

### Vaults, Tanks, and Container Overview

#### Vault

- A 54-slot physical item store connected to the owner's market account.
- Uses a compact custom 18×3 inventory screen.
- Supports three market modes:
  - `BOTH`: supplies sell orders and receives purchases.
  - `INPUT ONLY`: supplies sell orders but does not receive purchases.
  - `OUTPUT ONLY`: receives purchases but does not supply sell orders.

#### Fluid Tank

- Stores up to `128,000 mB` of one compatible fluid.
- Accepts loader-compatible fluid containers such as buckets and cells through its processing slot.
- Exposes the loader's standard fluid API for compatible pipes and automation.
- Uses a centered custom screen with a tiled/cropped fluid texture and a single `current / maximum` amount display.
- Renders its fluid on the Tank's front face using the fluid's true texture proportions.
- Supports the same `BOTH`, `INPUT ONLY`, and `OUTPUT ONLY` market behavior as Vaults.
- Storage modes govern market supply and delivery; direct inventory and fluid automation remains available independently.
- Includes its block/item model, texture, recipe, loot table, mining tags, and creative-tab entry.

#### Containers Tab

- The former `Vaults` tab is now `Containers`.
- Shows registered Vaults and Tanks together, including location, mode, capacity, stored item/fluid totals, and current contents.

### Currency Feedback

- Market rows and terminal balances use the actual Coin item texture.
- Economy command responses use a custom inline coin glyph instead of a generic text symbol.
- Payments and matched orders provide formatted chat notifications and the custom “Ka-Ching” sound.

---

## Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/economy balance` | Player | View your balance and open the Market Terminal |
| `/economy balance <player>` | OP Level 2 | View another player's balance |
| `/economy pay <player> <amount>` | Player | Transfer coins to another player |
| `/economy serverorder buy <commodity> <qty> <price>` | OP Level 2 | Create a server buy order for an item or fluid |
| `/economy serverorder sell <commodity> <qty> <price>` | OP Level 2 | Create a server sell order for an item or fluid |
| `/economy serverorder list` | OP Level 2 | List active server orders and their IDs |
| `/economy serverorder remove <order-id>` | OP Level 2 | Remove a server order by ID; active IDs are tab-completed |
| `/economy give <player> <amount>` | OP Level 2 | Add funds to a player's account |
| `/economy take <player> <amount>` | OP Level 2 | Remove funds from a player's account |
| `/economy set <player> <amount>` | OP Level 2 | Set a player's balance |

New server-order creation responses include the order ID. If an order was created by mistake, run `/economy serverorder list`, then `/economy serverorder remove <order-id>`.

For fluid commodities, quantity is entered in `mB`. For example:

```text
/economy serverorder sell minecraft:water 16000 2
```

This creates a server sell order for `16k mB` of water at 2 coins per mB.

---

## Addon / Developer API

Economy exposes a supported addon API under `com.nstut.economy.api`. Addons should enter through `EconomyApi` instead of depending on concrete managers, saved-data classes, blocks, menus, packets, or other implementation packages.

The public API supports:

- player, server, and tax accounts with atomic transfers;
- namespaced transaction causes, metadata, and loader-neutral account events;
- order creation/query/edit/cancel operations;
- immutable market/trade analytics;
- custom namespaced commodity types with versioned persistence codecs;
- pluggable durable storage providers and reservations;
- loader-neutral market events for order and trade integrations.

Developer documentation:

- [Getting Started](docs/GETTING_STARTED.md) — dependency setup, runtime lifecycle, orders, market reads, events, and addon verification.
- [Extending Economy](docs/EXTENDING_ECONOMY.md) — custom transaction causes, commodities/codecs, storage providers, persistence rules, and compatibility guidance.
- [API Reference](docs/API_REFERENCE.md) — practical catalog of the supported public classes and methods.

Economy currently defines loader-specific Maven publications but does not configure a public Maven repository. The Getting Started guide documents composite-build and Maven Local development until a public repository is officially available.

---

## Building and Testing

```bash
# Run every shared and version-specific test suite
./gradlew testAllVersions

# Build every supported loader target
./gradlew buildAll
```

Each loader module writes its compiled JAR to its own `build/libs/` directory.

---

## License

Distributed under the MIT License. Created by **NsTut**.