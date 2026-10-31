package com.lmaxroutercn.sustainedfire.ammo;

import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
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
 *   2. 容器展开 —— 背包内物品自带的 ITEM_HANDLER（潜影盒等；弹药箱物品跳过——其供弹走源 3）。
 *      受 supply.expandContainerItems 闸（默认 false）。
 *      [M16] 展开逻辑收敛到 appendExpandedSources 公共件：消耗 buildSources 与
 *      查询 buildExtendedSources 两线共用——「源 2 只在消耗装配线」的装配线分叉
 *      在此根除（C 立项①）。SB 背包在此三态化（b1）：带启用升级 → skip 给源 5
 *      （防双计）；无升级 → 专用源（root IO + 树清扫，与源 5 同构——闸开即供弹，
 *      双计由 b1 消除）；非 SB → 通用 cap bare。
 *   3. 背包内弹药箱物品 —— ammo_crate.backpackUsable=true 时（M6 预留，M11 接线），
 *      仅计数 1 的箱子参与（多叠物品的 NBT 写回无法定位唯一目标）。
 *      [M14] 扫描范围扩展：玩家背包 36 格 + SB 供弹升级背包树内（SbCompatImpl，
 *      语义统一：物品态弹药箱作为弹药源，与所在容器无关——max 2026-10-31 拍板）。
 *   4. 周围放置弹药箱（AABB 现查，开盖才供弹，半径来自 config）
 *   5. SB 供弹升级背包 —— 装有启用「供弹升级」的精妙背包（主 36 格+副手+盔甲+Curios 饰品槽）。
 *      [M16] 与源 2 的关系修订（b1 落档）：不再「完全正交」——闸开时无升级 SB 由
 *      源 2 展开供弹，带升级 SB 由源 5 独占（源 2 skip 防双计）；箱内 SB（源 4 内部
 *      展开）因源 5 只扫玩家身上而统一展开（升级不构成 skip 理由，见
 *      tryAppendBackpackSource 的 skipUpgraded 参数）。
 *
 * 查询侧（hasAmmoInCrates / countAmmoInCrates）与消耗侧同步覆盖 2+3+4+5 四源
 * （[M16-C] 源 2 补齐——此前仅消耗装配线含源 2，预检/HUD 看不见容器内弹）：
 * 缺任何一侧即复现"消耗能扣弹而预检说没弹，枪不开火"的预检坑。
 *
 * [M14] 直写清扫架构（max 2026-10-31 报 bug 根治，见 ExternalConsumeSweep）：
 * TACZ findAndExtractInventoryAmmo 对弹药盒（IAmmoBox）setAmmoCount 直改 NBT 绕过一切
 * handler 写操作——挂在 op 上的同步/持久化链（M12 漏斗 / BE onContentsChanged /
 * 副本写回）全部失明。修复：每个弹药源携带 sweep Runnable，消耗完成后
 * 由 CombinedView（实现 ExternalConsumeSweep）统一触发，TACZ 的 RETURN 钩（mixin）驱动。
 * 各源 sweep 责任：裸背包/通用展开 = null（vanilla broadcastChanges 每 tick NBT 深比较兜）；
 * 物品态箱副本 = flushExternalDirectWrites；世界箱 BE = sweepExternalAmmoBoxChange；
 * SB = 树级盒 fire + 箱副本 flush+fire（见 SbCompatImpl）。
 * [M16] crateSource 复合清扫：世界箱含内部展开时，内层 sweep（嵌套箱 flush / SB 树
 * fire）先行，BE sweep 收尾——sendBlockUpdated → serializeNBT 必须读到 flush 后
 * 的新数据，顺序反了客户端拿到陈旧快照。
 *
 * 设计：不重写弹药判定/消耗逻辑，只通过 mixin @Redirect 把 TACZ 各处
 * shooter.getCapability(ITEM_HANDLER) 的裸背包视图替换为本类的组合视图，
 * TACZ 自身的 findAndExtractInventoryAmmo / isAmmoOfGun 对全槽位照常工作。
 *
 * 1.20.1 Forge 的 getCapability 返回 LazyOptional（1.21 才改为 Optional），
 * 因此本类对外暴露 LazyOptional，与被替换调用点的类型完全一致。
 *
 * 已知限制：通用容器展开仅一层不递归（潜影盒无嵌套；其他 mod 嵌套容器 YAGNI）；
 * SB 树内的物品态弹药箱由源 5 路径覆盖（backpackUsable 门）；箱内 SB 树内再
 * 嵌套物品箱等深层组合不展开（双容器嵌套的清扫链复杂度与实际收益不成比例）。
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
        List<AmmoSource> sources = buildSources(shooter);
        if (sources.isEmpty()) {
            return LazyOptional.empty();
        }
        return LazyOptional.of(() -> new CombinedView(sources.toArray(new AmmoSource[0])));
    }

    /**
     * 构建弹药源列表（按优先级排序，见类注释 1-5）。
     */
    private static List<AmmoSource> buildSources(LivingEntity shooter) {
        List<AmmoSource> sources = new ArrayList<>();
        shooter.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                .ifPresent(cap -> sources.add(AmmoSource.bare(cap)));
        // 容器展开（闸：supply.expandContainerItems，默认 false——SB 背包等断供，M10 升级物品接管）
        // [M16] 展开逻辑收敛到 appendExpandedSources（与查询侧 buildExtendedSources 同构，
        // 供弹判定口径两线唯一——「源 2 只在消耗装配线」的装配线分叉在此根除）
        if (SustainedFireConfig.booleanOr(SustainedFireConfig.SUPPLY_EXPAND_CONTAINER_ITEMS)) {
            List<AmmoSource> expanded = new ArrayList<>();
            for (AmmoSource base : sources) {
                for (int i = 0; i < base.handler().getSlots(); i++) {
                    appendExpandedSources(base.handler().getStackInSlot(i), expanded, true);
                }
            }
            sources.addAll(expanded);
        }
        // 背包内弹药箱物品（闸：ammo_crate.backpackUsable）
        sources.addAll(collectInventoryCrates(shooter));
        // 周围放置弹药箱：查找半径 config 可调（默认 4.0，范围 1.0-64.0）
        // [M14] BE 级访问：sweep 需要 BE 身份（sweepExternalAmmoBoxChange）
        // [M15] crateSource：BE + 内部容器展开组合视图（expandContainersInside 闸，默认 true）
        for (AmmoCrateBlockEntity crate : AmmoSourceRegistry.getNearbyAmmoCrates(shooter, SustainedFireConfig.doubleOr(SustainedFireConfig.AMMO_CRATE_RADIUS))) {
            sources.add(crateSource(crate));
        }
        // [M10] SB 供弹升级背包：装了启用升级的精妙背包作为独立弹药源（SbCompat 门面，SB 缺席时空列表）
        // [M14] 源自带 sweep（树级直写清扫 + backpackUsable 门下的箱副本源）
        sources.addAll(SbCompat.collectAmmoSources(shooter));
        return sources;
    }

    /**
     * [M16] 源 2 展开公共件：单个物品 → 弹药源（消耗/查询两线共用，判定口径唯一）。
     * 分支（顺序即优先级）：
     *   1. 弹药箱物品 skip——物品态箱只走源 3（backpackUsable 门控），容器展开永不接手
     *      （闸关时源 3 断供，此处同样 skip 防绕门；多叠守卫在源 3 侧）
     *   2. SB 背包三态——带启用升级且 skipUpgraded（玩家背包上下文，源 5 在场）→
     *      skip 防双计（b1）；其余 → 专用源（root IO 视图 + 树级清扫 + refreshStaleTree
     *      客户端镜像消费，与源 5 同构）
     *   3. 通用容器 cap（潜影盒等一切暴露 ITEM_HANDLER 的物品）→ bare 源
     */
    private static void appendExpandedSources(ItemStack s, List<AmmoSource> out, boolean skipUpgraded) {
        if (s.getItem() instanceof AmmoCrateBlockItem) {
            return;
        }
        if (SbCompat.tryAppendBackpackSource(s, out, skipUpgraded)) {
            return;
        }
        s.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                .ifPresent(h -> out.add(AmmoSource.bare(h)));
    }

    /**
     * 背包内弹药箱物品收集：物品态弹药箱且计数为 1 的每格经物品 cap 取缓存 InInventoryCrateHandler 活实例
     * （过滤继承 AmmoCrateInventoryHandler.isItemValid，crateAmmoOnly 对背包内箱子同样生效）。
     * [M14] sweep = flushExternalDirectWrites（副本直改写回箱物品 NBT；
     * 玩家背包槽位由 vanilla broadcastChanges 兜同步与持久化，无需额外 fire）。
     */
    private static List<AmmoSource> collectInventoryCrates(LivingEntity shooter) {
        List<AmmoSource> result = new ArrayList<>();
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
                // [M15] 经物品 cap 取缓存活实例（AmmoCrateBlockItem 注册的存储 cap）：
                // 与外部读取/光标交互共用同一副本，写入互通；sweep 语义不变（直写清扫）
                stack.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(h -> {
                    InInventoryCrateHandler view = (InInventoryCrateHandler) h;
                    result.add(new AmmoSource(view, view::flushExternalDirectWrites));
                });
            }
        }
        return result;
    }

    /**
     * [M15] 世界箱弹药源：BE 本体 +（闸：ammo_crate.expandContainersInside，默认 true）
     * 内部容器物品的组合视图——潜影盒等暴露存储的物品在箱内同样可供弹。
     * 查询（预检/HUD）与消耗两侧共用本构造，供弹判据口径一致。
     * [M16] 箱中箱展开（推翻 2026-10-31「盒中盒不供弹」拍板，max 授权 C 立项）：
     * 箱内物品态弹药箱（count==1）以源 3 同款 cap 缓存副本参与（带 flush 清扫）；
     * 箱内 SB 背包升级为专用源（wrapper root IO + 树级清扫，skipUpgraded=false——
     * 源 5 只扫玩家身上 candidates，箱内带升级 SB 若 skip 即两头断供）。
     * [M16] 复合清扫：内层各源 sweep 先行（嵌套箱 flush 写回 NBT / SB 树序列化回写），
     * BE sweep 收尾——sendBlockUpdated → getUpdateTag → serializeNBT 必须读到
     * flush 后的新数据，顺序反了客户端拿到陈旧快照。
     */
    private static AmmoSource crateSource(AmmoCrateBlockEntity crate) {
        IItemHandler base = crate.getItemHandler();
        List<AmmoSource> parts = new ArrayList<>();
        parts.add(AmmoSource.bare(base));
        if (SustainedFireConfig.booleanOr(SustainedFireConfig.AMMO_CRATE_EXPAND_CONTAINERS_INSIDE)) {
            for (int i = 0; i < base.getSlots(); i++) {
                ItemStack s = base.getStackInSlot(i);
                if (s.getItem() instanceof AmmoCrateBlockItem) {
                    // [M16] 箱中箱：源 3 同款（cap 缓存 InInventoryCrateHandler 副本 + flush 清扫）。
                    // count==1 守卫与 cap 侧（CrateStorageProvider）同口径：多叠不展开
                    if (s.getCount() == 1) {
                        var opt = s.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve();
                        if (opt.isPresent() && opt.get() instanceof InInventoryCrateHandler view) {
                            parts.add(new AmmoSource(view, view::flushExternalDirectWrites));
                        }
                    }
                    continue;
                }
                // [M16] SB 三态（箱内上下文 skipUpgraded=false：源 5 够不着箱内树，
                // 升级不构成 skip 理由，统一 root IO 展开防两头断供）
                if (SbCompat.tryAppendBackpackSource(s, parts, false)) {
                    continue;
                }
                s.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                        .ifPresent(h -> parts.add(AmmoSource.bare(h)));
            }
        }
        if (parts.size() == 1) {
            // 单源（无内部展开）：现状语义一字不变——BE 主库存 + BE sweep 单回调
            return new AmmoSource(parts.get(0).handler(), crate::sweepExternalAmmoBoxChange);
        }
        CombinedView view = new CombinedView(parts.toArray(new AmmoSource[0]));
        // 复合清扫：内层先行（嵌套箱 flush / SB 树 fire），BE 收尾（推送读新数据）
        return new AmmoSource(view, () -> {
            view.afterExternalConsume();
            crate.sweepExternalAmmoBoxChange();
        });
    }

    /**
     * 只读组合视图：把多个弹药源的槽位拼接为连续槽位空间，并实现 [M14] 直写清扫接口。
     * Forge 的 CombinedInvWrapper 只接受 IItemHandlerModifiable[]，
     * 而弹药源仅保证 IItemHandler（TACZ 消耗走 getStackInSlot + extractItem），
     * 故自实现组合。insertItem 直通对应源（保持物品守恒语义）。
     * delegates 与 sweeps 双数组同构：槽位路由用前者，消耗完成清扫用后者。
     */
    private static final class CombinedView implements IItemHandler, ExternalConsumeSweep {
        private final IItemHandler[] delegates;
        private final Runnable[] sweeps;

        CombinedView(AmmoSource[] sources) {
            this.delegates = new IItemHandler[sources.length];
            this.sweeps = new Runnable[sources.length];
            for (int i = 0; i < sources.length; i++) {
                this.delegates[i] = sources[i].handler();
                this.sweeps[i] = sources[i].sweep();
            }
        }

        /** [M14] 消耗完成清扫：依次触发各源 sweep（null 跳过），幂等（见 ExternalConsumeSweep 契约） */
        @Override
        public void afterExternalConsume() {
            for (Runnable sweep : sweeps) {
                if (sweep != null) {
                    sweep.run();
                }
            }
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
     * 查询侧共用：源 2 容器展开（[M16-C] 补齐——消耗/查询两线同构）+
     * 物品态箱（含 SB 树内，backpackUsable 门）+ 世界箱（含内部容器展开，
     * 与消耗侧同一 crateSource 构造）+ SB 升级背包。
     * [M16-C] 源 2 补丁：闸开时预检/HUD 认容器内弹药——tacz 原方法只扫裸背包
     * 不展开容器，此处展开不构成重复计数；「消耗能扣弹而预检说没弹，枪不开火」
     * 的预检坑在源 2 上的最后一处闭合。
     * skipUpgraded=true：与玩家背包侧 buildSources 同语义（源 5 在场）。
     */
    private static List<AmmoSource> buildExtendedSources(LivingEntity shooter) {
        List<AmmoSource> sources = new ArrayList<>();
        // [M16-C] 源 2 补丁：与消耗侧同构调 appendExpandedSources（判定口径唯一）
        if (SustainedFireConfig.booleanOr(SustainedFireConfig.SUPPLY_EXPAND_CONTAINER_ITEMS)) {
            shooter.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(cap -> {
                for (int i = 0; i < cap.getSlots(); i++) {
                    appendExpandedSources(cap.getStackInSlot(i), sources, true);
                }
            });
        }
        sources.addAll(collectInventoryCrates(shooter));
        for (AmmoCrateBlockEntity crate : AmmoSourceRegistry.getNearbyAmmoCrates(shooter, SustainedFireConfig.doubleOr(SustainedFireConfig.AMMO_CRATE_RADIUS))) {
            sources.add(crateSource(crate));
        }
        sources.addAll(SbCompat.collectAmmoSources(shooter));
        return sources;
    }

    /**
     * 纯布尔查询：物品态弹药箱（backpackUsable 时，含 SB 树内）+ 附近开盖弹药箱 +
     * SB 供弹升级背包内，是否存在该枪可用的弹药/弹药盒。
     * 供 AbstractGunItemMixin 的 Inject-RETURN 兜底调用——背包侧已由原方法
     * （双装时含第三方扩展视图）查过，此处不重复。
     * [GOAL-PLAN 6.6] taczaddon 对撞迁移：布尔查询由 @Redirect 换源改为兜底。
     * [max 2026-10-29] 查询侧与消耗侧同步接入 backpackUsable（预检坑预防）。
     * [M14] SB 树内箱物品：InInventoryCrateHandler 视图的槽位即箱内物品，
     * containsAmmoForGun 对其照常工作（箱内是 IAmmo/IAmmoBox）——零特判。
     * [M16-C] 含源 2 容器展开（闸开时，见 buildExtendedSources）。
     */
    public static boolean hasAmmoInCrates(LivingEntity shooter, ItemStack gun) {
        // 匹配谓词收敛到 AmmoSourceRegistry.containsAmmoForGun，与黄线框高亮侧同源
        for (AmmoSource source : buildExtendedSources(shooter)) {
            if (AmmoSourceRegistry.containsAmmoForGun(source.handler(), gun)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统计物品态弹药箱（backpackUsable 时，含 SB 树内）+ 附近开盖弹药箱 + SB 供弹升级背包中
     * 可供该枪使用的弹药总数（枚数口径与 HUD 一致）。
     * [M6 修复 ⑤/③] GunHudOverlay 的 handleCacheCount 在纯背包计数后追加本值：
     * HUD 显示含弹药箱侧 + 直读枪弹匣 NBT 回写喂饱 doShoot 连发数。
     * [M16-C] 含源 2 容器展开（闸开时 HUD 认容器内弹药，见 buildExtendedSources）。
     */
    public static int countAmmoInCrates(LivingEntity shooter, ItemStack gun) {
        int count = 0;
        for (AmmoSource source : buildExtendedSources(shooter)) {
            count += countAmmoForGun(source.handler(), gun);
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