package com.lmaxroutercn.sustainedfire.client;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.client.gui.AmmoCrateScreen;
import com.lmaxroutercn.sustainedfire.menu.AmmoCrateMenu;
import com.lmaxroutercn.sustainedfire.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化：菜单 → 屏幕 绑定。
 * 仅物理客户端加载（Dist.CLIENT），服务端永不触碰 Screen 类。
 *
 * 1.20.1 Forge 没有 RegisterMenuScreensEvent（那是 NeoForge 侧的事件，本次踩坑实录），
 * 标准做法：FMLClientSetupEvent + enqueueWork 中调 vanilla MenuScreens.register——
 * 注册必须在主线程完成，mod 总线事件本身处于并行加载阶段。
 */
@Mod.EventBusSubscriber(modid = SustainedFire.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.AMMO_CRATE.get(), AmmoCrateScreen::new));
    }
}