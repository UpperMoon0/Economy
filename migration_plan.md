Economy → OpenUI MC Migration Plan

Mission

Migrate the Economy mod's entire client UI layer to OpenUI MC while preserving all market, vault, tank, packet, menu, inventory-slot and persistence behavior.

The finished UI must feel like a coherent premium trading/storage application rather than a collection of Minecraft buttons. It must support live, persisted light/dark switching in game.

This document is written as an implementation handoff. Follow the phases in order. Do not improvise a parallel UI framework.

## Status (as of 2026-08-21)

The previous status note ("migration complete, build unblocked") was inaccurate. The
branch was roughly 55-60% of the full plan. The gaps below have been closed in this
session; the remaining work is localization, responsive layout, and manual QA.

Completed (verified / in progress this session):

- OpenUI dependency wired via Maven Local + fg.deobf (circular composite substitute removed).
- Add OpenUI dependency and runtime metadata (Forge mod metadata required client dep).
- MarketClientPreferences theme mode + tests (10 MarketClientPreferencesTest pass).
- EconomyUiContainerScreen base class + per-screen live theme toggle.
- MarketClientStore (signal-based) with batch updates.
- Theme infrastructure (live runtime switch, persisted, unit tested).
- MarketScreen migrated and corrected:
  - Containers nav button actually wired into the sidebar (was built but never added).
  - Browse Grid/Rows preference now persisted (setBrowseGridView) and the label is reactive.
  - Item-search Popover rewritten to one stable signal + one VirtualList + one Popover
    (previously leaked a Computed per keystroke).
  - buildUI() now closes prior subscriptions before re-init (fixed resize leak).
  - Order type label corrected (was "from you", now "BUY").
  - Edit/Cancel row actions replaced manual drawSmallButton + coordinate hit-testing with
    real OpenUI ButtonWidgets (Market + Active Orders).
  - Empty states added to browse/active/history/containers/holdings/order columns.
- VaultScreen migrated to EconomyUiContainerScreen (was vanilla AbstractContainerScreen):
  - Real OpenUI mode ButtonWidget (was manual renderModeButton + mouseClicked bounds).
  - Shared theme toggle. Live VaultMode sync via tick + optimistic update.
- TankScreen migrated to EconomyUiContainerScreen (was vanilla AbstractContainerScreen):
  - Uses the existing FluidTankComponent (previously written but unused).
  - Real OpenUI mode ButtonWidget + theme toggle; no manual hit-testing.
- CI fixed: build-and-release.yml now checks out UpperMoon0/OpenUI-MC @ codex/multiloader-build
  and publishes it to Maven Local before building Economy, so a clean runner resolves
  com.nstut:openui-mc-forge-1.20.1:0.0.1.
- Localization pass: all player-facing UI strings moved to assets/economy/lang/en_us.json
  (ui.economy.* keys) and referenced via Component.translatable across MarketScreen,
  VaultScreen, TankScreen and the shared theme toggle.
- Responsive layout: EconomyUiContainerScreen.init() clamps the viewport to the game
  window (no clipping on small GUI scales) and MarketScreen uses Ui.responsive to switch
  between a wide sidebar shell and a narrow top-Tabs shell. Vault/Tank use flexible roots.

NOT yet done (definition-of-done gaps):

- Localization pass: COMPLETE. All player-facing UI strings across MarketScreen,
  VaultScreen, TankScreen and the shared theme toggle are now driven by
  assets/economy/lang/en_us.json (ui.economy.* keys) via Component.translatable.
- Responsive layout: COMPLETE. EconomyUiContainerScreen.init() now clamps the
  screen viewport to the game window so it never clips on small GUI scales, and
  MarketScreen wraps its shell in Ui.responsive: a wide layout (sidebar nav +
  content) for >= 300px and a narrow layout (top Tabs + content) below that.
  Vault/Tank inherit the viewport clamp and use flexible (flex) roots.
- Manual client QA: runClient smoke test (dark/light, slot drag, 3 tank/vault
  modes), full runGameTestServer, GUI-scale matrix — NOT run in this session
  (explicitly excluded; requires launching the game client).
- Old Economy UI framework (com.nstut.economy.ui.framework): already deleted and
  zero production imports remain (verified by grep). Definition-of-done items
  "no production import" and "old framework directory deleted" are satisfied.

