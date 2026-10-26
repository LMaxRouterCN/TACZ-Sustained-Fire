package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

/**
 * 弹链升级（belt_feed）：宝藏附魔，仅 1 级。
 * 射击时直接从玩家背包/精妙背包/弹药箱供弹（AmmoSourceResolver），
 * 免去换弹动作；canReload 同步禁换弹。
 * 实现点：AbstractGunItem.useInventoryAmmo 尾部分支。
 */
public class BeltFeedEnchantment extends SustainedFireEnchantment {
    /** 枪 NBT 中弹链供弹标记的键（统一子 tag "SustainedFire" 内） */
    public static final String BELT_FED_KEY = "sf_belt_fed";

    public BeltFeedEnchantment() {
        super(Rarity.RARE, 1);
    }

    @Override
    public boolean isTreasureOnly() {
        // 宝藏附魔：不出现在附魔台
        return true;
    }

    @Override
    public boolean isTradeable() {
        // 宝藏附魔：村民不卖
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        // 但可出现在战利品箱
        return true;
    }
}