package com.lmaxroutercn.sustainedfire.mixin.sb;

import com.lmaxroutercn.sustainedfire.compat.sb.SbAmmoSyncManager;
import net.minecraft.nbt.CompoundTag;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * [M12] 客户端镜像更新观测点：BackpackStorage.setBackpackContents。
 *
 * 收包落地（SB 自家 BackpackContentsMessage.handleMessage 调用）merge 进客户端镜像的
 * 唯一写入口——我们的推送、SB 的 GUI Request 链、tooltip 刷新链全部经过这里。
 * 钩 HEAD 单点标记（merge 语义与方法前后状态无差别，方法体多 return 出口无需逐个打）。
 *
 * 放 mixins.json 的 client 段：纯客户端语义（专用服务端不注入）；
 * onClientStorageSet 内部 client 线程组守卫再防一道（服务端 copyData/迁移同方法调用）。
 *
 * 注意：GUI 打开时 SB 自己的 Request 链也会触发本钩 → 关 GUI 后首读多一次幂等重建，无害。
 */
@Mixin(BackpackStorage.class)
public abstract class BackpackStorageMixin {

    @Inject(method = "setBackpackContents(Ljava/util/UUID;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("HEAD"), remap = false)
    private void sf$onClientStorageSet(UUID uuid, CompoundTag contents, CallbackInfo ci) {
        SbAmmoSyncManager.onClientStorageSet(uuid);
    }
}