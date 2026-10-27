package com.lmaxroutercn.sustainedfire.mixin;

import com.lmaxroutercn.sustainedfire.ammo.AmmoSourceResolver;
import com.lmaxroutercn.sustainedfire.registry.ModEnchantments;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AbstractGunItem（所有 TACZ 枪械的基类）：
 * - useInventoryAmmo 是整条弹药链（hasInventoryAmmo / consumeAmmoFromPlayer /
 *   canReload / dropAllAmmo / HUD 弹药显示）的根判定，
 *   「弹链升级」附魔在此 TAIL 翻转返回值 → 全链自动切换为背包直读语义。
 * - hasInventoryAmmo 的背包检查扩展为组合弹药源（@Redirect getCapability）。
 * - canReload 的背包检查同样扩展（源码 L147 独立调用点）：
 *   LocalPlayerReload:77-78 客户端预检 needCheckAmmo 时本地拦截不发包，
 *   不罩此处则「背包空但弹药箱有弹」场景无法触发换弹。
 * 与 ragnarok 的 MakeGunEnchantableMixin（isEnchantable/getEnchantmentValue/
 * canApplyAtEnchantingTable）注入点完全不相交。
 *
 * remap = false：目标均为 tacz 类成员，不在 SRG 映射内，AP 必须跳过重映射
 * （tacz 生产环境不混淆，refmap 无需条目）。getCapability 为 Forge patch 方法，
 * 生产环境保持原名，@At 字面匹配成立。
 */
@Mixin(AbstractGunItem.class)
public class AbstractGunItemMixin {

    /** 弹链升级：任意枪械 + belt_feed 附魔 → 视为背包直读（免换弹、禁换弹、HUD 切换） */
    @Inject(method = "useInventoryAmmo(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), remap = false, cancellable = true)
    private void sf$beltFeed(ItemStack gun, CallbackInfoReturnable<Boolean> cir) {
        // 原本就是背包直读的枪（FeedType.INVENTORY）无需处理
        if (cir.getReturnValueZ()) {
            return;
        }
        if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.BELT_FEED.get(), gun) > 0) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 背包弹药检查 → 组合弹药源检查（源码 L432 调用点实证：2 参 getCapability(cap, null).map(...)）。
     * 该方法是 LocalPlayerShoot:212（客户端预测）与 LivingEntityShoot:116（服务端）
     * 的公共检查入口，单点 mixin 双端自动覆盖。
     */
    @Redirect(method = "hasInventoryAmmo(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Z)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;",
                    remap = false),
            remap = false)
    private LazyOptional<IItemHandler> sf$expandAmmoSource(LivingEntity shooter, Capability<IItemHandler> capability, Direction side) {
        return AmmoSourceResolver.getCombinedSources(shooter);
    }

    /**
     * canReload 的背包检查 → 组合弹药源检查（源码 L147 调用点实证，与 L432 互为独立调用）。
     * 覆盖场景：非弹链枪从弹药箱换弹——客户端预检读组合视图（含客户端 BE 同步数据），
     * 服务端 defaultReloadFinishing 消耗侧已被 MKGSA:541 redirect 覆盖，链路闭环。
     */
    @Redirect(method = "canReload(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;",
                    remap = false),
            remap = false)
    private LazyOptional<IItemHandler> sf$expandAmmoSourceForReload(LivingEntity shooter, Capability<IItemHandler> capability, Direction side) {
        return AmmoSourceResolver.getCombinedSources(shooter);
    }
}