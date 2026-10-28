package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;

/**
 * 风冷（air_cooling）：枪械射速越高，过热增速越慢。
 * 减免随 rpm 线性增长，允许降到负值（射击反而散热，下限 floor 可配置）。
 * 实现点：MKGSA.handleShootHeat（基于枪械 rpm 计算减免系数）。
 * 设计决策（max 复核通过）："射速越高"取枪械静态 rpm 解释。
 *
 * [max 2026-10-29 配置化] DEFAULT_* 三常量删除，
 * 全部走 enchantments.air_cooling.*（默认值与原常量一致）。
 */
public class AirCoolingEnchantment extends SustainedFireEnchantment {

    public AirCoolingEnchantment() {
        super(Rarity.RARE);
    }

    /** rpm 达到该阈值后开始产生减免（config） */
    public static int thresholdRpm() {
        return SustainedFireConfig.intOr(SustainedFireConfig.AIR_THRESHOLD_RPM);
    }

    /** 每 1 rpm 超出阈值带来的 perShot 减免比例（每级，config） */
    public static double perRpm() {
        return SustainedFireConfig.doubleOr(SustainedFireConfig.AIR_PER_RPM);
    }

    /** perShot 缩放系数下限，负值 = 射击时主动散热（config） */
    public static double floor() {
        return SustainedFireConfig.doubleOr(SustainedFireConfig.AIR_FLOOR);
    }

    @Override
    public int getMaxLevel() {
        return SustainedFireConfig.intOr(SustainedFireConfig.AIR_MAX_LEVEL);
    }
}