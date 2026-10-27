package com.lmaxroutercn.sustainedfire;

import com.lmaxroutercn.sustainedfire.ammo.FireSessionTracker;
import com.lmaxroutercn.sustainedfire.registry.ModCreativeTabs;
import com.lmaxroutercn.sustainedfire.registry.ModEnchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
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

        // 附魔注册（方块/物品注册器在对应里程碑建立时再挂）
        ModEnchantments.ENCHANTMENTS.register(modBus);

        // 创造模式标签页（附魔书一览）
        ModCreativeTabs.TABS.register(modBus);

        // 连射会话追踪（渐进射速的数据源）：双端各自订阅 GunFireEvent，
        // 物理客户端在单人下同时持有 client/server 两张表，互不串扰
        MinecraftForge.EVENT_BUS.addListener(FireSessionTracker::onGunFire);

        LOGGER.info("Sustained Fire loaded. 现在你可以播放《Swamp Thing》了");
    }
}