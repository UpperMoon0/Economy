# NsTut Economy

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Forge Version](https://img.shields.io/badge/Forge-47.1.0%2B-orange)
![Version](https://img.shields.io/badge/Version-0.0.3-blue)
![License](https://img.shields.io/badge/License-MIT-green)

A modern, high-performance Minecraft economy and order-book market system for Minecraft 1.20.1 (Forge).

---

## Key Features

### Order Book Market Terminal UI
- **Full-Featured Graphical GUI**: Accessible via the Market Block terminal or `/economy balance`.
- **Unified Global Pricing**: Shows the true global price (cheapest active sell offer, highest bid, or last trade price) across all catalog cards and product detail views.
- **Price Trend & Portfolio Charts**: Interactive line graphs with min/max scale, live dotted guide lines, and right-aligned current value badges.
- **Compact Number Formatting**: Automatically formats large numbers with human-readable suffixes (k for thousands, m for millions, b for billions, t for trillions, up to 2 decimals, e.g. `14.76 k`).
- **Price Change Indicators**: Shows percentage price changes from the last distinct price (+400.00% in Green, -15.50% in Red, or No Change in Gray).
- **Autocomplete Product Search**: Fast search box with live item icons and a solid, opaque dropdown that sits above other elements to prevent text overlap.
- **Order Management & Safety**: Order list with compact fill percentage tracking (`(90% filled)`) and confirmation overlays.
- **Wrapped Multi-Line Tooltips**: All tooltips are bounded to a fixed maximum width to ensure readable, multi-line explanations without horizontal screen stretching.

### Vault Storage Logistics & Custom UI
- **Custom Dark Theme Container UI**: A sleek dark container screen replacing the vanilla chest texture.
- **Wide Compact 18x3 Grid Layout**: Redesigned 54-slot Vault storage into a wider layout for reduced screen height and zero slot text overlaps.
- **Vault Mode Configuration**:
  - **`[ BOTH ]` (Default)**: Used for extracting items for Sell Orders AND receiving bought items.
  - **`[ INPUT ONLY ]`**: Items stored inside are ONLY extracted for Sell Orders; bought items will NOT be deposited here.
  - **`[ OUTPUT ONLY ]`**: Bought items are deposited here; items inside are NOT detected for Sell Orders.
- **Vault Overview Dashboard**: Real-time listing of registered vaults, slot capacity metrics (`21/108`), item counts, dimension, and block coordinates.

### Chat Notifications & Auditory Feedback
- **Order Matching Chat Notifications**: Sends clear system chat alerts to both buyer and seller upon trade execution:
  - *Buyer*: `[Market] Order Matched! Bought 10x Iron Ingot for 100 coins.`
  - *Seller*: `[Market] Order Matched! Sold 10x Iron Ingot for 100 coins.`
- **Payment Notifications**: Sends formatted in-chat payment messages and plays the custom "Ka-Ching" money sound effect to both the paying sender and receiving player.

---

## Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/economy balance` | Player | View your current balance & open Market Terminal UI |
| `/economy balance <player>` | OP Level 2 | View another player's balance |
| `/economy pay <player> <amount>` | Player | Pay money to another player (with sound & chat notification) |
| `/economy serverorder buy <item> <qty> <price>` | OP Level 2 | Create an infinite server buy order |
| `/economy serverorder sell <item> <qty> <price>` | OP Level 2 | Create an infinite server sell order |
| `/economy give <player> <amount>` | OP Level 2 | Add funds to a player's account |
| `/economy take <player> <amount>` | OP Level 2 | Remove funds from a player's account |
| `/economy set <player> <amount>` | OP Level 2 | Set a player's balance directly |

---

## Building & Testing

```bash
# Run automated test suite
./gradlew :forge-1.20.1:test

# Build production JAR
./gradlew :forge-1.20.1:build
```

The compiled JAR file will be located under `forge-1.20.1/build/libs/`.

---

## License

Distributed under the MIT License. Created by **NsTut**.
