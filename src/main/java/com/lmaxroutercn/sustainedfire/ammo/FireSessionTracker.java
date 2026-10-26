package com.lmaxroutercn.sustainedfire.ammo;

import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连射会话追踪器：为「渐进射速」附魔提供"连续射击了多久"的状态。
 *
 * 数据来源：双端各自订阅 GunFireEvent（客户端预测 / 服务端权威各维护一份表，
 * 同 JVM 双端（单人/LAN）也不会串表）。
 *
 * 状态本身是 (lastShotMs, burstStartMs) 二元组，每发一次 O(1) 更新，
 * ramp 查询也是 O(1) map 读取——无轮询，全事件驱动。
 */
public final class FireSessionTracker {
    /** 连射中断判定窗口：距上一发超过该时长视为新连射（ms） */
    public static final long BURST_GAP_MS = 2000;

    /** 连射会话：一次不间断射击的开始与最近一发时间戳 */
    private record Session(long burstStartMs, long lastShotMs) {}

    /** 客户端预测侧会话表（键：shooter UUID 字符串） */
    private static final Map<String, Session> CLIENT_SESSIONS = new ConcurrentHashMap<>();

    /** 服务端权威侧会话表 */
    private static final Map<String, Session> SERVER_SESSIONS = new ConcurrentHashMap<>();

    private FireSessionTracker() {}

    /** GunFireEvent 回调：更新（或开启）该实体的连射会话 */
    public static void onGunFire(GunFireEvent event) {
        LivingEntity shooter = event.getShooter();
        if (shooter == null) {
            return;
        }
        Map<String, Session> table = event.getLogicalSide().isClient() ? CLIENT_SESSIONS : SERVER_SESSIONS;
        String key = shooter.getUUID().toString();
        long now = System.currentTimeMillis();
        table.compute(key, (k, prev) -> {
            // 首发或中断超过窗口：新会话；否则延续原会话起点
            if (prev == null || now - prev.lastShotMs() > BURST_GAP_MS) {
                return new Session(now, now);
            }
            return new Session(prev.burstStartMs(), now);
        });
    }

    /**
     * 查询连射 ramp 系数（0~1）：当前连射已持续时长 / 渐进满档时长。
     *
     * @param shooterId 射手 UUID
     * @param clientSide 是否读客户端表（与查询方所在端一致）
     * @return ramp 系数，无会话时为 0
     */
    public static float rampFactor(UUID shooterId, boolean clientSide) {
        Session session = (clientSide ? CLIENT_SESSIONS : SERVER_SESSIONS).get(shooterId.toString());
        if (session == null) {
            return 0f;
        }
        long elapsed = System.currentTimeMillis() - session.burstStartMs();
        if (elapsed <= 0) {
            return 0f;
        }
        return Math.min((float) elapsed / com.lmaxroutercn.sustainedfire.enchantment.ProgressiveTriggerEnchantment.RAMP_UP_TIME_MS, 1f);
    }
}