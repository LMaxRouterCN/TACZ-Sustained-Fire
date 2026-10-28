package com.lmaxroutercn.sustainedfire.menu;

import com.lmaxroutercn.sustainedfire.block.AmmoCrateBlock;
import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
import com.lmaxroutercn.sustainedfire.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药箱菜单：36 格规格（4 行 9 列），GUI 高度扩展至 186。
 * 槽位编号：0-35 箱 / 36-62 玩家背包 / 63-71 快捷栏。
 * 箱格用 SlotItemHandler 包装 BE 的 ItemStackHandler（Forge 标准）。
 */
public class AmmoCrateMenu extends AbstractContainerMenu {
    /** 服务端与正常客户端均为真实 BE；防御壳为 null */
    @Nullable
    private final AmmoCrateBlockEntity crate;

    public AmmoCrateMenu(int id, Inventory playerInventory, @Nullable AmmoCrateBlockEntity crate) {
        super(ModMenus.AMMO_CRATE.get(), id);
        this.crate = crate;
        IItemHandler handler = (crate != null) ? crate.getItemHandler() : new ItemStackHandler(AmmoCrateBlockEntity.CONTAINER_SIZE);
        // 箱内 36 格：4 行 9 列（首行坐标与原版单箱一致）
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new SlotItemHandler(handler, row * 9 + col, 8 + col * 18, 18 + row * 18));
            }
        }
        // 玩家背包 3 行
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 102 + row * 18));
            }
        }
        // 快捷栏
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 160));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < AmmoCrateBlockEntity.CONTAINER_SIZE) {
            // 箱 → 玩家（含快捷栏，倒序优先快捷栏）
            if (!this.moveItemStackTo(stack, AmmoCrateBlockEntity.CONTAINER_SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家 → 箱
            if (!this.moveItemStackTo(stack, 0, AmmoCrateBlockEntity.CONTAINER_SIZE, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        // 1.20.1 的 BlockEntity 无 stillValid(Player)（后期版本才加的，踩坑实录），
        // 手写原版 BaseContainerBlockMenu 同款检查：BE 身份比对 + 玩家 8 格内 + 盖子仍开
        if (this.crate == null) {
            return false;
        }
        BlockPos pos = this.crate.getBlockPos();
        return player.level().getBlockEntity(pos) == this.crate
                && this.crate.getBlockState().getValue(AmmoCrateBlock.OPEN)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}