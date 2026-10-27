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
- M6 实证补充（2026-10-27 晚）：换弹真实扣弹点 defaultReloadFinishing @ ModernKineticGunItem:415-450（装填→收尾跳变触发，MAGAZINE 分支 api.consumeAmmoFromPlayer(needAmmoCount)）→ MKGSA:541 → M4 mixin 已覆盖，换弹消耗侧零新增 mixin。
- 客户端预检闭环：LocalPlayerReload:66 直读枪 return（弹链枪天然免换弹）；:77-78 canReload 预检 needCheckAmmo 时本地拦截不发包；LocalPlayerShoot.preCheck:212 射击预测同走 hasInventoryAmmo（mixin 双端覆盖）→ 客户端 BE 数据可见性为唯一缺口，step3 同步补齐。
### 6.4 ragnarok 兼容性（refmap+常量池已验证）
- MakeGunEnchantableMixin全在Item本体：isEnchantable（幂等可共存）/getEnchantmentValue/canApplyAtEnchantingTable（委托GunEnchantmentHooks）→ 附魔获取主走铁砧书+我们附魔类override canEnchant。
- MixinModernKineticGunScriptAPI空壳、MixinAbstractGunItem只动dropAllAmmo、MixinModernKineticGunItem只动doMelee/doPerLivingHurt → 与我们全部注入点零冲突。

## 七、架构定案
核心=弹药源解析器 AmmoSourceResolver：CombinedInvWrapper(玩家背包+装备的SB背包+周围弹药箱BE)，优先级可配置。
- mixin注入点3处 @ModifyVariable 方法头换handler：AbstractGunItem.hasInventoryAmmo、MKGSA.consumeAmmoFromPlayer(:541)、LES.consumeAmmoFromPlayer(:268双参签名)。
- 需求3弹链：mixin AbstractGunItem.useInventoryAmmo 尾部追加分支（枪NBT sf_belt_fed→true），canReload自动false，HUD自动切背包弹药显示。
- 需求6（2026-10-27 改案，max 可否决，回滚点=AmmoSourceRegistry.getNearbyAmmoBoxes 单函数）：弃 per-level 注册表簿记，手写 chunk 网格扫描现查（getBlockEntitiesOfClass(Class,AABB) 系后期版本 API，1.20.1 无） + OPEN 态过滤（capability 不设限，漏斗免费互通）；BE 数据同步：getUpdateTag 带 Items + onContentsChanged 服务端 sendBlockUpdated（vanilla 默认 getUpdateTag 为空 tag，箱子即如此；GUI 显示走 menu 广播，两通道最终一致）。
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
4. [x] 项目骨架（build.gradle、主类、注册体系、libs复制、mixin配置）
5. [x] 附魔实现（冷却/风冷/弹链/预热/渐进射速）
6. [ ] 弹药箱方块（step1✅ 0344123 / step2✅ 7885a57 / step3 供弹接线+BE同步 / step4 开盖黄线框 / step5 漏斗+集成测试）
7. [ ] SB联动
8. [x] 构建通过、commit
9. [ ] 发布准备（README双语、许可证、图标）

## 十、运行时状态（agent工作日志）
- 2026-10-27：项目启动，源码调研开始
- 2026-10-27 04:00左右：调研完成，GOAL-PLAN v2 重写（本文件），进入骨架搭建阶段
- 2026-10-27（M5迭代，实测反馈4项→修复3+定案1）：
  - 附魔desc补全：en/zh 各5条 + itemGroup 键（EnchDesc 类模组可见 tooltip）。
  - 渐进射速会话按枪独立：FireSessionTracker 键由 shooter 改为 shooter>gunId（GunFireEvent 自带 gunItemStack 提取），修复跨枪共享连射时长；读路径惰性清扫（阈值256条，无轮询）。
  - 创造模式标签页 ModCreativeTabs：5本满级附魔书，等级引用 getMaxLevel()，零硬编码。
  - m134渐进无感定案：TACZ 实际射速上限 1200rpm = 每 tick 一发（ShootKey 逐tick判定 + shoot() 内 coolDown>=50ms 硬门槛 + GunData 内 Mth.clamp(rpm,1,1200) 三重决定）。原始 rpm 顶格的枪（转管机枪类）加成被引擎天花板吸收，低 rpm 枪（霰弹枪）加成明显；"接受上限/突破上限"两选项已抛给用户待定。
  - commit 09787ea，jar 27800B（增量构建 36s）。
- 2026-10-27（框架坑）：PokerAgent create 指令的路径必须写在指令行内（不能放 code 块首行），否则空路径落工作目录 → Errno13，且失败后队列中止；文件写入改走 exec + WriteAllText（UTF-8 noBOM，中文完整性已验证）。sfbuild schtasks 残留任务已清（防 23:59 空跑构建）。
- 2026-10-27（M6 弹药箱 step1-2 落地）：
  - step1 commit 0344123：AmmoCrateBlock（OPEN 属性/shift 开关盖/关盖防呆/双模型占位）+ AmmoCrateBE（27 格 ItemStackHandler+cap）+ 三注册器 + Config（backpackUsable=false/radius=4.0）+ lang/loot/mineable + System.Drawing 三占位贴图。
  - step2 commit 7885a57：ModMenus/AmmoCrateMenu（63 槽 ChestMenu 几何/手写 stillValid：BE 身份比对+distanceToSqr≤64）/AmmoCrateScreen/ClientSetup（FMLClientSetupEvent+enqueueWork+vanilla MenuScreens.register）+ NetworkHooks.openScreen 开 GUI + GUI 占位贴图。
  - 1.20.1 API 三坑（详见长期记忆 028）：RegisterMenuScreensEvent 系 NeoForge 事件；BlockEntity.stillValid(Player) 不存在；Player.openMenu(MenuProvider,BlockPos) 解析失败 → NetworkHooks.openScreen。
  - 过程教训：javac 吞未报下游错误（build1 只报 ClientSetup，Block/Menu 错误下轮才浮出），全绿前不信「就这几个错」。
  - step3 设计定案：AABB 现查（改案已记第七节）+ BE getUpdateTag 全量 Items 同步（弃摘要方案：空槽 NBT 约 500B/包，弹链高频 ~20 包/s 单箱可接受，复用 serializeNBT 零额外层；卡再换摘要）+ Resolver 接 config 半径（删硬编码 AMMO_BOX_RADIUS）。
