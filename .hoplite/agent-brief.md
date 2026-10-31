# Core plugin — brief for module authors (READ FULLY BEFORE CODING)

You are a Senior Java developer writing ONE feature module of the Minecraft server plugin **Core**
(Paper 1.21.11, Java 21, Maven, package `me.x1no4ekk.core`, author x1no4ekk). Server owners are Russian:
all player-facing texts and config comments are in Russian; code identifiers/comments in English.

Project root: `/tmp/hoplite/workspace/Core`. First run `ls /tmp/hoplite/workspace/Core/src/main/java/me/x1no4ekk/core`;
if it does not exist, stop immediately and report `NO_WORKSPACE`.

Other modules are being written in parallel by other developers. **Only create files inside your own package
and your own resource/fragment files** (listed in your task). Never edit existing files.

## Read these foundation files first (do not modify)
- `CorePlugin.java` — accessors: `settings()`, `messages()`, `mainThread()`, `database()`, `userManager()`, `sessions()`,
  `commands()`, `vault()`, `placeholders()`, `vanishManager()`, `combatManager()`, `teleportService()`, `api()`,
  `registerListener(Listener)`.
- `module/CoreModule.java` — your module's main class implements it (public constructor `(CorePlugin plugin)`).
- `command/BaseCommand.java` (+ examples `command/AdminCommand.java`, `command/DelHomeCommand.java`, `command/PlayerHomeCommand.java`).
- `gui/Gui.java`, `gui/GuiListener.java`, `gui/HomeGui.java` (example paginated GUI).
- `user/User.java`, `user/UserManager.java`, `database/DatabaseManager.java`, `database/Schema.java`.
- `teleport/TeleportService.java`, `teleport/TeleportOptions.java`, `api/*`, `combat/CombatManager.java`,
  `vanish/VanishManager.java`, `hook/VaultHook.java`, `hook/PlaceholderHook.java`, `session/SessionManager.java`,
  `config/Messages.java`, `config/ConfigFile.java`, `util/*`.
- `src/main/resources/messages.yml`, `settings.yml`, `plugin.yml`.

## Key APIs
- **Module lifecycle**: `enable()` runs inside onEnable after MySQL/user cache/services are ready:
  load your YAML with `ConfigFile.load(plugin, "file.yml")` (copied from the jar on first run, bundled defaults attached),
  parse it into an immutable config object stored in a `volatile` field; create extra tables with blocking
  `plugin.database().execute(connection -> { try (Statement st = connection.createStatement()) { st.execute(...); } return null; })`
  (blocking is allowed ONLY in enable()); register listeners (`plugin.registerListener`), commands
  (`plugin.commands().register(new XCommand(...))`) and quit cleanup (`plugin.sessions().onQuit(player -> ...)`).
  `reload()` re-reads your YAML (throw on invalid → previous config stays). `disable()` cancels your tasks and flushes.
- **Commands**: extend `BaseCommand(plugin, name, permissionOrNull, description, aliases...)`; implement
  `execute(CommandSender, String[] args)` and optionally `complete(CommandSender, String[] args)` (last arg = word being typed).
  Helpers: `requirePlayer`, `requirePermission`, `requireUser`, `findPlayer`/`requireOnline` (respect vanish),
  `targetOrSelf(sender, args, index, othersPermission)`, `parseInt/parseDouble(raw, min, max)`, `joinArgs`, `playerNames`,
  `filter`, `throw fail("section.key", resolvers...)`, `throw stop()`. Commands are registered through Paper's Brigadier
  lifecycle (NOT plugin.yml); the main label overrides vanilla commands with the same name, aliases do not.
  The permission passed to the constructor hides the command from players without it.
- **Messages**: `plugin.messages().send(sender, "section.key", Placeholder.unparsed("name", value), ...)`, `get(...)` → Component.
  Templates are MiniMessage; `<prefix>` is always available; YAML lists are joined with `<newline>`.
  Use `Placeholder.unparsed` for any player/user-provided text (never parse it as MiniMessage unless the player has a
  formatting permission), `Placeholder.component` for components. A literal `<` in YAML must be written as `\<`.
  Existing sections you may reuse (read-only): `general.*` (no-permission, players-only, player-not-found, data-not-loaded,
  database-error, invalid-number, number-range, command-error), `teleport.*`, `combat.*`.
