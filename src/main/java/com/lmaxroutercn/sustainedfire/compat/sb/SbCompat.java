package com.lmaxroutercn.sustainedfire.compat.sb;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * SB 联动门面：通用代码只允许 import 本类（本类零 SB/SC/Curios 引用）。
 * 实现引用全部下放 SbCompatImpl / CuriosBackpackCollector——
 * 方法体内的类引用是惰性解析的，SB 缺席时守卫分支不执行，实现类零加载，不会 NoClassDefFoundError。
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
     * SB 缺席时返回空列表。
     */
    public static List<IItemHandler> collectAmmoSources(LivingEntity shooter) {
        if (!sbPresent) {
            return List.of();
        }
        return SbCompatImpl.collectAmmoSources(shooter);
    }

    /** 创造标签页展示（SB 缺席时 no-op） */
    public static void addCreativeItems(CreativeModeTab.Output output) {
        if (sbPresent) {
            SbCompatImpl.addCreativeItems(output);
        }
    }
}