Repositories reviewed

Consuming mod: UpperMoon0/Economy, current main.

OpenUI target: UpperMoon0/OpenUI-MC, branch codex/multiloader-build.

Economy currently has only the common and forge-1.20.1 projects.

Current client screens:

forge-1.20.1/src/main/java/com/nstut/forge/client/MarketScreen.java

forge-1.20.1/src/main/java/com/nstut/forge/client/TankScreen.java

forge-1.20.1/src/main/java/com/nstut/forge/client/VaultScreen.java

Existing client preference store:

forge-1.20.1/src/main/java/com/nstut/forge/client/MarketClientPreferences.java

Old copied/extracted UI framework:

forge-1.20.1/src/main/java/com/nstut/economy/ui/framework/

currently includes UIComponent, Panel, HStack, VStack, Padding, Spacer, SizedBox, Divider, TextWidget, ButtonWidget, EditBoxWrapper, ScrollList, ScrollGrid, UiRender, UiTheme.

The old framework must be deleted only after all three screens have been migrated and all references are gone.

Non-negotiable behavior preservation

Do not change these while migrating UI:

MarketMenu, TankMenu, VaultMenu authority.

Inventory slot indices, transfer rules or server-side slot behavior.

Existing market network packet meaning.

Item/fluid commodity identity and formatting.

Order matching/business rules.

Infinite-order semantics.

Vault/tank BOTH/INPUT/OUTPUT mode semantics.

Balance, portfolio, history, order and container sync protocols unless a separate bug is discovered.

Forge fluid rendering/tint behavior.

Existing MarketClientPreferences browse-grid choice.

A UI migration is not permission to rewrite the economy backend.

OpenUI target and hard constraints

Source of truth: UpperMoon0/OpenUI-MC, branch codex/multiloader-build.

Do not implement against OpenUI main. Do not copy the old Economy-extracted UI classes into the target mod. The migration target is the completed multi-loader OpenUI branch.

OpenUI version on the reviewed branch: 0.0.1.

Supported OpenUI module mapping:

Minecraft

Loader

Java

OpenUI project

1.20.1

Fabric

17

:fabric-1.20.1

1.20.1

Forge

17

:forge-1.20.1

1.21.1

Fabric

21

:fabric-1.21.1

1.21.1

NeoForge

21

:neoforge-1.21.1

26.1.2

NeoForge

25

:neoforge-26.1.2

Important framework behavior already provided by OpenUI:

UiScreen for ordinary screens.

UiContainerScreen<M> for menu/container screens.

UiRuntime for mount/unmount, layout, rendering, input, focus, overlays and native widget ownership.

Ui, UIComponent, HStack, VStack, Stack, Padding, Responsive, DynamicGrid, VirtualList.

Reactive state: Signal<T>, ReadableSignal<T>, Computed<T>, Signals.batch(...), closeable Subscription.

Controls: ButtonWidget, TextField, Checkbox, SwitchControl, Slider, Select, Tabs, Table, Card, Badge, Chip.

Feedback: Dialog, Toast, Tooltip, Popover, ContextMenu, LoadingOverlay, Spinner, Skeleton, EmptyState, ErrorBoundary.

Data display: LineChart, AreaChart, BarChart, Sparkline, ProgressBar.

Themes: Theme.dark(), Theme.light(), Theme.highContrast().

Live theme switching: uiRuntime().theme(newTheme); this invalidates paint without requiring a screen restart.

Stock controls obtain their colors from the runtime theme.

Custom components must use theme().colors() rather than static dark-only constants.

TextField owns its native Minecraft EditBox. Never manually call addRenderableWidget() for an OpenUI TextField.

OpenUI handles focus traversal and input dispatch. Do not manually forward mouse/keyboard events unless a genuinely custom component requires it.

OpenUI's 26.1.2 module uses the Minecraft 26 render-state/extractor API. Do not paste a 1.20.1 custom renderer verbatim into 26.1.2.

FadeTransition is not available on 26.1.2. Use no transition, SlideTransition, or ScaleTransition when the same behavior must work on all supported targets.

Dependency rule

The consuming loader must depend on the matching OpenUI loader/version module. Never compile against one OpenUI module and ship another.

For a multi-target consuming repository, do not use one global generic composite substitution such as:

