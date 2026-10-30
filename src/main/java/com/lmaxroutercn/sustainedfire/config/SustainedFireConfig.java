package com.lmaxroutercn.sustainedfire.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 通用配置（common，存档无关）。
 *
 * 组结构（2026-10-29 扩容，max 三连拍板 + 附魔数值配置化拍板）：
 *   supply.expandContainerItems  供弹时是否展开背包内物品自带的容器（精妙背包等一切暴露
 *                                ITEM_HANDLER 的物品）。默认 false：背包类物品不再无条件供弹
 *                                （后续 M10 由专属升级物品重新开放，max 已确认断供后果）。
 *   ammo_crate.backpackUsable    弹药箱物品在玩家背包内是否同样作为弹药源（M6 预留，本次接线）。
 *   ammo_crate.radius            放置态弹药箱的取弹查找半径。
 *   ammo_crate.crateAmmoOnly     true = 弹药箱只允许装入 TACZ 弹药/弹药盒。统一过滤，
 *                                同时作用于放置态箱子、背包内物品态、光标交互与漏斗。
 *   sb_sync.enabled / intervalTicks  [M12] SB 背包内容变更主动推送携带者客户端（GUI 外同步）。
 *   enchantments.*               全部附魔数值（max 拍板：附魔具体数值必须进配置）。
 *
 * 已知限制：COMMON 配置不跨端同步（客户端与服务端各读各的文件），
 * 多人服务端需服管保证两端一致；单人/本地存档无感知。
 *
 * 读取模式：mod 构造/注册期不读配置（COMMON 尚未加载），运行期调用经
 * intOr/booleanOr/doubleOr 兜底为配置默认值（SPEC 未加载时 .get() 会抛异常）。
 */
public final class SustainedFireConfig {
    private SustainedFireConfig() {}

    public static final ForgeConfigSpec SPEC;

    // ---- supply：供弹源 ----

    /** 供弹时是否展开背包内物品自带的容器（SB 背包等），默认 false */
    public static final ForgeConfigSpec.BooleanValue SUPPLY_EXPAND_CONTAINER_ITEMS;

    // ---- ammo_crate：弹药箱 ----

    /** 弹药箱在背包内是否同样作为弹药源（false = 仅放置态供弹，默认） */
    public static final ForgeConfigSpec.BooleanValue AMMO_CRATE_BACKPACK_USABLE;

    /** 放置态弹药箱的取弹查找半径（格） */
    public static final ForgeConfigSpec.DoubleValue AMMO_CRATE_RADIUS;

    /** true = 弹药箱只收 TACZ 弹药/弹药盒（统一过滤，默认 false = 通用容器） */
    public static final ForgeConfigSpec.BooleanValue AMMO_CRATE_AMMO_ONLY;

    // ---- sb_sync：SB 供弹客户端同步（M12）----

    /** SB 背包内容变更主动推送携带者客户端（GUI 外弹药计数/升级开关同步的总闸） */
    public static final ForgeConfigSpec.BooleanValue SB_AMMO_SYNC;

    /** 推送节流间隔（服务端 tick，1 = 每 tick 延迟最低；调大省带宽） */
    public static final ForgeConfigSpec.IntValue SB_AMMO_SYNC_INTERVAL;

    // ---- enchantments：费用曲线（本模组全部附魔共享） ----

    public static final ForgeConfigSpec.IntValue ENCH_COST_BASE;
    public static final ForgeConfigSpec.IntValue ENCH_COST_STEP;
    public static final ForgeConfigSpec.IntValue ENCH_COST_SPAN;

    // ---- enchantments：冷却装置 ----

    public static final ForgeConfigSpec.IntValue COOLING_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue COOLING_PER_LEVEL_REDUCTION;

    // ---- enchantments：风冷 ----

