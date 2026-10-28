package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;

/**
 * 渐进射速（progressive_trigger）：连续射击一段时间后射速渐增。
 * 加成幅度低于「预热」。
 * 实现点：GunData.getShootInterval TAIL（mixin）+ FireSessionTracker 连射状态。
 *
 * [max 2026-10-29 配置化] PER_LEVEL_BONUS / RAMP_UP_TIME_MS 常量删除，
 * 全部走 enchantments.progressive_trigger.*（默认值与原常量一致）；
 * 连射中断窗口 burstGapMs 也在此组（FireSessionTracker 消费）。
 */
public class ProgressiveTriggerEnchantment extends SustainedFireEnchantment {

    public ProgressiveTriggerEnchantment() {
        super(Rarity.COMMON);
    }

    /** 满渐变时每级射速加成（0.15 = 每级 +15% rpm，低于预热，config） */
    public static double perLevelBonus() {
        return SustainedFireConfig.doubleOr(SustainedFireConfig.PROG_PER_LEVEL_BONUS);
    }

    /** 连射积累到满加成所需时长（ms，config） */
    public static long rampUpTimeMs() {
        return SustainedFireConfig.intOr(SustainedFireConfig.PROG_RAMP_UP_MS);
    }

    @Override
    public int getMaxLevel() {
        return SustainedFireConfig.intOr(SustainedFireConfig.PROG_MAX_LEVEL);
    }
}