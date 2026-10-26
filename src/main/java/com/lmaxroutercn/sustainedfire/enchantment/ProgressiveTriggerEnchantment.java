package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

/**
 * 渐进射速（progressive_trigger）：连续射击一段时间后射速渐增。
 * 加成幅度低于「预热」。
 * 实现点：lerpRPM mixin + 连射状态（IGunOperator data / 枪 NBT 时间戳）。
 */
public class ProgressiveTriggerEnchantment extends SustainedFireEnchantment {
    /** 每级提供的渐增加成上限（满状态每级 +15% rpm，低于预热） */
    public static final double PER_LEVEL_BONUS = 0.15;
    /** 连射积累到满加成所需时长（ms） */
    public static final int RAMP_UP_TIME_MS = 3000;

    public ProgressiveTriggerEnchantment() {
        super(Rarity.COMMON, 5);
    }
}