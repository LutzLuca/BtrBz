# BtrBz

BtrBz is a Fabric client mod for Hypixel SkyBlock's Bazaar. It puts order status, market prices, alerts, and quick actions into Bazaar screens. Configure BtrBz with `/btrbz`.

[Download on Modrinth](https://modrinth.com/project/btrbz) | [Discord](https://discord.gg/HVaZA7PfUU) | [Issue tracker](https://github.com/LutzLuca/BtrBz/issues)

## Features

### Track orders

BtrBz tracks active buy orders and sell offers against current Bazaar data. See whether each order is at the top, matched, or undercut, and get a notification when its status changes. It can also flag when your own orders compete. The Orders HUD keeps them visible while the Bazaar is closed.

Queue position, items ahead, and fill time are estimates.

### Check prices and order books

Open an order book for the selected product, including while entering a price. Product tooltips show the current price to buy or sell an item instantly. Hold Shift to see the total for a stack or the quantity represented by a supported menu item. The Price Difference widget shows the spread per item and across your sellable inventory.

Product lookup shortcuts open items on Skyblock.bz, Coflnet, or Skyblock.Finance.

### Reuse common Bazaar actions

Keep frequently traded products in the Bookmarks widget, which also shows whether you have a buy order or sell offer on a product. Save amount presets for the quantity menu and sign entry. Filled buy orders can be flipped into sell offers with one click.

### Set price and liquidity alerts

Price alerts notify you when a price to buy or sell instantly reaches a saved target. Liquidity alerts watch whether enough items are available to buy or sell instantly within your price bound. Open the alert editor with `/btrbz alert` or the bell on a Bazaar product page. Alerts are saved between sessions.

### Protect orders and track daily usage

Order Protection can block prices that undercut too aggressively or cross the spread to a price where an instant trade would fill. The warning explains why an order was blocked, and you can hold Ctrl while confirming to override it. The Daily Limit widget compares estimated Bazaar transaction value with a personal daily limit.

### Customize widgets

The Widget Manager lets you choose which widgets appear, where they sit, and how large they are. You can also set a background color and opacity for individual widgets. Open it from `/btrbz` settings or the quick access button in the Bazaar.

## Getting started

Run `/btrbz` to configure features. Use `/btrbz alert` to manage alerts and `/btrbz preset add <amount>` to save an amount preset.

## Installation

BtrBz currently supports Minecraft 26.1 and 26.2. It requires Java 25 or newer and Fabric Loader 0.19.3 or newer.

1. Download the release for your Minecraft version from the [BtrBz Versions page](https://modrinth.com/project/btrbz/versions).
2. Install matching versions of [Fabric API](https://modrinth.com/mod/fabric-api), [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api), [Yet Another Config Lib](https://modrinth.com/mod/yacl), and [owo-lib](https://modrinth.com/mod/owo-lib).
3. Place BtrBz and its dependencies in the Minecraft `mods` folder, then start Fabric and join Hypixel SkyBlock.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional and adds another way to open BtrBz settings.

> **Note:** BtrBz is in alpha. Saved data may be reset when its format changes.

## Notes

- Bazaar prices may be delayed. Liquidity alerts can only check the top 30 orders per side returned by Hypixel.
- The Daily Limit widget estimates usage from transactions observed by the mod and uses the tax rate set with `/btrbz tax set <rate>`.
- Opening a bookmarked product uses `/bz` and requires an active Cookie Buff.

## Icon attribution

This project uses icons from [Flaticon](https://www.flaticon.com/):

- <a href="https://www.flaticon.com/free-icons/bookmark" title="bookmark icons">Bookmark icons
  created by Ian Anandara - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/instagram-tools" title="instagram-tools icons">
  Instagram-tools icons created by Dewi Sari - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/red" title="red icons">Red icons created by
  hqrloveq - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/yes" title="yes icons">Yes icons created by
  hqrloveq - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/trash" title="trash icons">Trash icons created by
  Magnific - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/question" title="question icons">Question icons created
  by Magnific - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/bell" title="bell icons">Bell icons created
  by Pixel perfect - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/redo" title="redo icons">Redo icons created
  by Creatype - Flaticon</a>
