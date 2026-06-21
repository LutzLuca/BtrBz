package com.github.lutzluca.btrbz.data;

public record ProductRef(String productId, String displayName) implements ProductIdentity {

    public ProductRef {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }

        if (displayName == null || displayName.isBlank()) {
            displayName = productId;
        }
    }

    @Override
    public String identityKey() {
        return "id:" + this.productId;
    }
}
