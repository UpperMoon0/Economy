# Economy API reference

This is a practical catalog of the supported addon-facing API. The canonical signatures remain the Java sources for the selected Economy version. Unless documented otherwise, only `com.nstut.economy.api` is covered by the compatibility policy.

Read [Getting Started](GETTING_STARTED.md) for integration setup and [Extending Economy](EXTENDING_ECONOMY.md) for extension contracts and persistence rules.

## Service entry point

### `EconomyApi`

Stable static facade for runtime services and extension registries.

- `boolean isReady()` — whether a running server has bound all runtime services.
- `IAccountManager accounts()` — active account service; throws if Economy is not ready.
- `IOrderManager orders()` — active order-book service; throws if Economy is not ready.
- `IMarketDataService marketData()` — active read-only market analytics service; throws if Economy is not ready.
- `CommodityTypeRegistry commodityTypes()` — process-level registry for commodity type handlers/codecs.
- `StorageProviderRegistry storage()` — process-level registry for market storage providers.
- `Optional<ServerLevel> serverLevel()` — currently bound server overworld, when available.

`bindRuntime` and `unbindRuntime` are internal lifecycle hooks even though they are public Java methods. Addons must not call them.

## Identifiers

### `EconomyId`

Minecraft-version-neutral namespaced identifier.

```java
EconomyId id = EconomyId.of("myaddon", "salary");
EconomyId parsed = EconomyId.parse("minecraft:iron_ingot");
```

- `of(namespace, path)` — constructs a validated ID.
- `parse(value)` — parses `namespace:path`; values without a namespace default to `minecraft`.
- `namespace()` / `path()` — record accessors.
- `toString()` — canonical `namespace:path` representation.

Use your own addon namespace for extension IDs.

## Accounts

### `IAccountManager`

Central account service.

- `Optional<IBankAccount> getPlayerAccount(UUID player)`
- `IBankAccount getOrCreatePlayerAccount(UUID player)`
- `boolean hasAccount(UUID player)`
- `IBankAccount getServerAccount()`
- `IBankAccount getTaxAccount()`
- `boolean deleteAccount(UUID player)`
- `boolean transfer(IBankAccount source, IBankAccount target, BigDecimal amount, ITransactionContext context)`
- `boolean transfer(UUID sourcePlayer, UUID targetPlayer, BigDecimal amount, ITransactionContext context)`

`IAccountManager.getInstance()` is deprecated. New code should use `EconomyApi.accounts()`.

Transfers must preserve atomicity: a failed/rejected target credit must not leave the source debited.

### `IBankAccount`

Virtual currency account.

- `UUID getOwner()`
- `BigDecimal getBalance()`
- `boolean credit(BigDecimal amount, ITransactionContext context)`
- `boolean debit(BigDecimal amount, ITransactionContext context)`
- `boolean transferTo(IBankAccount target, BigDecimal amount, ITransactionContext context)`
- `List<ITransactionRecord> getRecentTransactions(int count)`
- `boolean hasSufficientFunds(BigDecimal amount)`

New code should provide a non-null transaction context with a namespaced cause.

## Transaction context and history

### `ITransactionContext`

Describes why a balance operation occurred.

- `UUID getTransactionId()`
- `Instant getTimestamp()`
- `String getDescription()`
- `String getSource()`
- `EconomyId getCauseId()` — preferred stable cause identifier.
- `Map<String, String> getMetadata()` — immutable structured metadata.
- `TransactionType getType()` — deprecated legacy classification.

`TransactionType` contains `CREDIT`, `DEBIT`, `TRANSFER`, `TRADE`, `TAX`, `ADMIN_GIVE`, `ADMIN_TAKE`, `STARTING_BALANCE`, and `CUSTOM`.

### `TransactionCauses`

Built-in cause IDs:

- `economy:credit`
- `economy:debit`
- `economy:transfer`
- `economy:trade`
- `economy:tax`
- `economy:admin_give`
- `economy:admin_take`
- `economy:starting_balance`
- `economy:custom`

Also provides `fromLegacy(TransactionType)` and `toLegacy(EconomyId)` for compatibility mapping. Addons should define their own cause IDs instead of extending the legacy enum.

### `ITransactionRecord`

