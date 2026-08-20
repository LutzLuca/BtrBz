package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetStateStore;
import com.github.lutzluca.btrbz.utils.GameUtils;
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
        Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "widgets"));

    private BtrBzWidgetKeybinds() {}

    public static KeyMapping registerMapping() {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.btrbz.toggle_bazaar_orders_hud",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_H,
            CATEGORY));
    }

    public static void registerHandler(
        KeyMapping toggleHud,
        WidgetDefinition<?, ?, ?> definition,
        WidgetStateStore store,
        Runnable dismissHint
    ) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHud.consumeClick()) {
                if (!canToggleHud(GameUtils.screen() != null, client.player == null, client.level == null)) {
                    continue;
                }

                boolean enabled = !store.isActive(definition);

                store.setActive(definition, enabled, false);
                dismissHint.run();
                store.save();
                Notifier.notifyPlayer(Notifier.prefix().append(Component.literal(
                    "Bazaar Orders HUD " + (enabled ? "enabled" : "disabled")).withStyle(ChatFormatting.GRAY)));
            }
        });
    }

    static boolean canToggleHud(boolean screenOpen, boolean playerMissing, boolean levelMissing) {
        return !screenOpen && !playerMissing && !levelMissing;
    }
}
