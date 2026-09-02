# Extending Economy

This guide covers the parts of Economy intended for addon authors who need more than balance and order access: custom transaction causes, commodity types, persistence codecs, storage backends, market events, and compatibility rules.

Read [Getting Started](GETTING_STARTED.md) first. The supported compatibility boundary is the top-level `com.nstut.economy.api` package. The `com.nstut.economy.api.internal` subpackage is implementation detail and is not supported for addon use.

## Compatibility contract

Economy's public addon surface is the top-level `com.nstut.economy.api` package. Treat `com.nstut.economy.api.internal` and implementation packages such as `core`, `trading`, `data`, block/menu code, networking code, and loader adapters as internal even when Java visibility allows access.

The API intentionally uses `EconomyId` instead of exposing Minecraft's `ResourceLocation` / `Identifier` naming differences. Prefer namespaced IDs owned by your addon:

```java
EconomyId SALARY = EconomyId.of("myaddon", "salary");
EconomyId TOKEN_TYPE = EconomyId.of("myaddon", "token");
EconomyId STORAGE = EconomyId.of("myaddon", "warehouse");
```

Do not claim IDs in the `economy` namespace.

## Runtime versus persistent registries

`EconomyApi.accounts()`, `orders()`, `marketData()`, and `serverLevel()` belong to the active server runtime. They are unavailable before Economy binds to a running server and are cleared when that server stops.

`EconomyApi.commodityTypes()` and `EconomyApi.storage()` are process-level registries and intentionally survive server restarts. Register addon handlers/providers during common mod initialization and do not register duplicate instances for every world load.

Use `EconomyApi.isReady()` before work that requires runtime services.

## Custom transaction causes

`ITransactionContext#getType()` exists for source compatibility with older integrations. New integrations should identify operations with `getCauseId()` and optional immutable metadata.

Implement `ITransactionContext` in your addon rather than constructing Economy internal classes. For example:

```java
public record AddonTransactionContext(
        UUID transactionId,
        Instant timestamp,
        EconomyId causeId,
        String description,
        String source,
        Map<String, String> metadata
) implements ITransactionContext {

    public AddonTransactionContext(EconomyId causeId, String description, String source,
                                   Map<String, String> metadata) {
        this(UUID.randomUUID(), Instant.now(), causeId, description, source,
             metadata == null ? Map.of() : Map.copyOf(metadata));
    }

    @Override public UUID getTransactionId() { return transactionId; }
    @Override public Instant getTimestamp() { return timestamp; }
    @Override public TransactionType getType() { return TransactionType.CUSTOM; }
    @Override public String getDescription() { return description; }
    @Override public String getSource() { return source; }
    @Override public EconomyId getCauseId() { return causeId; }
    @Override public Map<String, String> getMetadata() { return metadata; }
}
```

Then transfer through the account service:

```java
ITransactionContext context = new AddonTransactionContext(
    EconomyId.of("myaddon", "salary"),
    "Weekly salary",
    "myaddon:jobs",
    Map.of("job", "miner")
);

boolean success = EconomyApi.accounts().transfer(source, target, amount, context);
```

Keep metadata compact and stable. It is diagnostic/integration state, not a replacement for your addon's primary save format.

## Account and transfer events

The account event bus is synchronous and loader-neutral:

```java
EconomyEvents.Subscription sub = EconomyEvents.listen(
    EconomyEvents.TransferCompleted.class,
    event -> audit(event.source(), event.target(), event.amount(), event.context())
);
```

Listeners are registered for an exact event class. Registering a listener for the base `EconomyEvents.Event` interface does not subscribe it to every event subtype.

Cancellable pre-events currently include:

- `EconomyEvents.BalanceChangePre`
- `EconomyEvents.TransferPre`
- `MarketEvents.OrderCreatePre`

Post-events describe committed state and should not be treated as rollback hooks.

