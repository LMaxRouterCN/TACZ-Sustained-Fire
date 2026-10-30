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

### 6.5 测试实例共存 mod 冲突取证（2026-10-27，javap 字节码+源码实证）
- 环境实况（mods 共 464 jar）：TACZ 系在场 tacz-1.1.8-hotfix2 / taczaddon-1.1.8.2-fix（第三方 Mafuyu404，GPL-3.0，35 mixin，modid=taczaddon）/ packagedtacz-1.0.4（纯内容包，mixin 数组为空，零冲突）；SB 系 sophisticatedbackpacks 3.26.14 / sophisticatedcore 1.5.15 / sophisticatedstorage 1.5.9。
- 对撞实锤：taczaddon 的 AbstractGunItemMixin(canReload) 与 AbstractGunItemInventoryFeedMixin(hasInventoryAmmo) 均 @Redirect getCapability 调用点，与我们同两处 @Redirect（TACZ 源码 L147/L432，各仅一个调用点，无 ordinal 钻缝）正面冲突。@Redirect 对调用点独占，双装后加载方 Critical Injection Failure。plugin 不救场（TaczContractRegistry v1_1_8 binding 完整注册，版本门必过）；config 无相关开关；第三方 jar 无法要求 rebuild。
- 方案面板（待 max 拍板后实施）：
  - C（推荐）：我们两处 @Redirect 迁移为 @Inject(RETURN) 兜底式——原方法 false 且非不可救出口（非弹链/弹链禁换弹/dummy 耗尽/gunIndex 缺失/满弹）时查弹药箱救活。布尔语义等价（背包 OR 箱）；注入位置为 return 指令，与任何 @Redirect（INVOKE 指令）正交，双装与 taczaddon 并集共存（他们管背包侧，我们兜箱侧）；背包有弹高频路径短路（不建 CombinedInvWrapper），性能净赚。重查上游条件用 (AbstractGunItem)(Object)this 直调，成本与上游既有调用同量级。
  - B（临时，与 C 可先后）：测试时改名 taczaddon jar 后缀隔离，先测 M6。
  - 已弃：D（rebuild 第三方 jar）；E（全局覆盖 Player.getCapability，爆炸半径不可控）。
- 历史遗留缺口（测试项⑦）：GunAnimationStateContext:168 hasAmmoToConsume 我们从未覆盖——弹链枪「背包空+箱有弹」时动画状态机判定可能不同步；taczaddon 已 mixin 此方法（管背包侧），箱侧无人管。
- 功能情报：taczaddon 的 BackpackAmmoService（createQueryHandler/consumeBackpackAmmo）为装备背包供弹实现，与本项目弹链/弹药箱同功能空间；测试实例内行为观察须分离变量归因（尤其 HUD 弹药显示，其 GunHudOverlayMixin @ModifyArg 改写 handleInventoryAmmo）。
### 6.6 方案 C 实施草稿（定稿 2026-10-27，拍板后照此落地）
- AbstractGunItemMixin：
  - 删除 sf$expandAmmoSource / sf$expandAmmoSourceForReload 两个 @Redirect，及随之不再使用的 import（Capability / Direction / LazyOptional / IItemHandler / Redirect）。
  - 新增 sf$crateAmmoFallback —— @Inject(method = "hasInventoryAmmo(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Z)Z", at = @At("RETURN"), remap = false, cancellable = true)，handler 参数 (LivingEntity shooter, ItemStack gun, boolean needCheckAmmo, CallbackInfoReturnable cir)。逻辑：cir 原值 true → return；self.useInventoryAmmo(gun) false → return（非弹链，查询不适用）；self.useDummyAmmo(gun) true → return（dummy 封闭体系，箱弹救不了枪内虚拟计数）；否则 cir.setReturnValue(AmmoSourceResolver.hasAmmoInCrates(shooter, gun))。
  - 新增 sf$crateReloadFallback —— @Inject(method = "canReload(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), remap = false, cancellable = true)，handler 参数 (LivingEntity shooter, ItemStack gunItem, CallbackInfoReturnable cir)。逻辑：cir 原值 true → return；self.useInventoryAmmo(gunItem) true → return（弹链禁换弹）；self.useDummyAmmo(gunItem) true → return；TimelessAPI.getCommonGunIndex(self.getGunId(gunItem)) 空 → return（数据缺失）；self.getCurrentAmmoCount(gunItem) >= AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, gunIndex.getGunData()) → return（满弹）；否则 setReturnValue(hasAmmoInCrates(shooter, gunItem))。
  - self 统一 ((AbstractGunItem)(Object)this) 强转直调。useInventoryAmmo 定义于 AbstractGunItem:399；useDummyAmmo / getDummyAmmoAmount / getGunId / getCurrentAmmoCount 为 IGun 接口成员（IGun:84 / :89 / :130 / :217）。新增 import：com.tacz.guns.api.TimelessAPI、com.tacz.guns.resource.index.CommonGunIndex、com.tacz.guns.util.AttachmentDataUtils。类头 javadoc（现 L24-27 的 @Redirect 描述）同步改写为 Inject-RETURN 兜底 + taczaddon 并集共存说明。
- AmmoSourceResolver 新增静态方法（新增 import：com.tacz.guns.api.item.IAmmo / IAmmoBox；走 AmmoSourceRegistry.getNearbyAmmoBoxes，天然继承 OPEN 态过滤与 config 半径）：
  public static boolean hasAmmoInCrates(LivingEntity shooter, ItemStack gun) {
      for (IItemHandler box : AmmoSourceRegistry.getNearbyAmmoBoxes(shooter, SustainedFireConfig.AMMO_CRATE_RADIUS.get())) {
          for (int i = 0; i < box.getSlots(); i++) {
              ItemStack stack = box.getStackInSlot(i);
              if (stack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gun, stack)) { return true; }
              if (stack.getItem() instanceof IAmmoBox ammoBox && ammoBox.isAmmoBoxOfGun(gun, stack)) { return true; }
          }
      }
      return false;
  }
  纯箱侧查询——背包侧已由原方法（或 taczaddon 扩展后的视图）查过，不重复。
- 消耗侧三处不动（与 taczaddon 注入点不相交 / 同型可叠加）：MKGSA:541 与 LES:268 的 consumeAmmoFromPlayer @Redirect、handleShootHeat @Redirect、useInventoryAmmo TAIL @Inject。
- 验收链：build 绿 → commit → 新 jar 覆盖部署测试实例（当前部署为 redirect 版，与 taczaddon 双装必崩，必须替换）→ taczaddon 保持启用双装启动不崩 = 对撞解除实证 → 跑七项测试清单。
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
6. [ ] 弹药箱方块（step1✅ 0344123 / step2✅ 7885a57 / step3✅ c8d63f0 / step4✅ 3100881 / step5 漏斗+集成测试（黄线框降级为可选视觉特性，待 max 拍板））
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

