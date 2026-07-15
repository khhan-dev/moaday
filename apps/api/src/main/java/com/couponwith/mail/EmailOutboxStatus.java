package com.couponwith.mail;

public enum EmailOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SENT,
    DEAD,
    CANCELLED
}