Listeners execute inline with the operation that posts them. Do not block, sleep, perform network I/O, or run expensive scans from these callbacks. If work can be deferred safely, capture immutable information and schedule the heavy part through your own mechanism.

`OrderCreatePre` is authoritative: Economy posts it before creating a new storage-provider reservation. A cancelled order does not enter the order book or create new provider escrow. Compatibility call paths that supply already-extracted legacy escrow are unwound transactionally, with any unrecoverable remainder quarantined instead of discarded.

## Custom commodity types

A custom commodity has two pieces:

1. an `ICommodity` value representing one tradeable product;
2. an `ICommodityTypeHandler` registered under a namespaced type ID, responsible for behavior discovery and persistence.

A minimal commodity can look like this:

```java
public record TokenCommodity(EconomyId id, Component displayName, BigDecimal basePrice)
        implements ICommodity {

    private static final EconomyId TYPE = EconomyId.of("myaddon", "token");

    @Override public EconomyId getId() { return id; }
    @Override public CommodityType getType() { return CommodityType.CUSTOM; }
    @Override public EconomyId getTypeId() { return TYPE; }
    @Override public Component getDisplayName() { return displayName; }
    @Override public BigDecimal getBasePrice() { return basePrice; }
    @Override public boolean hasDynamicPricing() { return false; }

    // Legacy compatibility hooks. New storage integration belongs in IStorageProvider.
    @Override public boolean canExtractFrom(IStorage storage, int amount) { return false; }
    @Override public boolean canInsertInto(IStorage storage, int amount) { return false; }
    @Override public boolean extractFrom(IStorage storage, int amount) { return false; }
    @Override public boolean insertInto(IStorage storage, int amount) { return false; }
}
```

Register a handler once during addon initialization:

```java
EconomyApi.commodityTypes().register(new TokenCommodityHandler());
```

The registry rejects a second different handler with the same ID. Never silently replace another addon's handler.

### Commodity codec contract

`ICommodityTypeHandler` owns a versioned `CommodityPayload`:

```java
public final class TokenCommodityHandler implements ICommodityTypeHandler {
    private static final EconomyId TYPE = EconomyId.of("myaddon", "token");

    @Override public EconomyId id() { return TYPE; }
    @Override public int currentSchemaVersion() { return 1; }
    @Override public boolean supports(ICommodity commodity) {
        return TYPE.equals(commodity.getTypeId());
    }

    @Override
    public CommodityPayload encode(ICommodity commodity) {
        TokenCommodity token = (TokenCommodity) commodity;
        return new CommodityPayload(1, Map.of(
            "base_price", token.basePrice().toPlainString(),
            "name", token.displayName().getString()
        ));
    }

    @Override
    public ICommodity decode(EconomyId commodityId, CommodityPayload payload) {
        if (payload.version() != 1) {
            throw new IllegalArgumentException("Unsupported token schema " + payload.version());
        }
        return new TokenCommodity(
            commodityId,
            Component.literal(payload.values().getOrDefault("name", commodityId.toString())),
            new BigDecimal(payload.values().getOrDefault("base_price", "0"))
        );
    }
}
```

Rules for a durable codec:

- `encode` must preserve everything required to reconstruct the commodity after restart;
- increment `currentSchemaVersion()` when the stored meaning changes;
- keep decoding older versions for as long as released worlds may contain them;
- reject corrupt payloads explicitly instead of constructing half-valid commodities;
- do not depend on transient object identity or runtime registry iteration order.

Economy persistence stores the commodity type ID, payload schema version, and payload map. Malformed persisted orders are quarantined as raw snapshots instead of being silently discarded, so addon extension state is not intentionally destroyed when decoding fails.

## Commodity identity and analytics

The stable identity of a commodity is the pair `(commodityTypeId, commodityId)`, represented by `CommodityKey`.

```java
CommodityKey key = CommodityKey.of(commodity);
long volume = EconomyApi.marketData().tradedVolume(key);
```

