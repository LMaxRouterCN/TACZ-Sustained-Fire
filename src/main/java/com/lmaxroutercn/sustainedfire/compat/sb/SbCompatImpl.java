package com.lmaxroutercn.sustainedfire.compat.sb;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.ammo.AmmoSource;
import com.lmaxroutercn.sustainedfire.ammo.InInventoryCrateHandler;
import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;
import com.lmaxroutercn.sustainedfire.item.AmmoCrateBlockItem;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SB 联动实现层：全部 SB/SC/Curios 引用集中于此（review 红线：禁止 import 进通用代码路径）。
 * 类加载守卫：SbCompat 门面仅在 ModList.isLoaded("sophisticatedbackpacks") 分支内触碰本类。
 *
 * [M12] 两处接线：init() 注册 SbAmmoSyncManager 冲洗循环；collectAmmoSources 读弹药前 refreshStaleTree。
 * [M14] 直写清扫（max 2026-10-31 报 bug 根治）：
 *   根因——TACZ findAndExtractInventoryAmmo 对弹药盒 setAmmoCount 直改 ItemStack NBT，
 *   绕过一切 handler 写操作：M12 的 SC onContentsChanged 漏斗对直改全盲，客户端镜像死水
 *   （盒在 SB 内=三症状复现）。同时 SB 内弹药箱物品的 InInventoryCrateHandler 是反序列化副本，
 *   直改连"写回箱物品 NBT"都没有——服务端存档一并回滚。
 *   修复——每个 SB 弹药源携带 sweep（消耗完成后由 ExternalConsumeSweep 链统一触发）：
 *   ①盒槽位：对底层 InventoryHandler 直调 onContentsChanged(slot)（fire）。
 *     该方法是 SC 官方的智能写回入口：updateSlotNbt 深比较 NBT 缓存（L116-131），变了才
 *     saveInventory（序列化→SavedData 活引用→markDirty）+ triggerOnChangeListeners——
 *     GUI 编辑等效路径，幂等免费，M12 漏斗（mixin 挂本方法 RETURN）自动接力推送。
 *     客户端预测路径 fire 落客户端镜像：HUD 读数即刻修正（比服务端推送快一拍），markDirty 的
 *     server 守卫挡回环，双端无害。
 *   ②背包内弹药箱副本（backpackUsable 门）：flushExternalDirectWrites 写回 SB 槽位 stack NBT
 *     后同样 fire 该槽——箱物品 NBT 直改同样绕过 handler op，不 fire 则 SavedData 不落盘。
 *
 * 嵌套双计防（设计约束）：root 的 getInventoryForInputOutput 已展开嵌套子背包（M10 实证），
 * 消耗视图只有 root IO 一个 SB 源；树遍历只用于清扫（盒 fire）与箱副本收集，不给子背包加视图源。
 * 新鲜副本防：箱副本 sweep 闭包捕获建源实例——TACZ 直改的是该实例内部 stack，
 * 清扫时新建副本会从陈旧 NBT 反序列化，等于把消耗抹掉。
 */
