package ravioli.gravioli.gui;


import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public abstract class MenuItem {
    MenuPanel parentPanel;

    public abstract ItemStack getItemStack();

    public abstract void onClick(final InventoryClickEvent event);

    public final void update(@NotNull final Player player) {
        this.parentPanel.update(player);
    }
}