Do not use `commodityId` alone as an analytics/database key. Two different registered commodity types may intentionally expose the same commodity ID; Economy keeps their trades and active-order counts separate.

## Custom storage providers

Use `IStorageProvider` when Economy should buy from, sell into, or reserve goods in storage owned by another mod/system.

Register once:

```java
EconomyApi.storage().register(new WarehouseStorageProvider());
```

Providers are considered by descending `priority()`, then provider ID. A provider should only report support for commodities it can fully handle.

A reservation is always owned by exactly one provider. `StorageProviderRegistry.available(...)` therefore returns the largest amount that any one supporting provider can reserve atomically, not the sum of stock across providers. `reserve(...)` walks providers in priority order and never combines a request across backends. By contrast, `receivable(...)` may aggregate receiving capacity across supporting providers up to the requested amount. Delivery and release are always routed back to the provider ID stored in the reservation; Economy never substitutes another provider for missing escrow ownership.

### Simulation methods must be pure

```java
int available(ServerLevel level, UUID owner, ICommodity commodity);
int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount);
```

These are probes. They must not insert, extract, reserve, consume energy, mutate inventories, or create persistent side effects.

Return non-negative values. `receivable` should never claim more than the requested amount. It is only a capacity hint: the final exact delivery result is authoritative.

### Reservation is the escrow boundary

```java
Optional<StorageReservation> reserve(
    ServerLevel level,
    UUID owner,
    ICommodity commodity,
    int amount
);
```

A successful reservation means the requested goods have crossed into durable escrow. `reserve` must either complete atomically or return `Optional.empty()` without mutation. Do not assume Economy will combine partial reservations from several providers to satisfy one order.

A reservation contains:

- the provider ID;
- commodity ID;
- positive reserved amount;
- an opaque provider-owned token;
- optional small immutable string metadata;
- a structured `CompoundTag providerState` for provider-owned durable state.

`metadata` is for small diagnostic/index values. Do **not** Base64/SNBT-pack arbitrary inventories or large binary state into one metadata string. Put structured or potentially large provider state in `providerState`, using normal nested NBT compounds/lists.

Economy persists the reservation, including `providerState`, across server save/reload. A provider must be able to understand its own persisted token/state after restart.

### Delivery is one atomic state transition

```java
StorageDeliveryResult deliverReserved(
    ServerLevel level,
    StorageReservation reservation,
    UUID receiver,
    int amount
);
```

The return value contains both:

- `deliveredAmount()` — exactly how many units left escrow and reached the receiver/consumer;
- `remainingReservation()` — the provider-owned **exact** escrow state after the operation, or empty when nothing remains.

Economy validates the accounting invariant:

```text
delivered + remaining.amount = reservation.amount
```

It also validates that delivery never exceeds the requested amount and that a remaining reservation keeps the same provider and commodity identity. Invalid provider results fail explicitly instead of being silently normalized.

The provider must calculate the remainder from the actual mutation. Never return only a count and then guess which exact stacks/components remain. For heterogeneous item payloads, if one variant is rejected while another is accepted, the returned reservation must contain the exact rejected variant plus any untouched escrow.

Example shape:

```java
@Override
public StorageDeliveryResult deliverReserved(ServerLevel level,
                                             StorageReservation before,
                                             UUID receiver,
                                             int requested) {
    // Perform one provider-owned transaction and derive `remaining`
    // from what actually failed to move, not from a numeric prefix.
    int delivered = ...;
    StorageReservation remaining = ...;

    return remaining == null
        ? StorageDeliveryResult.complete(delivered)
        : StorageDeliveryResult.partial(delivered, remaining);
}
```

Use `StorageDeliveryResult.unchanged(before)` when nothing moved.

### Release is all-or-nothing

```java
boolean release(ServerLevel level, StorageReservation reservation);
```

