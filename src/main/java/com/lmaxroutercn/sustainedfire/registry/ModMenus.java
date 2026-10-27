package com.lmaxroutercn.sustainedfire.registry;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
import com.lmaxroutercn.sustainedfire.menu.AmmoCrateMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 菜单注册器（里程碑 6 step2 新建）。
 */
public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SustainedFire.MODID);

    /**
     * 弹药箱菜单。客户端工厂从同步缓冲读方块坐标查本地 BE——
     * BE 的方块状态（含 OPEN）随区块同步，打开时方块必然存在。
     */
    public static final RegistryObject<MenuType<AmmoCrateMenu>> AMMO_CRATE = MENUS.register("ammo_crate",
            () -> IForgeMenuType.create((id, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                if (inv.player.level().getBlockEntity(pos) instanceof AmmoCrateBlockEntity be) {
                    return new AmmoCrateMenu(id, inv, be);
                }
                // 防御：BE 查不到（理论上不该发生）给空壳菜单，避免客户端崩溃
                return new AmmoCrateMenu(id, inv, null);
            }));
}