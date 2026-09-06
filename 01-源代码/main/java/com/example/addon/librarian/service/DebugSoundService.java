// 自动图书管理员 调试声音服务
package com.example.addon.librarian.service;

public interface DebugSoundService {
    /** 播放指定调试提示音 */
    void play(DebugSoundEvent event);
}
