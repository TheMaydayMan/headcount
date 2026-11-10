package com.gradychastain.headcount.gui;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.text.Vars;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A yes/no menu. Layout comes from {@code gui.confirm} in config.yml (or a custom section). Confirm runs at most
 * once; closing the menu any other way counts as cancel.
 */
public final class ConfirmGui extends Gui {
    private final MenuLayout layout;
    private final Vars vars;
    private final ItemStack display;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean done;

    public ConfirmGui(HeadcountPlugin plugin, Player viewer, MenuLayout layout, Vars vars, ItemStack display, Runnable onConfirm, Runnable onCancel) {
        super(plugin, viewer);
        this.layout = layout;
        this.vars = vars;
        this.display = display;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        create(layout.rows(3), layout.title(plugin, viewer, vars, "<dark_gray>Are you sure?"));
    }

    @Override
    public void render() {
        clearButtons();
        List<Integer> taken = new ArrayList<>();
        for (int s : layout.slots("confirm")) {
            set(s, layout.buttonItem(plugin, "confirm", viewer, vars), c -> finish(true));
            taken.add(s);
        }
        for (int s : layout.slots("cancel")) {
            set(s, layout.buttonItem(plugin, "cancel", viewer, vars), c -> finish(false));
            taken.add(s);
        }
        if (display != null) {
            int s = layout.slot("display", 13);
            set(s, display.clone(), null);
            taken.add(s);
        }
        layout.fill(this, plugin, viewer, vars, taken);
    }

    private void finish(boolean confirmed) {
        if (done) return;
        done = true;
        viewer.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        if (confirmed) onConfirm.run();
        else if (onCancel != null) onCancel.run();
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (done) return;
        done = true;
        if (onCancel != null && event.getReason() != InventoryCloseEvent.Reason.DISCONNECT) {
            plugin.getServer().getScheduler().runTask(plugin, onCancel);
        }
    }
}