Immutable/read-only transaction-history contract. Use account history returned by `IBankAccount#getRecentTransactions` rather than depending on concrete transaction record implementations.

## Events

### `EconomyEvents`

Loader-neutral synchronous event bus.

```java
EconomyEvents.Subscription sub = EconomyEvents.listen(
    EconomyEvents.TransferCompleted.class,
    event -> handle(event)
);
```

- `listen(Class<E>, Consumer<E>)` — register an exact event-class listener and receive a closeable subscription.
- `post(E)` — publishes synchronously; primarily used by Economy and public extension registries.
- `Subscription.close()` — unregister listener.

Addon code should not call `clearListeners()` during normal operation because it clears listeners globally.

Account events:

- `BalanceChangePre` — cancellable; owner, previous balance, delta, resulting balance, context.
- `BalanceChanged` — committed balance change.
- `TransferPre` — cancellable; source, target, amount, context.
- `TransferCompleted` — committed transfer.

### `MarketEvents`

Market event payloads published through `EconomyEvents`.

- `OrderCreatePre` — cancellable order proposal.
- `OrderCreated` — resulting order plus requested/filled quantity.
- `OrderEdited`
- `OrderCancelled`
- `TradeCompleted`

Event delivery is synchronous; listeners should return quickly.

## Commodities

### `ICommodity`

Tradeable product contract.

- `EconomyId getId()` — stable commodity identity.
- `CommodityType getType()` — broad legacy category.
- `EconomyId getTypeId()` — namespaced handler/codec type.
- `Component getDisplayName()`
- `BigDecimal getBasePrice()`
- `boolean hasDynamicPricing()`

Built-in type IDs:

- `ICommodity.ITEM_TYPE` = `economy:item`
- `ICommodity.FLUID_TYPE` = `economy:fluid`
- `ICommodity.ENERGY_TYPE` = `economy:energy`

`CommodityType` contains `ITEM`, `FLUID`, `ENERGY`, and `CUSTOM`.

The direct `IStorage` extraction/insertion methods are legacy compatibility hooks. New storage integrations should use `IStorageProvider`.

### `ICommodityTypeHandler`

Behavior and persistence codec for a namespaced commodity type.

- `EconomyId id()`
- `int currentSchemaVersion()`
- `boolean supports(ICommodity commodity)`
- `CommodityPayload encode(ICommodity commodity)`
- `ICommodity decode(EconomyId commodityId, CommodityPayload payload)`
- `boolean fluidLike()` — optional display hint; defaults to `false`.

### `CommodityPayload`

Versioned immutable codec payload:

```java
new CommodityPayload(1, Map.of("key", "value"));
```

- `int version()` — must be at least 1.
- `Map<String, String> values()` — immutable map.
- `empty(version)` — convenience factory.

### `CommodityTypeRegistry`

Registry reached through `EconomyApi.commodityTypes()`.

- `register(ICommodityTypeHandler handler)`
- `boolean unregister(EconomyId id)`
- `Optional<ICommodityTypeHandler> handler(EconomyId id)`
- `ICommodityTypeHandler require(EconomyId id)`
- `ICommodityTypeHandler handlerFor(ICommodity commodity)`
- `CommodityPayload encode(ICommodity commodity)`
- `ICommodity decode(EconomyId typeId, EconomyId commodityId, int version, Map<String, String> values)`
- `boolean fluidLike(ICommodity commodity)`
- `List<ICommodityTypeHandler> handlers()`

Registration is namespaced and duplicate IDs are rejected.

## Orders

### `IOrderManager`

Supported order-book service.

Creation:

