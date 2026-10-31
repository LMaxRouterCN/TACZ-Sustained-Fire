package com.lmaxroutercn.sustainedfire.registry;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.crafting.AmmoCrateRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 配方序列化器注册器（[M16] 新建：弹药箱动态产物合成）。
 */
public final class ModRecipeSerializers {
    private ModRecipeSerializers() {}

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SustainedFire.MODID);

    /**
     * 弹药箱合成：JSON 按 vanilla crafting_shaped 编写，serializer 换本项——
     * matches/网络同步全委托 vanilla，只覆写 assemble（弹药随行）与
     * getRemainingItems（盒守恒返还），见 AmmoCrateRecipe。
     */
    public static final RegistryObject<RecipeSerializer<AmmoCrateRecipe>> AMMO_CRATE_RECIPE =
            RECIPE_SERIALIZERS.register("ammo_crate", AmmoCrateRecipe.Serializer::new);
}