    public static final ForgeConfigSpec.IntValue AIR_MAX_LEVEL;
    public static final ForgeConfigSpec.IntValue AIR_THRESHOLD_RPM;
    public static final ForgeConfigSpec.DoubleValue AIR_PER_RPM;
    public static final ForgeConfigSpec.DoubleValue AIR_FLOOR;

    // ---- enchantments：弹链升级 ----

    public static final ForgeConfigSpec.IntValue BELT_MAX_LEVEL;

    // ---- enchantments：预热 ----

    public static final ForgeConfigSpec.IntValue PREHEAT_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue PREHEAT_PER_LEVEL_BONUS;

    // ---- enchantments：渐进射速 ----

    public static final ForgeConfigSpec.IntValue PROG_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue PROG_PER_LEVEL_BONUS;
    public static final ForgeConfigSpec.IntValue PROG_RAMP_UP_MS;
    public static final ForgeConfigSpec.IntValue PROG_BURST_GAP_MS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("supply");
        SUPPLY_EXPAND_CONTAINER_ITEMS = builder
                .comment("Expand items' own inventories (e.g. Sophisticated Backpacks) inside the shooter's inventory as ammo sources.",
                        "Default false: backpacks no longer feed guns unconditionally.",
                        "供弹时是否把玩家背包内物品自带的容器（如精妙背包）展开为弹药源。",
                        "默认 false：背包类物品不再无条件供弹（后续版本由其专属升级控制）。")
                .define("expandContainerItems", false);
        builder.pop();

        builder.push("ammo_crate");
        AMMO_CRATE_BACKPACK_USABLE = builder
                .comment("Ammo crate in the player inventory also works as an ammo source (fully replaces vanilla ammo boxes).",
                        "弹药箱放在背包里时同样作为弹药源（完全替代原版弹药盒）。")
                .define("backpackUsable", false);
        AMMO_CRATE_RADIUS = builder
                .comment("Search radius (blocks) for placed ammo crates when resolving reload / belt-feed ammo.",
                        "放置态弹药箱作为换弹/弹链弹药源的查找半径（格）。")
                .defineInRange("radius", 4.0D, 1.0D, 64.0D);
        AMMO_CRATE_AMMO_ONLY = builder
                .comment("true = ammo crates (placed / item form / cursor / hoppers) only accept TACZ ammo and ammo boxes.",
                        "true = 弹药箱（放置态/物品态/光标交互/漏斗）只允许装入 TACZ 弹药与弹药盒。")
                .define("crateAmmoOnly", false);
        builder.pop();

        builder.push("sb_sync");
        SB_AMMO_SYNC = builder
                .comment("Push SB backpack contents to the carrier's client whenever they change, so ammo count / upgrade toggle stay in sync outside the SB GUI. Fixes: ghost firing after emptying, stale HUD, toggle not taking effect.",
                        "SB 背包内容变更时主动推送给携带者客户端，GUI 外弹药计数/升级开关保持同步。",
                        "修复：射空后假开枪、HUD 计数不刷新、GUI 内开关升级不生效（须重开背包才恢复）。")
                .define("enabled", true);
        SB_AMMO_SYNC_INTERVAL = builder
                .comment("Push interval in server ticks (1 = every tick, lowest latency; raise to save bandwidth on busy servers).",
                        "推送间隔（服务端 tick）：1 = 每 tick，延迟最低（幻影窗口 ≈ 原版背包直读枪）；调大可省带宽。")
                .defineInRange("intervalTicks", 1, 1, 20);
        builder.pop();

        builder.push("enchantments");
        ENCH_COST_BASE = builder
                .comment("Enchantment cost curve: min cost = costBase + costStep * (level - 1), max = min + costSpan.",
                        "附魔经验费用曲线：min = costBase + costStep*(等级-1)，max = min + costSpan（全部附魔共享）。")
                .defineInRange("costBase", 5, 0, 100);
        ENCH_COST_STEP = builder
                .comment("Per-level cost increment.", "每级费用增量。")
                .defineInRange("costStep", 8, 0, 100);
        ENCH_COST_SPAN = builder
                .comment("Span between min and max cost at the same level.", "同等级 min 与 max 的跨度。")
                .defineInRange("costSpan", 10, 0, 100);

