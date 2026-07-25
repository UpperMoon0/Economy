# NsTut Economy

Welcome to **NsTut Economy**, a Minecraft economy mod featuring an order-book market terminal UI, physical Vault storage integration, price trend charting, and sound-effect enabled transactions.

---

## Major Highlights

### Order Book Market Terminal
- **Sleek Custom Dark UI**: Designed with a curated dark mode palette, dynamic hover animations, and a custom UI framework.
- **Unified Global Price**: Uses shared price resolution so catalog cards, order books, and price trend charts always show the exact same global price (cheapest active sell offer).
- **Interactive Price History Charts**: Price charts feature min/max scaling, dotted guide lines, interactive data nodes, and right-aligned current value badges.
- **Price Change Percentage**: Highlights price movements from the last distinct price (+400.00% in Green, -15.50% in Red, or No Change in Gray).
- **Human-Readable Compact Numbers**: Formats large currency amounts and item quantities with compact suffixes (k for thousands, m for millions, b for billions, t for trillions).

### Vault Storage System & Mode Configuration
- **Physical Block Storage**: Craft Vault blocks (4 Diamonds, 4 Iron Ingots, 1 Chest) to serve as 54-slot item storage linked to market trading.
- **Custom Wide Container UI**: Replaces vanilla chest screens with a wider 18x3 compact layout that eliminates text overlap and minimizes screen height.
- **Vault Modes**:
  - `[ BOTH ]` *(Default)*: Used for both Sell Order item extraction and receiving bought items.
  - `[ INPUT ONLY ]`: Items stored inside are ONLY extracted for Sell Orders; bought items will NOT enter this Vault.
  - `[ OUTPUT ONLY ]`: Bought items are deposited here; items inside are NOT detected for Sell Orders.

### Real-time Notifications & Auditory Feedback
- **In-Chat Trade Notifications**: Immediate chat notifications when an order matches (`[Market] Order Matched! Bought 10x Iron Ingot for 100 coins.`).
- **Payment Notifications**: System chat messages and custom "Ka-Ching" sound effects played for both payer and receiver during `/economy pay` transactions.
- **Bounded Tooltips**: Tooltips wrap cleanly within a fixed maximum width to ensure clear descriptions without screen stretching.

---

## Commands

- `/economy balance` - Open your Market Terminal UI or view balance
- `/economy pay <player> <amount>` - Transfer money to another player with chat alert & sound
- `/economy serverorder buy <item> <qty> <price>` - OP command for infinite buy orders
- `/economy serverorder sell <item> <qty> <price>` - OP command for infinite sell orders
- `/economy give <player> <amount>` - OP command to give coins to a player
- `/economy take <player> <amount>` - OP command to take coins from a player
- `/economy set <player> <amount>` - OP command to set a player's balance
