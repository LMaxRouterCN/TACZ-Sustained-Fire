package com.lmaxroutercn.sustainedfire.registry;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.item.AmmoCrateBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册器（里程碑 6 新建）。
 */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SustainedFire.MODID);

    /**
     * 弹药箱方块物品。step2/3 演进点：backpackUsable 配置开放后
     * 升级为自定义 AmmoCrateItem（手持右键开 GUI + NBT 物品栏 + capability），
     * 与 AmmoSourceResolver 的背包模式过滤同批落地，
     * 避免"配置关了却仍从背包弹药箱供弹"的半成品状态。
     */
    // [2026-10-28] 升级为自定义物品：内容随物品 NBT + 背包光标交互 + 耐久条 + 开合盖模型谓词（下方原注释的 backpackUsable 手持演进仍为远期待办）
    public static final RegistryObject<Item> AMMO_CRATE = ITEMS.register("ammo_crate",
            () -> new AmmoCrateBlockItem(ModBlocks.AMMO_CRATE.get(), new Item.Properties()));
}