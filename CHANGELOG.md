# Changelog

## [0.11.3-alpha] - 2026-08-29

### Changed

- Reduced allocations from about 50% to 8% in the widget manager and from about 12% to 2% while browsing Bazaar menus by caching widget previews and text render states

### Fixed

- Fixed widget content being clipped along the right or bottom edge at fractional GUI scales by rounding scissor regions outward
- Fixed hidden bookmark, Bazaar, and tracked-order rows showing hover highlights outside the visible scroll-list viewport

## [0.11.2-alpha] - 2026-08-25

### Fixed

- Fixed the client shutdown watchdog firing after BtrBz left its Bazaar API executor running
- Fixed startup logging a clipboard tracker error before Minecraft created its keyboard handler
- Fixed config serialization logging warnings after Log4j had shut down

## [0.11.1-alpha] - 2026-08-20

### Added

- Added a one-time hint explaining how to disable the Bazaar Orders HUD with its configurable hotkey

### Changed

- Restored support for Minecraft 26.2

## [0.11.0-alpha] - 2026-08-15

### Added

* Added a widget manager for configuring Bazaar widgets with live previews
* Added a draggable launcher that opens the widget manager from supported Bazaar screens
* Added a Bazaar Orders HUD for checking tracked orders without opening the Bazaar
* Added detailed and status-count views to the Bazaar Orders HUD
* Added a remappable keybind for toggling the HUD, bound to `H` by default
* Added global and per-widget settings for scale, background, placement, size, and display options
* Added optional Alt-dragging for moving widgets during normal use
* Added a filled-order count that updates when Bazaar fill messages appear in chat

### Changed

* Bazaar widgets now share the same look, placement controls, previews, tooltips, and settings
* Widgets now adjust to the current Minecraft GUI scale and shrink when they would not fit on screen
* Reworked the layouts and controls for Tracked Orders, Order Value, Full Order Book, Order Book Price Entry, Bookmarks, Order Presets, Daily Bazaar Limit, and Price Difference
* Bookmark and tracked-order lists now keep their scroll positions when moving between supported screens
* Bazaar widgets now use associated item stacks for products with unusual or legacy item data
* BtrBz now requires `owo-lib`
* Dropped Minecraft 26.2 support (as `owo-lib` is yet to release for 26.2)
* Reduced repeated widget updates while browsing Bazaar menus
* Moved order-book actions directly into the Full Order Book and Price Entry widgets

### Fixed

* Fixed widget state being lost or left behind during Bazaar screen transitions
* (somewhat) Fixed widget placement and sizing at non-default GUI scales
* Fixed several drag, visibility, and tooltip edge cases
* Fixed tracked-order fill counts and status display

### Internal

* Replaced the old widget and module implementations with a retained widget runtime built on `owo-lib`
* Moved all Bazaar widgets to a shared registry, host, configuration, preview, and lifecycle system
* Added memoized widget data sources with dependency-based cache invalidation
* Added shared retained components for lists, rows, scrolling, tooltips, placement, and widget frames
* Updated Bazaar item-stack resolution to use compatible NEU overlays with converted legacy NEU data as a fallback
* Expanded tests for widget configuration, placement, scaling, caching, previews, sessions, interactions, retained components, and Bazaar item conversion
* Added automated Java formatting and code-style checks

### Breaking

> [!CAUTION]
> This release replaces the old widget configuration. Saved widget data is not migrated.
>
> * Existing bookmarks and order presets will be cleared and must be added again
> * Widget positions and settings will return to their defaults
> * Some widgets may need to be repositioned, especially when using a non-default GUI scale
> * `owo-lib` is now required
> * Minecraft 26.2 is no longer supported

## [0.10.3-alpha] - 2026-08-12

### Fixed

- Fixed Bazaar product-page slot actions not appearing, including bookmarks, Product Info, and the Order Book button

## [0.10.2-alpha] - 2026-08-09

### Fixed

- Fixed crashes when SkyblockAddons renders virtual equipment or container-preview slots

## [0.10.1-alpha] - 2026-08-04

### Added

- Added `/btrbz conversions refresh force` for applying a marked partial conversion index when Bazaar-to-NEU mappings cannot be resolved

### Changed

- Removed the singular `/btrbz conversion` command alias

### Fixed

- Fixed an incorrect Wetwing attribute shard mapping that prevented Bazaar conversion index refreshes
- Fixed Bazaar child-screen transitions losing inventory context or dispatching duplicate close events
- Fixed tracked-order ordering and status refreshes, including changed undercut amounts and orders sharing the best price
- Fixed Order Preset state handling and prevented preset input from leaking into unrelated containers

## [0.10.0-alpha] - 2026-07-18

### Added

- Added an optional Order Book price overlay when entering custom flip prices from the Order Options screen
- Added support for Minecraft 26.2

