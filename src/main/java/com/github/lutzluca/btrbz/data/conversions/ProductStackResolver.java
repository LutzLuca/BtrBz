package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.utils.IdentityScopedCache;
import com.mojang.serialization.Dynamic;
import io.vavr.control.Try;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.azureaaron.legacyitemdfu.LegacyItemStackFixer;
import net.azureaaron.legacyitemdfu.TypeReferences;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** Lazily decodes immutable NEU stack data after the client registries are available. */
@Slf4j
final class ProductStackResolver {
    private final ConversionIndexService service;
    private final IdentityScopedCache<String, ItemStackTemplate> templates = new IdentityScopedCache<>();

    ProductStackResolver(ConversionIndexService service) {
        this.service = service;
    }

    Optional<ItemStack> resolve(String productId) {
        if (productId == null || productId.isBlank()) {
            return Optional.empty();
        }

        var index = this.service.currentIndex();
        var stackData = index.productStackData(productId).filter(data -> ProductStackResolver.isCompatible(
            data,
            SharedConstants.getCurrentVersion().dataVersion().version()
        ));
        var legacyStackData = index.legacyProductStackData(productId);
        if (stackData.isEmpty() && legacyStackData.isEmpty()) {
            return Optional.empty();
        }

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }
        var registryAccess = level.registryAccess();

        return this.templates
            .getOrResolve(
                index,
                registryAccess,
                productId,
                () -> this.decode(
                    productId,
                    stackData,
                    legacyStackData,
                    RegistryOps.create(NbtOps.INSTANCE, registryAccess)
                )
            )
            .map(ItemStackTemplate::create);
    }

    void clear() {
        var size = this.templates.clear();
        log.trace("Cleared product stack template cache with {} entries", size);
    }

    static boolean isCompatible(ProductStackData stackData, int clientDataVersion) {
        return stackData.dataVersion() <= clientDataVersion;
    }

    private Optional<ItemStackTemplate> decode(
        String productId,
        Optional<ProductStackData> overlayData,
        Optional<LegacyProductStackData> legacyData,
        RegistryOps<Tag> registryOps
    ) {
        var baseStack = legacyData.flatMap(data -> this.decodeLegacy(productId, data, registryOps));
        var overlayStack = overlayData.flatMap(data -> this.decodeOverlay(productId, data, registryOps));
        if (baseStack.isPresent() && overlayStack.isPresent()) {
            var base = baseStack.orElseThrow();
            Try.run(() -> base.applyComponentsAndValidate(overlayStack.orElseThrow().getComponentsPatch()))
                .onFailure(err -> log.warn("Failed to apply NEU stack overlay for product {}", productId, err));
        }
        var template = baseStack.or(() -> overlayStack)
            .filter(stack -> !stack.isEmpty())
            .map(ItemStackTemplate::fromNonEmptyStack);
        var source = "compatible NEU overlay";
        if (baseStack.isPresent()) {
            source = overlayStack.isPresent()
                ? "legacy NEU data with compatible overlay"
                : "legacy NEU data";
        }
        var resolvedSource = source;
        template.ifPresent(_ -> log.debug(
            "Decoded product stack {} from {}",
            productId,
            resolvedSource
        ));
        return template;
    }

    private Optional<ItemStack> decodeOverlay(
        String productId,
        ProductStackData stackData,
        RegistryOps<Tag> registryOps
    ) {
        var result = Try.of(() -> {
            var tag = TagParser.parseCompoundFully(stackData.stackSnbt());
            return ItemStack.CODEC.parse(registryOps, tag).getOrThrow();
        });
        result.onFailure(err -> log.warn(
            "Failed to decode indexed product stack overlay {} from NEU data version {}",
            productId,
            stackData.dataVersion(),
            err
        ));
        return result.toJavaOptional().filter(stack -> !stack.isEmpty());
    }

    private Optional<ItemStack> decodeLegacy(
        String productId,
        LegacyProductStackData stackData,
        RegistryOps<Tag> registryOps
    ) {
        var result = Try.of(() -> {
            var item = new CompoundTag();
            item.put("tag", LegacyNbtParser.parse(stackData.nbtTag()));
            item.putString("id", stackData.itemId());
            item.putShort("Damage", (short) stackData.damage());
            item.putInt("Count", 1);

            var fixed = LegacyItemStackFixer.getFixer().update(
                TypeReferences.LEGACY_ITEM_STACK,
                new Dynamic<Tag>(registryOps, item),
                LegacyItemStackFixer.getFirstVersion(),
                LegacyItemStackFixer.getLatestVersion()
            );
            return LegacyStackNormalizer.normalize(ItemStack.CODEC.parse(fixed).getOrThrow());
        });
        result.onFailure(err -> log.warn("Failed to decode legacy NEU product stack {}", productId, err));
        return result.toJavaOptional().filter(stack -> !stack.isEmpty());
    }
}
