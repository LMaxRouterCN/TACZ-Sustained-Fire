package com.lmaxroutercn.sustainedfire.compat.sb;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Curios 饰品槽内容物采集器（M10 供弹升级覆盖范围之一，max 2026-10-29 拍板）。
 *
 * 仅被 SbCompatImpl 在 ModList.isLoaded("curios") 守卫内调用——curios 缺席时本类零加载。
 * SB 自身的 CuriosCompat 按 back 槽位类型点名；我们改用 getEquippedCurios() 全槽合并视图：
 * 背包可被 datapack 挂进任意饰品槽类型，合并视图天然全覆盖，且与升级判定正交。
 *
 * 客户端（HUD 计数链路）：本地玩家的饰品栏由 curios 同步，已装备物品可读。
 */
public final class CuriosBackpackCollector {
    private CuriosBackpackCollector() {}

    /** 全部已装备饰品的内容物（非空栈） */
    public static List<ItemStack> getEquippedCurioStacks(LivingEntity entity) {
        List<ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            IItemHandlerModifiable equipped = handler.getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                ItemStack stack = equipped.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    result.add(stack);
                }
            }
        });
        return result;
    }
}