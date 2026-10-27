package com.lmaxroutercn.sustainedfire.ammo;

import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹药源解析器：Sustained Fire 的核心。
 *
 * 将"玩家哪里有弹药"从 TACZ 原版的"裸背包 cap 遍历"扩展为多源组合视图：
 *   玩家裸背包（含快捷栏） → 背包内容器展开（精妙背包等一切暴露 ITEM_HANDLER 的物品） → 周围弹药箱（AABB 现查，开盖才供弹）
 *
 * 设计：不重写弹药判定/消耗逻辑，只通过 mixin @Redirect 把 TACZ 各处
 * shooter.getCapability(ITEM_HANDLER) 的裸背包视图替换为本类的组合视图，
 * TACZ 自身的 findAndExtractInventoryAmmo / isAmmoOfGun 对全槽位照常工作。
 *
 * 1.20.1 Forge 的 getCapability 返回 LazyOptional（1.21 才改为 Optional），
 * 因此本类对外暴露 LazyOptional，与被替换调用点的类型完全一致。
 *
 * 已知限制（里程碑 7 处理）：Curios 背槽佩戴的容器不在 vanilla ITEM_HANDLER 内；
 * 嵌套容器只展开一层，不递归。
 */
public final class AmmoSourceResolver {
    private AmmoSourceResolver() {}

    /**
     * 构建射手全部弹药源的组合视图（供 mixin redirect 调用）。
     * 每次调用构建新视图，调用期间（单 tick 主线程）各源一致。
     *
     * @param shooter 射手
     * @return 组合视图；无任何可用源时为空 LazyOptional（与原版 cap 缺失语义一致，
     *         调用方 .map().orElse() 链自动得到 0/false）
     */
    public static LazyOptional<IItemHandler> getCombinedSources(LivingEntity shooter) {
        List<IItemHandler> sources = buildSources(shooter);
        if (sources.isEmpty()) {
            return LazyOptional.empty();
        }
        return LazyOptional.of(() -> new CombinedView(sources.toArray(new IItemHandler[0])));
    }

    /**
     * 构建弹药源列表（按优先级排序）：
     * 1. 玩家裸背包 cap —— 原版唯一弹药源，保持最高优先级
     * 2. 容器展开 —— 对每个槽位物品查其自身 ITEM_HANDLER（SB 背包经 initCapabilities 暴露）
     * 3. 周围弹药箱 —— AABB 现查（查找半径来自 config；开盖过滤在 AmmoSourceRegistry 查询侧）
     */
    private static List<IItemHandler> buildSources(LivingEntity shooter) {
        List<IItemHandler> sources = new ArrayList<>();
        shooter.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(sources::add);
        List<IItemHandler> expanded = new ArrayList<>();
        for (IItemHandler base : sources) {
            for (int i = 0; i < base.getSlots(); i++) {
                base.getStackInSlot(i).getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(expanded::add);
            }
        }
        sources.addAll(expanded);
        // 弹药箱查找半径：config 可调（默认 4.0，范围 1.0-64.0）
        sources.addAll(AmmoSourceRegistry.getNearbyAmmoBoxes(shooter, SustainedFireConfig.AMMO_CRATE_RADIUS.get()));
        return sources;
    }

    /**
     * 只读组合视图：把多个 IItemHandler 的槽位拼接为连续槽位空间。
     * Forge 的 CombinedInvWrapper 只接受 IItemHandlerModifiable[]，
     * 而弹药源仅保证 IItemHandler（TACZ 消耗走 getStackInSlot + extractItem），
     * 故自实现组合。insertItem 直通对应源（保持物品守恒语义）。
     */
    private static final class CombinedView implements IItemHandler {
        private final IItemHandler[] delegates;

        CombinedView(IItemHandler[] delegates) {
            this.delegates = delegates;
        }

        @Override
        public int getSlots() {
            int n = 0;
            for (IItemHandler d : delegates) {
                n += d.getSlots();
            }
            return n;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int s = slot;
            for (IItemHandler d : delegates) {
                if (s < d.getSlots()) {
                    return d.getStackInSlot(s);
                }
                s -= d.getSlots();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int s = slot;
            for (IItemHandler d : delegates) {
                if (s < d.getSlots()) {
                    return d.insertItem(s, stack, simulate);
                }
                s -= d.getSlots();
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int s = slot;
            for (IItemHandler d : delegates) {
                if (s < d.getSlots()) {
                    return d.extractItem(s, amount, simulate);
                }
                s -= d.getSlots();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            int s = slot;
            for (IItemHandler d : delegates) {
                if (s < d.getSlots()) {
                    return d.getSlotLimit(s);
                }
                s -= d.getSlots();
            }
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int s = slot;
            for (IItemHandler d : delegates) {
                if (s < d.getSlots()) {
                    return d.isItemValid(s, stack);
                }
                s -= d.getSlots();
            }
            return false;
        }
    }
    /**
     * 弹药箱侧纯布尔查询：附近开盖弹药箱内是否存在该枪可用的弹药/弹药盒。
     * 供 AbstractGunItemMixin 的 Inject-RETURN 兜底调用——背包侧已由原方法
     * （双装时含第三方扩展视图）查过，此处不重复，只查箱。
     * [GOAL-PLAN 6.6] taczaddon 对撞迁移：布尔查询由 @Redirect 换源改为兜底。
     */
    public static boolean hasAmmoInCrates(LivingEntity shooter, ItemStack gun) {
        for (IItemHandler box : AmmoSourceRegistry.getNearbyAmmoBoxes(shooter, SustainedFireConfig.AMMO_CRATE_RADIUS.get())) {
            for (int i = 0; i < box.getSlots(); i++) {
                ItemStack stack = box.getStackInSlot(i);
                if (stack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gun, stack)) {
                    return true;
                }
                if (stack.getItem() instanceof IAmmoBox ammoBox && ammoBox.isAmmoBoxOfGun(gun, stack)) {
                    return true;
                }
            }
        }
        return false;
    }
    /**
     * 统计附近开盖弹药箱中可供该枪使用的弹药总数（枚数口径与 HUD 一致）。
     * [M6 修复 ⑤/③] GunHudOverlay 的 handleCacheCount 在纯背包计数后追加本值：
     * HUD 显示含箱 + 直读枪弹匣 NBT 回写喂饱 doShoot 连发数。
     */
    public static int countAmmoInCrates(LivingEntity shooter, ItemStack gun) {
        int count = 0;
        for (IItemHandler box : AmmoSourceRegistry.getNearbyAmmoBoxes(shooter, SustainedFireConfig.AMMO_CRATE_RADIUS.get())) {
            for (int i = 0; i < box.getSlots(); i++) {
                ItemStack ammo = box.getStackInSlot(i);
                if (ammo.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gun, ammo)) {
                    count += ammo.getCount();
                }
                if (ammo.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(gun, ammo)) {
                    count += iAmmoBox.getAmmoCount(ammo);
                }
            }
        }
        return count;
    }
}