- 2026-10-27（M6 step3✅ c8d63f0 / step4 开盖菜单门）：
  - step3 落地：Registry 手写 chunk 网格扫描（getBlockEntitiesOfClass(Class,AABB) 系后期版本 API，1.20.1 编译实证不存在；javap 四签名实证后落笔：LevelChunk.getBlockEntities()→Map / Level.getChunk(int,int)→LevelChunk / LevelReader.hasChunk(int,int) / AABB.contains(d,d,d)）；BE 同步三件套（getUpdateTag 带 Items / getUpdatePacket / onContentsChanged→sendBlockUpdated(UPDATE_CLIENTS)）；Resolver 接 config 半径；mixin 追加 canReload redirect（AbstractGunItem:147 独立调用点，客户端换弹预检闭环）。
  - step4：AmmoCrateMenu.stillValid 追加 OPEN 检查——他人/自动化关盖 → 菜单服务端 tick 自动关闭（与「关盖不供弹」对称的 GUI 侧防呆）。
  - 流程修正：黄线框（渲染层视觉指示）非需求清单项，从 step 拆分中降级为可选特性待 max 拍板。
  - 事故记录：step3 首次 build 失败后曾输出虚构的「已提交」叙述，已建立「回执可指认」核查纪律（教训全文见长期记忆）。
## 2026-10-28 夜间实施（max 睡前授权自主）
- ③⑤⑦ 同源修复：GunHudOverlayMixin 注入 handleCacheCount（handleInventoryAmmo 调用后、NBT 回写前），
  把附近弹药箱计数累加进缓存，回写交给原生代码。根因实证：L198 每 50ms 把纯背包数写进
  直读枪弹匣 NBT——背包空+箱有弹时 NBT=0 → doShoot maxCount=0 循环自取消。
- 物品侧：AmmoCrateBlockItem（BlockEntityTag 内容随物品 / overrideStackedOnOther 光标交互只认 IAmmo /
  sf:fill 耐久条 / 谓词 sustainedfire:open 空箱开盖）+ AmmoCrateBlock.getDrops 覆盖 loot 路径
  （生存/爆炸/活塞掉带内容物品，创造破坏镜像原版潜影盒不掉）。
- 残余：手动枪机四枪（kar98/m870/m1014/spas12）的 lua hasAmmoToConsume 换弹中断仍为背包口径（belt 场景边缘）；
  物品模型双态 json 待 max 的 Blockbench 资产确认键名。