        builder.push("cooling_device");
        COOLING_MAX_LEVEL = builder
                .comment("Max level. At 10 with default reduction the gun never overheats.",
                        "最大等级。默认削减下 10 级枪械免过热。")
                .defineInRange("maxLevel", 10, 1, 10);
        COOLING_PER_LEVEL_REDUCTION = builder
                .comment("Heat-per-shot reduction per level (0.10 = -10%/level).",
                        "每级对单发热量增速的削减比例（0.10 = 每级 -10%）。")
                .defineInRange("perLevelReduction", 0.10D, 0.0D, 1.0D);
        builder.pop();

        builder.push("air_cooling");
        AIR_MAX_LEVEL = builder
                .comment("Max level.", "最大等级。")
                .defineInRange("maxLevel", 5, 1, 10);
        AIR_THRESHOLD_RPM = builder
                .comment("RPM threshold above which the reduction kicks in.", "射速超过该阈值（rpm）后开始产生减免。")
                .defineInRange("thresholdRpm", 600, 0, 1200);
        AIR_PER_RPM = builder
                .comment("Per-level reduction per RPM over the threshold.", "每级每超出 1 rpm 的减免比例。")
                .defineInRange("perRpm", 0.002D, 0.0D, 1.0D);
        AIR_FLOOR = builder
                .comment("Lower bound of the heat multiplier. Negative = shooting actively cools the gun.",
                        "热量缩放系数下限。负值 = 射击时主动散热。")
                .defineInRange("floor", -1.0D, -10.0D, 1.0D);
        builder.pop();

        builder.push("belt_feed");
        BELT_MAX_LEVEL = builder
                .comment("Max level.", "最大等级。")
                .defineInRange("maxLevel", 1, 1, 10);
        builder.pop();

        builder.push("preheat");
        PREHEAT_MAX_LEVEL = builder
                .comment("Max level.", "最大等级。")
                .defineInRange("maxLevel", 5, 1, 10);
        PREHEAT_PER_LEVEL_BONUS = builder
                .comment("RPM bonus per level at 100% heat (0.40 = +40%/level).",
                        "满热度时每级射速加成（0.40 = 每级 +40%）。")
                .defineInRange("perLevelBonus", 0.40D, 0.0D, 10.0D);
        builder.pop();

        builder.push("progressive_trigger");
        PROG_MAX_LEVEL = builder
                .comment("Max level.", "最大等级。")
                .defineInRange("maxLevel", 5, 1, 10);
        PROG_PER_LEVEL_BONUS = builder
                .comment("RPM bonus per level at full ramp (0.15 = +15%/level, weaker than preheat).",
                        "满渐变时每级射速加成（0.15 = 每级 +15%，低于预热）。")
                .defineInRange("perLevelBonus", 0.15D, 0.0D, 10.0D);
        PROG_RAMP_UP_MS = builder
                .comment("Continuous-fire time (ms) to reach the full bonus.", "连射积累到满加成所需时长（毫秒）。")
                .defineInRange("rampUpTimeMs", 3000, 100, 600000);
        PROG_BURST_GAP_MS = builder
                .comment("Gap (ms) after which continuous fire is considered a new burst.",
                        "距上一发超过该时长（毫秒）视为新连射。")
                .defineInRange("burstGapMs", 2000, 100, 600000);
        builder.pop();

        builder.pop();
        SPEC = builder.build();
    }

    /** 配置已加载则取值，否则返回默认值（注册期/早期调用的兜底，防 get() 抛异常） */
    public static int intOr(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** 同上，布尔版 */
    public static boolean booleanOr(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** 同上，双精度版 */
    public static double doubleOr(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}