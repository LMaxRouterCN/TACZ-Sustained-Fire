package com.lmaxroutercn.sustainedfire.compat.sb;

import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackContentsMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.network.SBPPacketHandler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [M12] SB 供弹客户端同步管理器 —— 三症状（射空假开枪 / GUI 关升级假象 / GUI 开升级假死）的根治。
 *
 * 根因：SB 背包内容不在 ItemStack NBT（服务端真身在世界 SavedData，客户端镜像
 * BackpackStorage.clientStorageCopy 仅在 GUI 打开时经 Request→BackpackContentsMessage 链刷新）。
 * GUI 关闭后镜像死水，HUD/开火预检读 capability wrapper 的缓存快照（构造时反序列化）→
 * 假阳性（旧计数>0，客户端预检通过有动画，服务端无弹拒绝）/ 假阴性（旧状态=关，服务端有弹不射）。
 *
 * 修复链（标记 → 推送 → 落地 → 消费 四段）：
 *   服务端标记：mixin 挂 SB 变更汇聚点（BackpackWrapper.mark* / SC InventoryHandler.
 *             onContentsChanged / SC UpgradeHandler.onContentsChanged / UpgradeToggleMessage
 *             toggle lambda）→ markDirty(uuid)。全部幂等入集，热路径仅 uuid 查询 + set.add
 *   推送：ServerTickEvent END 冲洗（节流 intervalTicks，默认 1=每 tick）：dirty ∩ 携带者
 *             "带供弹升级的背包树" → SBPPacketHandler.sendToClient(BackpackContentsMessage)
 *   客户端落地：SB 自家收包 handler 把内容 merge 进镜像（复用其 GUI 同步链落地代码，零新逻辑）
 *             → BackpackStorageMixin 钩 setBackpackContents HEAD → uuid 入 NEEDS_REFRESH
 *   客户端消费：SbCompatImpl.collectAmmoSources 读弹药前 refreshStaleTree——root 树内任一
 *             uuid 待刷新 → root.onContentsNbtUpdated()（SB 自家完整失效原语：清 handler /
 *             upgradeHandler / settingsHandler 缓存并跑 refresh 链）→ 懒重建从新镜像反序列化，
 *             弹药计数与升级开关（getTypeWrappers 重扫 enabled wrapper）一并自愈
 *
 * 设计要点：
 * - 发送复用 SB 已注册的 BackpackContentsMessage（客户端落地零新代码；GPL 红线只调 API）
 * - 发送的 NBT 做了 copy() 深拷：网络序列化可能延迟到 Netty 线程，直接发 SavedData 活引用
 *   有并发修改风险（SB 自己的 Request 链发的是浅拷壳，我们比它稳一档）
 * - 懒消费：GUI 开着时无人读弹药（不开火），NEEDS_REFRESH 挂起到关 GUI 后首次读取，
 *   mid-GUI 重建（vanilla 槽同步正在维护 wrapper 缓存）的风险自动归零
 * - 线程守卫对称（单人模式 client/server 线程共存于同一 JVM，静态字段是共享的！）：
 *   markDirty 仅 server 线程（客户端预测消耗 / vanilla 槽写穿不污染推送集）；
 *   onClientStorageSet 仅 client 线程（服务端 copyData / 迁移不污染消费集）；
 *   refreshStaleTree 仅 client 线程（server 线程误消费会把客户端的待刷标记清掉——真 bug，已防）
 * - [M12.2] 守卫谓词换为 EffectiveSide.get()：Render thread=改名的 JVM 主线程（组="main" 定死），
 *   SidedThreadGroups.CLIENT 组全生态无构造者——旧线程组判断在客户端永假（M12 三症状的结构性根因）；
 *   EffectiveSide 对非 SidedThreadGroup 线程回退 CLIENT 是官方语义；SERVER 组单实例下 markDirty 严格等价 [长期记忆: 053]
 * - 推送规则 dirty && 在携带者"带升级的树"内：嵌套子背包 B 只有挂在带升级 A 树下才推
 *   （弹药读数经过 A 的 getInventoryForInputOutput 展开树），孤背包不推；
 *   无 carrier（方块态 / 地面 / 离线玩家）超时 100 tick 丢弃
 * - 已知限制（GOAL-PLAN M12 记录）：BE 态背包变更后 100 tick 内被捡回、或离线携带期间变更，
 *   携带者镜像可能漏一次推送，开一次 SB GUI 自愈（GUI 槽同步写穿兜底）
 */
public final class SbAmmoSyncManager {
    private SbAmmoSyncManager() {}

