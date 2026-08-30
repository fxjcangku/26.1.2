// 附魔交易所 移动状态
package com.example.addon.librarian.service;

public enum MovementStatus {
    IDLE,
    STARTING,
    PATHING,
    ARRIVED,
    FAILED,
    CANCELED,
    TIMED_OUT,
    UNAVAILABLE
}