public final class SbCompatImpl {
    private SbCompatImpl() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SustainedFire.MODID);

    /** 供弹升级物品：装进 SB 背包升级槽且启用，该背包即成弹药源（见 AmmoFeedUpgradeItem） */
    public static final RegistryObject<Item> AMMO_FEED_UPGRADE =
            ITEMS.register("ammo_feed_upgrade", AmmoFeedUpgradeItem::new);

    /** 仅 SB 在场时由门面调用（SustainedFire 主类构造器） */
    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        // [M12] SB 供弹客户端同步：服务端冲洗循环（物理双端注册无害——专用客户端无 server 循环不触发）
        MinecraftForge.EVENT_BUS.addListener(SbAmmoSyncManager::onServerTick);
        SustainedFire.LOGGER.info("SB compat: 供弹升级 ammo_feed_upgrade 已注册 + M12 内容同步已挂载");
    }

    /** 创造标签页展示（门面转发，SB 缺席时 no-op） */
    public static void addCreativeItems(CreativeModeTab.Output output) {
        output.accept(AMMO_FEED_UPGRADE.get());
    }

    /**
     * 收集射手身上"装了启用供弹升级"的 SB 背包弹药源。
     * 扫描槽位：主 36 格 + 副手 + 盔甲 + Curios 饰品槽（max 2026-10-29 拍板覆盖范围）。
     *
     * 门票谓词 = getTypeWrappers(TYPE) 非空：UpgradeHandler.initializeTypeWrappers 字节码实证
     * 只收 enabled wrapper（isEnabled 过滤后才 addTypeWrapper），非空 ⇔ 装了且启用；
     * 且走 UpgradeHandler 缓存，首查后零 NBT 反序列化。
     *
     * [M12] refreshStaleTree 必须在 TypeWrappers 判定前：toggle 后升级启用状态经整树重建才
     * 反映到 getTypeWrappers（假阴性链在此闭合）。双端同码：服务端线程守卫内零成本跳过。
     *
     * [M14] 每源携带 sweep；门开时（ammo_crate.backpackUsable）树内弹药箱物品
     * 以 InInventoryCrateHandler 副本视图加入弹药源（语义与玩家背包内箱一致：
     * 物品态弹药箱作为弹药源，与所在容器无关——max 2026-10-31 拍板）。
     * 门关时零树遍历（仅 root IO 视图，行为与 M13 前完全一致）。
     */
    public static List<AmmoSource> collectAmmoSources(LivingEntity shooter) {
        if (!(shooter instanceof Player player)) {
            return List.of();
        }
        List<AmmoSource> result = new ArrayList<>();
        for (ItemStack stack : collectCandidateStacks(player)) {
            stack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance())
                    .ifPresent(wrapper -> {
                        // [M12] 客户端镜像消费点：树内有待刷新 uuid → 整树重建后再读（见 SbAmmoSyncManager）
                        SbAmmoSyncManager.refreshStaleTree(wrapper);
                        if (!wrapper.getUpgradeHandler()
                                .getTypeWrappers(AmmoFeedUpgradeItem.TYPE).isEmpty()) {
                            // [M16] 主体抽取至 appendBackpackSourceBody（与容器展开路径
                            // tryAppendBackpackSource 共用同一份源构造逻辑，行为不变重构）
                            appendBackpackSourceBody(wrapper, result, new HashSet<>());
                        }
                    });
        }
        return result;
    }

    /**
     * [M16] SB 三态判定+专用源追加（AmmoSourceResolver.appendExpandedSources 调用，
     * 消耗/查询两线共用）。
     * skipUpgraded：玩家背包上下文 true（源 5 在场，带升级 SB skip 防双计，b1）；
     * 箱内上下文 false（源 5 只扫玩家身上 candidates，箱内带升级 SB 若 skip 即
     * 两头断供——b1 是防双计手段而非升级独占原则，无双计风险的上下文不 skip）。
     * 返回 true = 本物品已处理（SB 或 skip）；false = 非 SB（调用方走通用 cap）。
     */
    static boolean tryAppendBackpackSource(ItemStack stack, List<AmmoSource> out, boolean skipUpgraded) {
        return stack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance())
                .map(wrapper -> {
                    // [M12] 客户端镜像消费：读弹药前刷陈旧树（服务端线程守卫内零成本跳过）
                    SbAmmoSyncManager.refreshStaleTree(wrapper);
                    if (skipUpgraded && !wrapper.getUpgradeHandler()
                            .getTypeWrappers(AmmoFeedUpgradeItem.TYPE).isEmpty()) {
                        // [M16-b1] 带启用升级且源 5 在场：skip，源 5 独占防双计
                        return true;
                    }
                    appendBackpackSourceBody(wrapper, out, new HashSet<>());
                    return true;
                })
                .orElse(false);
    }

    /** [M16] 专用源主体（行为不变重构）：collectAmmoSources 与 tryAppendBackpackSource 共用 */
    private static void appendBackpackSourceBody(IBackpackWrapper wrapper, List<AmmoSource> out, Set<UUID> visited) {
        // root IO 视图（含嵌套子背包展开）：唯一 SB 消耗视图（嵌套双计防）
        // sweep = 树级直写清扫（各节点盒槽位 fire）
        out.add(new AmmoSource(wrapper.getInventoryForInputOutput(),
                () -> sweepTreeDirectWrites(wrapper)));
        // [M14] 背包内弹药箱：backpackUsable 门下树遍历收集副本源
        if (SustainedFireConfig.booleanOr(SustainedFireConfig.AMMO_CRATE_BACKPACK_USABLE)) {
            collectCrateSourcesInTree(wrapper, out, visited);
        }
    }

    /**
     * [M14] 树级直写清扫：各节点 InventoryHandler 槽位上的弹药盒 → fire（onContentsChanged）。
     * uuid visited 防环（Inception 嵌套理论可成环），capability 判子背包（与
     * SbAmmoSyncManager.collectWrapperTreeUuids 同口径，不依赖具体物品类）。
     * 树扫描发生在消耗完成后（TAIL），此时盒直改已落底层 stacks——fire 即刻序列化回 SavedData。
     */
    private static void sweepTreeDirectWrites(IBackpackWrapper wrapper) {
        sweepNodeDirectWrites(wrapper, new HashSet<>());
    }

    private static void sweepNodeDirectWrites(IBackpackWrapper wrapper, Set<UUID> visited) {
        wrapper.getContentsUuid().ifPresent(uuid -> {
            if (!visited.add(uuid)) {
                return;
            }
            InventoryHandler handler = wrapper.getInventoryHandler();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack s = handler.getStackInSlot(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.getItem() instanceof IAmmoBox) {
                    // fire：SC 官方智能写回（NBT 深比较→序列化回 SavedData→markDirty→M12 推送链）
                    handler.onContentsChanged(i);
                } else {
                    s.getCapability(CapabilityBackpackWrapper.getCapabilityInstance())
                            .ifPresent(sub -> sweepNodeDirectWrites(sub, visited));
                }
            }
        });
    }

    /**
     * [M14] 树内弹药箱副本源收集（backpackUsable 门内调用）。
     * count==1 守卫：多叠物品的 NBT 写回无法定位唯一目标（与玩家背包侧同思路）。
     * sweep 闭包捕获建源实例 + 节点 handler/槽位（新鲜副本防：直改发生在这一个实例内部）。
     */
    private static void collectCrateSourcesInTree(IBackpackWrapper wrapper, List<AmmoSource> out, Set<UUID> visited) {
        wrapper.getContentsUuid().ifPresent(uuid -> {
            if (!visited.add(uuid)) {
                return;
            }
            InventoryHandler handler = wrapper.getInventoryHandler();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack s = handler.getStackInSlot(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.getItem() instanceof AmmoCrateBlockItem && s.getCount() == 1) {
                    // [M15] 经物品 cap 取缓存活实例（与玩家背包内箱、外部读取共用同一副本）
                    InInventoryCrateHandler copy = (InInventoryCrateHandler) s.getCapability(
                            net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
                    if (copy == null) {
                        continue; // 防御：cap 意外缺席时跳过（正常路径恒非空）
                    }
                    int slot = i;
                    out.add(new AmmoSource(copy, () -> {
                        // 副本直改写回 SB 槽位 stack NBT，再 fire 该槽（NBT 变化进 SavedData + M12 推送）
                        copy.flushExternalDirectWrites();
                        handler.onContentsChanged(slot);
                    }));
                } else {
                    s.getCapability(CapabilityBackpackWrapper.getCapabilityInstance())
                            .ifPresent(sub -> collectCrateSourcesInTree(sub, out, visited));
                }
            }
        });
    }

    /**
     * 玩家身上全部候选槽位的内容物（SB 背包可能出现在任一槽）。
     * [M12] 可见性 private → 包级：SbAmmoSyncManager.flush 的携带者树扫描复用此逻辑
     * （同一套槽位口径，避免两处各写一份漂移）。
     */
    static List<ItemStack> collectCandidateStacks(Player player) {
        Inventory inv = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < inv.items.size(); i++) {
            stacks.add(inv.items.get(i));
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            stacks.add(inv.armor.get(i));
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            stacks.add(inv.offhand.get(i));
        }
        // [M10] Curios 饰品槽（back 等全类型合并视图）：类加载隔离——仅 curios 在场才触碰采集器
        if (ModList.get().isLoaded("curios")) {
            stacks.addAll(CuriosBackpackCollector.getEquippedCurioStacks(player));
        }
        return stacks;
    }
}