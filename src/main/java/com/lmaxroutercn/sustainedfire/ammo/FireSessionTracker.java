package com.lmaxroutercn.sustainedfire.ammo;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连射会话追踪器：为「渐进射速」附魔提供"每把枪连续射击了多久"的状态。
 *
 * 会话键 = 射手 UUID + 枪械 ID（gunId）：
 * 不同枪互不共享连射进度（用 A 枪打热后切 B 枪，B 从零开始积累）。
 * 已知边缘 case：同 gunId 的两把枪共享连射；严格每把独立需给 ItemStack
 * 塞 NBT 实例 ID，属过度设计，暂不实现。
 *
 * 数据来源：双端各自订阅 GunFireEvent（客户端预测 / 服务端权威各持一表，
 * 同 JVM 双端（单人/LAN）也不会串表）。
 *
 * 内存治理：无轮询。会话条目只在「该枪再次开火」时被 compute 更新；
 * 表膨胀超过阈值时，借下一次开火事件做一次全表惰性清扫
 * （ConcurrentHashMap 弱一致迭代，removeIf 安全），长期游玩不泄漏。
 */
public final class FireSessionTracker {
    /** 连射中断判定窗口：距上一发超过该时长视为新连射（ms） */
    public static final long BURST_GAP_MS = 2000;

    /** 惰性清扫触发阈值：表条目超过该数时，下一次开火顺带全表清扫 */
    private static final int CLEANUP_THRESHOLD = 256;

    /** 连射会话：一次不间断射击的开始与最近一发时间戳 */
    private record Session(long burstStartMs, long lastShotMs) {}

    /** 客户端预测侧会话表（键：shooter>gunId） */
    private static final Map<String, Session> CLIENT_SESSIONS = new ConcurrentHashMap<>();

    /** 服务端权威侧会话表 */
    private static final Map<String, Session> SERVER_SESSIONS = new ConcurrentHashMap<>();

    private FireSessionTracker() {}

    /** GunFireEvent 回调：更新（或开启）该射手 × 该枪的连射会话 */
    public static void onGunFire(GunFireEvent event) {
        LivingEntity shooter = event.getShooter();
        if (shooter == null) {
            return;
        }
        // 从事件自带的枪械 stack 提取 gunId（会话的第二维度）
        ItemStack gunStack = event.getGunItemStack();
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) {
            return; // 防御：事件携带的不是枪（理论不发生）
        }
        Map<String, Session> table = event.getLogicalSide().isClient() ? CLIENT_SESSIONS : SERVER_SESSIONS;
        String key = sessionKey(shooter.getUUID(), iGun.getGunId(gunStack).toString());
        long now = System.currentTimeMillis();
        table.compute(key, (k, prev) -> {
            // 首发或中断超过窗口：新会话；否则延续原会话起点
            if (prev == null || now - prev.lastShotMs() > BURST_GAP_MS) {
                return new Session(now, now);
            }
            return new Session(prev.burstStartMs(), now);
        });
        // 惰性清扫：表膨胀时才动，正常游玩永不触发
        if (table.size() > CLEANUP_THRESHOLD) {
            table.values().removeIf(s -> now - s.lastShotMs() > BURST_GAP_MS);
        }
    }

    /**
     * 查询该射手 × 该枪的连射 ramp 系数（0~1）：连射已持续时长 / 渐进满档时长。
     *
     * @param shooterId 射手 UUID
     * @param gunId 枪械 ID（资源路径字符串，如 "tacz:xxx"）
     * @param clientSide 是否读客户端表（与查询方所在端一致）
     * @return ramp 系数，无会话时为 0
     */
    public static float rampFactor(UUID shooterId, String gunId, boolean clientSide) {
        Session session = (clientSide ? CLIENT_SESSIONS : SERVER_SESSIONS).get(sessionKey(shooterId, gunId));
        if (session == null) {
            return 0f;
        }
        long elapsed = System.currentTimeMillis() - session.burstStartMs();
        if (elapsed <= 0) {
            return 0f;
        }
        return Math.min((float) elapsed / com.lmaxroutercn.sustainedfire.enchantment.ProgressiveTriggerEnchantment.RAMP_UP_TIME_MS, 1f);
    }

    /** 会话键：射手维度 × 枪械维度（同一把枪才继承连射时长） */
    private static String sessionKey(UUID shooterId, String gunId) {
        return shooterId + ">" + gunId;
    }
}