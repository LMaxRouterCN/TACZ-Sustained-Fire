package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;

/**
 * 预热（preheat）：过热值越高射速越快。
 * 实现点：GunData.getShootInterval TAIL（mixin 中按 heatPercentage 施加加成）。
 *
 * [max 2026-10-29 配置化] PER_LEVEL_BONUS / maxLevel 常量删除，
 * 全部走 enchantments.preheat.*（默认值与原常量一致）。
 */
public class PreheatEnchantment extends SustainedFireEnchantment {

    public PreheatEnchantment() {
        super(Rarity.UNCOMMON);
    }

    /** 满热度时每级射速加成（0.40 = 每级 +40% rpm，config） */
    public static double perLevelBonus() {
        return SustainedFireConfig.doubleOr(SustainedFireConfig.PREHEAT_PER_LEVEL_BONUS);
    }

    @Override
    public int getMaxLevel() {
        return SustainedFireConfig.intOr(SustainedFireConfig.PREHEAT_MAX_LEVEL);
    }
}