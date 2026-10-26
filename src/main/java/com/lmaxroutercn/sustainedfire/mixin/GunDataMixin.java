package com.lmaxroutercn.sustainedfire.mixin;

import com.lmaxroutercn.sustainedfire.ammo.FireSessionTracker;
import com.lmaxroutercn.sustainedfire.enchantment.PreheatEnchantment;
import com.lmaxroutercn.sustainedfire.enchantment.ProgressiveTriggerEnchantment;
import com.lmaxroutercn.sustainedfire.registry.ModEnchantments;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GunData.getShootInterval（射速节流链终点）：
 * 「预热」与「渐进射速」在返回值 TAIL 处统一施加加成。
 *
 * 选取此方法而非 lerpRPM 的原因：lerpRPM 仅对 hasHeatData 枪生效（:309-310 有守卫），
 * 渐进射速必须对全枪生效；预热对无热枪加成自然归零（heatPct=0），语义自洽。
 *
 * 加成换算：rpm *= bonus 等价于 interval /= bonus（interval = 60_000 / rpm）。
 * 调用方（LES 服务端 / MKGSA 脚本 / 客户端动画上下文）全部经此方法，一处注入全端生效。
 */
@Mixin(GunData.class)
public class GunDataMixin {

    @Inject(method = "getShootInterval(Lnet/minecraft/world/entity/LivingEntity;Lcom/tacz/guns/api/item/gun/FireMode;Lnet/minecraft/world/item/ItemStack;)J",
            at = @At("TAIL"), remap = false)
    private void sf$shootIntervalBoost(LivingEntity shooter, FireMode fireMode, ItemStack gunStack, CallbackInfoReturnable<Long> cir) {
        if (shooter == null || gunStack == null) {
            return;
        }
        float bonus = 1f;

        // 「预热」：heat 百分比越高加成越高（无热数据/无附魔时跳过，bonus 不变）
        IGun iGun = IGun.getIGunOrNull(gunStack);
        int preheatLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.PREHEAT.get(), gunStack);
        if (iGun != null && preheatLevel > 0 && iGun.hasHeatData(gunStack)) {
            float heatPct = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack))
                    .map(index -> index.getGunData().getHeatData())
                    .map(heatData -> heatData == null ? 0f : iGun.getHeatAmount(gunStack) / heatData.getHeatMax())
                    .orElse(0f);
            // 防御性钳制：setHeatAmount 只钳下界，上界溢出时不放大加成
            heatPct = Math.min(Math.max(heatPct, 0f), 1f);
            bonus += (float) (PreheatEnchantment.PER_LEVEL_BONUS * preheatLevel) * heatPct;
        }

        // 「渐进射速」：连射 ramp 系数 × 等级加成（读端与查询方所在端一致：客户端表/服务端表）
        int progressiveLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.PROGRESSIVE_TRIGGER.get(), gunStack);
        if (progressiveLevel > 0) {
            float ramp = FireSessionTracker.rampFactor(shooter.getUUID(), shooter.level().isClientSide());
            bonus += (float) (ProgressiveTriggerEnchantment.PER_LEVEL_BONUS * progressiveLevel) * ramp;
        }

        // 施加加成：interval /= bonus（bonus > 1 时射速加快；两附魔叠加相乘生效）
        if (bonus > 1f) {
            cir.setReturnValue((long) (cir.getReturnValue() / (double) bonus));
        }
    }
}