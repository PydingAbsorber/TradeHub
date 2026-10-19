# Trade Hub for Forge 1.20.1

Package `com.pyding.trade_hub`, mod ID `trade_hub`, author Pyding, Java 17.

## Current UI update

- Pressing the key bound to Open Inventory closes Trade Hub, including its create-trade and item-catalog overlays. The actual key mapping is honored if the player changes it in Controls.
- Removed unused GUI PNG assets; only textures referenced by TradeScreen remain.

- Two trade cards per row, each 138 logical pixels wide, with an 18-pixel player head overlapping the bottom midpoint and the seller name directly below.
- Item quantities placed to the left of both icons at consistent offsets.
- Wanted-item picker opens a searchable scrollable overlay of all registered items in the modpack.
- Centered creation labels and Publish button; selected offer comes from the visible player inventory.
- Server errors are displayed as translated notices over the open GUI rather than chat messages.
- Snapshot updates preserve the current screen and the create dialog instead of recreating it.
- Click sound file `assets/trade_hub/sounds/trade_hub_button.ogg`, registered in `sounds.json` and used by interactive GUI controls.
- `/trade_hub createTestingTrades` is an operator-only command that adds ten randomly generated ordinary listings owned by the fake `Bot` UUID. Counts range from 1 to 64. These items are created by the command intentionally for testing.
- Translations maintained for English, Russian, Vietnamese, and Simplified Chinese.

## Building

From the project root with JDK 17 and Gradle 8.x installed, run `gradle build`. This archive includes sources and assets, not a compiled .jar. A Forge compilation or in-game test could not be run in this environment.

## Notes

Item transfers remain server-authoritative. SavedData writes are not crash-atomic; the existing trading implementation has not been proven safe against duplication or item loss on hard power failure. Do not deploy to a public economy server until transaction durability and disconnect/restart integration tests have been completed.
