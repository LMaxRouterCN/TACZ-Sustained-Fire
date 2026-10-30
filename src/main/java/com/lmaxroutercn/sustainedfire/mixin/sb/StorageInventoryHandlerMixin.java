package com.lmaxroutercn.sustainedfire.mixin.sb;

import com.lmaxroutercn.sustainedfire.compat.sb.SbAmmoSyncManager;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [M12] SB 变更漏斗之二（主漏斗）：SC InventoryHandler.onContentsChanged。
 *
 * 射击消耗链必经点（extractItemInternal L635 直接调用）：TACZ 从背包直读枪扣弹 →
 * extractItem → onContentsChanged → 此钩。覆盖一切槽位变更（磁铁吸入 / GUI 存取 / 漏斗）。
 * BackpackInventoryHandler（SB 子类）方法表零覆写——本钩无遮蔽，是 SB 背包主内容
 * 变更的唯一回调点。
 *
 * 守卫两层：
 * - storageWrapper instanceof BackpackWrapper：SC 通用类，其他 storage（精妙箱子等）
 *   同样走这里，非 SB 背包直接放行
 * - markDirty 内部的 server 线程组守卫：客户端 vanilla 槽同步写穿同样触发本方法，
 *   不得污染推送集（防回环）
 *
 * persistent 标志与本钩无关（钩在方法本体 RETURN，不依赖 saveHandler 链）——
 * 消耗是否真的落盘由 SB 自管，我们只管"内容变了"这个事实。
 *
 * @Final 注解声明目标字段为 final（Mixin 规范形式——@Shadow 字段不写 Java final
 * 关键字、不带初始化器，避免 AP 对 shadow 初始化器语义的兼容性赌局）。
 * remap = false：SC 类成员不走 SRG 映射（mod 类生产不混淆）。
 */
@Mixin(InventoryHandler.class)
public abstract class StorageInventoryHandlerMixin {

    /** SC 通用 storage 引用（目标 protected final 字段，字节码实证） */
    @Shadow(remap = false)
    @Final
    protected IStorageWrapper storageWrapper;

    @Inject(method = "onContentsChanged(I)V", at = @At("RETURN"), remap = false)
    private void sf$markBackpackDirty(int slot, CallbackInfo ci) {
        if (!(storageWrapper instanceof BackpackWrapper wrapper)) {
            return;
        }
        wrapper.getContentsUuid().ifPresent(SbAmmoSyncManager::markDirty);
    }
}