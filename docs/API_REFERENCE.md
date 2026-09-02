# Economy API reference

This is a practical catalog of the supported addon-facing API. The canonical signatures remain the Java sources for the selected Economy version. Unless documented otherwise, only the top-level `com.nstut.economy.api` package is covered by the compatibility policy. The `com.nstut.economy.api.internal` subpackage is implementation detail and is not part of the supported addon surface.

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
- `compareTo(EconomyId)` — namespace-first, then path lexical ordering.
- `toString()` — canonical `namespace:path` representation.

Namespaces accept `[a-z0-9_.-]+`; paths accept `[a-z0-9/._-]+`. Use your own addon namespace for extension IDs.

### `CommodityKey`

Full stable market identity: commodity type plus commodity ID.

- `EconomyId commodityTypeId()`
- `EconomyId commodityId()`
- `static CommodityKey of(ICommodity commodity)`
- `boolean matches(ICommodity commodity)`

Use `CommodityKey` for analytics and persistent keys where two commodity types could expose the same commodity ID.

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

Read-only view of a completed balance transaction.

- `UUID getTransactionId()`
- `Instant getTimestamp()`
- `ITransactionContext.TransactionType getType()` — deprecated legacy classification.
- `EconomyId getCauseId()` — preferred namespaced cause.
- `Map<String, String> getMetadata()` — immutable transaction metadata.
- `BigDecimal getAmount()`
- `BigDecimal getResultingBalance()`
- `UUID getCounterparty()` — counterparty when the transaction has one; implementations may use `null` when it does not.
- `String getDescription()`

Use records returned by `IBankAccount#getRecentTransactions`; do not depend on concrete transaction record implementations.

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

Listeners are matched by the event's exact runtime class; registering for a base event interface does not subscribe to every subtype. Addon code should not call `clearListeners()` during normal operation because it clears listeners globally.

Account events:

- `BalanceChangePre` — cancellable; `owner()`, `previousBalance()`, `delta()`, `resultingBalance()`, `context()`.
- `BalanceChanged` — committed `owner`, `previousBalance`, `balance`, `delta`, and `context`.
- `TransferPre` — cancellable; `source()`, `target()`, `amount()`, `context()`.
- `TransferCompleted` — committed `source`, `target`, `amount`, and `context`.

### `MarketEvents`

Market event payloads published through `EconomyEvents`.

- `OrderCreatePre` — cancellable proposal with `owner()`, `commodity()`, `type()`, `quantity()`, and `pricePerUnit()`; posted before Economy creates new provider escrow.
- `OrderCreated` — `order`, `requestedQuantity`, `filledQuantity`.
- `OrderEdited` — resulting `order`.
- `OrderCancelled` — `orderId`, `owner`.
- `TradeCompleted` — immutable `TradeView trade`.

Storage-provider registry changes are also events:

- `StorageProviderRegistry.StorageProviderRegistered` — `providerId`.
- `StorageProviderRegistry.StorageProviderUnregistered` — `providerId`.

Event delivery is synchronous; listeners should return quickly.

## Commodities

### `ICommodity`

Tradeable product contract.

- `EconomyId getId()` — stable product ID inside its commodity type.
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

The direct nested `ICommodity.IStorage` marker and the `canExtractFrom`, `canInsertInto`, `extractFrom`, and `insertInto` methods are legacy compatibility hooks. New storage integrations should use `IStorageProvider`.

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

Registration is namespaced and duplicate IDs owned by different handler instances are rejected. `handlers()` is returned in stable ID order.

## Orders

### `IOrderManager`

Supported order-book service.

Creation:

