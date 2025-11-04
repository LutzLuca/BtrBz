package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.YetAnotherConfigLib.Builder;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ConfigScreen {

    public static void open() {
        var client = MinecraftClient.getInstance();
        client.send(() -> client
                .setScreen(ConfigScreen.create(client.currentScreen, ConfigManager.get())));
    }

    public static Screen create(Screen parent, Config config) {
        return YetAnotherConfigLib.create(ConfigManager.HANDLER, (defaults, cfg, builder) -> {
            builder.title(Text.literal(BtrBz.MOD_ID));
            buildGeneralConfig(builder, config);

            return builder;
        }).generateScreen(parent);
    }

    private static void buildGeneralConfig(Builder builder, Config config) {
        var general = ConfigCategory.createBuilder().name(Text.literal("General"))
                .group(config.trackedOrders.createGroup()).group(config.alert.createGroup())
                .group(config.orderLimit.createGroup()).group(config.bookmark.createGroup())
                .group(config.priceDiff.createGroup()).group(config.productInfo.createGroup())
                .group(config.orderCancelActions.createGroup())
                .group(config.orderHighlight.createGroup()).group(config.flipHelper.createGroup())
                .group(config.orderValueOverlay.createGroup()).group(config.orderList.getGroup())
                .build();

        builder.category(general);
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> option) {
        return BooleanControllerBuilder.create(option).onOffFormatter().coloured(true);
    }
}
