# Motions Client (Fabric, Minecraft 1.21.11)

Client-side mod: animated dark UI, full A-Z item picker, auction setup + live auction card, saved settings.

## Build on Replit
1. Create a new Repl (any template, e.g. "Bash"/"Java"), upload ALL files from this zip (keep folders), or import the zip.
2. Make sure `replit.nix` and `.replit` are in the project root (Replit may ask to reload the shell).
3. Open Shell and run:  `bash build.sh`   (first build downloads Minecraft + mappings; takes several minutes)
4. Download `build/libs/motions-client-1.0.0.jar` (right-click it in the file tree -> Download).
   Do NOT use the `-sources` jar.

Memory: remapping Minecraft needs roughly 2 GB RAM. If Replit kills the build, use a paid Repl with more RAM.
If a version fails to resolve, check https://fabricmc.net/develop and update gradle.properties.

## Install
1. Install Minecraft 1.21.11 + Fabric Loader 0.18.4+ (https://fabricmc.net/use/installer/).
2. Put Fabric API 0.141.1+1.21.11 (or newer for 1.21.11) from Modrinth in `.minecraft/mods`.
3. Put `motions-client-1.0.0.jar` in the same `mods` folder, launch the Fabric profile.

## Use
- Press **Right Shift** in-game (rebind: Options > Controls > Key Binds > Motions Client).
- Auction tab: pick any item (no inventory needed) -> price -> duration -> Start auction.
  Worth (what the item is really worth) and Quantity are optional.
- Fully client-side: nothing is sent to the server. The auction is a local glass card with a countdown.
  Payments are read from chat while an auction is on (e.g. "Steve paid you $500M", "You have been paid $500M by .Bedrock Player").
  Bedrock names with a leading "." (and spaces) are supported. Only the highest payer is shown: their name in green plus
  the amount; each payment also pops up as a small green amount, and when the auction ends the winner is shown for 8 seconds.
- When an auction ends the winner's name is copied to your clipboard (toggle: Settings > Copy winner) so you can paste it into /order, /tpa etc.
  Bedrock names keep their leading "." and use "_" instead of spaces. The action-bar winner message stays up for about 8 seconds.
- Live tab: press **Start session**, then type **/bal** in chat once. Your balance at that moment is the starting point.
  After that every "X paid you" adds and every "You paid X" subtracts automatically, so "LIVE: +87.9m" at the top of the
  screen follows your profit (red with a "-" when you're down). Typing /bal again just re-syncs the balance.
  The mod only reads chat; it never sends commands.
  On donutsmp.net (any subdomain, or a server named "donut") the 2% payment tax is counted too. Choose "I pay +2%" (your payments cost 2% extra)
  or "I get -2%" (payments you receive are 2% less) in the Live tab; on any other server no tax is applied.
- Layout tab: pick the auction card or the Live pill, drag it around a mini screen, scale it 0.5x-1.5x, snap to corners, or reset.
- Item picker: one "Items" tab with every item, A-Z, with search.
- Polish: click ripples and spark bursts, drifting glow and light specks, button shine, a shimmering title, hover halos, a stretchy toggle knob, slider value bubbles, rows of items that ripple in, a rolling LIVE number with floating +$/-$ amounts, and confetti for the auction winner. Everything decorative switches off with Settings > Animations.
- Welcome intro: a 5-second animated "Welcome <name>" each time you join a server (toggle: Settings > Welcome intro).
- Settings: notifications, auction card, UI sounds, animations, glass blur (real menu blur) and blur level (1-10), animation speed,
  accent colour and glass colour (smoke, ocean, violet, rose, emerald, amber).
- Glass blur uses Minecraft's own menu blur at the level you pick in Settings; your own blur setting is put back when the menu closes.
- Config file: `.minecraft/config/motions-client.json`.

## Build without Replit (recommended if Replit runs out of memory)
### Option A: GitHub Actions (free, no install)
1. Create a free GitHub account and a new repository.
2. Upload ALL project files (including the hidden `.github` folder) and commit.
3. Open the repo's **Actions** tab -> "Build Motions Client" -> wait ~5-10 min.
4. Open the finished run, download the **motions-client-jar** artifact, unzip it to get the .jar.

### Option B: Your own PC (needs 4 GB+ free RAM)
1. Install JDK 21 (e.g. Temurin) and Gradle 9.2.1 (or run `bash build.sh` on Linux/macOS/WSL).
2. In the project folder run: `gradle build`
3. Jar is at `build/libs/motions-client-1.0.0.jar`.
