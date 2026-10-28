package com.lmaxroutercn.sustainedfire.ammo;

import com.lmaxroutercn.sustainedfire.block.AmmoCrateBlock;
import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹药箱范围查找：AABB 现查方案（2026-10-27 改案，max 可否决，GOAL-PLAN 第七节）。
 *
 * 1.20.1 的 Level 没有 getBlockEntitiesOfClass(Class, AABB)（后期版本 API，
 * 编译器实证"找不到符号"），故手写 chunk 网格扫描：
 *   AABB → 覆盖的 chunk 网格（floor 后 >>4，负坐标算术移位与 floor 除 16 语义一致）
 *   → 逐 chunk 取 BE 表 → 类型过滤 → 中心点回检 AABB → OPEN 态过滤
 *
 * 开盖过滤在查询侧：capability 不设限（漏斗免费互通），
 * 仅"作为弹药源"时要求 OPEN（max 定稿：放置态必须开盖才供弹）。
 * 双端各自查各自 Level：服务端为权威消耗，客户端为预检
 * （canReload / hasInventoryAmmo 预测），客户端数据来自 BE 的 getUpdateTag 同步。
 *
 * 2026-10-28 结构拆分：chunk 网格扫描收敛到私有 collectNearbyCrates 核心，
 * getNearbyAmmoBoxes（供弹 handler）与 getNearbyAmmoBoxPositions（黄线框渲染）
 * 两个消费方共享同一口径——高亮集合 ≡ 供弹集合，所见即所得。
 */
public final class AmmoSourceRegistry {
    private AmmoSourceRegistry() {}

    /**
     * 核心扫描：shooter AABB+radius 内且 OPEN 的弹药箱 BE 列表。
     * AABB 用实体包围盒 inflate(radius)（与原 getNearbyAmmoBoxes 实现一致，行为不变）。
     */
    private static List<AmmoCrateBlockEntity> collectNearbyCrates(LivingEntity shooter, double radius) {
        Level level = shooter.level();
        AABB searchBox = shooter.getBoundingBox().inflate(radius);
        // AABB 覆盖的 chunk 网格范围（负坐标下 >>4 算术移位 = floor(x/16)，语义正确）
        int minCX = (int) Math.floor(searchBox.minX) >> 4;
        int maxCX = (int) Math.floor(searchBox.maxX) >> 4;
        int minCZ = (int) Math.floor(searchBox.minZ) >> 4;
        int maxCZ = (int) Math.floor(searchBox.maxZ) >> 4;
        List<AmmoCrateBlockEntity> result = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                // 未加载 chunk 不参与（供弹只发生在已加载世界；守卫在 getChunk 前防越界）
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof AmmoCrateBlockEntity crate)) {
                        continue;
                    }
                    BlockPos pos = crate.getBlockPos();
                    // chunk 是 16x16 全高度竖列，BE 须回检 AABB（排除 y 越界）
                    if (!searchBox.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                        continue;
                    }
                    // 仅开盖的箱子作为弹药源；关盖不供弹（漏斗路径不走本方法，不受此限制）
                    if (crate.getBlockState().getValue(AmmoCrateBlock.OPEN)) {
                        result.add(crate);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 获取 shooter 周围有效半径内的弹药箱物品处理器（已按开盖状态过滤）。
     *
     * @param shooter 射手（取位置与所在维度）
     * @param radius 查找半径（格，来自 config）
     * @return 弹药箱 IItemHandler 列表（新建列表）
     */
    public static List<IItemHandler> getNearbyAmmoBoxes(LivingEntity shooter, double radius) {
        List<IItemHandler> result = new ArrayList<>();
        for (AmmoCrateBlockEntity crate : collectNearbyCrates(shooter, radius)) {
            result.add(crate.getItemHandler());
        }
        return result;
    }

    /**
     * 供弹范围内（OPEN）弹药箱坐标列表：黄线框渲染用（2026-10-28，M7b）。
     * 与 getNearbyAmmoBoxes 走同一 collectNearbyCrates 核心，
     * 保证"被高亮的箱子"与"实际供弹的箱子"严格同源。
     *
     * @param shooter 射手（取位置与所在维度）
     * @param radius 查找半径（格，来自 config）
     * @return 弹药箱 BlockPos 列表（immutable，新建列表）
     */
    public static List<BlockPos> getNearbyAmmoBoxPositions(LivingEntity shooter, double radius) {
        List<BlockPos> result = new ArrayList<>();
        for (AmmoCrateBlockEntity crate : collectNearbyCrates(shooter, radius)) {
            result.add(crate.getBlockPos().immutable());
        }
        return result;
    }
}