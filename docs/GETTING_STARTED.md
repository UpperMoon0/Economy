# Getting started with Economy addons

Economy exposes a loader-neutral addon API for accounts, orders, market data, events, commodity types, and storage providers. New integrations should use only types under `com.nstut.economy.api` unless this documentation explicitly says otherwise.

The runtime implementation lives behind `EconomyApi`; addon code should not reach into `com.nstut.economy.core`, `trading`, `data`, blocks, menus, networking, or loader internals.

## Supported targets

| Minecraft | Loader | Java | Economy module / artifact |
|---|---|---:|---|
| 1.20.1 | Fabric | 17 | `fabric-1.20.1` / `economy-fabric-1.20.1` |
| 1.20.1 | Forge | 17 | `forge-1.20.1` / `economy-forge-1.20.1` |
| 1.21.1 | Fabric | 21 | `fabric-1.21.1` / `economy-fabric-1.21.1` |
| 1.21.1 | NeoForge | 21 | `neoforge-1.21.1` / `economy-neoforge-1.21.1` |
| 26.1.2 | NeoForge | 25 | `neoforge-26.1.2` / `economy-neoforge-26.1.2` |

Compile against the exact Minecraft/loader artifact you run. Do not compile against one generation and run against another.

## 1. Add Economy to a development workspace

Economy defines Maven publications, but this repository does not currently publish them to a public Maven repository. Until a public repository is documented, use a local composite build or Maven Local.

### Composite build

Keep the repositories next to each other:

```text
projects/
  Economy/
  Your-Addon/
```

In the consuming root `settings.gradle`, substitute the artifact matching your target. For Fabric 1.21.1:

```groovy
includeBuild('../Economy') {
    dependencySubstitution {
        substitute module('com.nstut:economy-fabric-1.21.1') using project(':fabric-1.21.1')
    }
}
```

Then add the dependency normally:

```groovy
dependencies {
    modImplementation "com.nstut:economy-fabric-1.21.1:0.0.11"
}
```

Use the matching loader artifact from the table above. Forge 1.20.1 consumers should wrap the coordinate with `fg.deobf(...)` as usual.

### Maven Local

From Economy, publish the target you need:

```shell
./gradlew :fabric-1.21.1:publishToMavenLocal
```

Then make sure the consuming project has `mavenLocal()` and depend on `com.nstut:<artifact>:<version>`.

End users need the matching Economy jar installed. Economy itself uses OpenUI MC for its client UI, so modpacks must also satisfy Economy's OpenUI dependency.

## 2. Wait for the server runtime

`EconomyApi` is the stable service entry point.

```java
if (!EconomyApi.isReady()) {
    return;
}

IAccountManager accounts = EconomyApi.accounts();
IOrderManager orders = EconomyApi.orders();
IMarketDataService market = EconomyApi.marketData();
```

The runtime services are bound while a server is active and unbound when it stops. Do not cache `accounts()`, `orders()`, `marketData()`, or `serverLevel()` across server restarts. Resolve them from `EconomyApi` when you need them.

The commodity-type and storage-provider registries intentionally survive server restarts:

```java
EconomyApi.commodityTypes();
EconomyApi.storage();
```

Register addon types/providers once during your mod's common initialization, not once per world load.

## 3. Read and create orders

```java
IOrderManager orders = EconomyApi.orders();

List<? extends IOrder> mine = orders.getPlayerOrders(playerId);
Optional<? extends IOrder> order = orders.getOrder(orderId);
```

Submit orders through `IOrderManager`, not through Economy's internal `Order` class:

```java
OrderCreateResult result = EconomyApi.orders().createBuyOrder(
    playerId,
    commodity,
    32,
    new BigDecimal("4.50")
);

if (!result.accepted()) {
    handleRejected(result.errorKey(), result.errorArgs());
}
```

`OrderCreateResult.Status` is one of `POSTED`, `PARTIALLY_FILLED`, `FILLED`, or `REJECTED`. `remainingOrder()` is null when no order remains on the book.

Use `cancelOrder` and `editOrder` on the manager so ownership, escrow, persistence, and market invariants are enforced centrally.

## 4. Read market analytics

Commodity identity is **type + commodity ID**. Never key addon analytics by commodity ID alone: two registered types are allowed to use the same product ID.

```java
CommodityKey iron = new CommodityKey(
    ICommodity.ITEM_TYPE,
    EconomyId.of("minecraft", "iron_ingot")
);

Optional<BigDecimal> lastPrice = EconomyApi.marketData().lastTradePrice(iron);
long volume = EconomyApi.marketData().tradedVolume(iron);
int activeOrders = EconomyApi.marketData().activeOrderCount(iron);
List<TradeView> recent = EconomyApi.marketData().recentTrades(iron, 20);
```

For an `ICommodity` you already have, use:

```java
CommodityKey key = CommodityKey.of(commodity);
```

Prefer `EconomyId`/`CommodityKey` over Minecraft's version-specific identifier classes in persistent addon state and cross-version source.

## 5. Listen for Economy events

Economy has a loader-neutral synchronous event bus. You do not need separate Fabric/Forge/NeoForge adapters for these API events.

```java
EconomyEvents.Subscription subscription = EconomyEvents.listen(
    MarketEvents.TradeCompleted.class,
    event -> onTrade(event.trade())
);
```

Keep the returned subscription when the listener has a shorter lifetime than the mod itself and call `close()` when it should stop receiving events.

Available event families include:

- account balance changes;
- transfers;
- order creation, editing, and cancellation;
- completed trades;
- storage-provider registration changes.

Pre-events such as `BalanceChangePre`, `TransferPre`, and `OrderCreatePre` are cancellable. `OrderCreatePre` is posted before Economy creates a new provider reservation; a cancelled order must not enter the order book or create new escrow. Event delivery is synchronous, so listeners should return quickly and must not perform blocking work on the server thread.

## 6. Use transaction context for money movement

Account transfers accept an `ITransactionContext`. New addons should provide a non-null context with a namespaced cause such as `myaddon:salary`, rather than relying on the deprecated legacy transaction enum.

```java
boolean moved = EconomyApi.accounts().transfer(
    sourceAccount,
    targetAccount,
    amount,
    context
);
```

The transfer contract is atomic: a rejected or failing target credit must not leave the source debited.

See [Extending Economy](EXTENDING_ECONOMY.md) for custom transaction causes, commodity codecs, storage providers, persistence rules, and lifecycle requirements.

## 7. Verify an addon

Before publishing an addon:

- run it against the exact Minecraft/loader pair it declares;
- exercise server stop/start without retaining stale runtime services;
- verify event listeners do not leak across reloads;
- verify custom commodity codecs round-trip saved state;
- verify analytics use `CommodityKey`, especially when two types can share a commodity ID;
- verify storage simulation is side-effect free;
- verify reservations survive save/reload without flattening provider state into one large string;
- verify partial delivery returns the exact provider-owned remainder;
- verify failed release leaves both storage and reservation ownership unchanged;
- verify successful release restores every remaining unit exactly once;
- test order rejection, pre-event cancellation, successful fills, partial fills, and cancellation.

For the complete public surface, see [API Reference](API_REFERENCE.md). For custom commodity/storage implementations and escrow invariants, continue with [Extending Economy](EXTENDING_ECONOMY.md).
