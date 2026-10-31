package me.x1no4ekk.core.module.inventory.enderchest;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.util.Sounds;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Multi-page ender chests stored in {@code core_enderchests}.
 * <p>
 * A {@link EnderChestSession} exists only while at least one player views it: it is created from data loaded
 * asynchronously on the first open and, once the last viewer closes, its changed pages are saved asynchronously
 * and the session is dropped. A new load of the same owner waits for that owner's pending save, so it never reads
 * stale rows. All state here is touched on the main thread only.
 */
public final class EnderChestService {

    public static final String PERMISSION = "core.enderchest";
    public static final String OTHERS_PERMISSION = "core.enderchest.others";
    public static final String MODIFY_PERMISSION = "core.enderchest.others.modify";
    public static final String PAGES_PERMISSION_PREFIX = "core.enderchest.pages.";

    private static final String SELECT_PAGES = "SELECT page, contents FROM core_enderchests WHERE uuid = ?";
    private static final String UPSERT_PAGE = "INSERT INTO core_enderchests (uuid, page, contents) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE contents = VALUES(contents)";
    /** Safety net for views that ended without a close event. */
    private static final long SWEEP_PERIOD_TICKS = 20L * 30;

    private final CorePlugin plugin;
    private final Supplier<EnderChestSettings> settings;
    private final NamespacedKey navigationKey;
    private final Map<UUID, EnderChestSession> sessions = new HashMap<>();
    /** Owners whose data is being loaded, with the players waiting to view it (cleaned on their quit). */
    private final Map<UUID, PendingLoad> loading = new HashMap<>();
    /** Last queued save per owner; entries remove themselves when the write completes. */
    private final Map<UUID, CompletableFuture<Void>> pendingSaves = new HashMap<>();
    /** Owners whose vanilla ender chest import is being written; vanilla is cleared once it succeeds. */
    private final Map<UUID, CompletableFuture<Void>> imports = new HashMap<>();
    private BukkitTask sweepTask;
    private boolean shuttingDown;

    private record PendingLoad(String ownerName, Set<UUID> viewers) {
    }

    public EnderChestService(CorePlugin plugin, Supplier<EnderChestSettings> settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.navigationKey = new NamespacedKey(plugin, "enderchest_navigation");
    }

    public EnderChestSettings settings() {
        return settings.get();
    }

    public void start() {
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
    }

    // ---------------------------------------------------------------- permissions

    /**
     * Pages the owner may use: the highest N with {@code core.enderchest.pages.N}, at least the default.
     */
    public int pageLimit(Player owner, EnderChestSettings config) {
        int limit = config.defaultPages();
        for (int pages = config.maxPages(); pages > limit; pages--) {
            if (owner.hasPermission(PAGES_PERMISSION_PREFIX + pages)) {
                return pages;
            }
        }
        return limit;
    }

    public boolean canEdit(Player viewer, EnderChestSession session) {
        return viewer.getUniqueId().equals(session.ownerId()) || viewer.hasPermission(MODIFY_PERMISSION);
    }

    private boolean mayView(Player viewer, UUID ownerId) {
        return viewer.hasPermission(viewer.getUniqueId().equals(ownerId) ? PERMISSION : OTHERS_PERMISSION);
    }

    public boolean isImporting(UUID ownerId) {
        return imports.containsKey(ownerId);
    }

    // ---------------------------------------------------------------- opening

    /**
     * Opens page 1 of the owner's chest, loading it first if no session exists.
     */
    public void open(Player viewer, UUID ownerId, String ownerName) {
        if (shuttingDown) {
            return;
        }
        EnderChestSession session = sessions.get(ownerId);
        if (session != null) {
            show(viewer, session, 1, true);
            return;
        }
        viewer.sendActionBar(plugin.messages().get("enderchest.loading"));
        PendingLoad pending = loading.get(ownerId);
        if (pending != null) {
            pending.viewers().add(viewer.getUniqueId());
            return;
        }
        pending = new PendingLoad(ownerName, new LinkedHashSet<>());
        pending.viewers().add(viewer.getUniqueId());
        loading.put(ownerId, pending);

        EnderChestSettings snapshot = settings.get();
        CompletableFuture<Void> previousSave = pendingSaves.get(ownerId);
        CompletableFuture<Void> barrier = previousSave == null
                ? CompletableFuture.completedFuture(null)
                : previousSave.exceptionally(error -> null);
        barrier.thenCompose(ignored -> plugin.database().query(connection -> selectPages(connection, ownerId)))
                .whenCompleteAsync((rows, error) -> onLoaded(ownerId, snapshot, rows, error), plugin.mainThread());
    }

