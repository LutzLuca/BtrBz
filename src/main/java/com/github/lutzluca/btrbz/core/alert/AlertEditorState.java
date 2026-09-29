package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;

import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

/** Editor transitions are separate from mounted controls so refreshes cannot change the draft. */
@Getter
@Accessors(fluent = true)
final class AlertEditorState {
    private @Nullable IndexedProduct product;
    private @Nullable UUID editingId;
    private Kind mode = Kind.Price;
    private boolean searching = true;
    @Setter
    private String query = "";
    @Setter
    private String expression = "";
    @Setter
    private PriceSource source = PriceSource.Buy;
    @Setter
    private Direction direction = Direction.Below;
    @Setter
    private LiquiditySide liquiditySide = LiquiditySide.BuyOrders;
    @Setter
    private String quantity = "";

    void mode(Kind mode) {
        if (this.mode == mode) {
            return;
        }
        this.mode = Objects.requireNonNull(mode, "mode");
        // Editing belongs to the original kind. A mode switch starts a new draft.
        this.editingId = null;
    }

    void beginSearch() {
        this.searching = true;
        this.query = "";
    }

    void cancelSearch() {
        this.searching = this.product == null;
        this.query = "";
    }

    void select(IndexedProduct product) {
        Objects.requireNonNull(product, "product");
        if (this.product == null || !this.product.productId().equals(product.productId())) {
            this.expression = "";
        }
        this.product = product;
        this.searching = false;
        this.query = "";
    }

    void refreshProduct(IndexedProduct product) {
        if (this.product != null && this.product.productId().equals(product.productId())) {
            this.product = product;
        }
    }

    void edit(UUID id, IndexedProduct product, AlertCondition condition) {
        this.mode = condition.kind();
        this.editingId = Objects.requireNonNull(id, "id");
        this.product = Objects.requireNonNull(product, "product");
        switch (condition) {
            case AlertCondition.Price price -> {
                this.source = price.type().source();
                this.direction = price.type().direction();
                this.expression = Utils.formatDecimal(price.price(), 1, true);
            }
            case AlertCondition.Liquidity liquidity -> {
                this.liquiditySide = liquidity.side();
                this.quantity = Long.toString(liquidity.quantity());
                this.expression = Utils.formatDecimal(liquidity.priceBound(), 1, true);
            }
        }
        this.searching = false;
        this.query = "";
    }

    boolean hasSelection() {
        return this.product != null && !this.searching;
    }

    AlertDraft draft() {
        return switch (this.mode) {
            case Price ->
                new AlertDraft.Price(this.product, new AlertType(this.source, this.direction), this.expression);
            case Liquidity ->
                new AlertDraft.Liquidity(this.product, this.liquiditySide, this.quantity, this.expression);
        };
    }

    void reset() {
        this.product = null;
        this.editingId = null;
        this.searching = true;
        this.query = "";
        this.expression = "";
        this.source = PriceSource.Buy;
        this.direction = Direction.Below;
        this.liquiditySide = LiquiditySide.BuyOrders;
        this.quantity = "";
    }
}
