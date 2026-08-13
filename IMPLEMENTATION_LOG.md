# Implementation Log

## 2026-08-13 - Bazaar Item Info Screen and Coflnet SDK

Branch: `feat/bazaar-item-info-screen` (based on `feat/new-widget-system`)

### Research and contract decisions

- Reviewed Coflnet's Swagger/OpenAPI document and API wiki for the Bazaar `snapshot`, `history/hour`, `history/day`, `history/week`, and custom `history?start=&end=` endpoints.
- Confirmed that snapshot payloads use `buyPrice`, `sellPrice`, and `timeStamp`, while history payloads use `buy`, `sell`, `minBuy`, `maxBuy`, `minSell`, `maxSell`, `buyVolume`, `sellVolume`, `buyMovingWeek`, `sellMovingWeek`, and `timestamp`.
- Confirmed against the live API that history is returned newest-first and offsetless timestamps currently represent UTC. The SDK accepts both offset-aware and offsetless timestamp forms.
- The live hour endpoint currently omits all four min/max fields despite OpenAPI marking them required. History DTOs therefore retain the API names with nullable `Double` min/max values, and the chart omits unavailable band segments.
- Retained local dual-window pacing for the documented `30 requests/10 seconds` and `100 requests/minute` limits because response headers expose only one active window. `Retry-After` controls 429 retries.
- Snapshot and history caching honors `Cache-Control: max-age`; Cloudflare's `Age` is subtracted before establishing the local TTL.
- No `tmp/` directory was present in this worktree. Reference material was used only for interaction/chart pattern awareness; no CoflSkyCore code or dependency was inspected or used.

### Coflnet SDK module

- Added the plain-Java `coflnet-sdk` Gradle module and nested it into the Fabric mod jar.
- Public API is asynchronous and limited to the Bazaar feature: `snapshot(itemTag)` plus preset/custom `history` ranges.
- DTO records mirror Coflnet JSON names and contain no BtrBz or Minecraft domain types.
- The module owns request validation, sequential pacing, rate-limit-header awareness, bounded transient retry/backoff, typed failures, response caching, and in-flight request deduplication.
- A custom range is represented by `HistoryRange.Custom(Instant start, Instant end)` for future UI use; v1 exposes only the hour/day/week presets.

### Bazaar Item Info screen

- Added a remappable `I` hotkey for hovered real slots in container screens, including player inventory slots. Focused text inputs suppress activation.
- Hovered stacks are read with virtual slot projections suppressed and resolved exclusively through `BazaarData.resolveProduct`, which delegates to the existing conversion index and product resolver.
- Added a full-screen owo UI with current buy/sell prices, hour/day/week controls, buy/sell/band visibility filters, and loading/empty/error states.
- Added a two-line chart with shared BtrBz buy/sell colors, exact 0.1-coin price formatting, chronological history, and shaded nullable min/max bands.
- Added a visible Coflnet attribution and `Open in Coflnet` button targeting `https://sky.coflnet.com/item/{tag}`.
- Async completions are marshalled to the Minecraft client thread and stale or disposed screen updates are rejected.

### Verification

- `./gradlew.bat clean build --console=plain --no-daemon` - passed.
- Coflnet SDK local-HTTP tests cover payload parsing, timestamp variants, endpoint selection, custom ranges, 204 handling, cache/in-flight behavior, edge-cache age, validation, 429 retry, and typed errors.
- BtrBz tests cover chart geometry, nullable bands, flat/one-point ranges, visibility filters, chronological sorting, provider stale-request handling, presentation formatting, and hotkey gating.
- Verified the built mod jar contains `META-INF/jars/coflnet-sdk-1.0.0.jar` and declares it in generated Fabric metadata.

### Atomic commits

- `7ec10a4` - add the internal Coflnet Bazaar SDK.
- `91e8401` - add Bazaar history chart geometry and state provider.
- `505425b` - honor Coflnet edge-cache age.
- `8c59dc6` - add the hotkey-triggered Bazaar Item Info screen.
