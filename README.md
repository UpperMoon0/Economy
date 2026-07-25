# Global Economy & Market Mod

A modern, high-performance Minecraft economy and order-book market system for Minecraft 1.20.1 (Forge).

---

## Key Features

- **Order Book Market Terminal UI**:
  - Full-featured graphical GUI (open via Market Block or `/economy balance`).
  - Real-time product directory with autocomplete item search and live item icons.
  - Price history chart with min/max Y-axis scale and interactive data node tooltips (`Price: $X | Volume: Y`).
  - Order columns (Sell Orders & Buy Orders) with partial fill tracking (`50/100 - 50% filled`).
  - Confirmation modal overlay to prevent accidental trade misclicks.
  - Interactive **Filter** (`All` / `Active Only`) and **Sort** (`Price ▲`, `Price ▼`, `Name A-Z`, `Most Active`).
- **Vault Overview & Physical Logistics**:
  - Craft Vault blocks using **4 Diamonds**, **4 Iron Ingots**, and **1 Chest**.
  - Vault blocks serve as 54-slot physical item storage linked to market trading.
  - **Vault Overview Dashboard**: View total registered vaults, slot capacity metrics, item counts, and block coordinates in real time.
  - Automated item reservation on sell order creation and automated delivery into buyer vaults upon trade execution.
- **Trade Order History**:
  - Detailed personal transaction log with item name, quantity, unit price, date/time, and counterparty.
  - Dedicated **History Filter** (`All Trades`, `Sales Only`, `Purchases Only`) and **Sort** (`Newest`, `Oldest`, `Highest Total $`).
  - Search bar to filter transaction history by **Item Name** or **Player Username**.
- **Financial Transactions & Sounds**:
  - Instant player-to-player payments via `/economy pay <player> <amount>`.
  - Auditory "Ka-Ching" sound effect (`economy:money`) played on trade completion and money transfers.

---

## Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/economy balance` | Player | View your current balance |
| `/economy balance <player>` | OP Level 2 | View another player's balance |
| `/economy pay <player> <amount>` | Player | Pay money to another player |
| `/economy vault` | Player | View your registered Vault coordinates |
| `/economy serverorder buy <item> <qty> <price>` | OP Level 2 | Create an infinite server buy order |
| `/economy serverorder sell <item> <qty> <price>` | OP Level 2 | Create an infinite server sell order |
| `/economy give <player> <amount>` | OP Level 2 | Add funds to a player's account |
| `/economy take <player> <amount>` | OP Level 2 | Remove funds from a player's account |
| `/economy set <player> <amount>` | OP Level 2 | Set a player's balance directly |

---

## Building from Source

```bash
./gradlew build
```

The compiled JAR file will be located under `forge-1.20.1/build/libs/`.

---

## License

Distributed under the MIT License. Created by **NsTut**.