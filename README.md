# Global Economy & Market Mod

A modern, high-performance Minecraft economy and order-book market system for Minecraft 1.20.1 (Forge).

---

## Key Features

- **Order Book Market Terminal UI**:
  - Full-featured graphical GUI (`/economy balance`, open via Market Block / Command).
  - Real-time product directory with guided autocomplete item search.
  - Price trends chart with live price badges.
  - Order columns (Sell Orders & Buy Orders) separated by visual dividers.
  - Interactive **Filter** (`All` / `Active Only`) and **Sort** (`Price ▲`, `Price ▼`, `Name A-Z`, `Most Active`).
- **Trade Order History**:
  - Detailed personal transaction log with item name, quantity, unit price, date/time, and counterparty.
  - Dedicated **History Filter** (`All Trades`, `Sales Only`, `Purchases Only`) and **Sort** (`Newest`, `Oldest`, `Highest Total $`).
  - Search bar to filter transaction history by **Item Name** or **Player Username**.
- **Vault Physical Storage Integration**:
  - Vault blocks serve as 54-slot physical item storage linked to market trading.
  - Automated item reservation on sell order creation.
  - Automated item delivery directly into buyer vaults upon trade execution.
- **Financial Transactions & Sounds**:
  - Instant player-to-player payments via `/economy pay <player> <amount>`.
  - Auditory "Ka-Ching" sound effect (`economy:money`) played on trade completion and money transfers.
  - Custom Coin currency item (`economy:coin`).

---

## Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/economy balance` | Player | View your current balance |
| `/economy balance <player>` | OP Level 2 | View another player's balance |
| `/economy pay <player> <amount>` | Player | Pay money to another player (or test pay yourself) |
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