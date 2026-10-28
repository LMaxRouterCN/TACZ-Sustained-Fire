package com.lmaxroutercn.sustainedfire.client.render;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.ammo.AmmoSourceRegistry;
import com.lmaxroutercn.sustainedfire.config.SustainedFireConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 黄线框（max 定案方案 A，2026-10-28）：手持 tacz 枪械时，为供弹半径内
 * 开盖弹药箱绘制黄色轮廓。所见即供弹——扫描走 AmmoSourceRegistry 同一
 * 口径（AABB + OPEN 过滤），服务端实际供弹的就是被高亮的箱子，不多不少。
 * 关盖箱不供弹，因此不高亮；如需"范围内但关盖"的弱提示（灰框等），
 * 属独立视觉层，另议。
 *
 * 职责切分：ClientTick(END) 持枪判定与重扫维护位置缓存（tick 侧零渲染），
 * RenderLevelStageEvent(AFTER_TRANSLUCENT_BLOCKS) 纯消费缓存画线（渲染侧零扫描）。
 * 两者同在客户端主线程（渲染线程），无并发问题。
 * 重扫节奏：换枪沿即时重扫 + 10tick 周期重扫（无感级轮询，半径小扫描代价低）。
 */
@Mod.EventBusSubscriber(modid = SustainedFire.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AmmoCrateOutlineRenderer {

    private AmmoCrateOutlineRenderer() {}

    /** 周期重扫间隔（tick）：0.5s；供弹半径内的 chunk 网格扫描代价极低 */
    private static final int RESCAN_INTERVAL_TICKS = 10;

    /** 供弹范围内（OPEN）弹药箱坐标缓存：client tick 维护，世界渲染只读 */
    private static final List<BlockPos> HIGHLIGHTED = new ArrayList<>();

    /** 上次判定时主手是否持枪（切换沿触发即时重扫） */
    private static boolean holdingGun = false;

    /** 周期重扫计数器 */
    private static int tickCounter = 0;

    // ---------- tick 侧：持枪判定 + 缓存维护 ----------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return; // 只在 END 相位判定一次（START/END 各触发一次，避免双跑）
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            // 离开世界：复位状态清缓存，防止下个存档渲染出幽灵框
            holdingGun = false;
            tickCounter = 0;
            HIGHLIGHTED.clear();
            return;
        }
        boolean nowHolding = player.getMainHandItem().getItem() instanceof IGun;
        if (nowHolding != holdingGun) {
            // 收枪/拔枪沿：立即重扫，视觉零延迟跟随
            holdingGun = nowHolding;
            tickCounter = 0;
            rescan(player);
        } else if (holdingGun && ++tickCounter >= RESCAN_INTERVAL_TICKS) {
            tickCounter = 0;
            rescan(player);
        }
        // 未持枪稳态：缓存已在收枪沿清空，其余 tick 零开销
    }

    private static void rescan(LocalPlayer player) {
        HIGHLIGHTED.clear();
        if (!holdingGun) {
            return; // 收枪沿：清空即完成
        }
        // 半径实时读 config（COMMON 配置双端共享）：改配置即时生效，无需重登
        HIGHLIGHTED.addAll(AmmoSourceRegistry.getNearbyAmmoBoxPositions(player, SustainedFireConfig.AMMO_CRATE_RADIUS.get()));
    }

    // ---------- 渲染侧：纯消费缓存 ----------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return; // 半透明方块之后：线框不被方块自身遮挡
        }
        if (HIGHLIGHTED.isEmpty()) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        // 暖黄 (1.0, 0.85, 0.2)，alpha 0.8：高存在感但不糊视线
        for (BlockPos pos : HIGHLIGHTED) {
            poseStack.pushPose();
            // 世界渲染矩阵原点在相机：平移 pos-cam 即世界坐标
            poseStack.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
            LevelRenderer.renderLineBox(poseStack, lines, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0F, 0.85F, 0.2F, 0.8F);
            poseStack.popPose();
        }
        // 显式冲刷：世界渲染钩子中立即提交线段批次，避免与其他 RenderType 交错
        buffers.endBatch(RenderType.lines());
    }
}