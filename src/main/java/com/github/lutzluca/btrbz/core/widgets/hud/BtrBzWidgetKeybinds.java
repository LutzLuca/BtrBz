package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The sole widget shortcut: a remappable normal-gameplay HUD toggle, default H. */
public final class BtrBzWidgetKeybinds {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "widgets")
    );

    private BtrBzWidgetKeybinds() {
    }

    public static void register() {
        var toggleHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.btrbz.toggle_bazaar_orders_hud",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_H,
            CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHud.consumeClick()) {
                if (client.screen != null || client.player == null || client.level == null) {
                    continue;
                }

                var definition = BtrBz.widgetRuntime().registry()
                    .find(BazaarOrdersWidgetDefinition.ID)
                    .orElseThrow();
                var store = BtrBz.widgetRuntime().stateStore();
                boolean enabled = !store.isActive(definition);

                store.setActive(definition, enabled);
                Notifier.notifyPlayer(Notifier.prefix().append(Component.literal(
                    "Bazaar Orders HUD " + (enabled ? "enabled" : "disabled")
                ).withStyle(ChatFormatting.GRAY)));
            }
        });
    }
}
