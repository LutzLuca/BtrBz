# BtrBz

BtrBz is a Fabric client mod for Hypixel SkyBlock's Bazaar. Track your orders inside and outside the Bazaar, with notifications when their status changes.

[Download on Modrinth](https://modrinth.com/project/btrbz) | [Discord](https://discord.gg/HVaZA7PfUU) | [Issue tracker](https://github.com/LutzLuca/BtrBz/issues)

## Features

- Track buy orders and sell offers as top, matched, or undercut, with status colours, notifications, and warnings when your own orders compete
- Keep orders visible outside the Bazaar with the Orders HUD
- See order details in tooltips, including estimated queue positions, items ahead, and fill times
- View order books while entering a price and select a price from the book
- Add order book hints for your tracked orders in widgets and the native order book, with optional remaining item estimates
- Check instant buy and sell prices in item tooltips, with totals for stacks and supported menu quantities
- Compare the buy and sell price gap per item and across your sellable inventory
- Get price alerts when instant buy or sell prices reach your targets
- Set liquidity alerts for when enough items are available to trade instantly within your price limit
- Keep reached targets in a persistent alert inbox, with optional return summaries and reminders
- Bookmark products with active-order indicators and save reusable amount presets
- Flip filled buy orders into sell offers with a suggested price from the Flip Helper
- Reopen a cancelled buy order's product page, copy its remaining amount, or return to the Bazaar after placing an order
- Open product pages on Skyblock.bz, Coflnet, or Skyblock.Finance
- Guard against aggressive undercuts and prices that would trigger an instant trade, with an option to override the block
- See an overview of your order value and track estimated daily transaction value against a personal limit
- Hide temporary Bazaar progress messages and open your orders from filled-order chat messages
- Configurable widgets
- Custom colour themes with a live preview and clipboard import/export

## Getting started

Run `/btrbz` to configure the mod.

Run `/btrbz alert` to manage price and liquidity targets together. Watching contains saved conditions,
and Inbox keeps triggered alerts until you dismiss them. Dismissed alerts move to History.
Choose an item, then Price or Liquidity in the same creation form. Switching types keeps the item,
price expression and buy or sell side. Choose Once for one event, or Repeat after dismissal to
resume watching after you handle each event. A repeating watch pauses while its event is in the
Inbox, then needs the condition to stop being met and be met again. Triggers remain at least one
minute apart. Watch again in History creates a new alert with the saved target, just like creating
one in the editor. It fires immediately when fresh data already meets the condition.

Remind About Pending Alerts is enabled by default. It summarizes pending events when you return
and every 10 minutes until dismissed, including targets that are no longer met. Turning it off stops
summaries while new triggers still notify you and pending events stay saved. Chat and toast
notifications can be enabled independently. Turning both off leaves the panel and Inbox as the
interaction points. Summaries are suppressed while the Inbox is open.
Closing a screen, opening an item, or making a trade leaves the event pending. Monitoring stops
outside SkyBlock, so crossings that happen and recover entirely while you are away cannot be detected.

The compact Alerts widget appears on Bazaar screens and during order input when events are pending.
It starts in the top-right. The bell, Alerts label and count share one Inbox button, and the chevron
collapses the list.
Up to three single-line item rows offer a Bazaar shortcut and an archive icon to dismiss the event.
Hover an item for the saved condition, latest value and trigger time. Larger batches open in the Inbox.
The widget remembers its collapse state and hides when empty. Configure its position, scale and
maximum width through the widget manager. Its position is shared between Bazaar screens and order input.
During sign input, navigation waits until you finish typing.

Inbox, Watching, History, item search and both editors share a fixed dialog size that fits the window.
Long content scrolls inside it without moving the dialog when you change views.

The editor's Order book button opens the selected item's book even when its widget is disabled,
without changing that setting. Closing it returns to the same alert draft. Comparisons use
At or below and At or above in the editor, and ≤ or ≥ in saved conditions. Hover the help icon beside
the target price for expression syntax, or focus it with the keyboard.

Alert changes use the normal config save path. There is no separate alert save queue or disk-write retry.

Run `/btrbz theme` to choose BtrBz Dark, [Catppuccin Mocha](https://catppuccin.com/palette/),
[Nord](https://www.nordtheme.com/docs/colors-and-palettes/), or [Dracula](https://draculatheme.com/spec),
or make your own theme. The four presets stay available. Choose New theme to start an editable copy,
name it in the editor and click Save to keep it in your library. Preview your changes live, then click
Apply to use them. Import and Export share complete themes.

## Installation

BtrBz currently supports Minecraft 26.1 and 26.2. It requires Java 25 or newer and Fabric Loader 0.19.3 or newer.

1. Download the release for your Minecraft version from the [BtrBz Versions page](https://modrinth.com/project/btrbz/versions).
2. Install matching versions of [Fabric API](https://modrinth.com/mod/fabric-api), [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api), [Yet Another Config Lib](https://modrinth.com/mod/yacl), and [owo-lib](https://modrinth.com/mod/owo-lib).
3. Place BtrBz and its dependencies in the Minecraft `mods` folder, then start Fabric and join Hypixel SkyBlock.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional and adds another way to open BtrBz settings.

> **Note:** BtrBz is in alpha. Saved data may be reset when its format changes.

## Notes

- Bazaar prices may be delayed. Liquidity alerts can only check the top 30 orders per side returned by Hypixel
- Alerts evaluate observed market data while monitoring is active. They cannot reconstruct crossings while the client is closed or monitoring is stopped
- The Daily Limit widget estimates usage from transactions observed by the mod and uses the tax rate set with `/btrbz tax set <rate>`
- Opening a bookmarked product uses `/bz` and requires an active Cookie Buff

## Icon attribution

The compact alert panel's bell, archive and chevrons are original project pixel artwork,
bundled as texture assets. The expression-help icon uses the attributed question icon below.

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
