package com.lmaxroutercn.sustainedfire.enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Sustained Fire 附魔基类。
 * 准入控制：只对 TACZ 枪械生效（canEnchant），
 * 获取路径以铁砧+附魔书为主；附魔台路径依赖 isEnchantable
 * （默认 false，ragnarok 的 mixin 可能放开，不强求）。
 *
 * [max 2026-10-29 配置化] 费用曲线（enchantments.costBase/costStep/costSpan）
 * 与各附魔 maxLevel（enchantments.*.maxLevel）均来自 config；
 * 构造/注册期不读配置（COMMON 未加载，get() 会抛异常），全部走
 * SustainedFireConfig.intOr 兜底（未加载时返回默认值），运行期才生效。
 */
public abstract class SustainedFireEnchantment extends Enchantment {

    protected SustainedFireEnchantment(Rarity rarity) {
        // category 仅影响附魔台筛选，实际准入由 canEnchant 决定
        super(rarity, EnchantmentCategory.BREAKABLE, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    // 只允许 TACZ 枪械接受本系列附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof AbstractGunItem;
    }

    @Override
    public boolean isTradeable() {
        // 村民图书管理员可出售（弹链等宝藏附魔单独覆盖）
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        // 可出现在战利品箱
        return true;
    }

    // 常规费用曲线（config 共享）：等级越高越贵
    @Override
    public int getMinCost(int level) {
        return SustainedFireConfig.intOr(SustainedFireConfig.ENCH_COST_BASE)
                + SustainedFireConfig.intOr(SustainedFireConfig.ENCH_COST_STEP) * (level - 1);
    }

    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + SustainedFireConfig.intOr(SustainedFireConfig.ENCH_COST_SPAN);
    }
}