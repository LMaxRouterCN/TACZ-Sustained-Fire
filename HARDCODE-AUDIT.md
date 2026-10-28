# 硬编码与魔法数字盘点表

> 2026-10-29 凌晨，三连任务 + 附魔数值配置化完成后的全量盘点。
> "max 拍板"列留空——每一行你来定，我只给建议和理由。
> 建议"进"的都列了候选配置键名；建议"不进"的写了理由，不同意就划掉。

## A. 本轮已进配置（确认即可）

| 配置键（toml） | 默认值 | 原硬编码位置 |
|---|---|---|
| supply.expandContainerItems | false | AmmoSourceResolver.buildSources（原无条件展开） |
| ammo_crate.radius | 4.0（1.0–64.0） | 原已有 |
| ammo_crate.backpackUsable | false | 原已有，本轮接线生效 |
| ammo_crate.crateAmmoOnly | false | AmmoCrateBlockItem.isDepositable（原硬编码只认 IAmmo） |
| enchantments.costBase / costStep / costSpan | 5 / 8 / 10 | SustainedFireEnchantment.getMinCost / getMaxCost |
| enchantments.cooling_device.maxLevel | 10 | 构造参数 |
| enchantments.cooling_device.perLevelReduction | 0.10 | CoolingDeviceEnchantment 常量 |
| enchantments.air_cooling.maxLevel | 5 | 构造参数 |
| enchantments.air_cooling.thresholdRpm | 600 | AirCoolingEnchantment 常量 |
| enchantments.air_cooling.perRpm | 0.002 | 同上 |
| enchantments.air_cooling.floor | -1.0 | 同上 |
| enchantments.belt_feed.maxLevel | 1 | 构造参数 |
| enchantments.preheat.maxLevel | 5 | 构造参数 |
| enchantments.preheat.perLevelBonus | 0.40 | PreheatEnchantment 常量 |
| enchantments.progressive_trigger.maxLevel | 5 | 构造参数 |
| enchantments.progressive_trigger.perLevelBonus | 0.15 | ProgressiveTriggerEnchantment 常量 |
| enchantments.progressive_trigger.rampUpTimeMs | 3000 | 同上 |
| enchantments.progressive_trigger.burstGapMs | 2000 | FireSessionTracker.BURST_GAP_MS |

注册期安全说明：全部经 intOr/doubleOr/booleanOr 兜底，COMMON 配置未加载时返回默认值，注册/构造期读取不炸（creative tab 的 getMaxLevel 调用即走此路径）。

## B. 待拍板项

| # | 位置 | 值 | 用途 | 我的建议 | 理由 | max 拍板 |
|---|---|---|---|---|---|---|
| 1 | FireSessionTracker.CLEANUP_THRESHOLD | 256 | 会话表惰性清扫阈值 | 不进 | 纯内存卫生，玩家改它无收益无意义 | |
| 2 | AmmoCrateBlockItem.TOOLTIP_MAX_LINES | 8 | tooltip 内容聚合行上限 | 可进（低优先级） | UI 数值，有玩家想看全量；键名 ammo_crate.tooltipMaxLines | |
| 3 | AmmoCrateBlockItem.getBarColor | 0x55FF55 | 耐久条颜色 | 不进 | 美术值，对齐 tacz 弹药盒；要改该跟美术整体走 | |
| 4 | AmmoCrateBlockItem.getBarWidth | 13（px） | 耐久条满宽 | 不进 | vanilla 耐久条标准宽度，改了显示错位 | |
| 5 | AmmoCrateMenu.stillValid | 64.0（=8格²） | GUI 有效距离 | 可进（低优先级） | vanilla BaseContainerBlockMenu 同款口径；不动也成立 | |
| 6 | AmmoCrateOutlineRenderer.RESCAN_INTERVAL_TICKS | 10 | 黄线框重扫描间隔 | 不进 | 若要动，正确方向是事件化（BE 内容/开盖变化时主动失效缓存），不是把轮询间隔做成旋钮 | |
| 7 | BlockItem 音效参数 | 0.8F / +0.4F 随机 | 装箱/取弹音量音高 | 不进 | vanilla BUNDLE 音效原版参数 | |
| 8 | GunHudOverlayMixin | 9999 | HUD 弹药计数显示上限 | 不进 | TACZ 原版口径 | |
| 9 | AmmoCrateBlockEntity.CONTAINER_SIZE | 36 | 容器规格 | 不进 | 与 GUI 贴图（4 行槽位）、quickMoveStack 分区、blockstate 8 级、耐久条分母全链联动；改它=数据包+贴图+模型工程，不是 config 旋钮 | |
| 10 | contentLevel 满级 8 | 8 | 内容物高度等级 | 不进 | 同上，blockstate + 模型美术管线联动 | |
| 11 | 附魔 Rarity 分配 | COMMON/UNCOMMON/RARE | 附魔台权重/交易定价 | 不进 | 玩法设计锚点；真要调该整体设计层调 | |
| 12 | BeltFeed isTreasureOnly/isTradeable/isDiscoverable | true/false/true | 宝藏属性 | 不进 | 设计锚点 | |
| 13 | GunDataMixin heatPct 钳制 | 0–1 | 数值卫生 | 不进 | 防御性钳制，非玩法数值 | |
| 14 | SustainedFireEnchantment 费用曲线 | — | 已进 config（见 A 组） | 完成 | | |
| 15 | 主类 LOGGER 彩蛋 | 《Swamp Thing》 | 开机日志 | 不动 | 该 | |

## C. 非本模组的值（记录备查，动不了）

- TACZ 引擎天花板：shoot() coolDown ≥50ms + GunData 内 clamp(rpm,1,1200) —— 预热/渐进的实际射速上限在这，非我们代码。
- chunk 网格 >>4（=16）—— Minecraft 世界常量。
- vanilla Item 耐久条 13px、GUI 槽位 18px 间距 —— 贴图规格。