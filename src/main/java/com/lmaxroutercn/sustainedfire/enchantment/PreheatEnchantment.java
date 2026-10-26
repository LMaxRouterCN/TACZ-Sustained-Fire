package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

/**
 * 预热（preheat）：过热值越高射速越快。
 * 实现点：GunItemDataAccessor.lerpRPM（mixin 中查附魔等级改写插值目标，
 * 使 maxRpmMod 随 heatPercentage 提升）。
 */
public class PreheatEnchantment extends SustainedFireEnchantment {
    /** 每级提供的射速加成上限（heat=100% 时每级 +40% rpm） */
    public static final double PER_LEVEL_BONUS = 0.40;

    public PreheatEnchantment() {
        super(Rarity.UNCOMMON, 5);
    }
}