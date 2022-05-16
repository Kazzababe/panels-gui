package ravioli.gravioli.gui;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ravioli.gravioli.gui.exception.InventoryException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

public abstract class MenuPanel {
    private final Table<Integer, Integer, MenuItem> itemTable;
    private final Table<Integer, Integer, MenuPanel> panelTable;
    private final Map<Integer, Consumer<InventoryClickEvent>> clickHandlers = new HashMap<>();

    private final int width;
    private final int height;

    private int x;
    private int y;

    protected Menu primaryMenu;
    protected Inventory inventory;

    public MenuPanel(final int width, final int height) {
        if (width < 1 || width > 9) {
            throw new IllegalArgumentException("Invalid menu panel width. Panel width must be between 1 and 9!");
        }
        if (height < 1 || height > 6) {
            throw new IllegalArgumentException("Invalid menu panel height. Panel height must be between 1 and 6!");
        }
        this.itemTable = HashBasedTable.create(height, width);
        this.panelTable = HashBasedTable.create(height, width);
        this.width = width;
        this.height = height;
    }

    protected final Inventory getInventory() {
        return this.inventory;
    }

    private void validatePositions(final int x, final int y) {
        if (x < 0 || x > this.width - 1) {
            throw new IllegalArgumentException("Invalid slot x position supplied for a panel of %s width."
                .formatted(this.width));
        }
        if (y < 0 || y > this.height - 1) {
            throw new IllegalArgumentException("Invalid slot y position supplied for a panel of %s height."
                .formatted(this.height));
        }
    }

    public final void setSlot(final int x, final int y, final MenuPanel menuPanel) {
        this.validatePositions(x, y);

        synchronized (this) {
            this.panelTable.put(y, x, menuPanel);

            menuPanel.x = x;
            menuPanel.y = y;
            menuPanel.inventory = this.inventory;
            menuPanel.primaryMenu = this.primaryMenu;
        }
    }

    public final void setSlot(final int x, final int y, final MenuItem menuItem) {
        this.validatePositions(x, y);

        synchronized (this) {
            this.itemTable.put(y, x, menuItem);

            menuItem.parentPanel = this;
        }
    }

    public final void setSlot(final int x, final int y, final ItemStack itemStack) {
        this.setSlot(x, y, new MenuIcon(itemStack));
    }

    public final void setSlot(final int x, final int y, final ItemStack itemStack,
                              final Consumer<InventoryClickEvent> clickEventConsumer) {
        this.setSlot(x, y, new MenuItemImpl(itemStack, clickEventConsumer));
    }

    public final void setSlot(final int slot, final MenuItem menuItem) {
        final int row = slot / this.width;
        final int column = slot % this.width;

        this.setSlot(column, row, menuItem);
    }

    public final void setSlot(final int slot, final ItemStack itemStack) {
        final int row = slot / this.width;
        final int column = slot % this.width;

        this.setSlot(column, row, itemStack);
    }

    public final void setSlot(final int slot, final ItemStack itemStack,
                              final Consumer<InventoryClickEvent> clickEventConsumer) {
        final int row = slot / this.width;
        final int column = slot % this.width;

        this.setSlot(column, row, itemStack, clickEventConsumer);
    }

    public final void setBorder(final ItemStack itemStack) {
        for (int x = 0; x < this.width; x++) {
            this.setSlot(x, 0, itemStack);
            this.setSlot(x, this.height - 1, itemStack);
        }
        for (int y = 0; y < this.height; y++) {
            this.setSlot(0, y, itemStack);
            this.setSlot(this.width - 1, y, itemStack);
        }
    }

    public final int getSize() {
        return this.width * this.height;
    }

    public void setTitle(@NotNull final Component title) {
        if (this.primaryMenu == null || this.primaryMenu.inventory == null) {
            return;
        }
        if (Objects.equals(title, this.primaryMenu.title)) {
            return;
        }
        this.primaryMenu.title = title;
        this.primaryMenu.createInventory();
    }

    public void update(@NotNull final Player player) {
        this.render(player);
    }

    public void updateMenu(@NotNull final Player player) {
        this.primaryMenu.render(player);
    }

    public void close(@NotNull final Player player) {
        this.close(player, null);
    }

    public void close(@NotNull final Player player, @Nullable final String message) {
        Bukkit.getScheduler().runTask(
            this.primaryMenu.plugin,
            () -> {
                player.closeInventory();

                if (message != null) {
                    player.sendMessage(Component.text(message, NamedTextColor.RED));
                }
            }
        );
    }

