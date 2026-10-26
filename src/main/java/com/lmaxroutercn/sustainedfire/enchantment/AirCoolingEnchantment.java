package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

/**
 * 风冷（air_cooling）：枪械射速越高，过热增速越慢。
 * 减免随 rpm 线性增长，允许降到负值（射击反而散热）。
 * 实现点：MKGSA.handleShootHeat（基于枪械 rpm 计算减免系数）。
 * 设计决策（留待 max 复核）："射速越高"取枪械静态 rpm 解释。
 */
public class AirCoolingEnchantment extends SustainedFireEnchantment {
    /** rpm 达到该阈值后开始产生减免 */
    public static final int DEFAULT_THRESHOLD_RPM = 600;
    /** 每 1 rpm 超出阈值带来的 perShot 减免比例（每级） */
    public static final double DEFAULT_PER_RPM = 0.002;
    /** perShot 缩放系数下限，负值 = 射击时主动散热 */
    public static final double DEFAULT_FLOOR = -1.0;

    public AirCoolingEnchantment() {
        super(Rarity.RARE, 5);
    }
}