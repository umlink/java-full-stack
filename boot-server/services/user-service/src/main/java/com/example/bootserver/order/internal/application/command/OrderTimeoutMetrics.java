package com.example.bootserver.order.internal.application.command;

/** 订单延时任务的稳定指标名与标签键。 */
public final class OrderTimeoutMetrics {

    public static final String OPERATIONS = "bootmall.order.timeout.operations";
    public static final String PROCESSING_DELAY = "bootmall.order.timeout.processing.delay";
    public static final String OPERATION_TAG = "operation";
    public static final String RESULT_TAG = "result";

    private OrderTimeoutMetrics() {
    }
}
