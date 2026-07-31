# NsTut Economy

**NsTut Economy** adds a complete player economy and trading market to Minecraft 1.20.1 Forge.

Buy and sell items or fluids through an in-game Market Terminal, build storage that connects directly to the market, follow changing prices, manage your orders, track your wealth, and send coins to other players.

---

## Market Terminal

The Market Terminal brings the economy together in one screen:

- Browse products and current prices.
- Trade both items and fluids.
- Create Buy and Sell Orders.
- View available Buy and Sell offers.
- Edit or cancel your active orders.
- Review your trade history.
- Follow price changes and trading activity.
- Track your balance, stored assets, and total portfolio value.
- Filter products and orders by items or fluids.
- Switch between compact Grid and Row layouts.

Open the Market Terminal by using a Market block or running `/economy balance`.

---

## Player-Driven Trading

Players choose what to trade, how much to offer, and their price per unit.

The market automatically matches compatible Buy and Sell Orders. Sell Orders use goods from your connected storage, while purchases are delivered back into storage with available space.

Market activity determines the prices shown throughout the terminal, including product cards, order books, charts, and portfolios.

Server administrators can also create server-managed Buy and Sell Orders to provide reliable market demand or supply.

---

## Vault Item Storage

Vaults connect your stored items to the market.

- Each Vault stores up to 54 stacks.
- Sell Orders can draw items from connected Vaults.
- Bought items can be delivered directly into Vaults.
- The custom Vault screen uses a compact 18×3 layout.
- Multiple Vaults can work together as one storage network.

---

## Fluid Tank Storage

Fluid Tanks provide market-connected storage for fluids.

- Each Tank stores up to `128,000 mB`.
- A Tank holds one compatible fluid at a time.
- Use buckets, cells, and other compatible fluid containers in the Tank slot.
- Fluid Sell Orders draw from connected Tanks.
- Bought fluids are delivered into Tanks with compatible free space.
- The stored fluid is visible in both the Tank screen and on the front of the block.

Fluid quantities are always shown in millibuckets (`mB`), with compact values such as `1k mB` and `128k mB`.

---

## Storage Modes

Every Vault and Fluid Tank can be assigned a market mode:

- `BOTH` — supplies your Sell Orders and receives your purchases.
- `INPUT ONLY` — supplies Sell Orders but does not receive purchases.
- `OUTPUT ONLY` — receives purchases but does not supply Sell Orders.

Use these modes to control exactly how each storage block participates in your market network.

---

## Containers Overview

The `Containers` tab shows all of your registered Vaults and Fluid Tanks in one place.

View each container’s:

- Type and location
- Current market mode
- Used and maximum capacity
- Stored item or fluid totals
- Current Tank contents

---

## Portfolio and Price History

The market tracks more than your coin balance:

- View your current cash, stored assets, and total net worth.
- See items and fluids held in your Vaults and Tanks.
- Follow historical price and portfolio charts.
- Scroll through older chart data or return directly to the latest value.
- Review completed purchases and sales in Trade History.

---

## Coins and Player Payments

- Send coins directly to another player.
- Receive clear chat notifications for payments and completed trades.
- Economy messages use the mod’s Coin icon.
- Payments play a custom “Ka-Ching” sound for both players.
- Large values use compact formatting such as `1k`, `2.5m`, `1b`, and `1t`.

---

## Commands

- `/economy balance` — View your balance and open the Market Terminal.
- `/economy pay <player> <amount>` — Send coins to another player.
- `/economy serverorder buy <commodity> <qty> <price>` — Create a server Buy Order for an item or fluid.
- `/economy serverorder sell <commodity> <qty> <price>` — Create a server Sell Order for an item or fluid.
- `/economy give <player> <amount>` — Give coins to a player.
- `/economy take <player> <amount>` — Take coins from a player.
- `/economy set <player> <amount>` — Set a player’s balance.

Balance and payment commands are available to players. Server-order and balance-management commands require operator permission.

For fluid server orders, quantity is entered in millibuckets. For example, `16000` represents `16k mB`.
