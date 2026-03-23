package com.wearhouse.order.domain.event;

public final class OrderEventType {

    public static final String INVENTORY_RESERVE_REQUESTED = "InventoryReserveRequested";
    public static final String INVENTORY_RELEASE_REQUESTED = "InventoryReleaseRequested";
    public static final String STOCK_RESERVED = "StockReserved";
    public static final String STOCK_RESERVE_FAILED = "StockReserveFailed";
    public static final String INVENTORY_RELEASED = "InventoryReleased";
    public static final String PAYMENT_PREPARE_REQUESTED = "PaymentPrepareRequested";
    public static final String PAYMENT_AUTHORIZED = "PaymentAuthorized";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";

    private OrderEventType() {
    }
}