substitute module('com.nstut:openui-mc') using project(':forge-1.20.1')

That only selects one OpenUI target for the entire build and is wrong when several loader projects coexist.

For local development, use loader-specific synthetic coordinates in the consuming root settings.gradle:

includeBuild('../OpenUI-MC') {
    dependencySubstitution {
        substitute module('com.nstut:openui-mc-fabric-1.20.1') using project(':fabric-1.20.1')
        substitute module('com.nstut:openui-mc-forge-1.20.1') using project(':forge-1.20.1')
        substitute module('com.nstut:openui-mc-fabric-1.21.1') using project(':fabric-1.21.1')
        substitute module('com.nstut:openui-mc-neoforge-1.21.1') using project(':neoforge-1.21.1')
        substitute module('com.nstut:openui-mc-neoforge-26.1.2') using project(':neoforge-26.1.2')
    }
}

Each consuming loader project then uses only its matching coordinate, for example:

modImplementation 'com.nstut:openui-mc-fabric-1.20.1:0.0.1'

or the equivalent dependency configuration for that loader/build system.

If OpenUI is published to a Maven repository before this migration is implemented, replace the synthetic composite setup with the real published loader-specific coordinates. Do not change the loader-to-loader mapping.

OpenUI is a required client-side library mod. Do not shadow/relocate the OpenUI classes into these mods during the first migration. Add openui_mc to each loader's mod metadata as a required client dependency and ship the matching OpenUI jar alongside the consuming mod. Bundling can be evaluated separately after the migration is stable.

Cross-mod visual language

All three migrations must follow the same suite-level visual rules:

Texture-free UI shell. Remove old GUI background PNG use from migrated screens.

Use semantic theme colors; no screen-level RGB palette constants.

Use OpenUI spacing/radius/theme tokens instead of hand-picked per-screen geometry.

Dark and light themes must have feature parity.

Every top-level screen must expose the same compact theme toggle in the header.

Theme changes must apply immediately to the open screen.

Theme choice must persist across Minecraft restarts.

Primary action = OpenUI primary button.

Secondary/navigation action = secondary/ghost style.

Destructive action = danger style and, for irreversible actions, a confirmation dialog.

Selection = semantic primary/accent state; do not encode selection only with text color.

Status = Badge, Toast, EmptyState, LoadingOverlay, or semantic text, not ad-hoc raw draw calls.

Use keyboard focus, Tab/Shift+Tab and arrow-key behavior supplied by OpenUI.

Hover state must come from OpenUI controls unless a custom data card explicitly implements it.

Narrow UI scales must reflow via Responsive, not clip.

Do not use absolute coordinates to build ordinary control layout.

Absolute positioning is allowed only for actual render content that intrinsically needs it, such as a fluid fill or image texture inside a custom component.

No UI polling in render() when the same data can be represented as a signal and updated by packet/client events.

No raw thread sleeps to "refresh later" as a final implementation.

No packet/business-rule changes merely to make the UI migration easier.

Shared theme-toggle implementation pattern

Each mod should expose one persisted client preference:

ui.theme = dark | light

Use an enum, not a boolean, so a future system or high_contrast option can be added without a file-format migration.

Suggested shape:

public enum UiThemeMode {
    DARK,
    LIGHT;

    public Theme toOpenUiTheme() {
        return this == LIGHT ? Theme.light() : Theme.dark();
    }

    public UiThemeMode next() {
        return this == DARK ? LIGHT : DARK;
    }
}

Each screen owns a signal seeded from the persisted value:

private final Signal<UiThemeMode> themeMode =
        Signals.of(ClientUiPreferences.getThemeMode());

After super.init() creates the OpenUI runtime:

@Override
protected void init() {
    super.init();
    uiRuntime().theme(themeMode.get().toOpenUiTheme());
}

The header toggle must:

Compute next = themeMode.get().next().

themeMode.set(next).

Persist next to the mod's client preference file.

Call uiRuntime().theme(next.toOpenUiTheme()).

Never close/reopen the screen just to update colors.

Use a supplier-backed button label so the same component shows the current mode, for example "☀ Light" while dark mode is active if the action means "switch to light", or "☾ Dark" while light mode is active if the action means "switch to dark". Include a tooltip such as Switch to light theme.

