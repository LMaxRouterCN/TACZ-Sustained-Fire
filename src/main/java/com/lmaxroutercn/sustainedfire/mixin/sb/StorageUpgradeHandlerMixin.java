package com.lmaxroutercn.sustainedfire.mixin.sb;

import com.lmaxroutercn.sustainedfire.compat.sb.SbAmmoSyncManager;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [M12] SB 变更漏斗之三：SC UpgradeHandler.onContentsChanged（升级槽位变更）。
 *
 * 覆盖 GUI 内增删升级物品（供弹升级装进/取出背包）。字节码：persistent 时 saveInventory +
 * contentsSaveHandler.run（= BackpackWrapper.markBackpackContentsDirty 的 method-ref，
 * BootstrapMethods #11）+ refreshUpgradeWrappers——即此处变更本身会再触发漏斗一，
 * 本钩与漏斗一在该场景互为冗余；但 persistent=false 路径漏斗一不跑，本钩兜底。
 *
 * @Shadow 可跨访问级别引用目标 private 字段（mixin 合并后同类访问合法）；
 * @Final 规范形式见 StorageInventoryHandlerMixin 注释。
 * remap = false：SC 类成员不走 SRG 映射。
 */
@Mixin(UpgradeHandler.class)
public abstract class StorageUpgradeHandlerMixin {

    /** SC 通用 storage 引用（目标 private final 字段，字节码实证） */
    @Shadow(remap = false)
    @Final
    private IStorageWrapper storageWrapper;

    @Inject(method = "onContentsChanged(I)V", at = @At("RETURN"), remap = false)
    private void sf$markBackpackDirty(int slot, CallbackInfo ci) {
        if (!(storageWrapper instanceof BackpackWrapper wrapper)) {
            return;
        }
        wrapper.getContentsUuid().ifPresent(SbAmmoSyncManager::markDirty);
    }
}