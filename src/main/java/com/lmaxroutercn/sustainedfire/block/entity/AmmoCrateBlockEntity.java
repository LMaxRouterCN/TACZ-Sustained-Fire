package com.lmaxroutercn.sustainedfire.block.entity;

import com.lmaxroutercn.sustainedfire.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药箱方块实体：27 格通用容器（与箱子同规格）。
 *
 * 只暴露 ItemStackHandler capability：
 *   - 漏斗等自动化 → Forge 原生路径直接互通（不受盖子限制，max 定稿）
 *   - 供弹（换弹/弹链）→ step3 由 AmmoSourceResolver 按开盖状态筛选用它
 * 弹药匹配（哪种弹药喂哪把枪）不在 BE 内做——BE 只是容器，
 * TACZ 的 isAmmoOfGun 在消耗侧照常工作，职责分离。
 */
public class AmmoCrateBlockEntity extends BlockEntity {
    /** 容器规格：27 格（对齐原版箱子） */
    public static final int CONTAINER_SIZE = 27;

    private final ItemStackHandler items = new ItemStackHandler(CONTAINER_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            // 任何槽位变动都标记脏，保证存档
            setChanged();
        }
    };

    private LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> items);

    public AmmoCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AMMO_CRATE.get(), pos, state);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        // 区块重新加载后重建懒持有
        itemHandler = LazyOptional.of(() -> items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Items 子标签存 27 格内容（ItemStackHandler 标准 NBT 布局）
        items.deserializeNBT(tag.getCompound("Items"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
    }
}