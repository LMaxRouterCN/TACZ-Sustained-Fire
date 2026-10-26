package com.lmaxroutercn.sustainedfire.ammo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.items.IItemHandler;

import java.util.Collections;
import java.util.List;

/**
 * 弹药箱方块注册表：维护所有已放置弹药箱的方块实体，
 * 供 AmmoSourceResolver 做范围查找（里程碑 6 接入实际逻辑，当前为空实现）。
 *
 * 设计：onLoad/onRemove 维护双端分表，AABB 范围查找——
 * 不轮询全量方块实体，注册即索引。
 */
public final class AmmoSourceRegistry {
    private AmmoSourceRegistry() {}

    /**
     * 获取 shooter 周围有效半径内的弹药箱物品处理器。
     * 里程碑 6 前恒返回空列表（此时弹药源仅玩家背包+容器展开）。
     *
     * @param shooter 射手（取位置与所在维度）
     * @param radius 查找半径（格）
     * @return 弹药箱 IItemHandler 列表（不可变）
     */
    public static List<IItemHandler> getNearbyAmmoBoxes(LivingEntity shooter, double radius) {
        // TODO 里程碑6: 弹药箱 BE 注册表 + AABB 范围查找
        return Collections.emptyList();
    }

    /** 供弹药箱 BE onLoad 调用（里程碑 6） */
    public static void onAmmoBoxLoaded(BlockPos pos, IItemHandler handler) {
        // TODO 里程碑6
    }

    /** 供弹药箱 BE onRemove 调用（里程碑 6） */
    public static void onAmmoBoxRemoved(BlockPos pos) {
        // TODO 里程碑6
    }
}