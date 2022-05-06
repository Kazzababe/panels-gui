package ravioli.gravioli.gui.exception;

import org.jetbrains.annotations.NotNull;

public class InventoryException extends Throwable {
    public InventoryException(@NotNull final String message) {
        super(message);
    }
}
