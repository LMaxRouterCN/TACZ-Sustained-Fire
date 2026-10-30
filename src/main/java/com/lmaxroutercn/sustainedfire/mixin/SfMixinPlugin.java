package com.lmaxroutercn.sustainedfire.mixin;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * [M12] mixin 应用门槛：mixin.sb 子包（SB 联动组）按 sophisticatedbackpacks 在场 gating。
 *
 * 必要性：该组 mixin 的钩子体引用 SB 类（BackpackWrapper / IBackpackWrapper /
 * UpgradeToggleMessage 等）——SB 缺席时若 mixin 照常应用，钩子代码里的 instanceof /
 * 方法调用首次执行即 NoClassDefFoundError。SC 单独在场（精妙储物箱用户无背包）时同样
 * 不应用：钩子判定 instanceof BackpackWrapper 永远无意义。
 *
 * 其余 mixin（TACZ 组）返回 true——plugin 字段对整个 mixins.json 生效，非 SB 组不受影响。
 *
 * [M12.1] 根因修复（2026-10-30）：原实现查询 runtime ModList.isLoaded，但
 * shouldApplyMixin 的实际调用时机 = mixin prepare 阶段（MixinInfo 构造期，一次性全判；
 * TEST 实例 debug.log 时间线：ModSorter 完成 03:26:44.9 -> veto 03:26:47.7 ->
 * 首个 FMLModContainer 03:26:54.4）。此时 runtime ModList 尚未构建：ModList.get()
 * 返回 null（或查询抛异常），null/异常分支一律保守返回 false——SB 在场也被整组误杀，
 * 症状即"完全没变"（sb 组 5 只 mixin 全被 veto，debug.log 零 Mixing 行）。
 * 且 Mixin 框架对 veto 不留任何日志，plugin 必须自己落决策日志。
 *
 * 修复后的判定链（结果缓存，全决策落 LOGGER "sf-mixin-plugin"）：
 *   1. FMLLoader.getLoadingModList().getModFileById —— mixin prepare 阶段即已可用
 *      （发现+排序后构建，早于 prepare）；mod 列表此后定型，命中=在场，未命中=缺席，
 *      结果即终局；
 *   2. runtime ModList.isLoaded —— LoadingModList 意外为 null/异常时的兜底
 *      （晚调用场景下可用；prepare 阶段它就是 null，正是原 bug 所在）；
 *   3. 判定链全失效 -> false 保守跳过（代价 = 回到 M12 前的同步缺失，无崩溃），
 *      WARN 日志钉死事实供排查。
 *
 * 接口方法集以 mixin-0.8.4 jar 内 IMixinConfigPlugin 定义为准（javap 实证 7 个抽象方法：
 * onLoad / getRefMapperConfig / shouldApplyMixin / acceptTargets / getMixins /
 * preApply / postApply——凭记忆写漏了两次，框架接口必须查证后落码）。
 */
public class SfMixinPlugin implements IMixinConfigPlugin {

    /** mixin.sb 子包前缀（与 mixins.json 中 sb.* 条目对应） */
    private static final String SB_MIXIN_PREFIX = "com.lmaxroutercn.sustainedfire.mixin.sb.";

    /** SB 的 modid（mod 列表与握手日志均以此 id 为准） */
    private static final String SB_MODID = "sophisticatedbackpacks";

    /** [M12.1] 独立命名的 logger：plugin 严禁引用 SustainedFire 主类，避免 mod 类过早类加载 */
    private static final Logger LOGGER = LoggerFactory.getLogger("sf-mixin-plugin");

    /** [M12.1] 判定结果缓存：shouldApplyMixin 逐 mixin 各调一次（sb 组共 5 次），查询只做一遍 */
    private Boolean sbPresent = null;

    @Override
    public void onLoad(String mixinPackage) {
        // 无需预载逻辑
    }

    @Override
    public String getRefMapperConfig() {
        // 使用 mixins.json 的 refmap 配置
        return null;
    }

    @Override
    public List<String> getMixins() {
        // 无动态 mixin：全部条目走 mixins.json 静态列表（null = 标准语义"无动态补充"）
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(SB_MIXIN_PREFIX)) {
            return true; // 非 SB 组：无条件应用
        }
        if (sbPresent == null) {
            // 首次触发时执行一次性判定（内部已有 INFO 汇总日志）
            sbPresent = detectSbOnce();
        }
        // per-mixin DEBUG 留痕：5 只逐一可见（汇总判定在 detectSbOnce 的 INFO 里）
        LOGGER.debug("[sf-mixin-plugin] mixin={} target={} -> apply={}",
                mixinClassName, targetClassName, sbPresent);
        return sbPresent;
    }

    /**
     * [M12.1] SB 在场一次性判定（结果由调用方缓存）：判定链见类注释 1/2/3。
     */
    private static boolean detectSbOnce() {
        // 1. 首选：LoadingModList —— mixin prepare 阶段唯一可靠的 mod 清单数据源
        try {
            LoadingModList loading = FMLLoader.getLoadingModList();
            if (loading != null) {
                // 发现+排序完成后 mod 列表已定型：命中即在场，未命中即缺席（终局，无需兜底）
                boolean present = loading.getModFileById(SB_MODID) != null;
                LOGGER.info("[sf-mixin-plugin] SB gating (LoadingModList): modid={} present={}", SB_MODID, present);
                return present;
            }
            LOGGER.warn("[sf-mixin-plugin] LoadingModList 为 null（早于 mod 发现阶段完成？），转入兜底");
        } catch (Throwable t) {
            LOGGER.warn("[sf-mixin-plugin] LoadingModList 查询异常，转入兜底", t);
        }
        // 2. 兜底：runtime ModList（晚调用场景下可用；prepare 阶段它就是 null——原 bug 所在）
        try {
            ModList runtime = ModList.get();
            if (runtime != null) {
                boolean present = runtime.isLoaded(SB_MODID);
                LOGGER.info("[sf-mixin-plugin] SB gating (ModList 兜底): modid={} present={}", SB_MODID, present);
                return present;
            }
        } catch (Throwable t) {
            LOGGER.warn("[sf-mixin-plugin] ModList 兜底查询异常", t);
        }
        // 3. 保守尾：判定链全失效，按缺席处理（联动缺失但不崩），日志钉死供排查
        LOGGER.warn("[sf-mixin-plugin] 两个 mod 清单均不可用，SB 组按缺席保守跳过");
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 无目标转移需求：不修改两个集合
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需前置处理
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需后置处理
    }
}