    /** 服务端：待推送背包 uuid → 首见 tick（孤儿超时基准）。ConcurrentHashMap：mixin 钩子全在主线程，防御性选型 */
    private static final Map<UUID, Long> DIRTY = new ConcurrentHashMap<>();

    /** 客户端：镜像已更新待消费的 uuid 集（BackpackStorageMixin 生产，refreshStaleTree 消费） */
    private static final Set<UUID> NEEDS_REFRESH = ConcurrentHashMap.newKeySet();

    /** NEEDS_REFRESH 上界：超界暴力全清（背包被丢弃后 uuid 永不被树命中会泄漏；全清=极端时多刷几个 wrapper，幂等无害） */
    private static final int NEEDS_REFRESH_MAX = 128;

    /** 无 carrier 的 dirty 标记存活期（tick）：方块态/地面/离线玩家的背包变更等不到推送，超时丢弃防泄漏 */
    private static final long ORPHAN_TIMEOUT_TICKS = 100;

    /** 服务端 tick 计数（孤儿超时与节流共用时基） */
    private static long tickCounter = 0;

    // ==================== 服务端：标记与冲洗 ====================

    /**
     * 变更标记入口（mixin 钩子统一调用）。
     * server 线程守卫：客户端预测路径的消耗、vanilla 槽同步写穿触发的 onContentsChanged
     * 不得污染推送集（否则客户端自己触发→推给自己→merge→再触发 的回环）。
     */
    public static void markDirty(UUID uuid) {
        if (uuid == null) {
            return; // 背包 NBT 尚无 uuid（理论不可能——内容变更必先落 uuid），防御
        }
        // [M12.2][长期记忆: 053] 线程组身份判断已死：Render thread 是改名的 JVM 主线程（组="main" 构造时定死），
        // 全 classpath 无 CLIENT 组线程构造者；EffectiveSide 对非 SidedThreadGroup 线程回退 CLIENT 是 Forge 官方语义
        // （SERVER 组单实例，本判断与原线程组判断严格等价）
        if (EffectiveSide.get() != LogicalSide.SERVER) {
            return;
        }
        if (!SustainedFireConfig.booleanOr(SustainedFireConfig.SB_AMMO_SYNC)) {
            return; // 总闸：配置关闭时整条同步链静默停摆
        }
        DIRTY.putIfAbsent(uuid, tickCounter);
    }