    void updateInventory(@NotNull final Inventory inventory) {
        this.inventory = inventory;

        for (final MenuPanel panel : this.panelTable.values()) {
            panel.updateInventory(inventory);
        }
    }

    void render(@NotNull final Player player) {
        final Map<Integer, ItemStack> itemStackMap = new TreeMap<>(Comparator.comparingInt(i -> i));

        synchronized (this) {
            this.processPopulation(player);
            this.render(itemStackMap);

            for (int i = 0; i < this.inventory.getSize(); i++) {
                final ItemStack currentItemStack = Objects.requireNonNullElse(this.inventory.getItem(i), Menu.AIR);
                final ItemStack menuItemStack = itemStackMap.getOrDefault(i, Menu.AIR);

                final int row = i / 9;
                final int column = i % 9;

                if (column < this.x || column >= this.x + this.width) {
                    continue;
                }
                if (row < this.y || row >= this.y + this.height) {
                    continue;
                }
                if (menuItemStack == null || currentItemStack.equals(menuItemStack)) {
                    continue;
                }
                if (menuItemStack.hasItemMeta()) {
                    final ItemMeta itemMeta = menuItemStack.getItemMeta();
                    final PersistentDataContainer container = itemMeta.getPersistentDataContainer();

                    container.set(new NamespacedKey(this.primaryMenu.plugin, "menu_item"), PersistentDataType.STRING, "1");
                    menuItemStack.setItemMeta(itemMeta);
                }
                this.inventory.setItem(i, menuItemStack);
            }
        }
        this.processPostPopulation(player);
    }

    private void render(final Map<Integer, ItemStack> itemStackMap) {
        this.clickHandlers.clear();

        final List<MenuPanel> foundMenuPanels = new ArrayList<>();

        for (int row = this.y; row < this.y + this.height; row++) {
            if (row >= 6) {
                break;
            }
            for (int column = this.x; column < this.x + this.width; column++) {
                if (column >= 9) {
                    break;
                }
                final int slot = row * 9 + column;
                final MenuItem menuItem = this.itemTable.get(row - this.y, column - this.x);

                if (menuItem != null) {
                    itemStackMap.put(
                        slot,
                        menuItem.getItemStack()
                    );
                    this.clickHandlers.put(slot, menuItem::onClick);
                }
                final MenuPanel menuPanel = this.panelTable.get(row - this.y, column - this.x);

                if (menuPanel != null) {
                    foundMenuPanels.add(menuPanel);
                }
            }
        }
        foundMenuPanels.forEach((menuPanel) -> {
            menuPanel.render(itemStackMap);
        });
    }

    void processPopulation(final Player player) {
        this.itemTable.clear();

        try {
            this.populate(player);
        } catch (final InventoryException e) {
            this.close(player, e.getMessage());

            return;
        }
        this.panelTable.values().forEach((panel) -> panel.processPopulation(player));
    }

    void processPostPopulation(final Player player) {
        this.postPopulate(player);

        this.panelTable.values().forEach((panel) -> panel.processPostPopulation(player));
    }

    void processClicks(final InventoryClickEvent event) {
        final int slot = event.getSlot();
        final Consumer<InventoryClickEvent> clickConsumer = this.clickHandlers.get(slot);

        if (clickConsumer != null) {
            clickConsumer.accept(event);
        }
        this.panelTable.values().forEach((panel) -> panel.processClicks(event));
    }

    protected void postPopulate(@NotNull final Player player) {

    }

    protected abstract void populate(@NotNull final Player player) throws InventoryException;

    private static class MenuIcon extends MenuItem {
        private final ItemStack itemStack;

        private MenuIcon(final ItemStack itemStack) {
            this.itemStack = itemStack;
        }

        @Override
        public ItemStack getItemStack() {
            return this.itemStack;
        }

        @Override
        public void onClick(final InventoryClickEvent __) {

        }
    }

    private static class MenuItemImpl extends MenuIcon {
        private final Consumer<InventoryClickEvent> clickEventConsumer;

        private MenuItemImpl(final ItemStack itemStack, final Consumer<InventoryClickEvent> clickEventConsumer) {
            super(itemStack);

            this.clickEventConsumer = clickEventConsumer;
        }

        @Override
        public void onClick(final InventoryClickEvent event) {
            if (this.clickEventConsumer == null) {
                return;
            }
            this.clickEventConsumer.accept(event);
        }
    }
}
