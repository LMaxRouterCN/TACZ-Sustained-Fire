package com.lmaxroutercn.sustainedfire.compat.sb;
// [M16] tryAppendBackpackSource 门面签名需要（原门面方法不含 ItemStack 参数故此前未 import）
import net.minecraft.world.item.ItemStack;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.ammo.AmmoSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * SB 联动门面：通用代码只允许 import 本类（本类零 SB/SC/Curios 引用）。
 * 实现引用全部下放 SbCompatImpl / CuriosBackpackCollector——
 * 方法体内的类引用是惰性解析的，SB 缺席时守卫分支不执行，实现类零加载，不会 NoClassDefFoundError。
 *
 * [M14] collectAmmoSources 返回类型 IItemHandler → AmmoSource：
 * 每个源携带自己的直写清扫 sweep（TACZ 对弹药盒 setAmmoCount 直改 NBT
 * 绕过一切 handler op，消耗完成后需要各源自行落地，见 ExternalConsumeSweep）。
 * AmmoSource 是本项目 ammo 包类型，门面引用无碍（隔离红线只针对 SB/SC/Curios）。
 */
public final class SbCompat {
    private static boolean sbPresent = false;

    private SbCompat() {}

    /** 主类构造器调用：SB 在场才注册供弹升级物品 */
    public static void init(IEventBus modBus) {
        if (ModList.get().isLoaded("sophisticatedbackpacks")) {
            sbPresent = true;
            SbCompatImpl.init(modBus);
            SustainedFire.LOGGER.info("检测到精妙背包：供弹升级已挂载");
        }
    }

    /** SB 是否在场 */
    public static boolean isPresent() {
        return sbPresent;
    }

    /**
     * 收集"装了启用供弹升级"的 SB 背包弹药源（消耗/预检/HUD 三链路共用）。
     * [M14] 源含 sweep（树级直写清扫 / 背包内弹药箱副本的 flush+fire）。
     * SB 缺席时返回空列表。
     */
    public static List<AmmoSource> collectAmmoSources(LivingEntity shooter) {
        if (!sbPresent) {
            return List.of();
        }
        return SbCompatImpl.collectAmmoSources(shooter);
    }

    /**
     * [M16] SB 背包三态判定+源追加（AmmoSourceResolver.appendExpandedSources 调用，
     * 消耗/查询两线共用）。
     * 三态：非 SB 背包 → false（调用方走通用 cap 展开）；SB 且带启用升级且
     * skipUpgraded（玩家背包上下文，源 5 在场）→ true（skip 防双计，b1）；
     * 其余 SB → 追加专用源（root IO + 树清扫）返回 true。
     * SB 缺席恒 false（通用 cap 分支自然接管，无 SB 类引用）。
     */
    public static boolean tryAppendBackpackSource(ItemStack stack, List<AmmoSource> out, boolean skipUpgraded) {
        if (!sbPresent) {
            return false;
        }
        return SbCompatImpl.tryAppendBackpackSource(stack, out, skipUpgraded);
    }

    /** 创造标签页展示（SB 缺席时 no-op） */
    public static void addCreativeItems(CreativeModeTab.Output output) {
        if (sbPresent) {
            SbCompatImpl.addCreativeItems(output);
        }
    }
}