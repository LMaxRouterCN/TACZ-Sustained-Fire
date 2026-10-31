package com.lmaxroutercn.sustainedfire.ammo;

import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
import com.lmaxroutercn.sustainedfire.item.AmmoCrateBlockItem;
import com.lmaxroutercn.sustainedfire.util.AmmoCrateInventoryHandler;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 背包内弹药箱物品的容器视图（ammo_crate.backpackUsable=true 时作为弹药源，
 * max 三连拍板 ②，M6 预留开关的本次接线）。
 *
 * 与 AmmoCrateBlockItem 光标交互同构：BlockEntityTag.Items 反序列化进来，
 * 变动写回同一键并同步 sf:fill 缓存（耐久条/开盖模型谓词的 O(1) 读不掉链）。
 *
 * 持有 ItemStack 活引用（非副本）：写回直接落容器槽位的那一格
 * （玩家背包格或 SB handler 槽），TACZ 消耗侧每发 extractItem 后经
 * onContentsChanged 写回。
 * 开销账：每发一次 36 格反序列化 + 一次序列化，主线程量级 <0.1ms，
 * 先实测再谈优化（真卡则上脏标记/延迟写回）。
 *
 * [M14] 本类是反序列化副本——TACZ 对盒 setAmmoCount 直改的是副本内的
 * ItemStack，不触发任何 handler op、不落 crateStack NBT：直改后副本与
 * 容器槽位 NBT 分叉，服务端存档回滚 + 客户端可见性双丢失。
 * flushExternalDirectWrites 把副本现状写回（消耗完成后由清扫链统一调用）。
 *
 * 前置守卫（调用方 AmmoSourceResolver.collectInventoryCrates）：
 * 仅 stack.getCount()==1 的箱子参与——多叠物品的 NBT 写回无法定位唯一目标
 * （与 SB initCapabilities 的 count 守卫同思路）。
 * 过滤继承 AmmoCrateInventoryHandler.isItemValid（crateAmmoOnly 对背包内箱子同样生效）。
 */
public class InInventoryCrateHandler extends AmmoCrateInventoryHandler {

    /** 箱子物品活引用（容器槽位上的那一格） */
    private final ItemStack crateStack;

    public InInventoryCrateHandler(ItemStack crateStack) {
        super(AmmoCrateBlockEntity.CONTAINER_SIZE);
        this.crateStack = crateStack;
        // 与 AmmoCrateBlockItem.loadHandler 同构：BlockEntityTag 下 "Items" 是
        // ItemStackHandler.serializeNBT() 的包装复合标签 {Items:[...], Size:N}
        CompoundTag beTag = crateStack.getTagElement("BlockEntityTag");
        if (beTag != null) {
            deserializeNBT(beTag.getCompound("Items"));
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        // handler 写操作路径（extractItem 等）→ 写回主体
        writeBack();
    }

    /**
     * [M14] 外部直写清扫：副本内含弹药盒时把现状写回容器槽位 NBT。
     * 只认盒不认散弹：散弹消耗走 extractItem（onContentsChanged 已即时写回），
     * 盒消耗走 setAmmoCount 直改（唯一需要兜的路径）。
     * 幂等：写回无副作用，多次触发安全（RETURN 钩语义）。
     */
    public void flushExternalDirectWrites() {
        for (int i = 0; i < getSlots(); i++) {
            if (getStackInSlot(i).getItem() instanceof IAmmoBox) {
                writeBack();
                return;
            }
        }
    }

    /** 写回主体（onContentsChanged 与 M14 清扫共用）：Items NBT + sf:fill 缓存 */
    private void writeBack() {
        // 写回容器槽位格 NBT + sf:fill 缓存（耐久条与开盖谓词跟随真实内容）
        crateStack.getOrCreateTagElement("BlockEntityTag").put("Items", serializeNBT());
        int occupied = 0;
        for (int i = 0; i < getSlots(); i++) {
            if (!getStackInSlot(i).isEmpty()) {
                occupied++;
            }
        }
        crateStack.getOrCreateTag().putInt(AmmoCrateBlockItem.TAG_FILL, occupied);
    }
}