- **Database** (`plugin.database()`): `query(conn -> ...)` → `CompletableFuture<T>` on the read pool; `update(conn -> ...)`
  on the single serial writer thread (preserves order); `updateInTransaction(...)`. Always `PreparedStatement` and
  `ResultSet` in try-with-resources (the Connection itself is managed by DatabaseManager). MySQL syntax
  (`INSERT ... ON DUPLICATE KEY UPDATE`). Never `.join()`/`.get()` on the main thread. Continue on the main thread with
  `thenAcceptAsync(..., plugin.mainThread())` / `whenCompleteAsync(..., plugin.mainThread())` and re-resolve the player
  with `Bukkit.getPlayer(uuid)` (capture UUIDs, not Players). On error: log SEVERE and send `general.database-error`.
  New tables: `CREATE TABLE IF NOT EXISTS ... ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`, UUID columns
  `CHAR(36) CHARACTER SET ascii`, proper PRIMARY KEY/indexes. Do not ALTER existing core tables.
- **Users**: `User user = plugin.userManager().getUser(player)` (null if not loaded → `general.data-not-loaded`).
  Persistent fields with setters (auto-saved in batches): nickname, fly, god, vanish, tpToggle, msgToggle, chatToggle,
  socialSpy, bossbarToggle, lastLocation (/back), kills/deaths/mobKills (`addKill()` ...), `isNewPlayer()`, first/last join.
  `ignored` set (`addIgnored/removeIgnored/isIgnoring`) — persist yourself in `core_ignores(uuid, ignored_uuid)`.
  Kit cooldowns `getKitLastUsed/setKitLastUsed` — persist yourself in `core_kit_cooldowns(uuid, kit, last_used)`.
  Generic persistent cooldowns (survive relog/restart): `userManager.getCooldownRemaining(user, key)` /
  `userManager.startCooldown(user, key, millis)`. Offline lookup: `userManager.findByName(name)` →
  `CompletableFuture<Optional<Map.Entry<UUID,String>>>`.
- **Teleport**: `plugin.teleportService().teleport(player, location, TeleportOptions.PLAYER | ADMIN)` →
  `CompletableFuture<TeleportResult>` completing on the main thread. It handles warmup + boss bar, cancel on move/damage,
  combat denial, failure messages and /back; you only send the success message when result == SUCCESS.
- **Combat**: `plugin.combatManager().isInCombat(uuid)`, `getRemainingSeconds(uuid)`, `CombatManager.BYPASS_PERMISSION`.
- **Vanish**: `plugin.vanishManager().isVanished(uuid)`, `canSee(viewer, target)`, `setVanished(player, boolean)`, `getVanished()`.
- **Economy (АРЫ)** via Vault: `plugin.vault().hasEconomy()`, `balance(p)`, `has(p, amount)`, `withdraw(p, amount)` → boolean,
  `deposit(p, amount)`, `format(amount)`, `prefix(player)`, `suffix(player)`, `primaryGroup(player)`. Main thread only.
  The currency is called «АР» (АРЫ); the Economy plugin itself is written later.
- **PlaceholderAPI**: `plugin.placeholders().isEnabled()`, `apply(player, text)`, `tag(player)` → TagResolver for
  `<papi:placeholder>` in MiniMessage, `PlaceholderHook.legacy(text)` → Component.
- **GUI**: extend `gui.Gui` (Paper `InventoryHolder` pattern): constructor `(rows, Component title)`, `setItem(slot, item[, GuiAction])`,
  `clear()`, `open(player)`, override `onClose(InventoryCloseEvent)`. All clicks in a Gui are cancelled automatically and
  routed to actions `(Player, InventoryClickEvent)`. Never open/close inventories inside a click handler directly —
  schedule with `Bukkit.getScheduler().runTask(plugin, ...)` and re-resolve the player by UUID (see HomeGui). GUI fields hold
  only immutable snapshots/UUIDs. No static/global registries of open menus; detect your menu with
  `inventory.getHolder(false) instanceof YourGui`. Use `util.ItemBuilder`, `util.Text.parse/item/itemLines`.
