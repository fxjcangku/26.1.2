package com.example.addon.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 语音播报系统 - 关键状态音效提示
 */
public class SoundNotifier {
    private final Minecraft mc = Minecraft.getInstance();
    
    private boolean enabled = true;
    private float volume = 1.0f;
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }
    
    private void playSound(float pitch) {
        if (!enabled || mc.player == null || mc.level == null) return;
        mc.level.playLocalSound(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS,
            volume,
            pitch,
            false
        );
    }
    
    /**
     * 卸货完成 - 清脆高音
     */
    public void notifyUnloadComplete() {
        playSound(1.5f);
    }
    
    /**
     * 食物不足 - 中音警示
     */
    public void notifyLowFood() {
        playSound(0.8f);
    }
    
    /**
     * 工具损坏 - 低音警告
     */
    public void notifyToolDamaged() {
        playSound(0.5f);
    }
    
    /**
     * 卡死检测 - 急促警报
     */
    public void notifyStuck() {
        if (!enabled || mc.player == null || mc.level == null) return;
        mc.level.playLocalSound(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            SoundEvents.ANVIL_FALL,
            SoundSource.PLAYERS,
            volume,
            0.6f,
            false
        );
    }
    
    /**
     * 死亡事件 - 沉重音效
     */
    public void notifyDeath() {
        if (!enabled || mc.player == null || mc.level == null) return;
        mc.level.playLocalSound(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            SoundEvents.WITHER_SPAWN,
            SoundSource.HOSTILE,
            volume * 0.5f,
            0.8f,
            false
        );
    }
}
