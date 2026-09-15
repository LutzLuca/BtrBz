package com.github.lutzluca.btrbz.core.alert;

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
    private boolean searching = true;
    @Setter
    private String query = "";
    @Setter
    private String expression = "";
    @Setter
    private PriceSource source = PriceSource.Buy;
    @Setter
    private Direction direction = Direction.Below;

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

    void edit(UUID id, IndexedProduct product, AlertType type, double threshold) {
        this.editingId = Objects.requireNonNull(id, "id");
        this.product = Objects.requireNonNull(product, "product");
        this.source = type.source();
        this.direction = type.direction();
        this.expression = Utils.formatDecimal(threshold, 1, true);
        this.searching = false;
        this.query = "";
    }

    boolean hasSelection() {
        return this.product != null && !this.searching;
    }

    AlertDraft draft() {
        return new AlertDraft(this.product, new AlertType(this.source, this.direction), this.expression);
    }

    void reset() {
        this.product = null;
        this.editingId = null;
        this.searching = true;
        this.query = "";
        this.expression = "";
        this.source = PriceSource.Buy;
        this.direction = Direction.Below;
    }
}
