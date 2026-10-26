package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

/**
 * 冷却装置（cooling_device）：1-10 级，每级过热增速 -10%。
 * 10 级时单发热量累积归零，等效免过热。
 * 实现点：MKGSA.handleShootHeat（perShot 缩放，见 mixin 注入计划）。
 */
public class CoolingDeviceEnchantment extends SustainedFireEnchantment {
    /** 每级对过热增速（perShot）的削减比例 */
    public static final double PER_LEVEL_REDUCTION = 0.10;

    public CoolingDeviceEnchantment() {
        super(Rarity.UNCOMMON, 10);
    }
}