    /**
     * ServerTickEvent END 冲洗（SbCompatImpl.init 注册，物理双端注册无害——专用客户端无 server 循环不触发）。
     * 节流：intervalTicks（默认 1 = 每 tick），变更标记后最多 interval tick 内发出。
     */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickCounter++;
        if (DIRTY.isEmpty()) {
            return; // 常态零成本：绝大多数 tick 无变更
        }
        int interval = SustainedFireConfig.intOr(SustainedFireConfig.SB_AMMO_SYNC_INTERVAL);
        if (interval < 1 || tickCounter % interval != 0) {
            return;
        }
        flush();
    }

    /** 冲洗主体：孤儿超时 → 逐玩家扫描"带升级树" → 命中推送 → 出集 */
    private static void flush() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            DIRTY.clear(); // server 已停（关服瞬间），清集防泄漏
            return;
        }
        // 孤儿超时（无 carrier 的标记：方块态/地面/离线携带），超时丢弃
        DIRTY.values().removeIf(seen -> tickCounter - seen > ORPHAN_TIMEOUT_TICKS);
        if (DIRTY.isEmpty()) {
            return;
        }
        Set<UUID> pushed = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 该玩家身上全部"带供弹升级"背包树的 uuid 全集（含嵌套子背包）
            Set<UUID> upgradeTreeUuids = new HashSet<>();
            collectUpgradeTreeUuids(player, upgradeTreeUuids);
            if (upgradeTreeUuids.isEmpty()) {
                continue;
            }
            for (UUID uuid : DIRTY.keySet()) {
                if (upgradeTreeUuids.contains(uuid)) {
                    // 深拷后发送：网络序列化可能延迟到 Netty 线程，SavedData 活引用有并发修改风险
                    CompoundTag contents = BackpackStorage.get().getOrCreateBackpackContents(uuid).copy();
                    SBPPacketHandler.INSTANCE.sendToClient(player, new BackpackContentsMessage(uuid, contents));
                    pushed.add(uuid);
                }
            }
        }
        DIRTY.keySet().removeAll(pushed);
    }

    // ==================== 客户端：落地与消费 ====================

    /**
     * 客户端镜像更新钩子（BackpackStorageMixin 钩 setBackpackContents HEAD 调用）。
     * client 线程守卫：服务端 copyData / 存档迁移同样会调 setBackpackContents，不得污染消费集。
     */
    public static void onClientStorageSet(UUID uuid) {
        // [M12.2][长期记忆: 053] 同 markDirty：EffectiveSide 官方原语（原线程组判断在客户端永假=死代码，三症状结构性根因）
        if (EffectiveSide.get() != LogicalSide.CLIENT) {
            return;
        }
        if (NEEDS_REFRESH.size() >= NEEDS_REFRESH_MAX) {
            NEEDS_REFRESH.clear(); // 上界防泄漏（见字段注释）
        }
        NEEDS_REFRESH.add(uuid);
    }

    /**
     * 客户端消费点（SbCompatImpl.collectAmmoSources 读弹药前调用，须在 TypeWrappers 判定前——
     * toggle 后升级启用状态经重建才反映到 getTypeWrappers，假阴性链在此闭合）。
     *
     * 树级判定：root 树内（含 root 与嵌套子背包）任一 uuid 待刷新 → root.onContentsNbtUpdated()
     * 整树重建。为什么必须树级：嵌套子背包 B 变更推的是 B 的包，但 B 的旧快照缓存在 A 的
     * handler 里——只看 root 的 uuid 会漏刷 A 缓存内的 B；root 重建级联子背包 ItemStack 新实例
     * → 子 wrapper 新建 → 从新镜像反序列化，整树自愈。
     *
     * 线程守卫：仅 client 线程执行（服务端 collectAmmoSources 同码路径也会走到这里，若放行
     * 会误清客户端待刷标记 + 白刷服务端 wrapper 缓存——真 bug，此处是防的它）。
     */
    public static void refreshStaleTree(IBackpackWrapper root) {
        // [M12.2][长期记忆: 053] 同 markDirty：EffectiveSide 官方原语（原线程组判断在客户端永假=死代码，三症状结构性根因）
        if (EffectiveSide.get() != LogicalSide.CLIENT) {
            return;
        }
        if (NEEDS_REFRESH.isEmpty()) {
            return; // 常态早退：无待刷时零 walk 成本（HUD 50ms 周期 + 开火预检都走这里）
        }
        Set<UUID> tree = new HashSet<>();
        collectWrapperTreeUuids(root, tree, new HashSet<>());
        boolean hit = false;
        for (UUID uuid : tree) {
            if (NEEDS_REFRESH.contains(uuid)) {
                hit = true;
                break;
            }
        }
        if (hit) {
            // SB 自家完整失效原语：清 handler/upgradeHandler/settingsHandler 缓存并跑 refresh 链，
            // 之后 getInventoryHandler / getUpgradeHandler 懒重建从（已 merge 的）镜像反序列化
            root.onContentsNbtUpdated();
            NEEDS_REFRESH.removeAll(tree); // 树内命中的出集（幂等：树外 uuid 保留）
        }
    }

    // ==================== 双端共用：树遍历 ====================

    /**
     * 收集玩家身上全部"带供弹升级"背包树的 uuid 全集。
     * 只有带升级的树才推送：弹药读数（HUD/预检/消耗）只经过这些树的 getInventoryForInputOutput
     * 展开视图，孤树的变更不影响任何弹药读数。
     */
    private static void collectUpgradeTreeUuids(Player player, Set<UUID> out) {
        for (ItemStack stack : SbCompatImpl.collectCandidateStacks(player)) {
            stack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance()).ifPresent(wrapper -> {
                if (!wrapper.getUpgradeHandler()
                        .getTypeWrappers(AmmoFeedUpgradeItem.TYPE).isEmpty()) {
                    collectWrapperTreeUuids(wrapper, out, new HashSet<>());
                }
            });
        }
    }

    /**
     * 递归收集单个背包树的全部 uuid（root + 嵌套子背包）。
     * visited 防环（Inception 嵌套理论可成环）。getInventoryHandler 懒构建与读取侧共享，
     * flush 侧多付一次构建（变更 tick 才发生，可接受）。
     */
    private static void collectWrapperTreeUuids(IBackpackWrapper wrapper, Set<UUID> out, Set<UUID> visited) {
        wrapper.getContentsUuid().ifPresent(uuid -> {
            if (!visited.add(uuid)) {
                return;
            }
            out.add(uuid);
            var inventory = wrapper.getInventoryHandler();
            for (int i = 0; i < inventory.getSlots(); i++) {
                inventory.getStackInSlot(i).getCapability(CapabilityBackpackWrapper.getCapabilityInstance())
                        .ifPresent(sub -> collectWrapperTreeUuids(sub, out, visited));
            }
        });
    }
}