package com.lmaxroutercn.sustainedfire.registry;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.compat.sb.SbCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

/**
 * 创造模式标签页：本模组全部附魔的满级附魔书一览，便于测试与创造模式取用。
 * 附魔等级引用 getMaxLevel()（运行期读 config enchantments.*.maxLevel，注册期不调用）。
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SustainedFire.MODID);

    public static final RegistryObject<CreativeModeTab> SUSTAINED_FIRE = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sustainedfire"))
                    // 标签页图标：附魔书（本模组核心内容即附魔）
                    .icon(() -> new ItemStack(Items.ENCHANTED_BOOK))
                    .displayItems((params, output) -> {
                        // 弹药箱方块（里程碑 6；GUI 与供弹在后续 step 接入）
                        output.accept(ModItems.AMMO_CRATE.get());
                        // [M10] SB 供弹升级：SB 在场才注册（缺席时门面 no-op）
                        SbCompat.addCreativeItems(output);
                        // 全部 5 个附魔各一本满级书
                        putMaxBook(output, ModEnchantments.COOLING_DEVICE.get());
                        putMaxBook(output, ModEnchantments.AIR_COOLING.get());
                        putMaxBook(output, ModEnchantments.BELT_FEED.get());
                        putMaxBook(output, ModEnchantments.PREHEAT.get());
                        putMaxBook(output, ModEnchantments.PROGRESSIVE_TRIGGER.get());
                    })
                    .build());

    /** 生成一本指定附魔满级等级的附魔书并放入标签页 */
    private static void putMaxBook(CreativeModeTab.Output output, Enchantment enchantment) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(Map.of(enchantment, enchantment.getMaxLevel()), book);
        output.accept(book);
    }
}