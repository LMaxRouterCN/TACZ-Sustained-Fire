package com.lmaxroutercn.sustainedfire.registry;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块实体注册器（里程碑 6 新建）。
 */
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SustainedFire.MODID);

    public static final RegistryObject<BlockEntityType<AmmoCrateBlockEntity>> AMMO_CRATE =
            BLOCK_ENTITY_TYPES.register("ammo_crate", () ->
                    BlockEntityType.Builder.of(AmmoCrateBlockEntity::new, ModBlocks.AMMO_CRATE.get()).build(null));
}