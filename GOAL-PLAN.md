# TACZ: Sustained Fire — 目标与计划
modid: sustainedfire
标语: 现在你可以播放《Swamp Thing》了

## 一、项目定位
TACZ（永恒枪械工坊：零）附属mod，主题为"持续火力"：枪管热量管理与供弹。
绿地项目，从零搭建。

## 二、需求清单（v2，已定稿）
1. 附魔「冷却装置」：1-10级，每级过热增速-10%
2. 附魔「风冷」：射速越高，过热增速越慢，允许负值（过热下降）
3. 附魔「弹链升级」（宝藏附魔）：射击直接消耗背包弹药，免换弹
4. 附魔「预热」：过热值越高射速越快
5. 附魔「渐进射速」：连续射击一段时间后射速渐增，加成幅度低于「预热」
6. 方块「弹药箱」：放置式供弹方块
7. 精妙背包联动：弹药可从精妙背包取出；未安装SB时相关物品整体不注册

## 三、目标环境
- Forge 1.20.1 / Java 17 / official mappings
- tacz-1.20.1-1.1.8-hotfix2（目标主mod）
- sophisticatedbackpacks-1.20.1-3.26.14.2226 + sophisticatedcore-1.20.1-1.5.15.2427（联动目标）
- 同实例装有 神化：现代诸神黄昏（ragnarok，tacz1.1.8 专用版）→ mixin 兼容是硬性要求
- 测试实例：E:/MC/.minecraft/versions/Industrial Revolution 2040/

## 四、路径与资源
- 本项目：D:\Documents\mcmod\TACZ-Sustained-Fire
- TACZ源码参考：D:\Documents\mcmod\sustained-fire-ref\tacz\TACZ（GPL-3.0）
- SB源码参考：D:\Documents\mcmod\sustained-fire-ref\SB（同目录 core 为 sophisticatedcore）
- gradle模板：D:\Documents\mcmod\energycapacitor（标准MDK 47.4.10，official mapping，无parchment）
- jar三件套来源：E:/MC/.minecraft/versions/Industrial Revolution 2040/mods/
  （tacz-1.20.1-1.1.8-hotfix2.jar, sophisticatedbackpacks-1.20.1-3.26.14.2226.jar, sophisticatedcore-1.20.1-1.5.15.2427.jar）

## 五、许可证风险备忘
TACZ为GPL-3.0。本mod引用TACZ类/API属于动态依赖，许可证传染性存在争议空间；
发布前需确定本mod许可证（倾向MPL-2.0，若与GPL要求冲突则再议）。
mixin注入点均为最小拦截，不复制TACZ代码。

## 六、技术调研结论（2026-10-27 已完成）
### 6.1 过热闭环（需求1/2/4）
- handleShootHeat @ ModernKineticGunScriptAPI(MKGSA):216-226：每发 heat=min(cur+perShot,heatMax)，到顶 setOverheatLocked(true)。调用点在 shootOnce 的 CycleTask 内（:171-177，fire事件后，Lua可覆盖）。
- GunHeatData：heatMax=100 / perShot=3 / coolingMultiplier / coolingDelay=1000ms / overHeatTime=3000ms / min-maxInaccuracy / min-maxRpmMod（默认1）。
- NBT：GunItemDataAccessor:395-412，GUN_OVERHEAT_LOCK_TAG/GUN_OVERHEAT_TAG。
- heatInaccuracy @ shootOnce:118-125：按heatPercentage lerp min/maxInaccuracy，MAX_HEAT走modifyProperty。
### 6.2 射速节流链（需求4/5）
- getShootInterval @ GunData:302-313：60_000L/rpm；:306 rpm=clamp(cacheProperty.getCache(RpmModifier.ID),1,1200)走配件缓存链；:310 rpm=(int)(rpm*lerpRPM)（仅hasHeatData枪）。
- lerpRPM @ GunItemDataAccessor:415-422：按heatPercentage插值min/maxRpmMod → mixin此方法（default方法）一处管全端。
- 调用方4处：LocalPlayerShoot:330（客户端预测）、LivingEntityShoot:258（服务端）、MKGSA:332（脚本）、GunAnimationStateContext:109（动画）。
- 防除零：乘后rpm clamp>=1。
### 6.3 弹药流（需求3/6/7）
- reduceAmmoOnce @ MKGSA:233-262+（膛内/闭膛/开膛分支）。
- LivingEntityShoot:95-158预检查流：:116 hasInventoryAmmo调用，:135-138 CLOSED_BOLT上膛consumeAmmoFromPlayer(1,gun,needCheck)。
- MKGSA.consumeAmmoFromPlayer(:541-553) / LES.consumeAmmoFromPlayer(:268-282)：均走 shooter ITEM_HANDLER → findAndExtractInventoryAmmo。
- canReload @ AbstractGunItem:122-140+：:135-136 直读枪禁止换弹。
- LocalPlayerShoot:212 客户端预测也走 iGun.hasInventoryAmmo → mixin双端自动覆盖。
- SB BackpackItem.initCapabilities:403-420：除 CapabilityBackpackWrapper 外，:418-419 也暴露 ForgeCapabilities.ITEM_HANDLER（getInventoryForInputOutput，带count>1保护）→ 弹药源解析走通用"物品容器展开"即可；升级物品注册仍需SB编译期依赖。
### 6.4 ragnarok 兼容性（refmap+常量池已验证）
- MakeGunEnchantableMixin全在Item本体：isEnchantable（幂等可共存）/getEnchantmentValue/canApplyAtEnchantingTable（委托GunEnchantmentHooks）→ 附魔获取主走铁砧书+我们附魔类override canEnchant。
- MixinModernKineticGunScriptAPI空壳、MixinAbstractGunItem只动dropAllAmmo、MixinModernKineticGunItem只动doMelee/doPerLivingHurt → 与我们全部注入点零冲突。

