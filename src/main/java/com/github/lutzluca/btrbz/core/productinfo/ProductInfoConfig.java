package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class ProductInfoConfig {
    public boolean enabled = true;
    public boolean itemClickEnabled = true;
    public boolean ctrlShiftEnabled = true;
    public boolean ctrlShiftOnBazaarItems = true;
    public boolean showOutsideBazaar = false;
    public boolean priceTooltipEnabled = true;
    public Site site = Site.SkyblockBz;

    public Builder<Boolean> createEnabledOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Enable Product Information"))
            .description(OptionDescription.of(Component.literal(
                "Show Bazaar prices in tooltips and open product pages on your preferred site.")))
            .binding(true, () -> this.enabled, value -> this.enabled = value)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createItemClickOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Show Product Info Paper on Product Page"))
            .description(ConfigScreen.createDescription(
                "Click the Product Info paper in a product's Bazaar menu to open its page on your preferred site.",
                ConfigImages.ProductInfoPaper))
            .binding(true, () -> this.itemClickEnabled, value -> this.itemClickEnabled = value)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createCtrlShiftOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Enable Product Lookup Click"))
            .description(OptionDescription.of(Component.literal(
                "Hold Ctrl+Shift and click a Bazaar product to open its page on your preferred site.")))
            .binding(true, () -> this.ctrlShiftEnabled, value -> this.ctrlShiftEnabled = value)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createCtrlShiftOnBazaarItemsOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Lookup Bazaar Menu Items"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Allow Ctrl+Shift lookup for items in Bazaar menus. Normal clicks keep their usual action."),
                ConfigScreen.requires("Enable Product Lookup Click"))))
            .binding(
                true,
                () -> this.ctrlShiftOnBazaarItems,
                value -> this.ctrlShiftOnBazaarItems = value)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createShowOutsideBazaarOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Lookup Items Outside the Bazaar"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text("Allow Ctrl+Shift lookup in inventories and chests outside the Bazaar."),
                ConfigScreen.requires("Enable Product Lookup Click"))))
            .binding(false, () -> this.showOutsideBazaar, value -> this.showOutsideBazaar = value)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createPriceTooltipOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Show Price Tooltips"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Show current Bazaar buy and sell prices in product tooltips."),
                ConfigScreen.note(
                    "Hold Shift to show the market total for a stack or the full original order."))))
            .binding(
                true,
                () -> this.priceTooltipEnabled,
                value -> this.priceTooltipEnabled = value)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Site> createSiteOption() {
        return Option
            .<Site>createBuilder()
            .name(Component.literal("Preferred Information Site"))
            .description(OptionDescription.of(Component.literal(
                "Choose the site to open for Product Info and Ctrl+Shift clicks.")))
            .binding(
                Site.SkyblockBz,
                () -> this.site != null ? this.site : Site.SkyblockBz,
                site -> this.site = site)
            .controller(Site::controller);
    }

    public OptionGroup createGroup() {
        var enabledBuilder = this.createEnabledOption();
        var ctrlShiftGroup = new OptionGrouping(this.createCtrlShiftOption()).addOptions(
            this.createCtrlShiftOnBazaarItemsOption(),
            this.createShowOutsideBazaarOption());
        var rootGroup = new OptionGrouping(enabledBuilder)
            .addOptions(this.createItemClickOption())
            .addSubgroups(ctrlShiftGroup)
            .addOptions(this.createPriceTooltipOption(), this.createSiteOption());

        return OptionGroup
            .createBuilder()
            .name(Component.literal("Product Information"))
            .description(ConfigScreen.createDescription(
                "Show Bazaar prices in tooltips and open product pages on your preferred site.",
                ConfigImages.ProductInfo))
            .options(rootGroup.build())
            .collapsed(true)
            .build();
    }

    public enum Site {
        Coflnet("https://sky.coflnet.com/item/%s?range=day"),
        SkyblockBz("https://skyblock.bz/product/%s"),
        SkyblockFinance("https://skyblock.finance/items/%s");

        private final String urlFormat;

        Site(String urlFormat) {
            this.urlFormat = urlFormat;
        }

        public static EnumControllerBuilder<Site> controller(Option<Site> option) {
            return EnumControllerBuilder
                .create(option)
                .enumClass(Site.class)
                .formatValue(site -> Component
                    .literal("Use site: ")
                    .append(Component
                        .literal(site.displayName())
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        }

        public String format(String productId) {
            return String.format(this.urlFormat, productId);
        }

        public String displayName() {
            return switch (this) {
                case SkyblockBz -> "Skyblock.bz";
                case SkyblockFinance -> "Skyblock.Finance";
                case Coflnet -> "Coflnet";
            };
        }
    }
}
