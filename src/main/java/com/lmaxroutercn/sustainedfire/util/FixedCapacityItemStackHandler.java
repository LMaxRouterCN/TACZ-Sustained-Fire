package com.lmaxroutercn.sustainedfire.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 固定容量 ItemStackHandler：容量是构造时定死的代码常量，NBT 的 Size 只是
 * 序列化快照，反序列化时被无视（2026-10-28 崩溃根因修复，M7c）。
 *
 * 事故：vanilla deserializeNBT 以 NBT 的 Size 为准 setSize——旧存档/旧物品
 * 带 Size=27，把 36 槽 handler 缩回 27，菜单 36 槽第一个 getItem(27) 即
 * RuntimeException("Slot 27 not in valid range")。BE 磁盘 load（服务端）与
 * getUpdateTag → handleUpdateTag → load（客户端同步）双路同病；
 * BlockItem.loadHandler 的物品侧容器视图同样静默缩容退化。
 *
 * 本类反序列化语义：按当前容量重建清空（防重复 load 残留，对应 vanilla
 * setSize 分支的隐式清空），Items 按 Slot 键回填，越界槽静默跳过
 * （与 vanilla 回填分支同构）。容量迁移（27→36）时旧数据按 Slot 键回填，
 * 无损。序列化端保持 vanilla 格式（照写 Size），对外 NBT 兼容性不变。
 * 2026-10-29 起具体箱子容器统一经子类 AmmoCrateInventoryHandler（统一过滤）材料化，本类直接实例化点已清零。
 */
public class FixedCapacityItemStackHandler extends ItemStackHandler {

    public FixedCapacityItemStackHandler(int size) {
        super(size);
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // 按当前容量重建清空：等价 vanilla 无 Size 分支的 setSize(stacks.size())，
        // 覆盖重复反序列化（连续 BE 更新包 / load 重入）不留残留
        setSize(stacks.size());
        // Items 按 Slot 键回填（vanilla 同构：slot ∈ [0, stacks.size) 才落位）
        ListTag items = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            int slot = item.getInt("Slot");
            if (slot >= 0 && slot < stacks.size()) {
                stacks.set(slot, ItemStack.of(item));
            }
        }
        onLoad();
    }
}