Do not place theme state in a server config and do not sync it over the network.

Target architecture

New/changed client classes

Create or refactor toward this structure:

forge-1.20.1/src/main/java/com/nstut/forge/client/
  EconomyUiThemeMode.java                 NEW
  MarketClientPreferences.java            EXTEND
  ui/
    EconomyUiContainerScreen.java         NEW
    EconomyUiComponents.java              NEW, only suite-level helpers
    MarketClientStore.java                NEW
    CommodityIconComponent.java           NEW, custom item/fluid render boundary
    FluidTankComponent.java               NEW, custom fluid visual only if useful
  MarketScreen.java                       REWRITE UI layer
  TankScreen.java                         REWRITE UI layer
  VaultScreen.java                        REWRITE UI layer

Do not create generic abstractions unless at least two Economy screens use them.

EconomyUiContainerScreen<M>

Create a small base class extending OpenUI UiContainerScreen<M>.

Responsibilities:

Constructor only delegates menu, inventory, title.

Holds or exposes Signal<UiThemeMode> themeMode.

In init(), call super.init() first, then apply persisted theme through uiRuntime().theme(...).

Provides protected ButtonWidget buildThemeToggle() or an equivalent helper.

Toggle persists to MarketClientPreferences.

Do not put market-specific data here.

Extend MarketClientPreferences

Current file already persists market.browse.grid in economy-client.properties. Reuse that same file.

Add:

ui.theme=dark

Refactor its property I/O so both settings are preserved by all setters.

Required methods:

public static synchronized UiThemeMode getThemeMode()
public static synchronized void setThemeMode(UiThemeMode mode)

Rules:

Missing/invalid value -> DARK.

Store lowercase strings dark or light.

Never rewrite the preference file with only one property.

Keep the current browse-grid API working unchanged.

Add unit tests for:

missing file

dark load/save

light load/save

invalid theme falls back to dark

setting theme preserves browse-grid

setting browse-grid preserves theme

MarketClientStore

MarketScreen currently mixes network cache state with view construction. Move the synchronized/display data into a signal-based client store.

At minimum create signals for:

Signal<List<MarketNetwork.ItemCardData>> cards
Signal<String> balance
Signal<Integer> vaultCount
Signal<MarketNetwork.SyncItemDetailPacket> detail
Signal<List<HistoryEntry>> history
Signal<List<MarketNetwork.VaultDetailEntry>> containerEntries
Signal<List<MarketNetwork.PortfolioPointData>> portfolioPoints
Signal<List<MarketNetwork.AssetHoldingData>> assetHoldings
Signal<List<MarketNetwork.ActiveOrderEntry>> activeOrders

Use immutable snapshots when writing lists:

cards.set(List.copyOf(packet.cards));

Use Signals.batch(...) when one packet updates multiple fields, especially item list/balance/vault count.

Migration safety:

Keep the existing static MarketScreen.handleSync... methods initially.

Change them into thin delegates to MarketClientStore.

After network handlers have been updated to call the store directly and tests pass, remove the bridge methods.

Do not keep two independent caches.

View state

Replace magic integer state with enums.

Suggested:

enum MarketView {
    BROWSE,
    DETAIL,
    NEW_ORDER,
    ORDERS,
    PORTFOLIO,
    CONTAINERS
}

enum OrdersTab { ACTIVE, HISTORY }
enum CommodityTypeFilter { ALL, ITEMS, FLUIDS }
enum BrowseActivityFilter { ALL, ACTIVE }
enum BrowseSort { PRICE_ASC, PRICE_DESC, NAME_ASC, MOST_ACTIVE }
enum HistoryFilter { ALL, SALES, PURCHASES }
enum HistorySort { NEWEST, OLDEST, HIGHEST_TOTAL }
enum ActiveOrderFilter { ALL, SELL, BUY, INFINITE }
enum ActiveOrderSort { NEWEST, OLDEST, PRICE_ASC, PRICE_DESC }
enum BrowseLayout { GRID, LIST }

Signals should replace the current fields such as:

viewMode

ordersSubTab

searchQuery

historySearchQuery

activeOrdersSearchQuery

browse/history/active-order filter mode integers

sort mode integers

browse grid boolean

selectedItemId

createSellMode

isCreateInfinite

order edit state