- `createBuyOrder(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `createSellOrder(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `createServerBuyOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `createServerSellOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit)`

Queries:

- `Optional<IOrder> getOrder(UUID orderId)`
- `List<IOrder> getAllOrders()`
- `List<IOrder> getOrders(ICommodity commodity)`
- `List<IOrder> getPlayerOrders(UUID player)`
- `List<IOrder> getBuyOrders(ICommodity commodity)`
- `List<IOrder> getSellOrders(ICommodity commodity)`

Mutation:

- `boolean cancelOrder(UUID orderId, UUID requester)`
- `boolean editOrder(UUID orderId, UUID requester, int newQuantity, BigDecimal newPrice, boolean infinite)`

Use the manager for mutation rather than concrete order implementations.

### `OrderCreateResult`

Stable result of submitting an order.

Fields:

- `Status status()` — `POSTED`, `PARTIALLY_FILLED`, `FILLED`, or `REJECTED`.
- `IOrder remainingOrder()` — nullable remaining book order.
- `int requestedQuantity()`
- `int filledQuantity()`
- `String errorKey()`
- `List<String> errorArgs()`

Helpers:

- `Optional<IOrder> order()`
- `boolean accepted()`

### `IOrder`

Read/operation contract for one order.

- `UUID getOrderId()`
- `UUID getOwner()`
- `ICommodity getCommodity()`
- `int getQuantity()`
- `BigDecimal getPricePerUnit()`
- `BigDecimal getTotalPrice()`
- `OrderType getType()` — `BUY` or `SELL`.
- `Instant getCreatedAt()`
- `Instant getExpiresAt()`
- `boolean isValid()`
- `boolean canExecute(UUID trader)`

`execute`/`cancel` remain on the compatibility interface, but addon code should prefer `IOrderManager` so world resolution, ownership, and order-book invariants stay centralized.

## Market data

### `IMarketDataService`

Read-only analytics surface.

- `List<TradeView> recentTrades(int limit)`
- `List<TradeView> recentTrades(EconomyId commodityId, int limit)`
- `Optional<BigDecimal> lastTradePrice(EconomyId commodityId)`
- `long tradedVolume(EconomyId commodityId)`
- `int activeOrderCount(EconomyId commodityId)`

### `TradeView`

Immutable completed-trade view:

- `EconomyId commodityId()`
- `EconomyId commodityTypeId()`
- `BigDecimal pricePerUnit()`
- `int quantity()`
- `UUID buyer()`
- `UUID seller()`
- `Instant timestamp()`

## Storage integration

### `IStorageProvider`

Owner-scoped market storage backend.

Identification:

- `EconomyId id()`
- `int priority()` — defaults to 0.
- `boolean supports(ICommodity commodity)`

Side-effect-free simulation:

- `int available(ServerLevel level, UUID owner, ICommodity commodity)`
- `int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount)`

Reservation lifecycle:

- `Optional<StorageReservation> reserve(...)`
- `int deliverReserved(ServerLevel level, StorageReservation reservation, UUID receiver, int amount)`
- `boolean release(ServerLevel level, StorageReservation reservation)`

Diagnostics:

- `String describe(ServerLevel level, UUID owner)`

See [Extending Economy](EXTENDING_ECONOMY.md) for the atomicity and losslessness requirements.

### `StorageReservation`

Durable opaque reservation persisted by Economy.

- `EconomyId providerId()`
- `EconomyId commodityId()`
- `int amount()` — positive.
- `String token()` — provider-owned durable lookup token.
- `Map<String, String> metadata()` — immutable optional data.

The provider must be able to resolve the reservation after save/reload.

### `StorageProviderRegistry`

Registry/dispatcher reached through `EconomyApi.storage()`.

- `register(IStorageProvider provider)`
- `boolean unregister(EconomyId id)`
- `Optional<IStorageProvider> provider(EconomyId id)`
- `List<IStorageProvider> providers()` — priority-descending, then ID.
- `int available(...)`
- `int receivable(...)`
- `Optional<StorageReservation> reserve(...)`
- `int deliver(...)`
- `boolean release(...)`

Registration changes emit `StorageProviderRegistered` and `StorageProviderUnregistered` through `EconomyEvents`.

## Compatibility and lifecycle checklist

- Depend only on `com.nstut.economy.api` for stable addon integration.
- Use `EconomyApi`, not singleton holders or concrete managers.
- Do not cache runtime service instances across server restarts.
- Register commodity handlers and storage providers once during mod initialization.
- Use namespaced `EconomyId` values owned by your addon.
- Prefer `getCauseId()` over deprecated transaction enums.
- Keep event listeners fast and close short-lived subscriptions.
- Make storage simulation side-effect free.
- Make reservations durable and delivery/release lossless.
- Keep old commodity payload schema decoders when released worlds may still contain them.
- Compile/run against the matching Minecraft and loader artifact.