## 七、架构定案
核心=弹药源解析器 AmmoSourceResolver：CombinedInvWrapper(玩家背包+装备的SB背包+周围弹药箱BE)，优先级可配置。
- mixin注入点3处 @ModifyVariable 方法头换handler：AbstractGunItem.hasInventoryAmmo、MKGSA.consumeAmmoFromPlayer(:541)、LES.consumeAmmoFromPlayer(:268双参签名)。
- 需求3弹链：mixin AbstractGunItem.useInventoryAmmo 尾部追加分支（枪NBT sf_belt_fed→true），canReload自动false，HUD自动切背包弹药显示。
- 需求6：弹药箱BE per-level注册表（onLoad/onRemove维护，双端分表），AABB范围查找。
- 需求7：SB条件注册（ModList.isLoaded隔离类）。
- 需求4/5：mixin GunItemDataAccessor.lerpRPM。渐进射速状态可复用IGunOperator的data字段或枪NBT时间戳。
- 需求1/2：mixin MKGSA.handleShootHeat。冷却装置 perShot*=(1-0.1*lvl)；风冷=rpm超阈值线性减免perShot可为负（下限配置），参数：thresholdRpm默认600、perRpm默认0.002、floor默认-1.0。设计决策留max复核："射速越高"取静态rpm解释。
- NBT统一子tag "SustainedFire"（sf_cooling_level/sf_belt_fed）避免键冲突。
- 附魔获取路径：铁砧书为主（ragnarok的canApplyAtEnchantingTable委托可能屏蔽附魔台）。

## 八、构建方案
- gradle骨架抄 energycapacitor（标准MDK），包名 com.lmaxroutercn.sustainedfire（对齐TACZ包名隔离）。
- libs/ 放三个jar，dependencies 用 fg.deobf("blank:...")。
- mixin：MDK原生支持，需在 build.gradle 加 mixin 配置（arg/annotation processor）+ resources 下 mixin json + mods.toml 无需声明（Forge自动发现 jar 内 mixin config）。

## 九、里程碑
1. [x] 定名、需求定稿
2. [x] 参考源码就位
3. [x] 源码调研（2026-10-27完成，结论见第六节）
4. [ ] 项目骨架（build.gradle、主类、注册体系、libs复制、mixin配置）
5. [ ] 附魔实现（冷却/风冷/弹链/预热/渐进射速）
6. [ ] 弹药箱方块
7. [ ] SB联动
8. [ ] 构建通过、commit
9. [ ] 发布准备（README双语、许可证、图标）

## 十、运行时状态（agent工作日志）
- 2026-10-27：项目启动，源码调研开始
- 2026-10-27 04:00左右：调研完成，GOAL-PLAN v2 重写（本文件），进入骨架搭建阶段