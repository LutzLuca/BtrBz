# BtrBz

BtrBz is a Hypixel SkyBlock quality of life mod focused on the experience in and around the Bazaar.

It provides order tracking, market information, price alerts, safety checks, and shortcuts for common Bazaar actions. All features can be configured through `/btrbz`.

[Download on Modrinth](https://modrinth.com/project/btrbz) | [Discord](https://discord.gg/HVaZA7PfUU) | [Issue tracker](https://github.com/LutzLuca/BtrBz/issues)

## Features

### Order Tracking and Notifications

- **Order state detection**: see whether each order is on top, matched, undercut, or unknown
- **State change notifications**: get alerted the moment an order changes, with configurable sounds and clickable navigation
- **Self-competition warnings**: warns you when your own orders compete with each other
- **Queue estimates**: shows estimated competing orders and items ahead of an order
- **State highlighting**: highlights orders by their current state and marks filled orders that are ready to claim
- **Tracked orders overlay**: a draggable overlay showing all active orders
- **Tooltips**: configurable status, queue, price, and fill time tooltips
- **Portfolio value**: displays the combined value of active and filled orders

### Market Information

- **Order book**: full order book for the selected product, also shown while entering a price
- **Quick undercut**: enter a price 0.1 coins ahead of a selected order book entry, or Ctrl-click to copy
- **Price tooltips**: buy order and sell offer prices on product tooltips, with stack prices on Shift
- **External links**: open products on Skyblock.bz, Coflnet, or Skyblock.Finance
- **Spread display**: spread per item and across the sellable inventory amount
- **Bookmarks**: overlay for frequently traded products with active order indicators

### Order Workflow

- **Amount presets**: reusable presets shown in the amount menu and beside the sign entry, including Max and Clipboard when available
- **Hide unaffordable presets**: only shows presets you can actually fill
- **Quick flip**: one-click flip action on filled buy orders
- **Cancel helpers**: copies the remaining amount and reopens the product page after cancelling a buy order
- **Return to Bazaar**: optionally reopen the Bazaar after placing an order
- **Message filter**: hides temporary Bazaar progress messages

### Order Protection and Limits

- **Aggressive price blocking**: blocks orders with unusually aggressive prices, with separate thresholds for buy orders and sell offers
- **Instant-price guard**: blocks limit orders placed at instant trade prices
- **Block explanations**: tells you why an order was blocked, with chat and sound warnings
- **Override**: confirm a blocked order by holding Ctrl
- **Daily limits**: tracks estimated daily Bazaar usage with compact and full displays

## Price Alerts

Price alerts notify you when a selected Bazaar price reaches a target. Alerts are saved between sessions and removed automatically after they trigger. Supports expressions like `buy * 1.1` and `(buy + sell) / 2`.

```
/btrbz alert
```

Search for an item in the Editor, choose a price and Above or Below, and enter a threshold. Use the Active alerts tab to edit or remove alerts.

<details>
<summary>Alert prices, expressions, and examples</summary>

### Alert prices

| Price | Price being watched |
| --- | --- |
| Buy Price | Lowest sell offer (instant buy) |
| Sell Price | Highest buy order (instant sell) |

Below triggers at or below the target; Above triggers at or above it.

### Price expressions

| Input | Meaning |
| --- | --- |
| `2500000` or `2.5m` | A fixed price |
| `5_000_000` or `5,000,000` | A formatted fixed price |
| `k`, `m`, and `b` | Thousand, million, and billion suffixes |
| `buy` | The current instant buy price |
| `sell` | The current instant sell price |
| `+`, `-`, `*`, and `/` | Addition, subtraction, multiplication, and division |
| `( )` | Groups part of an expression |

Multiplication and division are evaluated before addition and subtraction. Parentheses can be used to control the evaluation order.

The `buy` and `sell` references are resolved when the alert is saved. The resulting value becomes a fixed target and is not recalculated as the market changes.

### Examples

- Enchanted Gold Block: Sell Price, Below, `2.5m` for a fixed target.
- Smoldering V: Sell Price, Below, `sell - 5m` for a target 5 million below its current instant sell price.
- Enchanted Gold Block: Buy Price, Above, `buy * 1.1` for a target 10 percent above its current instant buy price.
- Use `(buy + sell) / 2` as the threshold for the midpoint between the two prices.

### Managing alerts

Open the Active alerts tab in `/btrbz alert`. Each alert has Edit and delete controls.

Alerts that remain active for more than a week or a month receive reminder messages. These reminders also include a removal action.

</details>

<details>
<summary>Commands</summary>

| Command | Description |
| --- | --- |
| `/btrbz` | Opens the configuration screen |
| `/btrbz alert` | Opens the alert editor and active alerts screen |
| `/btrbz orders list` | Lists orders currently tracked by the client |
| `/btrbz orders reset` | Clears the tracked order list |
| `/btrbz preset add <amount>` | Adds an order amount preset |
| `/btrbz preset remove <amount>` | Removes an order amount preset |
| `/btrbz preset list` | Lists saved presets with removal actions |
| `/btrbz preset clear` | Clears all saved presets |
| `/btrbz tax show` | Shows the configured Bazaar tax rate |
| `/btrbz tax set <rate>` | Sets the tax rate to `1`, `1.125`, or `1.25` |

</details>

## Installation

1. Open the [BtrBz Versions page](https://modrinth.com/project/btrbz/versions).
2. Download the release matching your Minecraft version.
3. Install the matching versions of [Fabric API](https://modrinth.com/mod/fabric-api) and [Yet Another Config Lib](https://modrinth.com/mod/yacl).
4. Place the downloaded files in the Minecraft `mods` folder.
5. Start the game with Fabric and run `/btrbz` in Hypixel SkyBlock.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional and provides another way to open the configuration screen.

> **Note:** BtrBz is currently in alpha. Saved data may be reset when its format changes.

## Notes

- Queue position, fill time, and daily limit values are estimates
- Market data can be delayed
- Opening a bookmarked product requires an active Cookie Buff
- The daily limit tracker uses the tax rate configured through `/btrbz tax set <rate>`

## Icon Attribution

This project uses icons from [Flaticon](https://www.flaticon.com/):

- <a href="https://www.flaticon.com/free-icons/bookmark" title="bookmark icons">Bookmark icons
  created by Ian Anandara - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/instagram-tools" title="instagram-tools icons">
  Instagram-tools icons created by Dewi Sari - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/red" title="red icons">Red icons created by
  hqrloveq - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/yes" title="yes icons">Yes icons created by
  hqrloveq - Flaticon</a>
<a href="https://www.flaticon.com/free-icons/trash" title="trash icons">Trash icons created by
  Magnific - Flaticon</a>
- <a href="https://www.flaticon.com/free-icons/question" title="question icons">Question icons created
  by Magnific - Flaticon</a>