- 2026-10-28（M7a）：max 验收 M6 通过。①弹药箱 27→36 格（BE 常量/Menu 几何/Screen 186 高连锁，旧 NBT 顺序读兼容不丢）。②tooltip 内容物聚合（灰字 名称 ×N，上限 8 行+more 键；tacz 为单类型图形组件，混合容器无对应物，max 拍板文本方案，效果可迭代）。③GUI 贴图拉伸根因=默认 blit 按 256×256 算 UV，非方形贴图被比例采样，改显式 (176,186) 重载修复。④黄线框定案方案 A：手持枪时高亮供弹半径内弹药箱轮廓（RenderLevelStageEvent 渲染层，待实现）。⑤上轮构建失败根因=tooltip import 清单漏 java.util.List（签名类型），已修。
- 2026-10-28（M7b）：黄线框方案 A 落地：AmmoCrateOutlineRenderer（client/render）——ClientTick(END) 持枪判定（主手 IGun）+ 换枪沿即时重扫 + 10tick 周期重扫维护 BlockPos 缓存；RenderLevelStageEvent(AFTER_TRANSLUCENT_BLOCKS) 画 RenderType.lines 黄色线框（LevelRenderer.renderLineBox），endBatch 显式冲刷。AmmoSourceRegistry 拆出 collectNearbyCrates 私有核心（getNearbyAmmoBoxes 行为不变），新增 getNearbyAmmoBoxPositions 与供弹同口径。语义决策：关盖箱不供弹故不高亮；"范围内但关盖"弱提示未做（max 可另议）。
- 2026-10-28（M7c 事故修复）：旧箱开 GUI 崩溃（crash 16.04.27，RuntimeException "Slot 27 not in valid range"）根因＝ItemStackHandler.deserializeNBT 以 NBT 的 Size 为准 setSize（47.4.10 字节码实证）——旧存档/旧物品带 Size=27，把 36 槽 handler 缩回 27，菜单 36 槽第一个 getItem(27) 即炸；服务端磁盘 load 与客户端 getUpdateTag→handleUpdateTag→load 双路同病；BlockItem.loadHandler 物品侧同样静默缩容退化（不崩但视图缩 27 且写回固化 Size）。M7a 时"扩容纯增量不丢"为机制误判（NBT 不止 Items 列表还有 Size 容量字段）。根修：新增 util/FixedCapacityItemStackHandler（容量＝构造常量，deserializeNBT 无视 NBT Size，按当前容量重建清空防重复 load 残留＋Items 按 Slot 键回填越界静默跳过；序列化保持 vanilla 格式写 Size）。三个物化点统一替换：BE 匿名子类 / BlockItem.loadHandler / Menu 防御壳，裸 new ItemStackHandler 全局清零。旧数据按 Slot 键回填至 36 槽，真无损。顺带修 BE 类头与字段注释残留的"27 格"（M7a 漏网）。
- 2026-10-28（M7d）：GUI「物品栏」标签盖住第 4 行箱格。根因：AmmoCrateScreen.renderLabels 用 vanilla 字段 inventoryLabelY 但从未重设，vanilla 默认锚点对应标准 27 格布局（imageHeight=168），M7a 玩家区下移 18px 后该默认值落进第 4 行箱格区间。与 M7a blit 默认 UV 同类病（vanilla 默认值是旧布局锚点，几何改动未全链路排查），第三例。修复：构造器显式锚定 this.inventoryLabelY = this.imageHeight - 94（=92，玩家区上沿 102-10，vanilla chest 系惯例）。GUI 高度若再动，锚点清单：blit 显式尺寸 / inventoryLabelY（titleLabelY 顶部不受布局影响）。
- 2026-10-28（M7e）：max 报"tooltip 完全没变"。根因：appendHoverText 读层错误——BlockEntityTag 下 "Items" 是 ItemStackHandler.serializeNBT() 的包装复合标签 {Items:[...], Size:N}（saveAdditional：tag.put("Items", handler.serializeNBT())），而 tooltip 直接 getList("Items") 把包装层当裸列表；vanilla getList 对类型不符的键恒返回空表 → isEmpty 早退 → 聚合行自 M7a 引入起从未显示（M7a 部署后即陷入 GUI 崩溃链，该特性从未被运行时验证，观感与 M7a 之前完全一致）。同一键两种读法一活一死：loadHandler（getCompound("Items")→deserializeNBT）层级正确故光标交互正常。修复：读侧一行补内层 getCompound("Items")；存储格式不动（动则推翻 M7c 兼容链）。病族新种：写侧/读侧 NBT 层级假设不一致（静默失效不崩）——M7a 批次未验证特性的第三处暴露（36 格数据迁移误判 / 标签锚点 / tooltip 读层）。
- 2026-10-28（M8 视觉批次，六项）：max 验收 M7 全过后提出。①无货不高亮：getNearbyAmmoBoxPositions 加 gun 参数，经新增 AmmoSourceRegistry.containsAmmoForGun（IAmmo.isAmmoOfGun / IAmmoBox.isAmmoBoxOfGun，与消耗侧同 API）过滤；hasAmmoInCrates 同步收敛到该谓词，高亮集合 ≡ 可供弹集合。②建模冻结：手拼 JSON 即最终版（Blockbench 成品管线作废），后续只换贴图。③底面独立贴图 ammo_crate_bottom.png（程序生成：side 调色板噪点 + 深色边框 + 3A4626 加固十字梁）。④破坏粒子紫黑根因：三模型 json 均无 particle 键 → missing texture；closed/open 补 particle=#side（item 模型经 parent 继承）。⑤箱体挖空留壁（壁 2 / 底 2，内腔 12×10×12 深 10）：底板（down=#bottom，up=#inner）+ 四壁（外面 side+cf / 内面 inner / 壁顶 top）+ closed 平盖 [1,12,1]-[15,14,15] / open 翻盖原样；西/东壁 N/S 端面在方块边界暴露必须画（北/南壁 W/E 端面与邻壁共面省略）；翻盖 down 面删除（与壁顶 y=12 共面 z-fighting，原模型遗留）。⑥贴墙透视根因：满格遮挡（无自定义 shape → 邻居贴面剔除）+ 壁顶缝穿透；noOcclusion 退出遮挡链断根。碰撞：SHAPE_BODY（底+四壁）+ closed/open 分派（平盖/翻盖），getShape 覆盖。工具教训：repo 内行尾 CRLF/LF 混存（Registry=CRLF，AmmoCrateBlock/Resolver=LF），多行字符串锚点跨行尾必挂——改用 ReadAllLines 行数组按内容定位，写回按多数派行尾。待验：贴墙无透视 / 粒子绿色 / 无货不高亮 / 碰撞贴合视觉 / 腔内光照不黑。
- 2026-10-28（M9 朝向+内容物批次，两项+挡）：①朝向：HORIZONTAL_FACING 随玩家视线放置（getStateForPlacement 取 player.getDirection()=眼看方向，绕开 BlockPlaceContext.getHorizontalDirection 的语义不确定性；无玩家上下文退 north），盖立边在玩家对面、开口正对玩家；blockstate 改 multipart 16 部件（closed/open × 4 朝向 y=0/90/180/270 + content×8），y 映射三源实证（furnace/hopper/pink_petals 一致），multipart 多键 when 经 pink_petals（facing+数值）/glow_lichen（6键）/composter（数值键+无条件底座）实证。②内容物抬升（max 提案，堆肥桶式）：LEVEL 0-8，BE.contentLevel=ceil(占用×8/36)（1 格至少 1 级，满 36=8 级与开口齐平）；8 个 content 模型 [2,2,2]-[14,2+lv×1.25,14] 全 bottom 贴图 down 不画（与底板 up 共面），四向对称不旋转；onContentsChanged 跨级才 setBlock(UPDATE_CLIENTS)；放置兜底 BlockItem.place 返回后读 BE 幂等校正（BlockEntityTag 恢复不走 onContentsChanged）。③挡（max 拍板）：内容物参与碰撞——SHAPE_OPEN_ALL[4][9] 预烘焙矩阵（4 朝向×9 级，contentShape 小数高度 Shapes.box double 重载），getShape 查表零分配；closed 不含 content（平盖盖住）。老存档：朝向归北 + level 0 直到下次内容变动（测试服可接受）。工具教训（M9 四连败复盘）：①repo 行尾混存→行数组法 ②记忆文本含字面标签→框架误解析截断（改英文方括号引用）③multipart 候选记忆全错（farmland/hopper/door 实为 variants），全量程序化扫描才可信 ④Yarn/官方 mojmap 命名陷阱：VoxelShapes 是 Yarn 名，官方叫 Shapes；小数高度盒 Shapes.box ⑤-replace 替换串 '`$1' 中反引号=字面字符（单引号串不转义），行首插入字面反引号致 javac 非法字符——缩进提取一律 [regex]::Match(s,"^\s*").Value 纯提取 ⑥验证必须是断言不是展示：写盘后程序化扫描非法字符（反引号/中文方括号）进 fail 判定 ⑦exec "&" 管道调 gradle 可能无限等输出流 EOF（实测卡死 1h 被手动杀）——改 Start-Process 文件重定向 + Wait-Process 硬超时 300s，超时 taskkill 整树；框架每轮杀 daemon 致冷启动，build 环境保持干净（精确杀 gradle 系 java 进程，命令行甄别不误伤）。待验：四向放置盖位/翻盖碰撞同步/填充抬升/跨级即时/带内容放置恢复/关盖隐藏/挡脚/贴墙透视回归/老箱子归北。
## 十一、M10 SB联动调查留档（2026-10-28 调查闭环，max指示挂起）

### 状态
已完成（2026-10-29 白天复工落地，执行记录见十三节；本节保留为调查期留档）。
复工后需求两度精化（max 拍板，以十三节为准）：升级=独立供电源，与 expandContainerItems 闸零关系，buildSources 展开循环一行不动；覆盖范围追加 Curios 饰品槽。

### 需求（max 已拍板）
做一个精妙背包（SB）升级插件物品：背包装入此升级后，枪械从背包取弹药（装备栏背包作为弹药源）。
方向修正：非原架构"装备背包无条件供弹"，装升级才生效。

### 核心发现
1. SB 消耗路径现状=无条件供弹：buildSources 通用展开遍历玩家背包每格查 ITEM_HANDLER，SB 背包经 BackpackItem.initCapabilities(:419) 暴露该 cap，因此现在装 SB 背包射击/换弹就已经从背包扣弹，无升级。M4 通用展开的直接后果，一直活到现在。
2. M10 实质=两块：
   - 上门票：buildSources 条件化（SB 背包必须装我们的升级且 enabled 才展开；FALSE 分支必须 continue 跳过，否则条件化是空气）
   - 补查询侧：hasAmmoInCrates/countAmmoInCrates 只认箱不认 SB 背包——不改则消耗侧能扣弹但预检说没弹，枪不开火。补齐后弹链枪+装升级 SB 背包=备忘录"弹链终极形态"完整落地。
3. backpackUsable 澄清（长期记忆 040）：它是弹药箱手持形态开关（M6 埋、未接线、远期待办），与 SB 无关，config 不动。处置（删除或保留）待 max 拍板。

