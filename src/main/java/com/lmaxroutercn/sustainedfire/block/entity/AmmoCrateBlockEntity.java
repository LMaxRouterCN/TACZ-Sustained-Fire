package com.lmaxroutercn.sustainedfire.block.entity;

import com.lmaxroutercn.sustainedfire.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
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
 *   - 供弹（换弹/弹链）→ AmmoSourceResolver 按开盖状态筛选用它
 * 弹药匹配（哪种弹药喂哪把枪）不在 BE 内做——BE 只是容器，
 * TACZ 的 isAmmoOfGun 在消耗侧照常工作，职责分离。
 *
 * 客户端可见性（step3）：vanilla 默认 getUpdateTag 是空 tag（箱子即如此，GUI 显示走 menu 广播），
 * 但换弹/弹链的客户端预检（canReload / hasInventoryAmmo 预测）直接读客户端 BE 的物品视图，
 * 故本 BE 额外把 Items 放入 getUpdateTag，内容变化时服务端 sendBlockUpdated 推送。
 * 两条通道（menu 广播 / BE 更新包）最终写入同一个客户端 ItemStackHandler，不冲突。
 */
public class AmmoCrateBlockEntity extends BlockEntity {
    /** 容器规格：27 格（对齐原版箱子） */
    public static final int CONTAINER_SIZE = 36;

    private final ItemStackHandler items = new ItemStackHandler(CONTAINER_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            // 任何槽位变动都标记脏，保证存档
            setChanged();
            // 服务端内容变化 → 推 BE 更新包给跟踪该区块的客户端（仅网络包，不触发区块重存/重渲染）
            // level==null 挡板：磁盘 load 时 deserializeNBT 也触发本回调，此时无 level
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    };

    /** 对外暴露物品处理器：菜单槽位 / 供弹查询共用同一实例 */
    public IItemHandler getItemHandler() {
        return items;
    }

    private LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> items);

    public AmmoCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AMMO_CRATE.get(), pos, state);
    }

    // ---- 客户端同步：getUpdateTag 同时服务 BE 更新包与区块数据包（进视距全量同步）----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put("Items", items.serializeNBT());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        // 客户端收到后走 vanilla 默认 handleUpdateTag → load(tag)，Items 键与 load 读取端对齐
        return ClientboundBlockEntityDataPacket.create(this);
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
        // Items 子标签存 36 格内容（ItemStackHandler 标准 NBT 布局）。
        // 同时服务于磁盘存档与 getUpdateTag 客户端同步（handleUpdateTag → load）
        items.deserializeNBT(tag.getCompound("Items"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
    }

    /** 已占用槽数：挖掘捕获时写进物品 "sf:fill"（耐久条/模型谓词 O(1) 读） */
    public int getOccupiedSlots() {
        int n = 0;
        for (int i = 0; i < items.getSlots(); i++) {
            if (!items.getStackInSlot(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }
}