package com.lmaxroutercn.sustainedfire;

import com.lmaxroutercn.sustainedfire.ammo.FireSessionTracker;
import com.lmaxroutercn.sustainedfire.compat.sb.SbCompat;
import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;
import com.lmaxroutercn.sustainedfire.registry.ModBlockEntities;
import com.lmaxroutercn.sustainedfire.registry.ModBlocks;
import com.lmaxroutercn.sustainedfire.registry.ModCreativeTabs;
import com.lmaxroutercn.sustainedfire.registry.ModEnchantments;
import com.lmaxroutercn.sustainedfire.registry.ModItems;
import com.lmaxroutercn.sustainedfire.registry.ModMenus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TACZ: Sustained Fire —— 持续火力附属
 * 枪管热量管理与供弹：冷却/风冷/预热/渐进射速附魔 + 弹链供弹 + 弹药箱方块 + SB 联动
 */
@Mod(SustainedFire.MODID)
public class SustainedFire {
    public static final String MODID = "sustainedfire";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public SustainedFire() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册器：附魔 + 弹药箱三件套（方块 / 物品 / 方块实体，里程碑 6）
        ModEnchantments.ENCHANTMENTS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);

        // 菜单注册（step2：弹药箱 GUI）
        ModMenus.MENUS.register(modBus);

        // 创造模式标签页（附魔书一览 + 弹药箱）
        ModCreativeTabs.TABS.register(modBus);

        // [M10] SB 联动：SB 在场才注册供弹升级物品（门面内部 ModList 守卫，缺席时 compat 零类加载）
        SbCompat.init(modBus);

        // 通用配置：弹药箱背包可用性 / 查找半径
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SustainedFireConfig.SPEC);

        // 连射会话追踪（渐进射速的数据源）：双端各自订阅 GunFireEvent，
        // 物理客户端在单人下同时持有 client/server 两张表，互不串扰
        MinecraftForge.EVENT_BUS.addListener(FireSessionTracker::onGunFire);

        LOGGER.info("Sustained Fire loaded. 现在你可以播放《Swamp Thing》了");
    }
}