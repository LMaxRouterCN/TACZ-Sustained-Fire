package com.lmaxroutercn.sustainedfire.registry;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.block.AmmoCrateBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块注册器（里程碑 6 新建）。
 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, SustainedFire.MODID);

    /** 弹药箱：木质军械箱音效占位（专属音效后续可换），强度对齐储物方块 */
    public static final RegistryObject<AmmoCrateBlock> AMMO_CRATE = BLOCKS.register("ammo_crate",
            () -> new AmmoCrateBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 8.0F)
                    .sound(SoundType.WOOD)));
}