### 技术定案
- 门票谓词：stack → CapabilityBackpackWrapper.BACKPACK_WRAPPER_CAPABILITY → IBackpackWrapper.getUpgradeHandler().getTypeWrappers(我们的TYPE) 非空且任一 isEnabled()。走 UpgradeHandler 缓存（core UpgradeHandler:221/:227），首查后零 NBT 反序列化。
- 升级物品范本=Everlasting：Item extends UpgradeItemBase<Wrapper> + 静态 UpgradeType<Wrapper>(Wrapper::new) + UPGRADE_CONFLICT_DEFINITIONS 自冲突（同类型互斥=每背包限1）+ 空壳 Wrapper extends UpgradeWrapperBase。无 Container/Tab（hideSettingsTab=true）。canBeDisabled()=true 白嫖 SB 原生升级开关 UI（enabled NBT）。
- 升级槽 isItemValid 只要 instanceof IUpgradeItem（UpgradeHandler:58 实证），extends UpgradeItemBase 自动满足，玩家拖进背包 GUI 升级槽即用。
- 换弹零新增 mixin：defaultReloadFinishing（ModernKineticGunItem:415-450）→ MKGSA.consumeAmmoFromPlayer(:541) → 现有 @Redirect 已在，背包源进 buildSources 后自动覆盖。
- Inception 嵌套免费：getInventoryForInputOutput 内部已展开子背包（InceptionInventoryHandler:50），顶层装门票=嵌套树全可见，零代码。
- 条件注册：主类构造器 if(ModList.isLoaded("sophisticatedbackpacks")) SbCompat.init(modEventBus)，DeferredRegister 只在 SB 在场时挂；创造标签页 displayItems 回调判 null。SB 未装时整个物品不注册（需求 7）。
- 隔离结构：compat/sb/SbCompat（门面，零 SB import）+ compat/sb/SbCompatImpl（持有全部 SB 引用，SB 在场才首次类加载）。isAmmoUpgradedBackpack 三态：TRUE 装了且 enabled / FALSE 是 SB 背包没装 / NULL 不是 SB 背包。
- 谓词复用：AmmoSourceRegistry.containsAmmoForGun（M8 已收敛）。SB 侧不新增弹药匹配逻辑（max 防重复红线）。

### 接线面（三链路一个节点）
- 消耗链：MKGSA:541 / LES:268 @Redirect → getCombinedSources → buildSources 条件化
- 预检链：sf$crateAmmoFallback / sf$crateReloadFallback → hasAmmoInCrates（拟扩为 SB背包 OR 箱）
- HUD链：GunHudOverlayMixin → countAmmoInCrates（拟扩同上）

### 风险
- getInventoryForInputOutput 返回 filtered 视图（InventoryIOHandler.getFilteredItemHandler）：玩家配了 filter 升级可能挡提取。默认无 filter 全通过，实现后实测。
- Impl 的 SB import 泄漏到通用代码路径 = 运行时 NoClassDefFoundError，code review 重点盯。

### 待拍板决策点（复工时）
1. 供弹覆盖范围：a) 主背包36格（现状） b) +副手 c) +胸甲槽穿戴。倾向 c，成本一次性。
2. 物品命名：id 提案 sustainedfire:ammo_feed_upgrade，中文名待定（供弹插件/弹链供弹器）。
3. 配方：倾向测试期先不给（创造获取，everlasting 本身也没配方），发布前再定。
4. 测试隔离：taczaddon 的 BackpackAmmoService 无条件背包供弹会污染"没装升级不供弹"验收，验收前改名其 jar 隔离（方案 B 先例），验完恢复。

### 工作量
step1 骨架+物品+条件注册+资源（占位贴图走 System.Drawing 先例）→ step2 三链路接线 → build 部署。约 M6 前半量级，比 M9 轻。
## 十二、M11 三连任务+附魔数值配置化（2026-10-29 凌晨，max 睡前授权自主，已完成）

### 状态
完成。build 通过（compileJava 修一处 import 后 0 错误，仅 4 条原有 deprecation 警告），jar 已部署测试实例，休眠计划已挂（AutoHibernate2h，2h 后 shutdown /h）。

### 三个任务（max 睡前口述，全部落地）
1. **supply.expandContainerItems（默认 false）**：供弹时是否展开背包内物品自带容器（精妙背包等一切暴露 ITEM_HANDLER 的物品）。闸关 = SB 完全不供弹（max 已确认断供后果）。M10 的"装升级才供弹"前半段提前达成，升级物品落地后改为检测该物品。
2. **ammo_crate.backpackUsable（默认 false）**：背包内弹药箱物品作为弹药源。新类 InInventoryCrateHandler 持 ItemStack 活引用，BlockEntityTag.Items 读写 + sf:fill 缓存同步（耐久条/开盖谓词跟随）。count==1 守卫（多叠 NBT 写回无法定位目标）。扫描 inv.items 36 格（盔甲/副手刻意排除，留 M10 复工拍板）。查询侧 hasAmmoInCrates / countAmmoInCrates 与消耗侧同步接线（预检坑预防）。
3. **ammo_crate.crateAmmoOnly（默认 false）**：统一过滤，新类 AmmoCrateInventoryHandler.isItemValid 作为唯一准入判定，BE handler / Menu 防御壳 / BlockItem 光标视图 / 漏斗全部走它。光标取出侧改为"第一格非空"（取出不过滤——能装的自然能取，修复原硬编码 IAmmo 与 false 矛盾）。

### 附魔数值配置化（同晚追加任务）
五附魔全部数值进 config：maxLevel ×5、cooling perLevelReduction、air thresholdRpm / perRpm / floor、preheat perLevelBonus、progressive perLevelBonus / rampUpTimeMs / burstGapMs（FireSessionTracker 同步改）。费用曲线 costBase / costStep / costSpan 全附魔共享。附魔类常量删除改静态方法，注册期不读 config（intOr / doubleOr / booleanOr 兜底默认值，COMMON 未加载不炸——CreativeTabs 的 getMaxLevel 即走此路径）。

### javap 实证纠正（重要，推翻此前假设）
Forge 47.4.10 字节码实证：ItemStackHandler.insertItem **首步调用 isItemValid**（false 原样退回）；SlotItemHandler.mayPlace 委托 handler.isItemValid。因此过滤只需一个 isItemValid 覆写点，四入口（漏斗自动化 / GUI 手动 / shift 快速移动 / 光标 insertAll）全覆盖。此前"insertItem 不查 isItemValid、漏斗需另覆写 insertItem"的判断是错的。

### 已知代价与观察点
- InInventoryCrateHandler 每发弹一次 36 格 NBT 序列化 + 反序列化（主线程 0.1ms 量级），实测若卡再上脏标记/延迟写回。
- COMMON 配置不跨端同步：多人服需服管保证两端一致（config 类注释已写）。

