package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;

import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import io.vavr.control.Try;
import java.util.Objects;
import java.util.UUID;
import java.text.NumberFormat;
import java.util.Locale;
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
    @Setter
    private AlertLifecycle lifecycle = AlertLifecycle.Once;

    void mode(Kind mode) {
        if (this.mode == mode) {
            return;
        }
        if (mode == Kind.Liquidity) {
            this.liquiditySide = this.source == PriceSource.Buy ? LiquiditySide.SellOffers : LiquiditySide.BuyOrders;
        } else {
            this.source = this.liquiditySide == LiquiditySide.SellOffers ? PriceSource.Buy : PriceSource.Sell;
        }
        this.mode = Objects.requireNonNull(mode, "mode");
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
                this.expression = AlertNotice.formatPrice(price.price());
            }
            case AlertCondition.Liquidity liquidity -> {
                this.liquiditySide = liquidity.side();
                this.quantity = NumberFormat.getIntegerInstance(Locale.US).format(liquidity.quantity());
                this.expression = AlertNotice.formatPrice(liquidity.priceBound());
            }
        }
        this.searching = false;
        this.query = "";
    }

    boolean hasSelection() {
        return this.product != null && !this.searching;
    }

    Try<AlertDefinition> resolve(MarketPrices prices, long timestamp) {
        return Try.of(() -> this.mode == Kind.Liquidity ? parseQuantity(this.quantity) : 0)
            .flatMap(required -> PriceExpressionParser.parse(this.expression)
                .flatMap(expression -> expression.resolve(prices))
                .map(price -> new AlertDefinition(timestamp, this.product, this.mode == Kind.Price
                    ? new AlertCondition.Price(new AlertType(this.source, this.direction), price)
                    : new AlertCondition.Liquidity(this.liquiditySide, required, price))))
            .flatMap(AlertDefinition::validate);
    }

    private static long parseQuantity(String input) {
        String normalized = input == null ? "" : input.trim().replace(",", "");
        if (normalized.isEmpty() || normalized.chars().anyMatch(value -> value < '0' || value > '9')) {
            throw new IllegalArgumentException("Enter a positive whole-number quantity");
        }
        try {
            long value = Long.parseLong(normalized);
            if (value <= 0) {
                throw new IllegalArgumentException("Enter a positive whole-number quantity");
            }
            return value;
        } catch (NumberFormatException err) {
            throw new IllegalArgumentException("Quantity is too large", err);
        }
    }

    void reset() {
        this.mode = Kind.Price;
        this.lifecycle = AlertLifecycle.Once;
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
