package com.lmaxroutercn.sustainedfire.ammo;

import net.minecraftforge.items.IItemHandler;

/**
 * [M14] 弹药源载体：handler 视图 + 外部直写清扫钩。
 *
 * 背景：TACZ 的 findAndExtractInventoryAmmo 对弹药盒（IAmmoBox）走 setAmmoCount
 * 直改 ItemStack NBT（AbstractGunItem L250-257），绕过一切 handler 写操作
 * （extractItem / setStackInSlot）——挂在 handler op 上的变更通知链
 * （M12 的 SC 四漏斗 / 弹药箱 BE onContentsChanged / 副本写回）全部失明。
 *
 * sweep：消耗完成后该源需要的"直写落地"动作（副本写回 / BE 标脏推送 /
 * SB 序列化回写），由 CombinedView.afterExternalConsume（见 ExternalConsumeSweep）
 * 在 findAndExtractInventoryAmmo RETURN 钩统一触发。
 * null = 无需清扫：裸玩家背包槽位（直改落在玩家 Inventory 活 ItemStack 上，
 * vanilla broadcastChanges 每 tick NBT 深比较自动同步——"盒拿在手里正常"的机制）。
 */
public record AmmoSource(IItemHandler handler, Runnable sweep) {

    /** 无清扫需求的源（见类注释末段） */
    public static AmmoSource bare(IItemHandler handler) {
        return new AmmoSource(handler, null);
    }
}