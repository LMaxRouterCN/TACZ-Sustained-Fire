package com.lmaxroutercn.sustainedfire.client;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.item.AmmoCrateBlockItem;
import com.lmaxroutercn.sustainedfire.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端物品模型谓词注册（里程碑 6 物品侧）。
 *
 * sustainedfire:open —— 0 = 有内容（关盖外观，parent 模型）
 *                      1 = 空箱（开盖外观，override 模型 ammo_crate_open）
 * 数据源是 "sf:fill" 整数 NBT（挖掘/光标交互时写入），O(1) 读取。
 */
@Mod.EventBusSubscriber(modid = SustainedFire.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AmmoCrateItemProperties {
    private AmmoCrateItemProperties() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // enqueueWork：物品属性注册必须在主线程、模型烘焙前完成
        event.enqueueWork(() -> ItemProperties.register(ModItems.AMMO_CRATE.get(),
                new ResourceLocation(SustainedFire.MODID, "open"),
                (stack, level, entity, seed) -> AmmoCrateBlockItem.getFill(stack) > 0 ? 0f : 1f));
    }
}