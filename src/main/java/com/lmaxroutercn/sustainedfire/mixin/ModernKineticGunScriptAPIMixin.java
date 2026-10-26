package com.lmaxroutercn.sustainedfire.mixin;

import com.lmaxroutercn.sustainedfire.ammo.AmmoSourceResolver;
import com.lmaxroutercn.sustainedfire.enchantment.AirCoolingEnchantment;
import com.lmaxroutercn.sustainedfire.enchantment.CoolingDeviceEnchantment;
import com.lmaxroutercn.sustainedfire.registry.ModEnchantments;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ModernKineticGunScriptAPI（MKGSA，射击脚本 API）：
 * - consumeAmmoFromPlayer(I)：背包直读消耗 → 组合弹药源（弹链的弹药来源，源码 L549 调用点）
 * - handleShootHeat()：单发热量缩放（冷却装置 + 风冷）
 *
 * 已知限制：枪械 Lua 脚本若覆写了 handle_shoot_heat 函数，
 * TACZ 会改调 Lua 版本（shootOnce:171-177），冷却/风冷对这类枪不生效——
 * 绝大多数枪使用默认实现，接受此限制。
 *
 * remap = false：@Shadow 字段与方法目标均为 tacz 类成员，不在 SRG 映射内。
 * getCapability 为 Forge patch 方法（生产保持原名），字面匹配成立。
 */
@Mixin(ModernKineticGunScriptAPI.class)
public class ModernKineticGunScriptAPIMixin {

    @Shadow(remap = false) private ItemStack itemStack;
    @Shadow(remap = false) private AbstractGunItem abstractGunItem;

    /** 背包直读消耗 → 组合弹药源消耗（1.20.1 getCapability 返回 LazyOptional） */
    @Redirect(method = "consumeAmmoFromPlayer(I)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;",
                    remap = false),
            remap = false)
    private LazyOptional<IItemHandler> sf$expandAmmoSource(LivingEntity shooter, Capability<IItemHandler> capability, Direction side) {
        return AmmoSourceResolver.getCombinedSources(shooter);
    }

    /**
     * 单发热量缩放：perShot * mult
     * mult = 1 - 0.10*冷却等级 - 0.002*风冷等级*max(0, rpm-600)，下限 -1.0（射击主动散热）
     * 返回负增长时 setHeatAmount 自带 >=0 钳制，无越界风险。
     * getRPM(ItemStack) 已实证（AbstractGunItem:452 / IGun:369 接口声明）。
     */
    @Redirect(method = "handleShootHeat()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/pojo/data/gun/GunHeatData;getHeatPerShot()F",
                    remap = false),
            remap = false)
    private float sf$scaleHeatPerShot(GunHeatData heatData) {
        float perShot = heatData.getHeatPerShot();
        // 冷却装置：每级 -10% 热量增速（10 级免过热）
        int coolingLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.COOLING_DEVICE.get(), this.itemStack);
        float mult = 1f - (float) (CoolingDeviceEnchantment.PER_LEVEL_REDUCTION * coolingLevel);
        // 风冷：射速超阈值部分线性减免，允许负值
        int airLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.AIR_COOLING.get(), this.itemStack);
        if (airLevel > 0 && this.abstractGunItem != null) {
            // 取当前有效 rpm（getRPM 内含 lerpRPM 修正，与热量联动）
            int rpm = this.abstractGunItem.getRPM(this.itemStack);
            float overThreshold = Math.max(0, rpm - AirCoolingEnchantment.DEFAULT_THRESHOLD_RPM);
            mult -= (float) (AirCoolingEnchantment.DEFAULT_PER_RPM * airLevel * overThreshold);
            mult = Math.max(mult, (float) AirCoolingEnchantment.DEFAULT_FLOOR);
        }
        return perShot * mult;
    }
}