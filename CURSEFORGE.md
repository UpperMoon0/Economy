# NsTut Economy (v0.0.4)

Welcome to **NsTut Economy**, a Minecraft economy mod featuring an order-book market terminal UI, physical Vault storage integration, price trend & portfolio charting, and sound-effect enabled transactions.

---

## Major Highlights (v0.0.4)

### Order Book Market Terminal & Order Editing
- **Interactive Order Editing (`EDIT ORDER`)**: Edit active orders on the fly with real-time text input fields for Quantity and Price, focused cursor support, and Infinite Buy Order toggle (`[ ∞ ]`).
- **Interactive Line Charts & Snap-to-Live**: Charts feature 15-step windowing, mouse-wheel history scrolling, real timestamps (`MM/dd HH:mm:ss`), and an interactive green **`▶ LIVE`** button to snap back to current live time.
- **Smart Portfolio History**: Tracks net worth, liquid cash, and vault assets with smart deduplication to prevent snapshot flooding.
- **Unified Global Price**: Catalog cards, order books, and charts display the exact same global price across all views.
- **Counterparty Trade Alerts**: In-chat trade notifications disclose counterparty player/server names and total cost (`Bought 10x Iron Ingot for 5 coins each (Total: 50 coins) from Steve.`).
- **Compact Numbers & Bounded Tooltips**: Formats values with human-readable suffixes (`k`, `m`, `b`, `t`) and wraps tooltips within 150px max width with `\n` line breaks.

### Vault Storage Logistics & Mode Configuration
- **Physical Block Storage**: Craft Vault blocks (4 Diamonds, 4 Iron Ingots, 1 Chest) to serve as 54-slot item storage linked to market trading.
- **Custom Wide Container UI**: Replaces vanilla chest screens with a wider 18x3 compact layout that eliminates text overlap and minimizes screen height.
- **Vault Modes**:
  - `[ BOTH ]` *(Default)*: Used for both Sell Order item extraction and receiving bought items.
  - `[ INPUT ONLY ]`: Items stored inside are ONLY extracted for Sell Orders; bought items will NOT enter this Vault.
  - `[ OUTPUT ONLY ]`: Bought items are deposited here; items inside are NOT detected for Sell Orders.

---

## Commands

- `/economy balance` - Open your Market Terminal UI or view balance
- `/economy pay <player> <amount>` - Transfer money to another player with chat alert & sound
- `/economy serverorder buy <item> <qty> <price>` - OP command for infinite buy orders
- `/economy serverorder sell <item> <qty> <price>` - OP command for infinite sell orders
- `/economy give <player> <amount>` - OP command to give coins to a player
- `/economy take <player> <amount>` - OP command to take coins from a player
- `/economy set <player> <amount>` - OP command to set a player's balance