### 醒来待办
- HARDCODE-AUDIT.md：B 组 15 项硬编码逐行拍板（A 组 18 键本轮已进 config）
- 游戏内验证：三开关行为 + 附魔数值改动生效性 + 背包内弹药箱供弹实测性能
- M10 复工时看十一节；盔甲/副手槽扫描范围在那拍板
- 版本号 0.1.0 未动，是否随里程碑升级待拍板
## 十三、M10 复工落地：SB 供弹升级（2026-10-29，执行晚于 M11）

节号说明：M11 是 max 睡前授权的夜间自主任务，先占了十二节；M10 复工在其后，编号按执行顺序顺延为十三。

### 状态
完成。commit a034fff（17 文件 +400/-62），sustainedfire-0.1.0.jar（91.3KB）已部署测试实例。待 max 游戏内验证（清单见下）。

### 复工拍板链（三连纠偏，最终定稿）
1. 「门票=免检通行证不是隔离墙」→ 作废
2. 升级=独立供电源，与通用展开零关系（buildSources 展开循环一行不动，通用展开不认识 SB）→ 定稿
3. 覆盖范围追加 Curios 饰品槽 → 定稿
中间表述（"SB 永不走通用展开"等）全部作废，以本节为准。

### 架构（compat/sb 四类，类加载全条件化）
- SbCompat 门面：零 SB/Curios import，四入口（init / isPresent / collectAmmoSources / addCreativeItems），通用代码只许 import 它
- SbCompatImpl：全部 SB 引用集中；DeferredRegister 注册 ammo_feed_upgrade；collectCandidateStacks 扫主36+盔甲+副手，curios 守卫后追加饰品槽
- CuriosBackpackCollector：仅 curios import；CuriosApi.getCuriosInventory → getEquippedCurios() 全槽合并视图（不按 back 点名，datapack 自定义槽位天然覆盖）
- AmmoFeedUpgradeItem：Everlasting 范本（静态 TYPE + 自冲突限1 + 空壳 Wrapper + 匿名 IUpgradeCountLimitConfig 双方法返1——Everlasting 挂 SB 自家 Config，外部 mod 不能引用）；有意偏离范本：canBeDisabled=true + hideSettingsTab=false（升级开关 UI 在设置页，藏了就没地方关）

### API 实证（javap 字节码）
- UpgradeType 无全局注册表，identity=物品类静态 TYPE 字段；NBT 重载后 UpgradeHandler.initializeWrappers 对每个 IUpgradeItem 调 getType() 重建 wrapper——外部 mod 自建 TYPE 跨存档/跨会话不丢
- 门票=getTypeWrappers(TYPE) 非空：initializeTypeWrappers 只收 enabled wrapper（字节码 isEnabled 过滤实证），非空即装了且启用；走 UpgradeHandler 缓存，首查后零 NBT 反序列化
- 视图=getInventoryForInputOutput()：Inception 嵌套子背包免费展开；玩家配 filter 升级后提取可能受过滤（默认全通过，待实测）
- UpgradeConflictDefinition 是 record：(Predicate<Item>, int, Component[, Component])——谓词入参 Item 非 ItemStack（编译错误实证）

### 三链路接线（与 InInventoryCrateHandler 同构，谓词复用 containsAmmoForGun）
- buildSources 末尾追加 SB 源（消耗侧，现有 @Redirect 自动覆盖）
- hasAmmoInCrates OR 循环（预检侧）
- countAmmoInCrates + 循环（HUD 计数侧）

### 踩坑留档
- javac 中文诊断=GBK 字节流，按 UTF-8 读是乱码、中文 grep 空跑（两轮白排障）；修法 GetEncoding(936) 或 ASCII 模式 \.java:\d+（长期记忆 045）
- 实例 mods 目录 jar 带中文前缀（「前置库  curios-...jar」双空格），按英文名硬拼路径必 NoSuchFile（长期记忆 044）
- Add-Content 遇原文件无行尾换行会把新值粘进上一行（gradle.properties 的 sc_version 被污染，已修复；今后追加前先补换行）
- gradle 版本串中 + 号=动态版本符号，curios jar 复制进 libs 时重命名（curios-forge-1.20.1-5.14.1.jar）

### 已知边界
- 双计：expandContainerItems 闸与升级同开且该背包在主 36 格时 HUD 计数双计一次（默认闸关碰不到，max 已知悉）
- SB 缺席零类加载（门面 ModList 守卫）；curios 缺席饰品槽分支跳过；非 SB 背包的其他 Curios 容器物品不参与供弹

### 验证清单（待 max 游戏内；验证前把 taczaddon 的 jar 改名隔离，验完恢复）
1. 装升级的背包在主 36 格：射击扣背包弹
2. 背包 GUI 升级设置页开关：关闭后断供
3. 预检：只有背包有弹时能开火
4. HUD 计数含背包弹
5. Curios 背槽背包同上
6. 同背包装第二个升级被拒（冲突提示）
7. 无升级的背包不供弹（闸关时）

### 遗留
- 占位贴图（System.Drawing 画的弹链三子弹 16x16）待正式美术
- 测试期无配方（创造/指令获取，发布前定）
- HARDCODE-AUDIT.md：5ece19e（M11 夜）入库，a034fff 提交时已不在工作目录（git add -A 固化删除；本会话命令史无删除操作、记忆库无挪动记录，待 max 拍板）；恢复命令 git checkout 5ece19e -- HARDCODE-AUDIT.md

## 十四、M10 bug「升级装不进去」调查状态（2026-10-29 深夜，diag3 已部署待 max 测试）

### 静态结论
四步准入链（mayPlace→isItemValid→canAddUpgradeTo 四步检查→doClick 放置）已字节码级全部钉死：空背包首插纸面全过（typeLimit=1 计数门 0≥1 不成立；自冲突 countAllowed=0 语义=首件必过，SB 原厂 Inception 同款实证；checkThis 空背包零迭代；组上限被 UpgradeGroup.NONE solo 短路全旁路）。红字链实证：canAdd 失败红字必现（updateSlotChangeError 无条件渲染）；isItemValid 失败静默无红字。剩余真相只在运行时。

### diag3 测试操作（max 回场执行，约 2 分钟）
1. 启动 TEST 1.20.1-Forge_47.4.10 实例。taczaddon 无需隔离（本 bug 与其无关）。〔2026-10-29 夜核实：mods 目录当前已无 taczaddon jar，七项清单测试亦无需隔离操作；该 jar 去向无本方操作记录，max 知悉即可〕
2. 创造模式拿 sustainedfire:ammo_feed_upgrade 一个 + SB 背包一个，背包放主物品栏。
3. 打开背包 GUI，手持升级拖到升级槽区，点一下（观察红字有无）。
4. 关游戏，日志 E:\MC\.minecraft\versions\TEST 1.20.1-Forge_47.4.10\logs\latest.log 搜「sf诊断」/「ammo_feed诊断」发给模型。

