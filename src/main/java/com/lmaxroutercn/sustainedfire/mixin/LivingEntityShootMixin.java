package com.lmaxroutercn.sustainedfire.mixin;

import com.lmaxroutercn.sustainedfire.ammo.AmmoSourceResolver;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * LivingEntityShoot（服务端权威射击逻辑）：
 * consumeAmmoFromPlayer 的背包消耗 → 组合弹药源（源码 L279 调用点实证：2 参 getCapability(cap, null).map(...)）。
 * 覆盖闭膛上膛（:138）与每发消耗两条路径的弹药来源。
 * 与 ragnarok 的 mixin（MakeGunEnchantableMixin 等作用于 Item 本体）零冲突。
 *
 * remap = false：目标为 tacz 类成员，不在 SRG 映射内。
 * getCapability 为 Forge patch 方法（生产保持原名），字面匹配成立。
 */
@Mixin(LivingEntityShoot.class)
public class LivingEntityShootMixin {

    @Redirect(method = "consumeAmmoFromPlayer(ILnet/minecraft/world/item/ItemStack;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;",
                    remap = false),
            remap = false)
    private LazyOptional<IItemHandler> sf$expandAmmoSource(LivingEntity shooter, Capability<IItemHandler> capability, Direction side) {
        return AmmoSourceResolver.getCombinedSources(shooter);
    }
}