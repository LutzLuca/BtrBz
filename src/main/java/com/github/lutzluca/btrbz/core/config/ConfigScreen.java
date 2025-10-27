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
                .setScreen(ConfigScreen.create(client.currentScreen, Config.HANDLER.instance())));
    }

    public static Screen create(Screen parent, Config config) {
        return YetAnotherConfigLib.create(Config.HANDLER, (defaults, cfg, builder) -> {
            builder.title(Text.literal(BtrBz.MOD_ID));
            buildGeneralConfig(builder, config);

            return builder;
        }).generateScreen(parent);
    }

    private static void buildGeneralConfig(Builder builder, Config config) {
        var generalBuilder = ConfigCategory.createBuilder().name(Text.literal("General"));

        generalBuilder.option(config.orderLimit.createEnabledOption());
        generalBuilder.option(config.orderLimit.createCompactOption());

        generalBuilder.option(config.bookmark.createEnabledOption());
        generalBuilder.option(config.bookmark.createMaxVisibleOption());

        generalBuilder.option(config.priceDiff.createEnabledOption());

        generalBuilder.option(config.productInfo.createEnabledOption());
        generalBuilder.option(config.productInfo.createItemClickOption());
        generalBuilder.option(config.productInfo.createCtrlShiftOption());
        generalBuilder.option(config.productInfo.createShowOutsideBazaarOption());
        generalBuilder.option(config.productInfo.createSiteOption());

        generalBuilder.option(config.orderCancel.createEnabledOption());

        builder.category(generalBuilder.build());
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> option) {
        return BooleanControllerBuilder.create(option).onOffFormatter().coloured(true);
    }
}
