package com.lmaxroutercn.sustainedfire.util;

import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.world.item.ItemStack;

/**
 * 弹药箱专用容器：统一过滤入口（max 三连拍板 ③，过滤合二为一）。
 *
 * crateAmmoOnly=true 时只收 TACZ 弹药（IAmmo）与弹药盒（IAmmoBox）；
 * false 时为通用容器（历史行为，光标交互原先的 IAmmo 硬编码随之废除）。
 *
 * Forge 47.4.10 字节码实证（javap，2026-10-29）：
 * ItemStackHandler.insertItem 首步调用 isItemValid，false 直接原样退回；
 * SlotItemHandler.mayPlace 委托 handler.isItemValid。
 * 因此只覆盖 isItemValid 一处即可同时挡住四条入口：
 *   - 漏斗/自动化（BE capability → insertItem → isItemValid）
 *   - GUI 手动放取与 shift 快速移动（SlotItemHandler.mayPlace → isItemValid）
 *   - 物品态光标交互（AmmoCrateBlockItem 的 insertAll → insertItem → isItemValid）
 * 三/四条入口共用一个判定源，无第二份过滤逻辑副本。
 *
 * 材化点（全部使用本类）：AmmoCrateBlockEntity.items / AmmoCrateBlockItem.loadHandler /
 * AmmoCrateMenu 防御壳 / InInventoryCrateHandler（backpackUsable 的背包内视图）。
 */
public class AmmoCrateInventoryHandler extends FixedCapacityItemStackHandler {

    public AmmoCrateInventoryHandler(int size) {
        super(size);
    }

    /** 统一准入判定：crateAmmoOnly 开启时仅 TACZ 弹药/弹药盒 */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!super.isItemValid(slot, stack)) {
            return false;
        }
        return canAccept(stack);
    }

    /**
     * 唯一改点：未来扩展收纳范围只改本方法（原 AmmoCrateBlockItem.isDepositable 的继任者）。
     */
    public static boolean canAccept(ItemStack stack) {
        if (stack.isEmpty()) {
            return true; // 空堆无物可装，交由上层语义处理
        }
        if (!SustainedFireConfig.booleanOr(SustainedFireConfig.AMMO_CRATE_AMMO_ONLY)) {
            return true;
        }
        return stack.getItem() instanceof IAmmo || stack.getItem() instanceof IAmmoBox;
    }
}