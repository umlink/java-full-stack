package com.example.bootserver.catalog.internal.domain;

/** 商品写模型；销售状态由领域对象统一约束。 */
public record Product(Long id, String name, String description, String status) {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String STATUS_OFF_SALE = "OFF_SALE";

    public static Product draft(String name, String description) {
        return new Product(null, name, description, STATUS_DRAFT);
    }

    public Product changeStatus(String newStatus) {
        if (!STATUS_ON_SALE.equals(newStatus) && !STATUS_OFF_SALE.equals(newStatus)) {
            throw new InvalidProductStatusException();
        }
        return new Product(id, name, description, newStatus);
    }
}