Derived visible collections should be Computed<List<...>>, not rebuilt inside render().

Persist only presentation choices that are already persisted or intentionally user-facing. Do not persist temporary order-edit form values by default.

Dependency wiring — Economy

Economy is Forge 1.20.1 only.

settings.gradle

Add the OpenUI composite substitution for the Forge 1.20.1 project. A minimal Economy-only mapping is sufficient:

includeBuild('../OpenUI-MC') {
    dependencySubstitution {
        substitute module('com.nstut:openui-mc-forge-1.20.1') using project(':forge-1.20.1')
    }
}

forge-1.20.1/build.gradle

Add the OpenUI development dependency.

Because this project uses ForgeGradle rather than Architectury Loom, first try a normal composite project dependency through:

implementation 'com.nstut:openui-mc-forge-1.20.1:0.0.1'

If ForgeGradle refuses to handle the composite mod dependency correctly in a run configuration, publish the OpenUI Forge 1.20.1 project to Maven Local and use:

implementation fg.deobf('com.nstut:openui-mc-forge-1.20.1:0.0.1')

Do not work around dependency problems by copying OpenUI sources into Economy.

Mod metadata

Add a required client-side dependency on mod id openui_mc in the Forge metadata. Match a compatible OpenUI version range beginning at 0.0.1.

The server-side Economy logic must not load OpenUI-only client classes during dedicated-server startup.

Visual specification

Overall market shell

Use a centered application shell approximately equivalent to the current 356×248 footprint at normal scale, but make it responsive instead of assuming those pixels always fit.

Wide layout:

┌──────────────────────────────────────────────────────────────┐
│ ECONOMY / MARKET                       balance   theme toggle │
├─────────────┬────────────────────────────────────────────────┤
│ Browse      │                                                │
│ New Order   │            active content view                 │
│ Orders      │                                                │
│ Portfolio   │                                                │
│ Containers  │                                                │
│             │                                                │
└─────────────┴────────────────────────────────────────────────┘

Narrow layout:

Replace the vertical rail with top Tabs<MarketView> or a compact horizontal navigation row.

Stack filter controls instead of shrinking text to unreadable widths.

Switch browse grid to fewer columns automatically.

Never clip primary actions off-screen.

Header:

Product title Economy.

Context subtitle (Market, Vault, Fluid Tank).

Balance as a compact card/badge with coin icon.

Theme toggle at top-right.

No decorative background texture.

Restrained hierarchy; avoid excessive glowing borders.

Color rules

Never import the legacy com.nstut.economy.ui.framework.UiTheme.

Standard components must use OpenUI theme styling.

Custom render components use theme().colors().

Positive price/change -> colors.success().

Negative price/change -> colors.danger().

Warning -> colors.warning().

Current/selected -> colors.primary().

Do not use green/red for neutral layout.

Item/fluid texture tint is domain data and may retain actual source color.

MarketScreen detailed migration

Step M1 — change base class

Change:

extends AbstractContainerScreen<MarketMenu>

to:

extends EconomyUiContainerScreen<MarketMenu>

Keep imageWidth/imageHeight as the OpenUI viewport size initially so risk is low. Only make sizing more responsive after feature parity.

Remove direct management of:

UIComponent root

EditBoxWrapper

manual addRenderableWidget(...)

syncEditBoxes()

manual root layout/render/input forwarding

old ScrollList/ScrollGrid

manual late-render modal/dropdown passes

buildUI() becomes the single top-level UI composition method.

Step M2 — split the huge screen into view builders

Keep view builders private in MarketScreen initially. Do not explode them into many files before the migration compiles.

Recommended methods:

private UIComponent buildMarketShell()
private UIComponent buildNavigation()
private UIComponent buildHeader()
private UIComponent buildBrowseView()
private UIComponent buildCommodityCard(ItemCardData card)
private UIComponent buildDetailView()
private UIComponent buildNewOrderView()
private UIComponent buildOrdersView()
private UIComponent buildActiveOrdersView()
private UIComponent buildOrderHistoryView()
private UIComponent buildPortfolioView()
private UIComponent buildContainersView()
private UIComponent buildThemeToggle()

Use Ui.switcher(activeView) or a computed/switching component for active content rather than storing six prebuilt trees and toggling visibility flags.

Step M3 — Browse view

State:

Signal<String> browseQuery
Signal<BrowseActivityFilter> browseActivity
Signal<CommodityTypeFilter> browseType
Signal<BrowseSort> browseSort
Signal<BrowseLayout> browseLayout
Computed<List<ItemCardData>> visibleBrowseCards

Controls:

Search: Ui.textField(browseQuery).

Activity/type/sort: Ui.select(...), not cycling buttons.

Grid/list toggle: compact icon button or segmented choice.

Result count: muted text/badge.

Empty result: Ui.emptyState(...).

Grid: Ui.grid(visibleBrowseCards, this::buildCommodityCard).

List: Ui.list(visibleBrowseCards, this::buildCommodityRow) if desired.

DynamicGrid must use card layout that can tolerate variable screen widths.

Each commodity card should contain:

item/fluid icon

display name

namespace/id in muted text only if useful

current/reference price

activity/volume

compact trend indicator/sparkline if data exists

click opens DETAIL and requests/uses selected detail

Do not create raw click-coordinate hit boxes around cards. Use component event/click handling.

Step M4 — Commodity icons

Create CommodityIconComponent for only the rendering behavior that OpenUI cannot express directly.

Item:

Prefer Ui.icon(ItemStack) where possible.

Fluid:

Keep Forge IClientFluidTypeExtensions, atlas sprite and tint behavior.

Put it behind a custom OpenUI component.

Cache atlas sprites by fluid id.

Render within component bounds.

Restore render shader color after fluid draw.

Never use layout coordinates outside component-local bounds.

Use theme only for surrounding border/surface, not the fluid's actual tint.

Step M5 — Detail view

Convert manual statistic boxes into Cards.

Suggested hierarchy:

[Back] Commodity name / icon                           [Buy] [Sell]

[Last price] [24h/period change] [Available/volume]

[                    price chart                    ]

[ Sell / asks table ]        [ Buy / bids table ]

Use OpenUI LineChart or AreaChart for the price series.

Requirements:

Preserve the current maximum/live-window behavior where it is meaningful.

Replace manual chart button coordinates (detailLiveBtnX/...) with a real button.

If the existing chart offset supports historical paging, represent it as a signal and recalculate chart data with Computed.

Do not manually test mouse coordinates over chart controls.

Use Table or VirtualList for asks/bids.

Show explicit empty state when no book entries exist.

Do not lose exact price formatting.

Step M6 — New Order

Use an OpenUI form-oriented layout.

State:

Signal<String> commodityQueryOrId
Signal<String> quantityText
Signal<String> priceText
Signal<Boolean> sellMode
Signal<Boolean> infiniteBuy
Signal<Optional<ItemSearchResult>> selectedCommodity
Computed<ValidationState> validation
Computed<OrderPreview> preview

Use:

TextField for commodity search/id.

Popover anchored to commodity field for autocomplete results.

A keyed VirtualList<ItemSearchResult> inside the popover, key = item id.

Tabs/segmented buttons or two explicit Buy/Sell choices.

SwitchControl for infinite buy only when valid.

quantity/price text fields.

order summary card.

primary submit button disabled until locally valid.

server remains authoritative.

Replace pendingDropdown/manual scrollbar/late render with the Popover.

Replace pendingConfirmation/manual confirmation overlay with Dialog.

The confirmation dialog must show:

Buy/Sell

commodity name + id

quantity

unit price

total

infinite status where applicable

Cancel

Confirm

Only Confirm sends the existing order packet.

Step M7 — Orders

Top-level Tabs<OrdersTab>:

Active Orders

History

Active Orders:

search field

type/filter/sort selects

responsive Table on wide screens

VirtualList row cards on narrow screens if table becomes cramped

Row actions:

Edit

Cancel

Edit must use an OpenUI Dialog, not the current manual editingOrder modal and manually positioned editQtyField/editPriceField.

Dialog owns temporary signals seeded from the selected order. Closing without save discards them.

History:

search

sale/purchase/type/sort filters

Table with semantic Buy/Sell badge

exact date/time format currently expected by players

empty state

Step M8 — Portfolio

Use:

KPI cards for total value and relevant summary numbers.

AreaChart or LineChart for portfolio history.

Table for holdings.

semantic change indicators.

responsive stacking below the chart on narrow widths.