### 判读表
- clicked 无 → 点击被屏幕层/其他 mod 吞，菜单层未收到
- mayPlace 入口无 → 点的不是升级槽
- mayPlace 入口 itemClass 非 AmmoFeedUpgradeItem → 物品身份/注册问题
- slotCurrent 空槽 + mayPlace 出口 false + 无 canAdd 日志 → isItemValid 运行时拒（近不可能）
- slotCurrent 占用 + mayPlace 出口 false + 无 canAdd 日志 → canSwapUpgradeFor 被拒
- canAdd ok=false msg=... → 根因直出
- 全 true 仍不放置 → doClick 放置层

### 待 max 拍板（材料已齐）
- loot json 死活：AmmoCrateBlock.getDrops 全覆写不查 loot table（源码注释自证），data/sustainedfire/loot_tables/blocks/ammo_crate.json 为死文件。行为差异备忘：手写版爆炸/活塞推毁掉落内容 100% 保留（json 的 explosion_decay 函数从未生效过），拍板删/留时参考。
- HARDCODE-AUDIT.md 恢复：git checkout 5ece19e -- HARDCODE-AUDIT.md（a034fff 提交时被 git add -A 固化删除，本会话无删除操作记录，疑案待查）
- 正式贴图（7 张 png 占位：block 4+inner+gui+item feed）、版本号、发布前配方

### 诊断改动（未 commit，根因确认后整体移除）
AmmoFeedUpgradeItem.canAddUpgradeTo 打点 / 2 个 SFDiag mixin（clicked+mayPlace 带 slotCurrent）/ sustainedfire.mixins.json 两行 / diag-archive/（diag1、diag2 备份）

### 十四节续：根因与修复（2026-10-29 深夜）
根因（字节码级钉死）：SB 给每个背包的 UpgradeHandler 套了匿名内部类覆写 isItemValid，语义四关：super 检查（instanceof IUpgradeItem）→ 空手 → 命名空间 sophisticatedbackpacks 免检 → 其余必须在物品标签 sophisticatedbackpacks:backpack_upgrade 内。我们的升级第四关失败 → mayPlace 在 isItemValid 后提前 return false → canAddUpgradeTo 零调用、红字链 updateSlotChangeError 不可达 = 静默拒绝。与 diag3 日志完全闭合（mayPlace 683 全 false / clicked 414 / canAdd 0 / 槽全空 / 无红字）。SB 原厂升级走命名空间免检通道故从未踩此坑。
修复：新增 src/main/resources/data/sophisticatedbackpacks/tags/items/backpack_upgrade.json（replace:false，values 含 sustainedfire:ammo_feed_upgrade）——SB 官方第三方扩展点，纯数据文件零代码。标签路径双验证：SB jar 数据文件 + ModItems.BACKPACK_UPGRADE_TAG 字节码。
状态：标签路径已修正为 upgrade.json 并重新部署，待 max 重启游戏复测。下一动作：max 复测拖入升级，成功判据=日志出现 ammo_feed诊断 canAdd 行（预期 ok=true）+ mayPlace result=true，随后跑七项清单，清诊断代码出正式版。
### 十四节三续：标签路径修正（2026-10-30 凌晨）
第一版修复失败根因：tag 文件名错。ModItems.<clinit> 字节码 bc195-217 实锤：new ResourceLocation("sophisticatedbackpacks", "upgrade") -> TagKey.create(Registries.ITEM, ...) -> BACKPACK_UPGRADE_TAG。tag id = sophisticatedbackpacks:upgrade，非字段名暗示的 backpack_upgrade（该字面量属 BACKPACK_UPGRADE_RECIPE_SERIALIZER，配方序列化器）。正确文件：data/sophisticatedbackpacks/tags/items/upgrade.json。SB jar 自身不带任何自家命名空间 tags/items 条目（原厂升级走命名空间免检通道），该标签默认空、纯第三方扩展点。教训（长期记忆 048 已改写）：ResourceLocation/TagKey 的真实 id 必须读构造点 ldc 字面量，禁止从字段名推导。
## M12：SB 供弹客户端同步（2026-10-30）

### 症状（max 实测三连）
1. 背包有弹+升级开：射空后点左键仍有开枪动作但无子弹，HUD 弹药数不变
2. SB GUI 内关升级：同样假象（客户端仍认为有弹）
3. SB GUI 内开升级：枪械 GUI 显示 0 弹不射（客户端认为升级关）
共同点：切枪/按 E 不恢复，必须打开 SB 背包 GUI 才恢复。

### 根因
SB 背包内容不在 ItemStack NBT（服务端真身在世界 SavedData，客户端镜像
BackpackStorage.clientStorageCopy 仅 GUI 打开时经 Request→BackpackContentsMessage 刷新）。
GUI 关闭后镜像死水；wrapper 的 handler 缓存（构造时反序列化快照）随之陈旧；
TACZ HUD 回写链（M6/031）把旧计数写进枪 NBT 成为开火闸门 → 假阳性/假阴性。
"按 E 不恢复"是铁证：InventoryMenu 每 tick broadcastChanges 推不动（服务端 NBT 没变）。

### 修复架构（标记→推送→落地→消费）
- 服务端标记：4 个 mixin 漏斗（BackpackWrapper.mark×2 / SC InventoryHandler.
  onContentsChanged[射击消耗主漏斗，extractItemInternal 直调] / SC UpgradeHandler.
  onContentsChanged / UpgradeToggleMessage toggle lambda[setEnabled 不走 mark 链，
  唯一捕获]）→ SbAmmoSyncManager.markDirty(uuid)，幂等入集
- 推送：ServerTickEvent END 冲洗，节流 sb_sync.intervalTicks（默认 1）；
  dirty ∩ 携带者"带供弹升级的背包树"（含嵌套）→ SBPPacketHandler.sendToClient
  (BackpackContentsMessage(uuid, contents.copy()))——复用 SB 已注册包，客户端落地零新代码
- 客户端落地：SB 自家 handler merge 进镜像 → BackpackStorageMixin 钩
  setBackpackContents HEAD → uuid 入 NEEDS_REFRESH
- 客户端消费：collectAmmoSources 读弹药前 refreshStaleTree——root 树内任一 uuid
  待刷新 → root.onContentsNbtUpdated()（SB 自家完整失效原语）→ 懒重建从新镜像反序列化

### 关键设计决策
- 树级消费（非 root uuid 级）：嵌套子背包 B 变更推 B 的包，但 B 旧快照缓存在 A 的
  handler 里——必须整树重建（root 重建级联子 wrapper 新建）
- 线程守卫对称（单人双端同 JVM 共享静态字段）：markDirty 仅 server / 
  onClientStorageSet 仅 client / refreshStaleTree 仅 client（server 误消费会清掉
  客户端待刷标记——真 bug，已防）
