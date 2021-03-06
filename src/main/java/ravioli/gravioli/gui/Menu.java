package ravioli.gravioli.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public abstract class Menu extends MenuPanel implements Listener {
    public static final ItemStack AIR = new ItemStack(Material.AIR);

    final Plugin plugin;

    private final InventoryType inventoryType;
    private final int rows;
    private boolean open;
    private boolean canInteractBottom;

    protected Component title;

    public Menu(@NotNull final Plugin plugin, @NotNull final InventoryType type) {
        this(plugin, 1, type);
    }

    public Menu(@NotNull final Plugin plugin, final int rows) {
        this(plugin, rows, InventoryType.CHEST);
    }

    private Menu(@NotNull final Plugin plugin, final int rows, @NotNull final InventoryType type) {
        super(9, rows);

        this.rows = rows;
        this.plugin = plugin;
        this.inventoryType = type;

        if (type == InventoryType.CHEST) {
            this.inventory = Bukkit.createInventory(null, rows * 9);
        } else {
            this.inventory = Bukkit.createInventory(null, type);
        }
        this.primaryMenu = this;
    }

    protected void setCanInteractWithBottom(final boolean canInteractBottom) {
        this.canInteractBottom = canInteractBottom;
    }

    private void refreshInventory() {
        final Inventory newInventory;

        if (this.inventoryType == InventoryType.CHEST) {
            newInventory = Bukkit.createInventory(null, this.rows * 9, this.title);
        } else {
            newInventory = Bukkit.createInventory(null, this.inventoryType, this.title);
        }
        for (int i = 0; i < this.inventory.getSize(); i++) {
            final ItemStack itemStack = this.inventory.getItem(i);

            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            newInventory.setItem(i, itemStack);
        }
        final List<HumanEntity> viewers = new ArrayList<>(this.inventory.getViewers());

        this.updateInventory(newInventory);

        for (final HumanEntity viewer : viewers) {
            viewer.openInventory(newInventory);
        }
    }

    protected void createInventory() {
        if (Bukkit.isPrimaryThread()) {
            this.refreshInventory();

            return;
        }
        final CountDownLatch latch = new CountDownLatch(1);

        Bukkit.getScheduler().runTask(
            this.plugin,
            () -> {
                this.refreshInventory();

                latch.countDown();
            }
        );
        try {
            latch.await();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void onPlayerInventoryClicked(@NotNull final Player player, final int slot) {

    }

    /**
     * Open the menu for the specified player.
     *
     * @param player The player opening the menu.
     */
    public final void open(@NotNull final Player player) {
        Bukkit.getServer().getScheduler()
            .runTaskAsynchronously(this.plugin, () -> {
                this.render(player);

                Bukkit.getServer().getScheduler().runTask(this.plugin, () -> {
                    player.openInventory(this.inventory);

                    Bukkit.getPluginManager().registerEvents(this, this.plugin);

                    this.open = true;
                });
            });
    }

    /**
     * Returns whether the menu is currently open.
     *
     * @return True if the menu is open, false if it is closed.
     */
    public final boolean isOpen() {
        return this.open;
    }

    @EventHandler
    public final void onShiftClick(final InventoryClickEvent event) {
        if (!event.getClick().isShiftClick()) {
            return;
        }
        final InventoryView view = event.getView();
        final Inventory inventory = view.getTopInventory();

        if (!this.inventory.equals(inventory)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public final void onClickEvent(final InventoryClickEvent event) {
        final Inventory inventory = event.getClickedInventory();

        if (inventory == null || !inventory.equals(this.inventory)) {
            return;
        }
        event.setCancelled(true);

        this.processClicks(event);
    }

    @EventHandler
    public final void onBottomClickEvent(final InventoryClickEvent event) {
        if (this.canInteractBottom || !(event.getWhoClicked() instanceof final Player player)) {
            return;
        }
        final Inventory topInventory = event.getView().getTopInventory();

        if (topInventory == null || !topInventory.equals(this.inventory)) {
            return;
        }
        final Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory == null || !clickedInventory.equals(event.getView().getBottomInventory())) {
            return;
        }
        event.setCancelled(true);

        this.onPlayerInventoryClicked(player, event.getSlot());
    }

    @EventHandler
    public final void onDrag(final InventoryDragEvent event) {
        if (!this.canInteractBottom) {
            event.setCancelled(true);

            return;
        }
        for (final int slot : event.getRawSlots()) {
            if (slot < this.inventory.getSize()) {
                event.setCancelled(true);

                break;
            }
        }
    }

    @EventHandler
    public final void onCloseEvent(final InventoryCloseEvent event) {
        if (!this.open) {
            return;
        }
        final Inventory inventory = event.getInventory();

        if (!inventory.equals(this.inventory)) {
            return;
        }
        if (!(event.getPlayer() instanceof final Player player)) {
            return;
        }
        this.handleClose(player);
    }

    private void handleClose(final Player player) {
        HandlerList.unregisterAll(this);

        this.open = false;
        this.onClose(player);

        if (player.isOnline()) {
            final PlayerInventory inventory = player.getInventory();
            final NamespacedKey key = new NamespacedKey(this.plugin, "menu_item");

            for (int i = 0; i < inventory.getSize(); i++) {
                final ItemStack itemStack = inventory.getItem(i);

                if (itemStack == null || itemStack.getType().isAir()) {
                    continue;
                }
                final ItemMeta itemMeta = itemStack.getItemMeta();
                final PersistentDataContainer container = itemMeta.getPersistentDataContainer();

                if (container.has(key, PersistentDataType.STRING)) {
                    inventory.clear(i);
                }
            }
        }
    }

    /**
     * Called when the menu is closed.
     *
     * @param player The player that opened the menu
     */
    public void onClose(@NotNull final Player player) { }
}
