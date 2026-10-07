# BtrBz

BtrBz is a Fabric client mod for Hypixel SkyBlock's Bazaar. Track your orders inside and outside the Bazaar, with notifications when their status changes.

[Download on Modrinth](https://modrinth.com/project/btrbz) | [Discord](https://discord.gg/HVaZA7PfUU) | [Issue tracker](https://github.com/LutzLuca/BtrBz/issues)

## Features

- Track buy orders and sell offers as top, matched, or undercut, with status colours, notifications, and warnings when your own orders compete
- Keep orders visible outside the Bazaar with the Orders HUD
- See order details in tooltips, including estimated queue positions, items ahead, and fill times
- View order books while entering a price and select a price from the book
- Check instant buy and sell prices in item tooltips, with totals for stacks and supported menu quantities
- Compare the buy and sell price gap per item and across your sellable inventory
- Get price alerts when instant buy or sell prices reach your targets
- Set liquidity alerts for when enough items are available to trade instantly within your price limit
- Bookmark products with active-order indicators and save reusable amount presets
- Flip filled buy orders into sell offers with a suggested price from the Flip Helper
- Reopen a cancelled buy order's product page, copy its remaining amount, or return to the Bazaar after placing an order
- Open product pages on Skyblock.bz, Coflnet, or Skyblock.Finance
- Guard against aggressive undercuts and prices that would trigger an instant trade, with an option to override the block
- See an overview of your order value and track estimated daily transaction value against a personal limit
- Hide temporary Bazaar progress messages and open your orders from filled-order chat messages
- Configurable widgets

## Getting started

Run `/btrbz` to configure the mod.

## Installation

BtrBz currently supports Minecraft 26.1 and 26.2. It requires Java 25 or newer and Fabric Loader 0.19.3 or newer.

1. Download the release for your Minecraft version from the [BtrBz Versions page](https://modrinth.com/project/btrbz/versions).
2. Install matching versions of [Fabric API](https://modrinth.com/mod/fabric-api), [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api), [Yet Another Config Lib](https://modrinth.com/mod/yacl), and [owo-lib](https://modrinth.com/mod/owo-lib).
3. Place BtrBz and its dependencies in the Minecraft `mods` folder, then start Fabric and join Hypixel SkyBlock.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional and adds another way to open BtrBz settings.

> **Note:** BtrBz is in alpha. Saved data may be reset when its format changes.

## Notes

- Bazaar prices may be delayed. Liquidity alerts can only check the top 30 orders per side returned by Hypixel
- The Daily Limit widget estimates usage from transactions observed by the mod and uses the tax rate set with `/btrbz tax set <rate>`
- Opening a bookmarked product uses `/bz` and requires an active Cookie Buff

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
