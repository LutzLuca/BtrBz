# Changelog

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
