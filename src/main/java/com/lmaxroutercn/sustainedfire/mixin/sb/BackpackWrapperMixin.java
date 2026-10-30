package com.lmaxroutercn.sustainedfire.mixin.sb;

import com.lmaxroutercn.sustainedfire.compat.sb.SbAmmoSyncManager;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [M12] SB 变更漏斗之一：BackpackWrapper 的内容落盘标记。
 *
 * markBackpackContentsChanged（public）与 markBackpackContentsDirty（private）字节码同体
 * （都只是 contentsSource.markDirty()），BootstrapMethods #11 证实 markDirty 被包装成
 * Runnable 供给 SC 升级/物品 handler 的 saveHandler 消费——大量持久化路径汇聚于此。
 * 两者都打钩（幂等入集），互为冗余保险。
 *
 * 类加载隔离：本 mixin 在 mixin.sb 子包，由 SfMixinPlugin 按 SB 在场 gating——
 * SB 缺席时整组不应用（目标类/IBackpackWrapper 引用无处安放）。
 * remap = false：SB 类成员不走 SRG 映射（mod 类生产不混淆，refmap 无需条目）。
 */
@Mixin(BackpackWrapper.class)
public abstract class BackpackWrapperMixin {

    /** 内容落盘标记（public 入口）→ 记入待推送集 */
    @Inject(method = "markBackpackContentsChanged()V", at = @At("RETURN"), remap = false)
    private void sf$onMarkChanged(CallbackInfo ci) {
        // cast 到接口拿 uuid（mixin 无法直接调用本类 private 字段链，接口方法最干净）
        SbAmmoSyncManager.markDirty(((IBackpackWrapper) (Object) this).getContentsUuid().orElse(null));
    }

    /** 内容落盘标记（private 入口，saveHandler Runnable 的实际目标）→ 同上 */
    @Inject(method = "markBackpackContentsDirty()V", at = @At("RETURN"), remap = false)
    private void sf$onMarkDirty(CallbackInfo ci) {
        SbAmmoSyncManager.markDirty(((IBackpackWrapper) (Object) this).getContentsUuid().orElse(null));
    }
}