package com.lmaxroutercn.sustainedfire.mixin.sb;

import com.lmaxroutercn.sustainedfire.compat.sb.SbAmmoSyncManager;
import net.minecraft.server.level.ServerPlayer;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.network.UpgradeToggleMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [M12] SB 变更漏斗之四（toggle 专属通道）：升级开关翻转。
 *
 * UpgradeToggleMessage.lambda$handleMessage$1（C2S 收包 handler 的背包遍历 lambda，
 * static void(消息, 玩家, wrapper)，签名字节码实证）——GUI 内点升级开关的服务端入口。
 *
 * 必要性：UpgradeWrapperBase.setEnabled 字节码只有 NBT 写入 + save() + refreshWrappers，
 * 不调 markBackpackContentsChanged——save() 内部链（是否经 UpgradeHandler.onContentsChanged
 * 落到漏斗三）未验明，本钩按"唯一捕获"设计；即便 save 链最终也触发漏斗三，幂等入集无副作用。
 *
 * 该 lambda 对玩家每个背包都会调用（runOnBackpacks 遍历），未实际 toggle 的背包（无该
 * 槽位，containsKey false）也会被标记——多推一个 redundant 包，低频事件可忽略。
 *
 * toggle 是 GUI 内低频操作，无需节流，统一走 markDirty → flush 通道（架构单一）。
 */
@Mixin(UpgradeToggleMessage.class)
public abstract class UpgradeToggleMessageMixin {

    /** static 方法对 static handler；lambda 方法名 javap 字节码稳定（编译器生成规则固定） */
    @Inject(method = "lambda$handleMessage$1", at = @At("RETURN"), remap = false)
    private static void sf$markToggled(UpgradeToggleMessage msg, ServerPlayer player, IBackpackWrapper wrapper, CallbackInfo ci) {
        wrapper.getContentsUuid().ifPresent(SbAmmoSyncManager::markDirty);
    }
}