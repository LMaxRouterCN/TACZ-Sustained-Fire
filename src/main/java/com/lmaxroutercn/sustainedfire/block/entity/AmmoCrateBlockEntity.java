package com.lmaxroutercn.sustainedfire.block.entity;

import com.lmaxroutercn.sustainedfire.block.AmmoCrateBlock;
import com.lmaxroutercn.sustainedfire.registry.ModBlockEntities;
import com.lmaxroutercn.sustainedfire.util.AmmoCrateInventoryHandler;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药箱方块实体：36 格通用容器（4 行 × 9 列）。
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
 *
 * [M14] 外部直写：TACZ 对盒 setAmmoCount 直改 BE 槽位内的活 ItemStack（数据已落真身），
 * 但绕过 handler op → onContentsChanged 不触发，存档标脏与客户端同步双失明。
 * sweepExternalAmmoBoxChange 由消耗完成后的清扫链调用（见 ExternalConsumeSweep）。
 */
public class AmmoCrateBlockEntity extends BlockEntity {
    /** 容器规格：36 格（4 行 × 9 列，2026-10-28 由 27 扩容；NBT 的 Size 非权威，见 FixedCapacityItemStackHandler） */
    public static final int CONTAINER_SIZE = 36;

    private final ItemStackHandler items = new AmmoCrateInventoryHandler(CONTAINER_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            // handler 写操作路径（GUI / 漏斗 / extractItem）→ 统一善后
            onInventoryContentsChanged();
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

    /**
     * 容器内容变化的标准善后：handler op 路径与 [M14] 外部直写清扫共用。
     * 从匿名类 onContentsChanged 主体抽出（行为不变），sweep 复用同一口径
     * ——直改与 op 两类变更走完全相同的同步链，无第二份逻辑。
     */
    private void onInventoryContentsChanged() {
        // 任何槽位变动都标记脏，保证存档
        setChanged();
        // 服务端内容变化 → 推 BE 更新包给跟踪该区块的客户端（仅网络包，不触发区块重存/重渲染）
        // level==null 挡板：磁盘 load 时 deserializeNBT 也触发本回调，此时无 level
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            // [M9] 内容物抬升：占用槽数跨级才写 level blockstate（同级别变动零方块更新开销）
            BlockState visualState = getBlockState();
            int newLevel = contentLevel();
            if (visualState.getValue(AmmoCrateBlock.LEVEL) != newLevel) {
                level.setBlock(getBlockPos(), visualState.setValue(AmmoCrateBlock.LEVEL, newLevel), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * [M14] 外部直写清扫：槽内含弹药盒时补跑标准善后（setChanged + 客户端推送）。
     * 直改数据已落 BE 槽位活 ItemStack（数据不丢），缺的只是通知链——本方法补上。
     * [M15] 触发面：盒（IAmmoBox）或容器物品（暴露 ITEM_HANDLER 的直写路径）任一在场即触发；
     * 散弹消耗走 extractItem（onContentsChanged 已即时善后）。
     * 幂等：标脏/推送无副作用；客户端误入（防御）时 sendBlockUpdated 有 isClientSide 守卫。
     */
    public void sweepExternalAmmoBoxChange() {
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack s = items.getStackInSlot(i);
            // [M15] 触发面扩展：内部容器物品（潜影盒等暴露存储 cap 的物品）同样会被
            // 外部消耗直改 NBT（其 wrapper 写回不触发 BE 槽位 handler op）→ 一并纳入
            if (s.getItem() instanceof IAmmoBox
                    || s.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent()) {
                onInventoryContentsChanged();
                return;
            }
        }
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
        // [M12.3 loot 数据包化，max 2026-10-30 拍板] 占用槽数持久化：loot 表 copy_nbt 的源键
        // （Fill → 物品 sf:fill，耐久条/物品模型谓词的 O(1) 缓存）。存档时现算无运行时字段，
        // 旧档下次保存自动带上零迁移；客户端不消费（getUpdateTag 不加），纯 loot 链数据源。
        tag.putInt("Fill", getOccupiedSlots());
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

    /** [M9] 内容物等级：占用槽数 → 0-8（空箱 0，放 1 格至少 1 级，满 36 格 = 8 级与开口齐平）。
     * 供 onContentsChanged 跨级同步与 BlockItem.place 放置兜底共用（BlockEntityTag 恢复不走 onContentsChanged）。 */
    public int contentLevel() {
        int occupied = 0;
        for (int i = 0; i < getItemHandler().getSlots(); i++) {
            if (!getItemHandler().getStackInSlot(i).isEmpty()) {
                occupied++;
            }
        }
        return (int) Math.ceil(occupied * 8.0 / CONTAINER_SIZE);
    }
}