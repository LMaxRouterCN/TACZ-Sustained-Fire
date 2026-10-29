package com.lmaxroutercn.sustainedfire.ammo;

import com.lmaxroutercn.sustainedfire.compat.sb.SbCompat;
import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;
import com.lmaxroutercn.sustainedfire.item.AmmoCrateBlockItem;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹药源解析器：Sustained Fire 的核心。
 *
 * 将"玩家哪里有弹药"从 TACZ 原版的"裸背包 cap 遍历"扩展为多源组合视图，优先级：
 *   1. 玩家裸背包（含快捷栏）—— 原版唯一弹药源，保持最高优先级
 *   2. 容器展开 —— 背包内物品自带的 ITEM_HANDLER（精妙背包等）。
 *      [max 2026-10-29] 受 supply.expandContainerItems 闸（默认 false）：
 *      背包类物品不再无条件供弹，M10 将由其专属升级物品重新开放（max 已确认断供后果）。
 *   3. 背包内弹药箱物品 —— ammo_crate.backpackUsable=true 时（本次接线，M6 预留），
 *      仅计数 1 的箱子参与（多叠物品的 NBT 写回无法定位唯一目标）。
 *      扫描范围：主背包 36 格 + 快捷栏（inv.items 全覆盖）；盔甲/副手槽刻意排除，扩展留 M10 复工拍板。
 *   4. 周围放置弹药箱（AABB 现查，开盖才供弹，半径来自 config）
 *   5. SB 供弹升级背包 —— 装有启用「供弹升级」的精妙背包（主 36 格+副手+盔甲+Curios 饰品槽），
 *      与源 2 的 expandContainerItems 闸完全正交：升级是背包自身属性而非通用容器展开（max 2026-10-29 拍板）
 *
 * 查询侧（hasAmmoInCrates / countAmmoInCrates）与消耗侧同步覆盖 3+4 两源：
 * 缺任何一侧即复现"消耗能扣弹而预检说没弹，枪不开火"的预检坑。
 *
 * 设计：不重写弹药判定/消耗逻辑，只通过 mixin @Redirect 把 TACZ 各处
 * shooter.getCapability(ITEM_HANDLER) 的裸背包视图替换为本类的组合视图，
 * TACZ 自身的 findAndExtractInventoryAmmo / isAmmoOfGun 对全槽位照常工作。
 *
 * 1.20.1 Forge 的 getCapability 返回 LazyOptional（1.21 才改为 Optional），
 * 因此本类对外暴露 LazyOptional，与被替换调用点的类型完全一致。
 *
 * 已知限制：通用容器展开仅一层不递归；Curios 饰品槽仅 SB 背包经供弹升级覆盖（M10 落地），
 * 其余 Curios 容器物品不参与供弹。
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
     * 构建弹药源列表（按优先级排序，见类注释 1-4）。
     */
    private static List<IItemHandler> buildSources(LivingEntity shooter) {
        List<IItemHandler> sources = new ArrayList<>();
        shooter.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(sources::add);
        // 容器展开（闸：supply.expandContainerItems，默认 false——SB 背包等断供，M10 升级物品接管）
        if (SustainedFireConfig.booleanOr(SustainedFireConfig.SUPPLY_EXPAND_CONTAINER_ITEMS)) {
            List<IItemHandler> expanded = new ArrayList<>();
            for (IItemHandler base : sources) {
                for (int i = 0; i < base.getSlots(); i++) {
                    base.getStackInSlot(i).getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(expanded::add);
                }
            }
            sources.addAll(expanded);
        }
        // 背包内弹药箱物品（闸：ammo_crate.backpackUsable，本次接线）
        sources.addAll(collectInventoryCrates(shooter));
        // 周围放置弹药箱：查找半径 config 可调（默认 4.0，范围 1.0-64.0）
        sources.addAll(AmmoSourceRegistry.getNearbyAmmoBoxes(shooter, SustainedFireConfig.doubleOr(SustainedFireConfig.AMMO_CRATE_RADIUS)));
        // [M10] SB 供弹升级背包：装了启用升级的 SB 背包作为独立弹药源（SbCompat 门面，SB 缺席时空列表）
        // 边界：expandContainerItems 闸与升级同开且该背包在主 36 格时会双计一次（默认闸关碰不到，max 已知悉）
        sources.addAll(SbCompat.collectAmmoSources(shooter));
        return sources;
    }

    /**
     * 背包内弹药箱物品收集：物品态弹药箱且计数为 1 的每格建一个 InInventoryCrateHandler 视图
     * （过滤继承 AmmoCrateInventoryHandler.isItemValid，crateAmmoOnly 对背包内箱子同样生效）。
     */
    private static List<IItemHandler> collectInventoryCrates(LivingEntity shooter) {
        List<IItemHandler> result = new ArrayList<>();
        if (!SustainedFireConfig.booleanOr(SustainedFireConfig.AMMO_CRATE_BACKPACK_USABLE)) {
            return result;
        }
        if (!(shooter instanceof Player player)) {
            return result;
        }
        Inventory inv = player.getInventory();
        // inv.items = 主背包 36 格（0-8 快捷栏 + 9-35 主格）；盔甲（36-39）/副手（40）槽刻意不扫
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.getItem() instanceof AmmoCrateBlockItem && stack.getCount() == 1) {
                result.add(new InInventoryCrateHandler(stack));
            }
        }
        return result;
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
     * 纯布尔查询：背包内弹药箱物品（backpackUsable 时）+ 附近开盖弹药箱内，
     * 是否存在该枪可用的弹药/弹药盒。
     * 供 AbstractGunItemMixin 的 Inject-RETURN 兜底调用——背包侧已由原方法
     * （双装时含第三方扩展视图）查过，此处不重复，只查弹药箱侧（含物品态）。
     * [GOAL-PLAN 6.6] taczaddon 对撞迁移：布尔查询由 @Redirect 换源改为兜底。
     * [max 2026-10-29] 查询侧与消耗侧同步接入 backpackUsable（预检坑预防）。
     */
    public static boolean hasAmmoInCrates(LivingEntity shooter, ItemStack gun) {
        // 匹配谓词收敛到 AmmoSourceRegistry.containsAmmoForGun，与黄线框高亮侧同源
        for (IItemHandler crate : collectInventoryCrates(shooter)) {
            if (AmmoSourceRegistry.containsAmmoForGun(crate, gun)) {
                return true;
            }
        }
        for (IItemHandler box : AmmoSourceRegistry.getNearbyAmmoBoxes(shooter, SustainedFireConfig.doubleOr(SustainedFireConfig.AMMO_CRATE_RADIUS))) {
            if (AmmoSourceRegistry.containsAmmoForGun(box, gun)) {
                return true;
            }
        }
        // [M10] SB 供弹升级背包：查询侧同步接入（与消耗侧同源，防「能扣弹却不开火」的预检坑）
        for (IItemHandler backpack : SbCompat.collectAmmoSources(shooter)) {
            if (AmmoSourceRegistry.containsAmmoForGun(backpack, gun)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统计背包内弹药箱物品（backpackUsable 时）+ 附近开盖弹药箱中可供该枪使用的弹药总数
     * （枚数口径与 HUD 一致）。
     * [M6 修复 ⑤/③] GunHudOverlay 的 handleCacheCount 在纯背包计数后追加本值：
     * HUD 显示含弹药箱侧 + 直读枪弹匣 NBT 回写喂饱 doShoot 连发数。
     */
    public static int countAmmoInCrates(LivingEntity shooter, ItemStack gun) {
        int count = 0;
        for (IItemHandler crate : collectInventoryCrates(shooter)) {
            count += countAmmoForGun(crate, gun);
        }
        for (IItemHandler box : AmmoSourceRegistry.getNearbyAmmoBoxes(shooter, SustainedFireConfig.doubleOr(SustainedFireConfig.AMMO_CRATE_RADIUS))) {
            count += countAmmoForGun(box, gun);
        }
        // [M10] SB 供弹升级背包：HUD 计数同步接入（枚数口径与弹药箱侧一致）
        for (IItemHandler backpack : SbCompat.collectAmmoSources(shooter)) {
            count += countAmmoForGun(backpack, gun);
        }
        return count;
    }

    /** 单容器内该枪可用弹药计数（IAmmo 按堆数 / IAmmoBox 按盒内枚数，与消耗侧同 API） */
    private static int countAmmoForGun(IItemHandler box, ItemStack gun) {
        int count = 0;
        for (int i = 0; i < box.getSlots(); i++) {
            ItemStack ammo = box.getStackInSlot(i);
            if (ammo.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gun, ammo)) {
                count += ammo.getCount();
            }
            if (ammo.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(gun, ammo)) {
                count += iAmmoBox.getAmmoCount(ammo);
            }
        }
        return count;
    }
}