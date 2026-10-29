package com.lmaxroutercn.sustainedfire.compat.sb;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * SB 联动实现层：全部 SB/SC 引用集中于此（review 红线：禁止 import 进通用代码路径）。
 * 类加载守卫：SbCompat 门面仅在 ModList.isLoaded("sophisticatedbackpacks") 分支内触碰本类。
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
        SustainedFire.LOGGER.info("SB compat: 供弹升级 ammo_feed_upgrade 已注册");
    }

    /** 创造标签页展示（门面转发，SB 缺席时 no-op） */
    public static void addCreativeItems(CreativeModeTab.Output output) {
        output.accept(AMMO_FEED_UPGRADE.get());
    }

    /**
     * 收集射手身上"装了启用供弹升级"的 SB 背包的物品视图（弹药源）。
     * 扫描槽位：主 36 格 + 副手 + 盔甲 + Curios 饰品槽（max 2026-10-29 拍板覆盖范围）。
     *
     * 门票谓词 = getTypeWrappers(TYPE) 非空：UpgradeHandler.initializeTypeWrappers 字节码实证
     * 只收 enabled wrapper（isEnabled 过滤后才 addTypeWrapper），非空 ⇔ 装了且启用；
     * 且走 UpgradeHandler 缓存，首查后零 NBT 反序列化。
     *
     * 视图 = getInventoryForInputOutput()：Inception 嵌套子背包已在内部展开（嵌套树全可见，零代码）。
     * 注意这是 filtered 视图——玩家装了 filter 升级并配置过滤时提取可能被挡（默认全通过，游戏内实测确认）。
     */
    public static List<IItemHandler> collectAmmoSources(LivingEntity shooter) {
        if (!(shooter instanceof Player player)) {
            return List.of();
        }
        List<IItemHandler> result = new ArrayList<>();
        for (ItemStack stack : collectCandidateStacks(player)) {
            stack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance())
                    .ifPresent(wrapper -> {
                        if (!wrapper.getUpgradeHandler()
                                .getTypeWrappers(AmmoFeedUpgradeItem.TYPE).isEmpty()) {
                            result.add(wrapper.getInventoryForInputOutput());
                        }
                    });
        }
        return result;
    }

    /** 玩家身上全部候选槽位的内容物（SB 背包可能出现在任一槽） */
    private static List<ItemStack> collectCandidateStacks(Player player) {
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