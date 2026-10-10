package com.example.bootserver.order.internal.infrastructure.messaging;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 订单延时消息、Outbox 重试和消费重试的部署配置。 */
@Validated
@ConfigurationProperties(prefix = "app.order-timeout.rocketmq")
public class RocketMqOrderTimeoutProperties {

    private boolean enabled;

    @NotBlank
    private String namesrvAddr;

    @NotBlank
    private String topic;

    @NotBlank
    private String producerGroup;

    @NotBlank
    private String consumerGroup;

    @Min(1)
    @Max(18)
    private int delayLevel;

    @NotNull
    private Duration sendTimeout;

    @NotNull
    private Duration pollInterval;

    @Min(1)
    @Max(500)
    private int batchSize;

    @Min(1)
    @Max(100)
    private int maxSendAttempts;

    @NotNull
    private Duration retryBackoff;

    @NotNull
    private Duration sendingLease;

    @Min(0)
    @Max(16)
    private int maxReconsumeTimes;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public int getDelayLevel() {
        return delayLevel;
    }

    public void setDelayLevel(int delayLevel) {
        this.delayLevel = delayLevel;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxSendAttempts() {
        return maxSendAttempts;
    }

    public void setMaxSendAttempts(int maxSendAttempts) {
        this.maxSendAttempts = maxSendAttempts;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public Duration getSendingLease() {
        return sendingLease;
    }

    public void setSendingLease(Duration sendingLease) {
        this.sendingLease = sendingLease;
    }

    public int getMaxReconsumeTimes() {
        return maxReconsumeTimes;
    }

    public void setMaxReconsumeTimes(int maxReconsumeTimes) {
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    @AssertTrue(message = "RocketMQ 时长配置必须大于等于 1 毫秒")
    public boolean isDurationConfigurationValid() {
        Duration minimum = Duration.ofMillis(1);
        return atLeast(sendTimeout, minimum) && atLeast(pollInterval, minimum)
                && atLeast(retryBackoff, minimum) && atLeast(sendingLease, minimum);
    }

    private boolean atLeast(Duration value, Duration minimum) {
        return value != null && value.compareTo(minimum) >= 0;
    }
}