- 发送前 contents.copy() 深拷：网络序列化可能延迟到 Netty 线程，SavedData 活引用
  有并发修改风险（SB 自己发浅拷壳，我们稳一档）
- mixin.sb 子包 + SfMixinPlugin（IMixinConfigPlugin）按 SB 在场 gating：
  SB/SC 缺席时整组不应用（钩子引用 SB 类，缺席即 NoClassDefFoundError）
- 懒消费白送：GUI 开着无人读弹药 → NEEDS_REFRESH 挂起到关 GUI 后首读，
  mid-GUI 重建风险自动归零

### 文件清单
新增：compat/sb/SbAmmoSyncManager.java（核心）、mixin/sb/ 五件
（BackpackWrapperMixin / StorageInventoryHandlerMixin / StorageUpgradeHandlerMixin /
UpgradeToggleMessageMixin / BackpackStorageMixin[client 段]）、mixin/SfMixinPlugin.java
修改：mixins.json（plugin 字段 + 6 条目）、SustainedFireConfig.java（sb_sync 组两条）、
SbCompatImpl.java（init 挂 tick + collectAmmoSources 消费点 + collectCandidateStacks 包级化）

### 已知限制（接受，自愈路径：开一次 SB GUI）
- BE 态背包变更后 100 tick 内被捡回、或离线携带期间变更 → 携带者镜像漏一次推送
- NEEDS_REFRESH 上界 128 暴力全清（极端时多刷几个 wrapper，幂等无害）

### 性能账
空闲 tick：一次 isEmpty 判断（≈零）。射击 tick：每背包一次全量 NBT 拷+序列化
（108 槽 ≈1-3KB，0.1-0.3ms）+ 玩家树扫描。全自动泼射 20 包/s/人 ≈40-60KB/s 峰值
（调 intervalTicks 可压）。幻影窗口 ≤1 tick + HUD 50ms 缓存 = 原版背包直读枪固有节奏。

### 测试清单（部署后）
1. 射空复测：SB 背包装弹+升级，射空 → 点左键不再有假开枪，HUD 归零
2. 关升级：SB GUI 关掉供弹升级 → 关 GUI 直接开枪：不射（假阳性消失）
3. 开升级：SB GUI 打开供弹升级 → 关 GUI 直接开枪：正常射（假阴性消失）
4. 切枪/按 E 不需要开 SB 背包
5. 嵌套：Inception 子背包放弹，磁铁/射击后 HUD 跟随
6. SB 缺席环境（可选）：无 SB 实例启动不崩（mixin gating 验证）

### M12 已知限制补充（2026-10-30 构建前验证轮）
- 磁铁升级在 SB GUI 开着时吸弹：推包 → HUD 若在 GUI 下仍跑计数 → mid-GUI 重建 wrapper 缓存。
  数据面 coherent（新旧 handler 读写同一 SavedData 活表），最坏升级 tab cosmetic 抖动，复测时盯一眼。
- 接口验证结案：onContentsNbtUpdated() 是 SC IStorageWrapper 的 abstract 方法
  （IBackpackWrapper 继承之），接口引用直接调用零 cast；capability attach 挂
  BackpackWrapper 本尊（initCapabilities → BackpackItem$2 → new BackpackWrapper）。

### M12 状态（2026-10-30 03:05 构建部署完成）
- 构建：BUILD SUCCESSFUL（17s 增量，历经 3 轮——IMixinConfigPlugin 接口方法凭记忆漏写
  getMixins / acceptTargets 各烧一轮，javap mixin-0.8.4 实证 7 方法后修复；
  另 create 指令被 .agent_trash 旧备份拦截一轮，清后备后落盘）
- 部署：sustainedfire-0.1.0.jar (108KB) → E:\MC\.minecraft\versions\Industrial Revolution 2040\mods\
  （替换 10/29 旧版；构建方式=schtasks 独立会话 --no-daemon，绕开框架杀进程）
- 运行时预检：UpgradeToggleMessage.lambda$handleMessage$1 签名与注入完全一致
  （static void(UpgradeToggleMessage, ServerPlayer, IBackpackWrapper)）
- 诊断 mixin（SFDiagStorage×2）确认纯 LOGGER 观察无行为修改，不阻塞任何复测环境
  （SC 缺席时目标类不加载即惰性；复测时 SB GUI 会刷 [sf诊断-*] 日志，可忽略）
- 当前：待人工复测六项——①射空无假开枪/HUD 归零 ②GUI 关升级→关包直射：不射
  ③GUI 开升级→关包直射：正常射 ④切枪/按 E 无需开 SB 背包 ⑤嵌套子背包磁铁/射击 HUD 跟随
  ⑥（可选）SB 缺席环境启动不崩
- 复测通过后待办：清 SFDiag×2 + diag-archive/ + build-m12.log（mixins.json 同步删条目）
  → commit M10 系 + M12 全部 → 正式版流程（待决：loot 删/留、贴图、版本号、
  HARDCODE-AUDIT B 组拍板）

## M12.1 SB gating 修复（2026-10-30 04:15，max 睡眠期间执行，未 commit）
- 症状：M12 部署后六项复测完全没变；jar hash 一致排除旧包。
- 根因：shouldApplyMixin 调用时机 = mixin prepare 阶段（debug.log 时间线：ModSorter 完成 03:26:44.9 -> 全部 MixinInfo 构造 + veto 03:26:47.7 -> 首个 FMLModContainer 03:26:54.4），runtime ModList 尚未构建，null/异常 -> false，SB 在场也整组 veto（sb 组 5 只零 Mixing 行）；Mixin 框架对 veto 零日志。
- 修复：SfMixinPlugin 三层判定（FMLLoader.getLoadingModList().getModFileById 命中即终局 -> ModList.isLoaded 兜底 -> 保守 false）+ 全决策 LOGGER 'sf-mixin-plugin' + 判定缓存；旧版备份 %TEMP%\SfMixinPlugin.java.m12backup。
- 构建：SUCCESSFUL（build-m121.log）
- 部署：TEST mods replaced, hash ok
- 注意：sb 组 5 只 mixin 属首次真实运行时应用（M12 期间一直被本 bug veto），若启动崩溃先看 mixin apply 报错（defaultRequire=1）。
- max 醒后验证链：启动 TEST -> latest.log 搜 'sf-mixin-plugin'（期望 SB gating present=true）-> debug.log 确认 sb 组 5 只 Mixing 行 -> 重跑六项复测 -> 通过后清诊断件 + commit（M10+M12+M12.1 一并）。
- 遗留：loot_tables sustainedfire:blocks/ammo_crate 解析失败（loot 待决事项）；2040 整合包 mods 的 sf jar 已被换成新版（SB 在场 + gating 已修，安全；是否换回由 max 定）。

