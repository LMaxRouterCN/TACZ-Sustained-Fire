package com.lmaxroutercn.sustainedfire.mixin;

import com.lmaxroutercn.sustainedfire.ammo.AmmoSourceResolver;
import com.lmaxroutercn.sustainedfire.registry.ModEnchantments;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AbstractGunItem（所有 TACZ 枪械的基类）：
 * - useInventoryAmmo 是整条弹药链（hasInventoryAmmo / consumeAmmoFromPlayer /
 *   canReload / dropAllAmmo / HUD 弹药显示）的根判定，
 *   「弹链升级」附魔在此 RETURN 翻转返回值 → 全链自动切换为背包直读语义。
 * - hasInventoryAmmo / canReload 的弹药箱扩展为 Inject-RETURN 兜底式
 *   [GOAL-PLAN 6.6，替代旧 @Redirect 换源]：原方法返回 false 且排除
 *   不可救出口（非背包直读 / 虚拟备弹 / 数据缺失 / 满弹）后，
 *   查附近开盖弹药箱救活。布尔语义 = 背包 OR 箱，与旧方案等价。
 *   注入点为 return 指令，与 @Redirect 的 INVOKE 调用点字节码正交——
 *   与 taczaddon 等第三方背包扩展（getCapability @Redirect）双装共存，
 *   语义为并集：他们扩展背包侧，我们兜弹药箱侧。
 *   高频 true 路径原方法短路返回，不再构建组合视图，性能更优。
 * 与 ragnarok 的 MakeGunEnchantableMixin（isEnchantable/getEnchantmentValue/
 * canApplyAtEnchantingTable）注入点完全不相交。
 *
 * remap = false：目标均为 tacz 类成员，不在 SRG 映射内，AP 必须跳过重映射
 * （tacz 生产环境不混淆，refmap 无需条目）。
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
     * hasInventoryAmmo 背包无弹时的弹药箱兜底（源码 L432 背包检查是唯一可救 false 出口）。
     * 该方法是 LocalPlayerShoot:212（客户端预测）与 LivingEntityShoot:116（服务端）
     * 的公共检查入口，单点 mixin 双端自动覆盖。
     */
    @Inject(method = "hasInventoryAmmo(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Z)Z",
            at = @At("RETURN"), remap = false, cancellable = true)
    private void sf$crateAmmoFallback(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo, CallbackInfoReturnable<Boolean> cir) {
        // 原逻辑已 true（背包有弹 / 免检）——不动
        if (cir.getReturnValueZ()) {
            return;
        }
        AbstractGunItem self = (AbstractGunItem) (Object) this;
        // 非背包直读枪：查询不适用，false 是正确答案
        if (!self.useInventoryAmmo(gun)) {
            return;
        }
        // 虚拟备弹体系封闭：箱内真弹救不了枪内虚拟计数
        if (self.useDummyAmmo(gun)) {
            return;
        }
        // 背包侧已由原方法查过（双装时含第三方扩展视图）——只兜弹药箱
        cir.setReturnValue(AmmoSourceResolver.hasAmmoInCrates(shooter, gun));
    }

    /**
     * canReload 背包无弹时的弹药箱兜底（源码 L147 调用点，独立于 L432）。
     * 覆盖场景：非弹链枪从弹药箱换弹——客户端预检 needCheckAmmo 时本地放行，
     * 服务端 defaultReloadFinishing 消耗侧由 MKGSA:541 redirect 覆盖，链路闭环。
     */
    @Inject(method = "canReload(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"), remap = false, cancellable = true)
    private void sf$crateReloadFallback(LivingEntity shooter, ItemStack gunItem, CallbackInfoReturnable<Boolean> cir) {
        // 原逻辑已 true（无限备弹 / 背包有弹）——不动
        if (cir.getReturnValueZ()) {
            return;
        }
        AbstractGunItem self = (AbstractGunItem) (Object) this;
        // 背包直读（含弹链附魔枪）不换弹：false 是特性语义
        if (self.useInventoryAmmo(gunItem)) {
            return;
        }
        // 虚拟备弹体系封闭
        if (self.useDummyAmmo(gunItem)) {
            return;
        }
        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(self.getGunId(gunItem)).orElse(null);
        // 枪数据缺失 / 已满弹：不可救
        if (gunIndex == null || self.getCurrentAmmoCount(gunItem) >= AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, gunIndex.getGunData())) {
            return;
        }
        // 背包侧已查过——兜弹药箱
        cir.setReturnValue(AmmoSourceResolver.hasAmmoInCrates(shooter, gunItem));
    }
}