- `OrderCreateResult createBuyOrder(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `OrderCreateResult createSellOrder(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `IOrder createServerBuyOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `IOrder createServerSellOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit)`

Player order creation always returns an `OrderCreateResult`. Server-order creation uses the compatibility return shape and may return `null` when domain validation rejects creation, so addon callers must check the result before dereferencing it.

Queries:

- `Optional<? extends IOrder> getOrder(UUID orderId)`
- `List<? extends IOrder> getAllOrders()`
- `List<? extends IOrder> getOrders(ICommodity commodity)`
- `List<? extends IOrder> getPlayerOrders(UUID player)`
- `List<? extends IOrder> getBuyOrders(ICommodity commodity)`
- `List<? extends IOrder> getSellOrders(ICommodity commodity)`

Mutation:

- `boolean cancelOrder(UUID orderId, UUID requester)`
- `boolean editOrder(UUID orderId, UUID requester, int newQuantity, BigDecimal newPrice, boolean infinite)`

Use the manager for mutation rather than concrete order implementations.

### `OrderCreateResult`

Stable result of submitting a player order.

Fields:

- `Status status()` — `POSTED`, `PARTIALLY_FILLED`, `FILLED`, or `REJECTED`.
- `IOrder remainingOrder()` — nullable remaining book order.
- `int requestedQuantity()`
- `int filledQuantity()`
- `String errorKey()` — may be `null` for accepted results.
- `List<String> errorArgs()` — immutable; empty when no error arguments exist.

Helpers:

- `Optional<IOrder> order()`
- `boolean accepted()`

A fully filled accepted order has no `remainingOrder()` even though `accepted()` is true.

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

Read-only analytics surface. Commodity-specific queries require the full `CommodityKey` identity.

- `List<TradeView> recentTrades(int limit)`
- `List<TradeView> recentTrades(CommodityKey commodity, int limit)`
- `Optional<BigDecimal> lastTradePrice(CommodityKey commodity)`
- `long tradedVolume(CommodityKey commodity)`
- `int activeOrderCount(CommodityKey commodity)`

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

- `Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount)`
- `StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation, UUID receiver, int amount)`
- `boolean release(ServerLevel level, StorageReservation reservation)`

`reserve` is atomic: empty means no mutation. `deliverReserved` is one provider-owned transition and must return the exact remaining reservation after the actual delivery. `release` is all-or-nothing: `false` means the entire input reservation must still be treated as escrowed.

Diagnostics:

- `String describe(ServerLevel level, UUID owner)` — defaults to the provider ID string.

See [Extending Economy](EXTENDING_ECONOMY.md) for the atomicity, exact-state, and losslessness requirements.

### `StorageReservation`

Durable opaque reservation persisted by Economy.

- `EconomyId providerId()`
- `EconomyId commodityId()`
- `int amount()` — positive.
- `String token()` — provider-owned durable token.
- `Map<String, String> metadata()` — small immutable optional metadata.
- `CompoundTag providerState()` — defensive copy of structured provider-owned durable state.

Use `providerState` for exact inventories/components or potentially large state. Do not pack arbitrary escrow into one Base64/SNBT metadata string.

### `StorageDeliveryResult`

Atomic result of a provider delivery.

- `int deliveredAmount()`
- `Optional<StorageReservation> remainingReservation()`
- `static unchanged(StorageReservation)`
- `static complete(int deliveredAmount)`
- `static partial(int deliveredAmount, StorageReservation remainingReservation)`
- `validateAgainst(StorageReservation before, int requestedAmount)` — returns the same result after validating request bounds, delivered amount, total accounting, provider identity, and commodity identity; invalid provider results throw instead of being silently accepted.

Economy expects:

```text
deliveredAmount + remainingReservation.amount = previousReservation.amount
```

The remainder must describe the exact state that still belongs to escrow, not a reconstruction from a delivered count.

### `StorageProviderRegistry`

Registry/dispatcher reached through `EconomyApi.storage()`.

- `register(IStorageProvider provider)`
- `boolean unregister(EconomyId id)`
- `Optional<IStorageProvider> provider(EconomyId id)`
- `List<IStorageProvider> providers()` — priority-descending, then ID.
- `int available(...)`
- `int receivable(...)`
- `Optional<StorageReservation> reserve(...)`
- `StorageDeliveryResult deliver(...)`
- `boolean release(...)`

Dispatcher semantics are intentionally asymmetric:

- `available(...)` returns the largest amount offered by any single supporting provider because one reservation is never split across providers.
- `reserve(...)` walks providers in priority order and succeeds only when one provider can atomically reserve the full requested amount.
- `receivable(...)` may aggregate receiving capacity across supporting providers, capped at the requested amount.
- `deliver(...)` and `release(...)` route strictly to the provider ID stored in the reservation. A missing provider therefore leaves delivery unchanged and makes release fail rather than guessing another backend.

Registration changes emit `StorageProviderRegistered` and `StorageProviderUnregistered` through `EconomyEvents`.

## Compatibility and lifecycle checklist

- Depend only on the top-level `com.nstut.economy.api` package for stable addon integration; never depend on `com.nstut.economy.api.internal`.
- Use `EconomyApi`, not singleton holders or concrete managers.
- Do not cache runtime service instances across server restarts.
- Register commodity handlers and storage providers once during mod initialization.
- Use namespaced `EconomyId` values owned by your addon.
- Use `CommodityKey` for commodity-specific market analytics.
- Prefer `getCauseId()` over deprecated transaction enums.
- Keep event listeners fast and close short-lived subscriptions.
- Make storage simulation side-effect free.
- Make reservation creation atomic and assume a reservation will never span multiple providers.
- Return exact provider-owned remainder state from every partial delivery.
- Make failed release perform zero externally visible mutation.
- Persist structured/large escrow in `providerState`, not one string.
- Keep old commodity payload schema decoders when released worlds may still contain them.
- Compile/run against the matching Minecraft and loader artifact.
