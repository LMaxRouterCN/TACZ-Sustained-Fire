package com.lmaxroutercn.sustainedfire.compat.sb;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import net.minecraft.ChatFormatting;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeSlotChangeResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;

import java.util.List;
import java.util.function.Consumer;

/**
 * 供弹升级（sustainedfire:ammo_feed_upgrade）——SB 背包的供弹门票。
 *
 * 装进精妙背包升级槽且启用后，该背包成为枪械弹药源：
 * AmmoSourceResolver 三链路（消耗 buildSources / 预检 hasAmmoInCrates / HUD countAmmoInCrates）
 * 经 SbCompat 门面扫描玩家主 36 格+副手+盔甲+Curios 饰品槽收集，与 expandContainerItems 闸正交。
 *
 * 本类仅 SB 在场时注册（SbCompat.init 的 ModList 守卫），
 * 因此本类及其静态 TYPE（SC 类型）的加载被整体条件化——SB 缺席时零类加载。
 *
 * 范本 = SB 的 EverlastingUpgradeItem（javap 字节码实证 2026-10-29）：
 * - UpgradeType 无全局注册表，identity 就是本类的静态 TYPE 字段；NBT 重载后
 *   UpgradeHandler.initializeWrappers 对每个 IUpgradeItem 调 getType() 重建 wrapper，
 *   外部 mod 自建 TYPE 完全可还原（升级槽跨存档/跨会话不丢）。
 * - UPGRADE_CONFLICT_DEFINITIONS 自冲突（countAllowed=0）：同背包第二个装不进 → 限 1。
 * - 与 Everlasting 的差异：它是纯被动升级（canBeDisabled=false + hideSettingsTab=true）；
 *   我们 canBeDisabled=true 需要保留设置页——SB 原生的启用开关就在升级设置页里。
 */
public class AmmoFeedUpgradeItem extends UpgradeItemBase<AmmoFeedUpgradeItem.Wrapper> {

    /** 升级类型 identity：UpgradeHandler.getTypeWrappers(TYPE) 以此检索本升级的 wrapper */
    public static final UpgradeType<Wrapper> TYPE = new UpgradeType<>(Wrapper::new);

    /** 自冲突（第二个装不进来）：谓词=本类物品，countAllowed=0（Inception 的 add.inception_exists 字节码同款；Everlasting 实无静态冲突定义，唯一性走 SB Config——2026-10-29 实证修正） */
    public static final List<IUpgradeItem.UpgradeConflictDefinition> UPGRADE_CONFLICT_DEFINITIONS = List.of(
            new IUpgradeItem.UpgradeConflictDefinition(
                    item -> item instanceof AmmoFeedUpgradeItem, // 谓词入参是 Item（Predicate<Item>，javac 报错实证；Everlasting 同款）
                    0,
                    Component.translatable("message.sustainedfire.ammo_feed_upgrade.conflict")));

    public AmmoFeedUpgradeItem() {
        // 计数上限自实现（Everlasting 挂 SB 自己的 Config，外部 mod 不能引用）：
        // 每背包 1 个、每组 1 个——与自冲突定义双保险
        super(new IUpgradeCountLimitConfig() {
            @Override
            public int getMaxUpgradesPerStorage(String storageType, ResourceLocation storageId) {
                return 1;
            }

            @Override
            public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup group) {
                return 1;
            }
        });
    }

    @Override
    public UpgradeType<Wrapper> getType() {
        return TYPE;
    }

    @Override
    public List<IUpgradeItem.UpgradeConflictDefinition> getUpgradeConflicts() {
        return UPGRADE_CONFLICT_DEFINITIONS;
    }

    /**
     * [临时诊断] M10 验证 bug 排查：mayPlace 到 canAddUpgradeTo 链路打点。
     * 打印调用侧/type上限/组上限/背包内同类计数/结果与错误消息——根因确认后整段移除。
     */
    @Override
    public UpgradeSlotChangeResult canAddUpgradeTo(IStorageWrapper storageWrapper, ItemStack stack, boolean firstLevelStorage, boolean isClientSide) {
        int sameCount = 0;
        for (int i = 0; i < storageWrapper.getUpgradeHandler().getSlots(); i++) {
            if (storageWrapper.getUpgradeHandler().getStackInSlot(i).getItem() instanceof AmmoFeedUpgradeItem) {
                sameCount++;
            }
        }
        UpgradeSlotChangeResult result = super.canAddUpgradeTo(storageWrapper, stack, firstLevelStorage, isClientSide);
        SustainedFire.LOGGER.info("[ammo_feed诊断] side={} storageType={} typeLimit={} groupLimit={} sameCount={} ok={} msg={}",
                isClientSide ? "CLIENT" : "SERVER",
                storageWrapper.getStorageType(),
                getUpgradesPerStorage(storageWrapper.getStorageType()),
                getUpgradesInGroupPerStorage(storageWrapper.getStorageType()),
                sameCount,
                result.isSuccessful(),
                result.getErrorMessage().map(c -> c.getString()).orElse("-"));
        return result;
    }
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.sustainedfire.ammo_feed_upgrade.desc")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * 空壳 wrapper：本升级无行为逻辑（供弹判定在 AmmoSourceResolver 侧），
     * 存在意义只有两个——canBeDisabled=true 激活 SB 原生启用开关；
     * hideSettingsTab=false 让设置页（含开关 UI）可见。
     */
    public static class Wrapper extends UpgradeWrapperBase<Wrapper, AmmoFeedUpgradeItem> {
        public Wrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> saveHandler) {
            super(storageWrapper, upgrade, saveHandler);
        }

        @Override
        public boolean canBeDisabled() {
            return true;
        }

        @Override
        public boolean hideSettingsTab() {
            return false;
        }
    }
}