Replace manual portfolioLiveBtnX/... hit boxes with a real OpenUI button.

Step M9 — Containers

Use a Table or VirtualList of VaultDetailEntry.

Keep container identity, capacity/contents and whatever actions are currently present.

Do not fetch data from inside row render(); the network/store signal owns the snapshot.

Step M10 — remove legacy Market plumbing

Once parity is reached, delete from MarketScreen:

buildTree()

syncEditBoxes()

direct EditBoxWrapper registration

manual root layoutTree

manual old-framework mouse event forwarding

manual dropdown rendering/scroll hit testing

manual confirmation modal drawing

screen palette alias constants

old scroll list/grid fields

stale visibility-switch helpers

Do not delete formatting/business helpers still used elsewhere.

TankScreen detailed migration

Target base

Change to:

extends EconomyUiContainerScreen<TankMenu>

Keep imageWidth = 280, imageHeight = 186 for first parity pass.

Composition

buildUI() should create:

Header: FLUID TANK, muted LIQUID RESERVE, theme toggle.

Main status card:

fluid name

amount / capacity

progress bar

fluid visualization component

mode control

Player inventory label/surface.

Fluid visualization

The actual animated/static fluid atlas draw is valid custom rendering.

Extract it from TankScreen into FluidTankComponent.

It must:

read the current TankBlockEntity fluid/capacity through suppliers or signals

draw a theme-aware outer vessel border/background

draw the real Forge fluid sprite/tint within the inner clipping region

render fill height from amount/capacity

restore shader color

have no knowledge of screen leftPos/topPos

Mode control

Replace renderModeButton() + manual mouseClicked() bounds with a real OpenUI control.

Prefer a Select<TankMode> if all modes should be directly selectable, or a button/segmented control if server protocol only supports cycling.

If the current server packet only toggles/cycles mode, preserve it:

click sends ToggleTankModePacket(targetPos)

visual label derives from menu.getMode()

tooltip text explains the three modes

do not invent a direct set-mode packet during UI migration

Mode semantic presentation:

BOTH = primary/accent

INPUT = warning or danger only if the meaning is intentionally restrictive

OUTPUT = success is acceptable if consistent with existing semantics

Vanilla slots

Do not reimplement Slot.

UiContainerScreen keeps vanilla slot rendering/input. Use renderBackgroundLayer(...) only if a theme-aware slot frame must be drawn behind the real slots.

Do not place an OpenUI interactive component on top of slots.

Test drag-splitting, shift-click, pickup, quick-move and tooltips after the migration.

VaultScreen detailed migration

Target base

Change to:

extends EconomyUiContainerScreen<VaultMenu>

Keep the current 348×200 viewport for the first pass.

Composition

Header: VAULT, subtitle SECURE ITEM STORAGE, theme toggle.

Storage card framing real vault slots.

Player inventory card framing real inventory slots.

Mode control using an actual OpenUI control.

No raw screen palette constants.

Mode packet behavior must remain ToggleVaultModePacket.

As with Tank, preserve vanilla slots and their input.

Old Economy UI framework removal

Do this only after Market, Tank and Vault compile and run with OpenUI.

Run a repository search for:

com.nstut.economy.ui.framework

Expected result after migration: zero production imports.

Then delete the entire:

forge-1.20.1/src/main/java/com/nstut/economy/ui/framework/

Also search for old UiAnimationUtil usage. Keep Economy-domain animation helpers only if genuinely used outside migrated UI; otherwise prefer OpenUI animations.

Do not leave a compatibility copy "just in case". That creates two sources of truth.

Localization pass

Move hard-coded UI strings introduced or retained by these screens into language keys.

At minimum include:

Economy / Market

Browse

New Order

Orders

Active Orders

History

Portfolio

Containers

Buy

Sell

Edit

Cancel

Confirm

Search

filter labels

sort labels

theme switch labels/tooltips

Fluid Tank / Liquid Reserve

Vault / Secure Item Storage

Both / Input / Output mode descriptions

empty/loading/error messages

Do not construct sentences by concatenating translated fragments.

Error and loading UX

For request/response data:

first load -> Skeleton or LoadingOverlay

valid empty response -> EmptyState

request failure if exposed by current protocol -> Toast/error state

successful destructive/update action -> optional short Toast

