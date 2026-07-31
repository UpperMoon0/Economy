# NsTut Economy (v0.0.6)

**NsTut Economy** is an order-book economy mod for Minecraft 1.20.1 (Forge). Trade items and fluids through a full market terminal backed by physical Vault and Tank storage, automated order matching, price history, portfolio tracking, and server-managed liquidity.

---

## Major Highlights in v0.0.6

### Fluid Trading and the New Fluid Tank

- Items and fluids now use one unified commodity, order, history, and server-order system.
- The new **Fluid Tank** stores `128,000 mB` and works with fluid containers such as buckets and cells.
- Tanks support `BOTH`, `INPUT ONLY`, and `OUTPUT ONLY` market modes, matching Vault behavior.
- Fluid sell orders reserve fluid from eligible Tanks. Purchases require enough compatible Tank space before matching.
- Cancelling a fluid sell order restores its remaining reserved fluid.
- Fluid orders persist across world reloads, including their commodity type and reserved fluid.
- Only source/container-compatible fluids are shown; duplicate flowing variants are hidden.

### Market Terminal Improvements

- Browse now supports a persistent **Grid/Rows** view toggle, with Grid as the default.
- Browse, Active Orders, and Trade History include product-type filters for Items and Fluids.
- Compact two-line filter buttons use clear labels such as `Activity`, `Order`, `Trade`, `Product`, and `Sort`.
- The Browse catalog only shows active or previously traded commodities instead of unused registry entries.
- The former `Vaults` page is now **Containers**, combining Vault and Tank status in one dashboard.
- Long order text is clipped inside its card and uses a left-to-right ping-pong marquee; the Coin icon moves with it.
- Fluid icons, quantities, stock, chart volume, order details, history, and portfolio holdings are displayed correctly throughout the terminal.
- Large money and commodity counts share compact suffixes without extra spacing, such as `1k`, `2.5m`, and `128k mB`.

### Tank Rendering and Interaction Fixes

- Fluid is rendered on the Tank's front face with corrected depth and pixel alignment.
- Fluid textures tile/crop instead of stretching, preserving their native proportions.
- The Tank screen is vertically centered and shows one `current / maximum` amount line.
- Bucket/container transfers now update the authoritative Tank amount and synchronize the resulting container and fluid state.
- Added detailed Tank transfer logging for diagnosing modded fluid-container behavior.
- Added the Tank recipe, loot table, block/item models, texture, mining tags, language entries, and creative-tab registration.

### Currency Presentation and Reliability

- Economy command messages now use the actual custom Coin glyph instead of a generic currency character.
- Market marquees move the Coin icon and associated price text as one unit.
- Fluid and item availability, destination capacity, cancellation restoration, and order persistence received expanded automated test coverage.

---

## Storage Modes

- `BOTH` — supplies Sell Orders and receives purchases.
- `INPUT ONLY` — supplies Sell Orders but does not receive purchases.
- `OUTPUT ONLY` — receives purchases but does not supply Sell Orders.

These modes apply independently to Vault item storage and Tank fluid storage.

---

## Commands

- `/economy balance` — View your balance and open the Market Terminal.
- `/economy pay <player> <amount>` — Transfer coins to another player.
- `/economy serverorder buy <commodity> <qty> <price>` — Create an infinite server buy order for an item or fluid.
- `/economy serverorder sell <commodity> <qty> <price>` — Create an infinite server sell order for an item or fluid.
- `/economy give <player> <amount>` — Give coins to a player.
- `/economy take <player> <amount>` — Take coins from a player.
- `/economy set <player> <amount>` — Set a player's balance.

Fluid command quantities use millibuckets (`mB`), so a quantity of `16000` is displayed as `16k mB`.
