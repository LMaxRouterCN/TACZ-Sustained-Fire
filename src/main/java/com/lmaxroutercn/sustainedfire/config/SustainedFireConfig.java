package com.lmaxroutercn.sustainedfire.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 通用配置（common，存档无关）：弹药箱里程碑 6 的开关。
 * 设计（max 定稿）：
 *   - backpackUsable = false（默认）：弹药箱仅放置后作为弹药源；
 *     true 时在背包内同样供弹（完全替代原版弹药盒，手持右键开 GUI）
 *   - radius：放置态弹药箱的取弹查找半径
 * 盖子限制不做配置：放置态必须开盖才供弹、漏斗不受盖子限制，均为定稿行为。
 */
public final class SustainedFireConfig {
    private SustainedFireConfig() {}

    public static final ForgeConfigSpec SPEC;

    /** 弹药箱在背包内是否同样可作为弹药源（false = 仅放置态供弹，默认） */
    public static final ForgeConfigSpec.BooleanValue AMMO_CRATE_BACKPACK_USABLE;

    /** 放置态弹药箱的取弹查找半径（格） */
    public static final ForgeConfigSpec.DoubleValue AMMO_CRATE_RADIUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("ammo_crate");
        AMMO_CRATE_BACKPACK_USABLE = builder
                .comment("Ammo crate in the player inventory also works as an ammo source (fully replaces vanilla ammo boxes).",
                        "弹药箱放在背包里时同样作为弹药源（完全替代原版弹药盒）。")
                .define("backpackUsable", false);
        AMMO_CRATE_RADIUS = builder
                .comment("Search radius (blocks) for placed ammo crates when resolving reload / belt-feed ammo.",
                        "放置态弹药箱作为换弹/弹链弹药源的查找半径（格）。")
                .defineInRange("radius", 4.0D, 1.0D, 64.0D);
        builder.pop();
        SPEC = builder.build();
    }
}