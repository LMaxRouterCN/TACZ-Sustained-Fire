package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;

/**
 * 冷却装置（cooling_device）：1-10 级，每级过热增速 -10%（config 可调）。
 * 满配削减下 10 级时单发热量累积归零，等效免过热。
 * 实现点：MKGSA.handleShootHeat（perShot 缩放，见 mixin）。
 *
 * [max 2026-10-29 配置化] PER_LEVEL_REDUCTION / maxLevel 常量删除，
 * 全部走 enchantments.cooling_device.*（默认值与原常量一致）。
 */
public class CoolingDeviceEnchantment extends SustainedFireEnchantment {

    public CoolingDeviceEnchantment() {
        super(Rarity.UNCOMMON);
    }

    /** 每级对过热增速（perShot）的削减比例（config） */
    public static double perLevelReduction() {
        return SustainedFireConfig.doubleOr(SustainedFireConfig.COOLING_PER_LEVEL_REDUCTION);
    }

    @Override
    public int getMaxLevel() {
        return SustainedFireConfig.intOr(SustainedFireConfig.COOLING_MAX_LEVEL);
    }
}