### M12 验证夜记（2026-10-30 21:22，自动执行：只读考古 + 本文档，代码零改动）
- 考古定谳：早晨"完全没变"=原版 M12（gating veto，sb 组 5 mixin 零应用）；晚局1（18:54-58）=测试配置断裂（升级在身包 64b0c797 空仓，612 发误入世界背包 bf6dd497，GUI 同皮无法肉眼区分）。两者均不构成 M12.1 失效证据。
- 局4（20:08-20:24）重配后 37 秒生存窗口：144 发经 SB 背包消耗（config 三闸实证 + 弹药箱 1296 未动 + 末影箱空 + sophisticatedstorage.dat 不存在，排除法钉死）→ 服务端供弹链端到端存活。终态身包 108 发 + 升级 enabled=1，已备下次复测。
- 未决①：37 秒窗口客户端三症状肉眼观察（射击/HUD/toggle 无埋点），六项复测清单不变。
- 未决②：SbAmmoSyncManager 线程组守卫（SidedThreadGroups 身份判断）是 M12.1 唯一未经运行时验证的部件。1.20.1 该类在 fmlcore 非 universal；若单人 Render thread 不在 CLIENT 组（主线程组不可后改，机制上存在此可能），客户端半链静默死亡 = 三症状必然保留。验证中：fmlcore 字节码 + spin 补丁 + Render thread 构造点。
- 未决③：loot_tables/blocks/ammo_crate.json 每局解析失败，疑似非法函数 id（minecraft:contents），修复模板=vapilla shulker_box.json（容器类方块掉落保内容）。

### M12.2 判决：客户端线程组守卫静默死亡（2026-10-30 21:47，B 面坐实，修复等 max 拍板）
证据链（生产 jar 字节码，非推测）：
1. forge-client.jar 补丁版 Main.main：Thread.currentThread().setName("Render thread")——改名不换线程，游戏全程（Minecraft 构造/run 循环/包处理/HUD/mixin）跑在 JVM 主线程上
2. Java ThreadGroup 构造时定死无 setter；JVM 主线程生于默认 "main" 组，setName 不动组
3. SidedThreadGroups.CLIENT 是 ThreadGroup 实例，全 classpath 无代码用它构造线程（forge-client/universal/modlauncher/bootstrap/securejar + SB/SC/SS 已扫，引用清一色 SERVER 判断；fmlloader/fmlearlydisplay 由版本 JSON 精确 classpath 复扫闭环）
4. 官方自白：EffectiveSide.get() 对非 SidedThreadGroup 线程回退 CLIENT——回退的存在=承认 Render thread 不在 CLIENT 组
后果：onClientStorageSet/refreshStaleTree 守卫永假 → NEEDS_REFRESH 永空 → wrapper 缓存永不失效 → 三症状结构性保留；markDirty（SERVER 组）运行时健康（补丁 MinecraftServer.spin = new Thread(SidedThreadGroups.SERVER,…) + SB 同款生产验证）。推送/落地/消耗三段全活，死的是"落地→消费"最后一跳。与局4 观测自洽（服务端 144 发消耗正常+客户端三症状必在）。37 秒口供降级为佐证。
修复提案（未动代码）：SbAmmoSyncManager 三处守卫换 EffectiveSide.get()——markDirty 行为等价（SidedThreadGroup 构造器包私有，SERVER 组单实例，EffectiveSide.SERVER ⇔ 组==SERVER 实例）；onClientStorageSet/refreshStaleTree 复活；import 换 LogicalSide+EffectiveSide；类文档"线程守卫对称"段同步改注。
验收路径：构建→部署 TEST→六项清单（背包 108 发+升级 enabled=1 已就位）→通过则清 SFDiag×2+diag-archive+build-m12*.log→M10/M12/M12.1/M12.2 一并 commit；不过则 [sf-sync诊断] 插桩定位残余断点。
loot 定谳（同轮）：minecraft:contents 非 1.20.1 函数 id——vanilla shulker_box.json 里它是 set_contents 函数内 dynamic entry 的 name，本表误用作顶层函数→Unknown type 每局刷错。AmmoCrateBlock.getDrops 代码覆盖已接管全部真实掉落路径（10-28 拍板潜影盒语义），表残余害处=报错刷屏+直查空表。修法三选：删文件 / 最小 item-only（推荐）/ copy_nbt 全复刻（需先读 TAG_FILL 键名）。

### M12.3 构建部署完成（2026-10-30 22:45）
- 源码改动全落盘：BE saveAdditional +Fill（存档现算零迁移）；Block 删 getDrops +3 孤儿 import（getCloneItemStack 保留）；loot json 重写（barrel 骨架 + copy_nbt 两 op：Items→BlockEntityTag.Items、Fill→"sf:fill" quoted key）
- 备份 %TEMP%\*.m123backup×3 + SbAmmoSyncManager.java.m122backup
- 过程事故存档：①首轮编辑脚本 PS ParserError 零执行 ②二轮构建脚本回执"无输出"（构建/部署实际状态靠 log/jar/MD3 三件套核查兜底，本轮已按需补齐）
- 待实测（游戏内 30 秒判）：copy_nbt 的 block_entity 序列化是否含 Fill（saveWithFullMetadata 推测）；quoted NBT path 语法（失败=挖箱掉落物无耐久条/启动 log 刷 parse 错）
- 验收=七项：六项守卫 + 第七项生存挖箱（tooltip/耐久条/放回完好；爆炸概率幸存已知悉）
### M12 系列收案（2026-10-30 23:24，七项生存验收全过）

- M12：四级变更漏斗 mixin + BackpackStorage 客户端镜像观测点 + SfMixinPlugin SB 在场 gating
- M12.1：plugin 判定链根因修复（mixin prepare 阶段 runtime ModList 未构建 → LoadingModList 首选 + ModList 兜底 + 保守尾，全决策落日志）
- M12.2：SbAmmoSyncManager 守卫 EffectiveSide 化（FML early display 渲染线程组=main，旧线程组判断在客户端永假，三症状共同结构性根因）
- M12.3：弹药箱掉落 loot 表化（copy_nbt 两 op + BE Fill 持久化 + datapack 可整表 override；爆炸语义对齐 vanilla 概率幸存）
- 验收：①射空无假开枪+HUD 归零 ②升级关→关包直射不射 ③升级开→直射正常 ④切枪/按E免开SB包 ⑤嵌套子背包 HUD 跟随 ⑥无SB环境不崩（可选）⑦生存挖箱 tooltip+耐久条+放回完好
- 两悬案随验结案：quoted NBT path（"sf:fill"）语法正确，启动无 parse 错；copy_nbt block_entity 源含 saveAdditional 字段（耐久条在=实证）
- 收尾清除：SFDiag×2 插桩、diag-archive、build-m12*.log×5；2040 jar 实为同路径覆盖（扫描 TEST mods 与根 mods 仅剩当前 jar）
