package com.lmaxroutercn.sustainedfire.ammo;

/**
 * [M14] 外部直写清扫接口：由组合视图 CombinedView 实现，
 * AbstractGunItemMixin 的 findAndExtractInventoryAmmo RETURN 钩子
 * instanceof 判定后触发。
 *
 * 为什么是接口而不是直接调 AmmoSource：RETURN 钩时 mixin 手上只有
 * itemHandler 参数（静态类型 IItemHandler），AmmoSource record 在调用方视野外；
 * 接口让"视图自己知道怎么清扫"，mixin 对具体实现零依赖。
 * 放 ammo 包而非 mixin 包：CombinedView（ammo 包）实现它，
 * mixin import ammo 包是正常分层方向（反向才是倒挂）。
 */
public interface ExternalConsumeSweep {

    /**
     * 外部（TACZ 消耗链）对视图内 ItemStack 的直改完成后调用：
     * 各源把直写落地（副本写回 / BE 标脏推送 / SB 序列化回写）。
     * 实现须幂等——RETURN 钩对多条返回路径可能多次触发。
     */
    void afterExternalConsume();
}