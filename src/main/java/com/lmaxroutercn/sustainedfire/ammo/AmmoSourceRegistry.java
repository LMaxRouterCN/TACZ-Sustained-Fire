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
 */
public final class AmmoSourceRegistry {
    private AmmoSourceRegistry() {}

    /**
     * 获取 shooter 周围有效半径内的弹药箱物品处理器（已按开盖状态过滤）。
     *
     * @param shooter 射手（取位置与所在维度）
     * @param radius 查找半径（格，来自 config）
     * @return 弹药箱 IItemHandler 列表（新建列表）
     */
    public static List<IItemHandler> getNearbyAmmoBoxes(LivingEntity shooter, double radius) {
        Level level = shooter.level();
        AABB searchBox = shooter.getBoundingBox().inflate(radius);
        // AABB 覆盖的 chunk 网格范围（负坐标下 >>4 算术移位 = floor(x/16)，语义正确）
        int minCX = (int) Math.floor(searchBox.minX) >> 4;
        int maxCX = (int) Math.floor(searchBox.maxX) >> 4;
        int minCZ = (int) Math.floor(searchBox.minZ) >> 4;
        int maxCZ = (int) Math.floor(searchBox.maxZ) >> 4;
        List<IItemHandler> result = new ArrayList<>();
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
                        result.add(crate.getItemHandler());
                    }
                }
            }
        }
        return result;
    }
}