    private void onLoaded(UUID ownerId, EnderChestSettings snapshot, Map<Integer, byte[]> rows, Throwable error) {
        PendingLoad pending = loading.get(ownerId);
        if (pending == null || shuttingDown) {
            return;
        }
        if (error != null) {
            failLoad(ownerId, pending, "Failed to load the ender chest of " + ownerId, error);
            return;
        }
        Map<Integer, ItemStack[]> pages = new TreeMap<>();
        try {
            for (Map.Entry<Integer, byte[]> row : rows.entrySet()) {
                pages.put(row.getKey(), ItemStack.deserializeItemsFromBytes(row.getValue()));
            }
        } catch (RuntimeException e) {
            // Never open (and later overwrite) a chest whose stored data could not be read.
            failLoad(ownerId, pending, "Corrupted ender chest data of " + ownerId + "; it was not opened", e);
            return;
        }
        if (rows.isEmpty() && snapshot.importVanilla()) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && !owner.getEnderChest().isEmpty() && !imports.containsKey(ownerId)) {
                importVanilla(owner, pending, snapshot);
                return;
            }
        }
        createSession(ownerId, pending, snapshot, pages, rows.isEmpty());
    }

    private void failLoad(UUID ownerId, PendingLoad pending, String log, Throwable error) {
        loading.remove(ownerId);
        plugin.getLogger().log(Level.SEVERE, log, error);
        for (UUID viewerId : pending.viewers()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                plugin.messages().send(viewer, "general.database-error");
            }
        }
    }

    private void createSession(UUID ownerId, PendingLoad pending, EnderChestSettings snapshot,
                               Map<Integer, ItemStack[]> pages, boolean fresh) {
        loading.remove(ownerId);
        int pageCount = Math.max(snapshot.defaultPages(), pages.isEmpty() ? 0 : Collections.max(pages.keySet()));
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            pageCount = Math.max(pageCount, pageLimit(owner, snapshot));
        }
        EnderChestSession session = new EnderChestSession(ownerId, pending.ownerName(), snapshot, navigationKey,
                pages, fresh, pageCount);
        sessions.put(ownerId, session);
        for (UUID viewerId : pending.viewers()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && mayView(viewer, ownerId)) {
                show(viewer, session, 1, true);
            }
        }
        if (!session.hasViewers()) {
            end(session);
        }
    }

    private void show(Player viewer, EnderChestSession session, int page, boolean initial) {
        boolean owner = viewer.getUniqueId().equals(session.ownerId());
        if (owner) {
            int limit = pageLimit(viewer, session.settings());
            if (page > limit) {
                plugin.messages().send(viewer, "enderchest.page-locked", Placeholder.unparsed("page", String.valueOf(page)));
                return;
            }
            session.ensurePageCount(limit);
        }
        viewer.openInventory(session.page(page));
        if (!initial || !isViewing(viewer, session)) {
            return;
        }
        Sounds.play(viewer, session.settings().openSound());
        if (!canEdit(viewer, session)) {
            plugin.messages().send(viewer, "enderchest.read-only", Placeholder.unparsed("player", session.ownerName()));
        }
    }

    private static boolean isViewing(Player player, EnderChestSession session) {
        return player.getOpenInventory().getTopInventory().getHolder(false) instanceof EnderChestHolder holder
                && holder.session() == session;
    }

    /**
     * Handles a click on navigation slot {@code index} (0-8). The page switch runs on the next tick:
     * inventories must not be opened inside InventoryClickEvent.
     */
    void navigate(Player viewer, EnderChestHolder holder, int index) {
        EnderChestSession session = holder.session();
        int target;
        if (index == session.settings().previous().slot()) {
            target = holder.page() - 1;
        } else if (index == session.settings().next().slot()) {
            target = holder.page() + 1;
        } else {
            return;
        }
        if (target < 1 || target > session.pageCount()) {
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player current = Bukkit.getPlayer(viewerId);
            if (current == null || session.isEnded() || !isViewing(current, session)) {
                return;
            }
            if (!mayView(current, session.ownerId())) {
                current.closeInventory();
                return;
            }
            show(current, session, target, false);
        });
    }

    // ---------------------------------------------------------------- vanilla import

    /**
     * Moves the owner's vanilla ender chest into the empty custom chest. The imported pages are written first;
     * vanilla is cleared only after that write succeeded, and vanilla ender chests stay closed to the owner
     * meanwhile (see the listener), so items can be neither lost nor duplicated.
     */
    private void importVanilla(Player owner, PendingLoad pending, EnderChestSettings snapshot) {
        UUID ownerId = owner.getUniqueId();
        ItemStack[] vanilla = owner.getEnderChest().getContents();
        int amount = 0;
        for (int slot = 0; slot < vanilla.length; slot++) {
            vanilla[slot] = EnderChestSession.isEmpty(vanilla[slot]) ? null : vanilla[slot].clone();
            if (vanilla[slot] != null) {
                amount++;
            }
        }
        Map<Integer, ItemStack[]> layout = layoutImport(vanilla, snapshot.storageSize(), pageLimit(owner, snapshot));
        if (layout == null) {
            plugin.messages().send(owner, "enderchest.import-no-space");
            // Not fresh: no empty marker row is written, so the import is retried while the chest stays empty.
            createSession(ownerId, pending, snapshot, Map.of(), false);
            return;
        }
        Map<Integer, byte[]> serialized = new TreeMap<>();
        layout.forEach((page, contents) -> serialized.put(page, ItemStack.serializeItemsAsBytes(contents)));

        int importedAmount = amount;
        CompletableFuture<Void> write = writePages(ownerId, serialized, "import the vanilla ender chest into");
        imports.put(ownerId, write);
        write.whenCompleteAsync((ignored, error) -> {
            CompletableFuture<Void> tracked = imports.remove(ownerId);
            PendingLoad current = loading.get(ownerId);
            if (error != null) {
                loading.remove(ownerId);
                notifyDatabaseError(current == null ? Set.of() : current.viewers());
                return;
            }
            Player online = Bukkit.getPlayer(ownerId);
            // A null tracker means the owner quit meanwhile and the quit hook already cleared vanilla.
            if (tracked != null && online != null) {
                online.getEnderChest().clear();
                plugin.messages().send(online, "enderchest.imported", Placeholder.unparsed("amount", String.valueOf(importedAmount)));
            }
            if (current != null) {
                createSession(ownerId, current, snapshot, layout, false);
            }
        }, plugin.mainThread());
    }

    /**
     * Keeps vanilla slot positions when page 1 is large enough, otherwise fills pages in order.
     *
     * @return pages to write, or {@code null} if the items do not fit into the available pages
     */
    private static Map<Integer, ItemStack[]> layoutImport(ItemStack[] vanilla, int storageSize, int pages) {
        if (storageSize >= vanilla.length) {
            ItemStack[] page = new ItemStack[storageSize];
            System.arraycopy(vanilla, 0, page, 0, vanilla.length);
            return Map.of(1, page);
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : vanilla) {
            if (item != null) {
                items.add(item);
            }
        }
        if (items.size() > storageSize * pages) {
            return null;
        }
        Map<Integer, ItemStack[]> result = new TreeMap<>();
        for (int i = 0; i < items.size(); i++) {
            result.computeIfAbsent(i / storageSize + 1, page -> new ItemStack[storageSize])[i % storageSize] = items.get(i);
        }
        return result;
    }

    // ---------------------------------------------------------------- viewers & closing

    void viewerOpened(UUID viewerId, EnderChestSession session) {
        session.addViewer(viewerId);
    }

    void viewerClosed(Player viewer, EnderChestSession session, InventoryCloseEvent.Reason reason) {
        if (session.isEnded()) {
            return;
        }
        session.removeViewer(viewer.getUniqueId());
        if (reason != InventoryCloseEvent.Reason.OPEN_NEW && reason != InventoryCloseEvent.Reason.DISCONNECT) {
            Sounds.play(viewer, session.settings().closeSound());
        }
        scheduleEndIfIdle(session);
    }

    /**
     * Checked on the next tick: a page switch closes the old page and opens the new one in the same tick,
     * so the viewer is counted again by then.
     */
    private void scheduleEndIfIdle(EnderChestSession session) {
        if (shuttingDown || session.hasViewers()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!session.isEnded() && !session.hasViewers() && sessions.get(session.ownerId()) == session) {
                end(session);
            }
        });
    }

    private void end(EnderChestSession session) {
        UUID ownerId = session.ownerId();
        sessions.remove(ownerId, session);
        Map<Integer, byte[]> changes = session.end();
        if (changes.isEmpty()) {
            return;
        }
        CompletableFuture<Void> save = writePages(ownerId, changes, "save");
        pendingSaves.put(ownerId, save);
        save.whenCompleteAsync((ignored, error) -> {
            pendingSaves.remove(ownerId, save);
            if (error != null) {
                notifyDatabaseError(Set.of(ownerId));
            }
        }, plugin.mainThread());
    }

    private void sweep() {
        for (EnderChestSession session : List.copyOf(sessions.values())) {
            session.retainViewers(viewerId -> {
                Player viewer = Bukkit.getPlayer(viewerId);
                return viewer != null && isViewing(viewer, session);
            });
            if (!session.hasViewers()) {
                end(session);
            }
        }
    }

    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        for (EnderChestSession session : List.copyOf(sessions.values())) {
            if (session.removeViewer(uuid)) {
                scheduleEndIfIdle(session);
            }
        }
        for (PendingLoad pending : loading.values()) {
            pending.viewers().remove(uuid);
        }
        CompletableFuture<Void> importWrite = imports.remove(uuid);
        if (importWrite != null && !importWrite.isCompletedExceptionally()) {
            // The imported copy is (being) written; clearing now keeps vanilla from duplicating it after the quit.
            player.getEnderChest().clear();
        }
    }

    /**
     * Closes every view and queues the final saves; DatabaseManager.close() drains them afterwards.
     */
    public void shutdown() {
        shuttingDown = true;
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof EnderChestHolder) {
                player.closeInventory();
            }
        }
        // Main-thread continuations no longer run: finish pending imports here.
        imports.forEach((ownerId, write) -> {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && !write.isCompletedExceptionally()) {
                owner.getEnderChest().clear();
            }
        });
        imports.clear();
        for (EnderChestSession session : List.copyOf(sessions.values())) {
            end(session);
        }
        sessions.clear();
        loading.clear();
        pendingSaves.clear();
    }

    // ---------------------------------------------------------------- storage

    private static Map<Integer, byte[]> selectPages(Connection connection, UUID ownerId) throws SQLException {
        Map<Integer, byte[]> rows = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PAGES)) {
            statement.setString(1, ownerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.put(rs.getInt("page"), rs.getBytes("contents"));
                }
            }
        }
        return rows;
    }

    /**
     * Writes pages in one transaction on the serial writer. A failure is logged from the writer thread with the
     * contents in Base64, so items stay recoverable even during shutdown.
     */
    private CompletableFuture<Void> writePages(UUID ownerId, Map<Integer, byte[]> pages, String action) {
        Map<Integer, byte[]> data = Map.copyOf(pages);
        CompletableFuture<Void> write = plugin.database().updateInTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_PAGE)) {
                for (Map.Entry<Integer, byte[]> page : data.entrySet()) {
                    statement.setString(1, ownerId.toString());
                    statement.setInt(2, page.getKey());
                    statement.setBytes(3, page.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
        write.whenComplete((ignored, error) -> {
            if (error == null) {
                return;
            }
            StringJoiner dump = new StringJoiner("; ");
            data.forEach((page, bytes) -> dump.add("page " + page + "=" + Base64.getEncoder().encodeToString(bytes)));
            plugin.getLogger().log(Level.SEVERE, "Failed to " + action + " the ender chest of " + ownerId
                    + ". Contents for manual recovery (Base64 of ItemStack.serializeItemsAsBytes): " + dump, error);
        });
        return write;
    }

    private void notifyDatabaseError(Set<UUID> players) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.messages().send(player, "general.database-error");
            }
        }
    }

    /**
     * Whether a raw slot of the view lies in the page's bottom (navigation) row.
     */
    static boolean isNavigationSlot(Inventory top, int rawSlot) {
        return rawSlot >= top.getSize() - 9 && rawSlot < top.getSize();
    }
}