### Changed

- Reorganized the configuration screen into clearer categories with improved labels, descriptions, examples, images, color legends, and dependency guidance
- Configuration sections now open collapsed by default for easier navigation
- Alert commands now accept clearer order type names such as `buy-order`, `sell-offer`, `insta-buy`, and `insta-sell`

## [0.9.0-alpha] - 2026-07-12

### Added

- Added `/conversions` and `/conversion` commands for checking and refreshing Bazaar item data

### Changed

- Improved Bazaar item recognition across bookmarks, alerts, tracked orders, tooltips, and other Bazaar features
- Significantly reduced memory usage while browsing Bazaar menus

### Fixed

- Fixed price alerts being saved unnecessarily and improved the reliability of reached alerts and reminders
- Fixed Order Protection attempting to parse unrelated items without Bazaar order information
- Fixed pricing and order information for enchanted books and other items with unusual Bazaar identifiers

### Internal

- Reworked Bazaar item handling to provide more reliable and consistent product matching ([LutzLuca/BtrBz#51](https://github.com/LutzLuca/BtrBz/pull/51))

### Breaking

- Bookmarks and price alerts will be reset because associated items are now stored in a new format

## [0.8.0-alpha] - 2026-06-26

### Added

- Added an Order Presets setting to hide unaffordable fixed, clipboard, and Max presets instead of showing them disabled

### Changed

- Reduced render-thread time in Bazaar menus by replacing per-slot list scans, stream checks, and duplicate map/item lookups on hot render paths
- Added shared product ID caching so Bazaar hooks no longer resolve the same product repeatedly while rendering

### Fixed

- Fixed a rare render freeze when menu projections are requested off the main game thread during quick menu swaps

## [0.7.3-alpha] - 2026-06-24

### Fixed

- Fixed Order Preset affordability and item counts using an incorrect purse value after scoreboard formatting cleanup stopped removing Hypixel's nonstandard `§j` owner token

## [0.7.2-alpha] - 2026-06-23

### Fixed

- Fixed Bazaar order parsing when item titles and lore contain raw Minecraft formatting codes

## [0.7.1-alpha] - 2026-06-22

### Fixed

- Fixed bookmarked Bazaar items disappearing after restarting on Minecraft 26.1.x

## [0.7.0-alpha] - 2026-06-21

### Changed

- Ported to Minecraft 26.1.x
- Dropped support for 1.21.10 and 1.21.11

### Fixed

- Bookmark tag now respects enabled option in display rendering
- FlipHelper double subtraction of 0.1 removed
- Order Book button clicks no longer let vanilla clicks slip through
- Screen restoration after product info lookup dismiss now works regardless of screen type
- Catharsis compatibility via IMC integration

### Internal

- Replaced split slot click/render override system with unified Slot Hook architecture
- FlipHelper display stack now cached via CachedHelperDisplay record (using SlotHook's)

## [0.6.0-alpha] - 2026-05-22

### Fixed

- Added a recursive check to prevent crashes with skyblock-api 4.1.11+
- Fixed order lists retrieval by correcting buy and sell summaries swap logic

### Changed

- Switched draggable widget position config fields to nested `Position` objects instead of separate coordinate keys
- Updated conversions.json with latest Hypixel item mappings (2026-04-29)

### Internal

- Added comprehensive unit tests for data models (UtilsTest, OrderModelsTest, OrderInfoParserTest, TimedStoreTest) covering utility methods, domain value semantics, lore parsing, and cache expiry behavior
- Added optional config flag for making message style configurable
- Added recursion detection in slot override processing to prevent infinite loops when mods like skyblock-api enumerate menu slots
- Improved null safety throughout codebase

### Breaking

- Reset saved widget positions because the alpha-stage config layout changed from flat `x`/`y` fields to nested `Position` objects with no backward-compatibility migration

## [0.5.0-alpha] - 2026-04-08

### Added

- Added self-undercut detection alerts - notifies you when your own orders get undercut by others at the same price level
- Added grouped order notifications (opt-in, not fully tested - might be a little weird) - consolidates multiple orders with identical product, type, and price into single notifications with group size display
- Added price per unit display option in order notifications

### Fixed

- Fixed order status computation with ghost orders - ghost orders from Hypixel API are now correctly treated as "Top" instead of "Matched", preventing false status escalation
- Fixed click rule to only apply outside player's inventory, matching the ItemOverride behavior
- Fixed opposing-order protection to remain independent from percentage blocking logic

### Internal

- Added references directory to gitignore
- Converted static components (BazaarOrderActions, ProductInfoProvider, OrderBookScreenController) to instance-based dependency injection
- Moved conversion loading logic into BazaarData class with improved error handling
- Converted OrderProtectionManager from singleton to constructor injection
- Renamed ModContext to ModuleContext and inlined into ModuleManager
- Downgraded sound utility and widget manager logging to trace level

## [0.4.0-alpha] - 2026-03-18

### Added

- Added estimated fill time tooltips for bazaar orders based on moving week volume
- Added sound notifications for alerts and order events
- Added queue information display in matched and undercut chat notifications
- Added clipboard volume preset (parse clipboard as number)
- Added sign screen support during order setup flow

### Changed

- Renamed filledAmount to filledAmountSnapshot in OrderInfo to clarify it's a UI snapshot value
- Changed notification message format and style

## [0.3.0-alpha] - 2026-03-10

### Added

- Added "Reopen Last Cancelled Buy Order" button in Manage Orders screen to quickly return to a product's Bazaar page
- Added active order indicators (colored dots) for items with tracked orders in the bookmark list
- Added chat message filtering for transient [Bazaar] system notifications

### Fixed

- Fixed synchronization issues when clearing tracked orders via a new batch reset mechanism
- Fixed client crashes by adding null-safe guards for configuration enum bindings
- Fixed bookmark indicators rendering when the module or feature is disabled
- Fixed price formatting in the order book to use a fixed US locale for correct clipboard copying

### Changed

- Refactored Bazaar notifications to be entirely clickable instead of just the bracketed action
- Changed order cancellation to require a optional modifier key (Ctrl/Alt) before copying the remaining amount
- Improved notification styling and internal action component structure

## [0.2.0-alpha] - 2026-03-06

### Changed

- Complete widget system rewrite with more appealing visuals and improved interactivity
- Added drag insertion indicator when reordering list items
- Order tooltips split into separate list and item configurations with different content per context
- Click callbacks now fire on mouse release instead of press for button-style behavior
- Increased default max visible children from 6 to 8 in bookmark and tracked orders modules

### Added

- Preset module MAX entry now shows calculated item count in tooltip (e.g., "71,680 items")
- MAX entry now shows "Missing X coins" tooltip when insufficient funds

### Fixed

- Click is cancelled if mouse is released outside the original item (prevents accidental activations)
- Fixed missing Bazaar menu detections menu detection (InstaBuyConfirmation, InstaSellConfirmation)
- Fixed ItemGroup menu detection logic bugs

## [0.1.3-alpha] - 2025-02-27

### Added

- Detection for the "Confirm" menu within in the Bazaar

### Fixed

- Fixed mod failing to load on Minecraft due to missing Apache HttpClient classes
- Some Item conversions
- Fixed an issue where some menus were incorrectly classified as Bazaar menus, causing widgets to appear in the wrong screens
- Fixed an issue where the custom Order Book item override in the Product Menu was also applied to the player inventory, unintentionally replacing the "Skyblock Menu" item.
- `normalizeProductName` now handles last tokens that are already Roman numerals,
  uppercasing them correctly instead of leaving them in title case.
- Fixed modmenu being incorrectly declared as a required dependency.
  It is now compile-only, meaning the mod should work correctly with or
  without modmenu installed.

## [0.1.2-alpha] - 2025-12-28

### Added

- "Display everywhere" option for the bookmark module to show bookmarks throughout all Bazaar menus, not just item-specific screens

### Changed

- Updated build system and Gradle wrapper to support Minecraft 1.21.11 with new mappings and structures
- Changed default binding for "In Bazaar" option in Tracked Orders List from `false` to `true`

### Removed

- Obsolete "Go back to Order Screen" feature from order cancel actions
- Removed automatic order screen reopening after cancelling orders
- Related configuration option `reopenOrders` from `OrderActionsConfig`

## [0.1.1-alpha] - 2025-12-06

### Added

- Tooltip delay reduction from 500ms to 200ms for faster UI feedback

### Fixed

- Dangling tooltips persisting on screen after widget interactions
- Conflict with Skyblocker's sign calculator mod
- GUI incorrectly closing when cancelling widget drag operations
- Ctrl+Right click deletion for tracked order list (now disabled to prevent accidental deletion)

### Changed

- Updated key event handling to use the new `KeyEvent` type for better compatibility
- Changed default action on matched and undercut orders to "Order"
- Default goto action changed to Order

## [0.1.0-alpha] - 2025-12-05

### Added

- Order book implementation for tracking in-game orders
- Subgroup handling in option groups for better organization
- Blocking system to prevent accidental order mistakes

### Changed

- Ported to Minecraft 1.21.10 with Yarn to Mojang mapping migration

### Fixed

- Tooltip prices calculation error
- Item filtering for parsing orders (e.g., Enchanted Hopper)
- Bookmark symbol visibility in player inventory
- Config field unnecessary sync on save
- Centralized slot checking logic

## [0.0.1-alpha] - 2025-11-13

### Content

- Initial alpha release
- See [README.md](https://github.com/LutzLuca/BtrBz) for features and usage
