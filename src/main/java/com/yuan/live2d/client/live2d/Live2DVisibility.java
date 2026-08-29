package com.yuan.live2d.client.live2d;

public final class Live2DVisibility {
    private Live2DVisibility() {}

    public record InventorySnapshot(
            boolean mainHand,
            boolean offHand,
            boolean hotbar,
            boolean inventory) {
    }

    public static boolean matches(Live2DConfig.Visibility mode, InventorySnapshot inventory) {
        if (mode == null || inventory == null) return false;
        return switch (mode) {
            case MAIN_HAND -> inventory.mainHand();
            case EITHER_HAND -> inventory.mainHand() || inventory.offHand();
            case HOTBAR -> inventory.hotbar();
            case INVENTORY -> inventory.inventory();
            case ALWAYS -> true;
        };
    }
}
