# Changelog

## [Unreleased]

### Changed

- Switched draggable widget position config fields to nested `Position` objects instead of separate coordinate keys

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
