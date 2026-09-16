# Economy verification

Economy uses layered tests so each failure surface is exercised at the cheapest level that can prove it.

## Layers

### 1. Harness contracts

```bash
python -m unittest discover -s tools -p 'test_*.py' -v
```

Covers CI path selection, the supported live target matrix, exact-head result receipts, and fatal runtime signature handling. These tests must remain dependency-light so CI can validate the verification machinery before Minecraft starts.

### 2. Fast JVM/version regression suite

```bash
./gradlew testAllVersions
# or
python tools/verify.py fast
```

Owns pure/domain behavior, validation, codecs, migration, escrow/order logic, addon API contracts, and version-specific adapters. Do not move logic here into a real client/server test merely to increase the E2E count.

### 3. Canonical real-world GameTest

```bash
./gradlew coreCheck
# or
python tools/verify.py core
```

`coreCheck` runs the complete JVM/version layer plus the canonical Forge 1.20.1 GameTest. The GameTest owns behavior that requires an actual `ServerLevel`: registered Vault/Tank placement, block entities, inventory/fluid mutation, commodity identity against real registries, variant-aware storage matching/extraction, order lookup/execution, and trade-history integration.

One canonical world test is intentional. Loader wrappers should be covered by cheaper tests unless a loader has genuinely different world/runtime behavior.

### 4. Real loader/client integration

```bash
python tools/verify.py live --target forge-1.20.1
python tools/verify.py live
```

The live layer starts a real dedicated server and graphical auto-joining client. It runs on all supported targets:

- Fabric 1.20.1
- Forge 1.20.1
- Fabric 1.21.1
- NeoForge 1.21.1
- NeoForge 26.1.2

A pass requires both the Economy server lifecycle (`Economy data loaded for dimension ...`) and the client live-join marker. The harness also fails on critical Mixin, crash, packet encode/decode, or server-start signatures even if a nominal pass marker appeared.

The live harness deletes stale run state safely, prevents concurrent runs from owning the same checkout, prints server/client log tails on failure, and can produce exact-head receipts for CI aggregation.

### 5. Full local verification

```bash
python tools/verify.py full
```

Runs `core` followed by the live layer for every loader target and writes stage timing/status to `build/verification-full.json`.

## CI policy

Real Minecraft boots are expensive. `tools/ci_policy.py` permits skipping them only for documentation-only changes. Build scripts, workflows, resources, verification code, unknown paths, and an empty/unresolvable diff all fail closed and require the live layer.

This follows the same principle used by the Endless and Immersive Portals CE suites: keep broad cheap coverage always-on, use a canonical real-world test for world semantics, and reserve graphical/dedicated E2E for failures that cannot be proved below that layer.