`true` means every remaining unit was restored to the original owner and the reservation may be discarded. `false` means Economy must continue treating the **entire input reservation as owned escrow**.

Therefore a provider must not partially mutate storage and then return `false`. Simulate or otherwise prove the full restore can succeed before committing, or use a provider-internal transaction/rollback mechanism. A failed release must leave both storage and reservation ownership unchanged.

### Escrow invariant

Across reserve, delivery, cancellation, restart, and provider outages:

```text
initial goods = delivered + still_escrowed + successfully_released
```

No code path may duplicate, destroy, or silently forget goods. If a codec/provider is unavailable while loading a persisted order, Economy quarantines escrow-bearing snapshots so they remain persisted instead of dropping them.

### Storage-provider checklist

Test at minimum:

- insufficient stock: reservation fails with no mutation;
- exact stock: reservation succeeds once;
- stock split across multiple providers cannot incorrectly satisfy one atomic reservation;
- priority ordering chooses the first provider capable of the full reservation;
- save/reload between reserve and delivery;
- structured provider state substantially larger than one NBT UTF string limit;
- heterogeneous exact-stack partial delivery where an earlier variant is rejected and a later one succeeds;
- partial delivery followed by later delivery;
- partial delivery followed by release;
- failed release causes zero storage mutation and leaves the whole reservation valid;
- successful release restores every remaining unit exactly once;
- cancellation after restart;
- receiver capacity changing between simulation and delivery;
- provider temporarily unavailable when persisted orders load;
- multiple providers supporting the same commodity;
- repeated release/delivery attempts do not duplicate goods.

## Market events and read models

Use `MarketEvents` for order/trade observation and `IMarketDataService` for immutable analytics. Do not inspect internal order lists, trade ledgers, or saved-data objects.

`MarketEvents` currently exposes:

- `OrderCreatePre`
- `OrderCreated`
- `OrderEdited`
- `OrderCancelled`
- `TradeCompleted`

For dashboards or quest checks, construct a `CommodityKey` and prefer `marketData()` queries over maintaining a second copy of Economy's entire market state.

## Order ownership and server orders

Create, edit, and cancel through `IOrderManager`. The manager owns authorization, persistence, escrow, events, and invariant checks.

Server orders are created with `createServerBuyOrder` / `createServerSellOrder`. These compatibility-shaped methods may return `null` when domain validation rejects the request, so callers must check the result. Do not imitate server ownership by inventing UUIDs or instantiating Economy's internal `Order` implementation.

For player-facing/admin recovery, Economy also exposes `/economy serverorder list` and `/economy serverorder remove <order-id>`.

## Cross-version source guidance

Keep business logic expressed in Economy API types wherever possible. `EconomyId` exists specifically so your persistent IDs do not need conditional source for Minecraft identifier renames.

Minecraft-facing types still appear where unavoidable, such as `Component`, `ServerLevel`, and structured NBT provider state, so a compiled addon jar must target the exact Minecraft generation and loader it declares.

Recommended addon structure:

```text
shared/
  domain logic using com.nstut.economy.api
fabric-1.20.1/
forge-1.20.1/
fabric-1.21.1/
neoforge-1.21.1/
neoforge-26.1.2/
```

You do not need loader-specific adapters for Economy's own event bus, commodity registry, storage registry, or service facade.

## What not to depend on

Avoid dependencies on:

- `com.nstut.economy.api.internal.*`
- `com.nstut.economy.core.*`
- `com.nstut.economy.trading.*`
- `com.nstut.economy.data.*`
- internal blocks, block entities, menus, packets, screens, and saved-data layouts
- concrete account/order implementations
- internal server-owner UUID constants

If an addon cannot be implemented without one of those internals, open an issue describing the missing capability. The correct fix is usually to extend the public API rather than normalize an internal dependency.

For a catalog of the supported public types, continue with [API Reference](API_REFERENCE.md).
