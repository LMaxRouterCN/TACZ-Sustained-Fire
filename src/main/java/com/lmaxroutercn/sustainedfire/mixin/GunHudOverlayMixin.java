package com.lmaxroutercn.sustainedfire.mixin;

import com.lmaxroutercn.sustainedfire.ammo.AmmoSourceResolver;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [M6 ③⑤⑦ 同源修复] 枪械 HUD 弹药缓存追加附近弹药箱计数。
 *
 * 根因（实证）：GunHudOverlay.handleCacheCount 每 50ms 把 cacheInventoryAmmoCount
 * （纯背包口径）回写进背包直读枪的弹匣 NBT —— 背包空 + 箱有弹时 NBT 被写成 0，
 * doShoot 的 maxCount=0 导致连发循环第一帧自我取消（③不射击的根因）；
 * 同一 NBT 亦驱动 HUD 显示（⑤）与 BULLET_CHAIN 弹链外观（⑦）。
 *
 * 注入点选 INVOKE(AFTER) 而非 RETURN 的原因：
 * RETURN 会在 50ms 门限未过、缓存未刷新的帧上重复累加箱数（通胀 bug）；
 * INVOKE 紧跟 handleInventoryAmmo 返回，天然只在刷新帧执行一次，
 * 且 dummy 弹药/创造模式分支不经过该方法，天然不需要额外守卫。
 * 回写 NBT 由紧随其后的原生代码完成，零重复逻辑。
 */
@Mixin(GunHudOverlay.class)
public abstract class GunHudOverlayMixin {

    /** 纯背包口径的缓存计数（原生私有静态字段） */
    @Shadow(remap = false)
    private static int cacheInventoryAmmoCount;

    @Inject(method = "handleCacheCount", remap = false, at = @At(
            value = "INVOKE",
            target = "Lcom/tacz/guns/client/gui/overlay/GunHudOverlay;handleInventoryAmmo(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Inventory;)V",
            shift = At.Shift.AFTER))
    private static void sf$crateAmmoInHud(LocalPlayer player, ItemStack stack, GunData gunData, IGun iGun, boolean useInventoryAmmo, CallbackInfo ci) {
        // 仅背包直读枪需要增广（非直读枪弹匣口径本就有效）
        if (!useInventoryAmmo) {
            return;
        }
        // 9999 = 创造弹药盒哨兵（handleInventoryAmmo 创造分支已置位并提前返回），不掺箱数
        if (cacheInventoryAmmoCount >= 9999) {
            return;
        }
        int crateAmmo = AmmoSourceResolver.countAmmoInCrates(player, stack);
        if (crateAmmo <= 0) {
            return;
        }
        cacheInventoryAmmoCount = Math.min(cacheInventoryAmmoCount + crateAmmo, 9999);
        // 到此为止：紧随其后的原生 setCurrentAmmoCount 会用增广后的缓存回写弹匣 NBT
    }
}