- **Utilities**: `util.CooldownTracker` (in-memory, call `clear(uuid)` on quit), `util.Durations.format(seconds)`,
  `util.Sounds.play(player, key, volume, pitch)`, `util.Locations.serialize/deserialize`.

## Hard rules
1. Never store `Player`, `Entity`, `World`, `Inventory` of a player etc. in fields/collections that outlive a method call.
   Use UUIDs, names and immutable records. Locations only inside short-lived objects or serialized.
2. Every Map/Set keyed by a player UUID must be cleaned in `plugin.sessions().onQuit(...)`, including entries where the
   quitting player is a *value* (reply targets, pending requests, spectate targets...). Timed entries must also expire by timer.
3. All SQL asynchronous via `plugin.database()`; no blocking of the main thread; no SQLite/YAML for player data.
4. Modern API only: Adventure Components/MiniMessage (no `ChatColor`, no `§` strings in code), PDC, `RegistryAccess`
   registries, `MenuType` builders for workstation menus, `teleportAsync`. No deprecated API (your files must compile with
   zero `-Xlint:deprecation` warnings), no NMS, no reflection, no new libraries.
5. No TODO/FIXME, no stubs, no "simplified" placeholder logic — everything must actually work. Brief comments only where
   something is non-obvious. Every command validates arguments and shows a usage message.
6. Bukkit API on the main thread only (AsyncChatEvent code must be thread-safe).
7. Keep classes cohesive (one command per class is fine; group small related commands sensibly).

## Reference
Old plugin TrustEssentials (features only — do NOT copy its architecture): configs in
`/tmp/hoplite/workspace/.hoplite/te-ref/` (`language.yml` = original Russian texts, `guis.yml`, `settings.yml`, `kits.yml`,
`killermoney.yml`). Bytecode: `unzip -oq /tmp/hoplite/workspace/.hoplite/attachments/art_upload_36c3cb93a38b4c50956ebb55ac0ce466/TrustEssentials-patched.jar -d /tmp/te-$RANDOM`
then `javap -p -c` on `ru/bestrax/essentials/...` classes if you need behaviour details.
Paper API javadoc: https://jd.papermc.io/paper/1.21.11/ (the jar is in `~/.m2/repository/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/`,
inspect signatures with `javap -cp <jar> <class>` when unsure).

## Deliverables
- Java sources only under `Core/src/main/java/me/x1no4ekk/core/module/<pkg>/` (subpackages allowed); main class
  `<Name>Module implements CoreModule`.
- Your config file(s) in `Core/src/main/resources/` (names given in your task), richly commented in Russian, MiniMessage texts.
- `Core/fragments/<pkg>.messages.yml` — ONLY the top-level sections allowed in your task (will be appended to messages.yml).
  Every message key you use must exist there (or in the reusable sections above). Chat messages start with `<prefix>`.
- `Core/fragments/<pkg>.permissions.yml` — every permission you check, indented for appending under `permissions:`:
  ```
    core.example.use:
      description: 'Описание'
      default: true   # true | op | false
  ```
- Compile ONLY foundation + your package (other modules may be half-written):
  ```
  cd /tmp/hoplite/workspace && rm -rf /tmp/build-<pkg> && javac -d /tmp/build-<pkg> -cp "$(cat .hoplite/core-cp.txt)" \
    -Xlint:deprecation -Xlint:unchecked -encoding UTF-8 \
    $(find Core/src/main/java -name '*.java' -not -path '*/module/*') Core/src/main/java/me/x1no4ekk/core/module/CoreModule.java \
    $(find Core/src/main/java/me/x1no4ekk/core/module/<pkg> -name '*.java')
  ```
  Zero errors and zero warnings in your files. Validate YAML: `uv run -q --with pyyaml python -c "import yaml,sys;[yaml.safe_load(open(f)) for f in sys.argv[1:]]" <files>`.
  Check with a script that every `"section.key"` literal used in your Java exists in your fragment (or reusable sections).
- Do NOT run mvn, do NOT git add/commit/push, do NOT touch files outside your scope.
- Final report (concise): files created; commands (label, aliases, permission); config files; tables; quit-cleanup hooks;
  assumptions or deviations.
