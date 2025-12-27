# Changelog

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