validation failure -> inline semantic validation near field

server rejection -> do not silently close form; surface reason if packet gives one

Do not use log messages as the only player feedback.

Performance requirements

Filter/sort only when relevant signals change, not every render frame.

Use VirtualList for potentially long history/order/container lists.

Use stable keys: order id if present, otherwise a stable composite key; commodity card key = commodity id.

Do not rebuild dynamic textures or fluid sprites every frame.

Batch multi-field packet updates.

Close custom Subscriptions during unmount/removed.

Repeatedly opening/closing MarketScreen must not grow subscriptions/native widgets.

Avoid rebuilding the entire root just to update balance/price data.

Implementation order

Implement in this exact sequence:

Add OpenUI dependency and runtime metadata.

Extend/refactor MarketClientPreferences with theme mode + tests.

Add EconomyUiContainerScreen and verify a trivial themed screen compiles.

Migrate VaultScreen first. It is the smallest container UI.

Test every vault inventory interaction in dark/light.

Migrate TankScreen.

Extract/test fluid visualization.

Test every tank inventory interaction and all three modes.

Add MarketClientStore.

Convert Market header/navigation and one empty view shell.

Convert Browse.

Convert Detail.

Convert New Order + autocomplete Popover + confirmation Dialog.

Convert Active Orders + edit Dialog.

Convert History.

Convert Portfolio.

Convert Containers.

Remove all manual Market modal/dropdown/edit-box plumbing.

Delete old Economy UI framework.

Run full tests + manual QA.

Only then adjust responsive polish/animation.

Do not start by deleting the old framework.

Build and test gates

After each numbered phase, run the relevant Forge 1.20.1 compile/test task used by this repository.

Minimum automated coverage to add:

preference theme persistence

derived filter/sort computations extracted into testable methods

order local validation

store packet acceptance/batching where practical

no legacy framework imports (can be a simple source-level test/grep in CI)

Manual matrix:

Case

Required

1.20.1 Forge dev client launches

yes

Dedicated server launches without client/OpenUI classload failure

yes

Market opens/closes repeatedly

yes

Vault opens/closes repeatedly

yes

Tank opens/closes repeatedly

yes

Dark theme

yes

Light theme

yes

Toggle applies while screen is open

yes

Theme persists after game restart

yes

GUI Scale Auto/1/2/3/4 where available

yes

1280×720

yes

1920×1080

yes

narrow window

yes

keyboard Tab/Shift+Tab

yes

Escape closes overlays before screen

yes

shift-click/drag inventory interactions

yes

item commodity icon

yes

fluid commodity icon

yes

long names/prices

yes

empty market/history/orders

yes

large market/history/orders

yes

Definition of done

The Economy migration is complete only when all are true:

MarketScreen uses OpenUI as its UI system.

TankScreen uses OpenUI as its UI system.

VaultScreen uses OpenUI as its UI system.

All three have live light/dark theme toggle.

Theme persists in economy-client.properties.

Existing browse-grid preference still persists.

No production import from com.nstut.economy.ui.framework.

The old framework directory is deleted.

No migrated screen uses a GUI background texture.

No OpenUI TextField is manually registered as a vanilla widget.

Manual Market confirmation/edit overlays are replaced by OpenUI Dialog.

Manual item-search overlay is replaced by OpenUI Popover/list.

Long lists are virtualized.

Market sync data is signal-backed rather than frame-polled.

Tank/Vault vanilla slot behavior is unchanged.

Both themes are readable at every supported GUI scale.

Dedicated server starts successfully.

No leaked subscriptions/widgets after repeated screen open/close.

Existing economy tests still pass.

Explicit anti-patterns for the implementation model

Do not:

copy OpenUI classes into Economy

keep the old framework and "wrap" it with OpenUI

use UiTheme.SHELL, UiTheme.TEXT_PRIMARY, etc. from the old Economy package

create new screen-level RGB constants

manually call addRenderableWidget() for OpenUI text fields

use render() as a synchronization loop

keep magic integer enums for tabs/filters if touching that code

manually draw modal hit boxes

manually draw dropdown scrollbars

replace vanilla inventory slots with fake UI slots

change order economics/network semantics

send a network packet from every render frame

silently catch and ignore UI/network exceptions

use fade animation in code intended to be copied to 26.1.2 later