# TACZ: 持续火力

[TACZ](https://github.com/MCModderAnchor/TACZ)的附属模组

### 附魔

下表均为默认值，全部数值可在配置文件中调整。

| ID | 名称 | 上限 | 效果 |
|---|---|---|---|
| `sustainedfire:belt_feed` | 弹链升级 | 1 | 射击直接消耗全部已解析弹药源，免换弹。宝藏附魔 |
| `sustainedfire:preheat` | 预热 | 5 | 过热值越高射速越快（有热量数据的枪），满热度时每级 +40% 射速 |
| `sustainedfire:progressive_trigger` | 渐进射速 | 5 | 持续射击时射速渐增：连续射击 3 秒后每级 +15% 射速 |
| `sustainedfire:cooling_device` | 冷却装置 | 10 | 每级降低 10% 单发热量积累，10 级时枪械永不过热 |
| `sustainedfire:air_cooling` | 风冷 | 5 | 射速超 600 RPM 后，每级按超出部分每 rpm 减免 0.2% 热量，可为负,射击减少热量 |

### 弹药箱

36 格放置式弹药容器，对附近枪械供弹，是整条补给链的核心。

- **开盖状态下对半径内（默认 4 格）的枪供弹**，持枪者换弹与弹链射击均直接取弹
- **黄线框 = 正在为你供弹的箱子。** 手持 TACZ 枪械时，半径内、开盖且含有该枪可用弹药的箱子会被黄色轮廓标出，所见即供弹；空箱与关盖箱不高亮
- **箱内存放的容器物品（潜影盒、背包等）同样参与供弹**，默认开启，可配置
- 物品态弹药箱放在背包里时同样可作为弹药源，默认关闭，可配置

### 精妙背包联动（可选）

安装精妙背包模组后可用**供弹升级**：

- 无序合成：TACZ 铁弹药盒+ 空白升级
- 装入背包后，枪械直接从背包内容物中取用弹药，可在背包 GUI 的升级设置中开关；同一背包限装一个
- 背包内容变更时主动推送给携带者客户端，GUI 外弹药计数与升级开关状态保持准确
- 未安装 SB 时升级物品与配方整体不注册

佩戴在 Curios 饰品槽的背包同样支持（Curios 为独立软依赖）。

### 弹药源解析

弹链射击与换弹的弹药从以下组合视图解析：

1. 玩家背包（原版行为）
2. 背包内的容器物品——潜影盒等一切暴露存储的物品
3. 启用供弹升级的精妙背包
4. Curios 饰品槽佩戴的背包
5. 半径内的放置态弹药箱（开盖）
6. 弹药箱内部的容器物品

### 配置文件

配置文件位于 `sustainedfire-common.toml`（COMMON 配置，不跨端同步，多人服务端需两端保持一致）。

```
[supply]
	#Expand container items (shulker boxes, ...) inside the shooter's inventory as ammo sources.
	#When enabled, non-upgraded Sophisticated Backpacks also feed through this switch;
	#backpacks with an active Ammo Feed upgrade are governed by the upgrade alone (no double counting).
	#Default false.
	#供弹时是否展开玩家背包内的容器物品（潜影盒等一切自带存储格的物品）为弹药源。
	#开启后，未装升级的精妙背包同样经本开关供弹；
	#装启用供弹升级的背包由升级独占，弹药不重复计算；默认 false。
	expandContainerItems = false

[ammo_crate]
	#Ammo crate item in the player inventory feeds guns as an ammo source (fully replaces TACZ's own ammo boxes).
	#Its 36-slot storage is always accessible to other mods (e.g. item pipes), regardless of this switch.
	#物品态弹药箱是否作为弹药源参与供弹（完全替代 TACZ 本体弹药盒）。
	#其 36 格存储始终可被其他模组（如管道）读写，不受本开关影响。
	backpackUsable = false
	#Search radius (blocks) for placed ammo crates when resolving reload / belt-feed ammo.
	#放置态弹药箱作为换弹/弹链弹药源的查找半径（格）。
	#Range: 1.0 ~ 64.0
	radius = 4.0
	#true = ammo crates (placed / item form / cursor / hoppers) only accept TACZ ammo and ammo boxes.
	#true = 弹药箱（放置态/物品态/光标交互/漏斗）只允许装入 TACZ 弹药与弹药盒。
	crateAmmoOnly = false
	#Whether placed ammo crates also feed guns from container items inside them
	#(shulker boxes, backpacks - anything with internal storage).
	#Item-form ammo crates placed inside also feed guns (a crate inside a crate).
	#放置态弹药箱是否把内部容器物品（潜影盒、背包等一切自带存储格的物品）展开为弹药源。
	#放入箱内的物品态弹药箱同样参与供弹（箱中箱）。
	expandContainersInside = true

[sb_sync]
	#Push SB backpack contents to the carrier's client whenever they change, so ammo count / upgrade toggle stay in sync outside the SB GUI. Fixes: ghost firing after emptying, stale HUD, toggle not taking effect.
	#SB 背包内容变更时主动推送给携带者客户端，GUI 外弹药计数/升级开关保持同步。
	#修复：射空后假开枪、HUD 计数不刷新、GUI 内开关升级不生效（须重开背包才恢复）。
	enabled = true
	#Push interval in server ticks (1 = every tick, lowest latency; raise to save bandwidth on busy servers).
	#推送间隔（服务端 tick）：1 = 每 tick 同步一次，延迟最低；调大可节省带宽。
	#Range: 1 ~ 20
	intervalTicks = 1

[enchantments]
	#Enchantment cost curve: min cost = costBase + costStep * (level - 1), max = min + costSpan.
	#附魔经验费用曲线：min = costBase + costStep*(等级-1)，max = min + costSpan（全部附魔共享）。
	#Range: 0 ~ 100
	costBase = 5
	#Per-level cost increment.
	#每级费用增量。
	#Range: 0 ~ 100
	costStep = 8
	#Span between min and max cost at the same level.
	#同等级 min 与 max 的跨度。
	#Range: 0 ~ 100
	costSpan = 10

	[enchantments.cooling_device]
		#Max level. At 10 with default reduction the gun never overheats.
		#最大等级。默认削减下 10 级枪械免过热。
		#Range: 1 ~ 10
		maxLevel = 10
		#Heat-per-shot reduction per level (0.10 = -10%/level).
		#每级对单发热量增速的削减比例（0.10 = 每级 -10%）。
		#Range: 0.0 ~ 1.0
		perLevelReduction = 0.1

	[enchantments.air_cooling]
		#Max level.
		#最大等级。
		#Range: 1 ~ 10
		maxLevel = 5
		#RPM threshold above which the reduction kicks in.
		#射速超过该阈值（rpm）后开始产生减免。
		#Range: 0 ~ 1200
		thresholdRpm = 600
		#Per-level reduction per RPM over the threshold.
		#每级每超出 1 rpm 的减免比例。
		#Range: 0.0 ~ 1.0
		perRpm = 0.002
		#Lower bound of the heat multiplier. Negative = shooting actively cools the gun.
		#热量缩放系数下限。负值 = 射击时主动散热。
		#Range: -10.0 ~ 1.0
		floor = -1.0

	[enchantments.belt_feed]
		#Max level.
		#最大等级。
		#Range: 1 ~ 10
		maxLevel = 1

	[enchantments.preheat]
		#Max level.
		#最大等级。
		#Range: 1 ~ 10
		maxLevel = 5
		#RPM bonus per level at 100% heat (0.40 = +40%/level).
		#满热度时每级射速加成（0.40 = 每级 +40%）。
		#Range: 0.0 ~ 10.0
		perLevelBonus = 0.4

	[enchantments.progressive_trigger]
		#Max level.
		#最大等级。
		#Range: 1 ~ 10
		maxLevel = 5
		#RPM bonus per level at full ramp (0.15 = +15%/level, weaker than preheat).
		#满渐变时每级射速加成（0.15 = 每级 +15%，低于预热）。
		#Range: 0.0 ~ 10.0
		perLevelBonus = 0.15
		#Continuous-fire time (ms) to reach the full bonus.
		#连射积累到满加成所需时长（毫秒）。
		#Range: 100 ~ 600000
		rampUpTimeMs = 3000
		#Gap (ms) after which continuous fire is considered a new burst.
		#距上一发超过该时长（毫秒）视为新连射。
		#Range: 100 ~ 600000
		burstGapMs = 2000
```

### 技术说明

- Forge **1.20.1** (47.4.10)，TACZ **1.1.8-hotfix2**。精妙背包与 Curios 为可选依赖（对应 mixin 类条件加载）。
- 枪械物品零 NBT / 数据包篡改。全部行为由 10 个 mixin 类交付：TACZ 侧 5 个——`AbstractGunItem`（4 处注入）、`GunData`、`GunHudOverlay`、`LivingEntityShoot`、`ModernKineticGunScriptAPI`（2 处注入）；SB 侧 5 个（内容变更推送同步）。
- `AbstractGunItem` 的注入为 `@Inject`-RETURN 兜底而非 `@Redirect`：占用返回指令而非 `getCapability` 调用点，与重定向同调用点的第三方 mod（如 taczaddon）正交共存；背包命中即短路，不进箱扫描。
- 连射会话跟踪为双端（客户端预测 + 服务端权威），基于 `GunFireEvent`。

### 已知限制

- Lua 脚本覆写 `handle_shoot_heat` 的枪不享受冷却/风冷缩放（绝大多数枪走默认路径）。
- 换弹动画的弹药判定仍只查裸背包——仅影响动画表现，不影响实际供弹。
- COMMON 配置不跨端同步，多人服务端需保证两端配置文件一致。
- tacz的引擎限制每个tick只能发射一次,所以枪械的射速最高就是1200rpm,超过1200rpm会被限制到1200rpm

### 构建

```
.\gradlew build
```

需要 Java 17，依赖仓库内 `libs/` 中的三个 jar（TACZ、精妙背包、精妙核心）。

### 许可

[MPL-2.0](LICENSE)

---

# TACZ: Sustained Fire

## English

An addon for [TACZ (Timeless and Classics Zero)](https://github.com/MCModderAnchor/TACZ).

### Enchantments

All numbers below are defaults; every one of them lives in the config.

| ID | Name | Max | Effect |
|---|---|---|---|
| `sustainedfire:belt_feed` | Belt Feed | 1 | Shoot straight from all resolved ammo sources — no magazine reload. Treasure enchantment. |
| `sustainedfire:preheat` | Preheat | 5 | Higher heat = faster fire rate (heat-based guns), up to +40% RPM per level at full heat. |
| `sustainedfire:progressive_trigger` | Progressive Trigger | 5 | Fire rate ramps up during sustained fire: +15% RPM per level after 3 s of continuous shooting. |
| `sustainedfire:cooling_device` | Cooling Device | 10 | -10% heat per shot per level; at level 10 the gun never overheats. |
| `sustainedfire:air_cooling` | Air Cooling | 5 | Above 600 RPM, each level cuts heat gain by 0.2% per RPM over the threshold — can go negative (shooting cools the gun). |

### Ammo Crate

A 36-slot placeable ammo container that feeds nearby guns — the centerpiece of the supply chain.

- **Feeds guns within the radius (default 4 blocks) while open.** Held guns draw ammo directly from it — reload or belt feed.
- **Yellow outline = this crate is feeding you right now.** While holding a TACZ gun, crates that are in range, open, and actually contain usable ammo for that gun get a yellow wireframe — what you see is what feeds you. Empty or closed crates stay unlit.
- **Container items inside the crate (shulker boxes, backpacks, …) feed too** — default on, configurable.
- The item form can also act as an ammo source while sitting in your inventory — default off, configurable.

### Sophisticated Backpacks Integration (optional)

With SB installed, an **Ammo Feed Upgrade** becomes available:

- Crafted shapeless from a TACZ iron ammo box (`Level:0`) + an SB upgrade base.
- Install it into a backpack and guns draw ammo directly from the backpack's contents — toggle it in the backpack GUI's upgrade settings. One per backpack.
- Backpack contents are pushed to the carrier's client on change, so ammo counts and upgrade toggles stay accurate outside the SB GUI.
- Without SB the upgrade item and its recipe are never registered.

Backpacks worn in Curios slots are supported as well (Curios is a separate soft dependency).

### Ammo source resolution

Both belt-feed shooting and reloading resolve ammo from the following combined view:

1. The player's inventory (vanilla behavior)
2. Container items in the inventory — shulker boxes and anything else exposing internal storage
3. Backpacks with an active Ammo Feed upgrade
4. Backpacks worn in Curios slots
5. Placed ammo crates in range (open lid)
6. Container items inside those crates

### Configuration

The config file lives at `sustainedfire-common.toml` (COMMON config — not synced between client and server; keep both sides consistent on multiplayer servers).

```
[supply]
	#Expand container items (shulker boxes, ...) inside the shooter's inventory as ammo sources.
	#When enabled, non-upgraded Sophisticated Backpacks also feed through this switch;
	#backpacks with an active Ammo Feed upgrade are governed by the upgrade alone (no double counting).
	#Default false.
	#供弹时是否展开玩家背包内的容器物品（潜影盒等一切自带存储格的物品）为弹药源。
	#开启后，未装升级的精妙背包同样经本开关供弹；
	#装启用供弹升级的背包由升级独占，弹药不重复计算；默认 false。
	expandContainerItems = false

[ammo_crate]
	#Ammo crate item in the player inventory feeds guns as an ammo source (fully replaces TACZ's own ammo boxes).
	#Its 36-slot storage is always accessible to other mods (e.g. item pipes), regardless of this switch.
	#物品态弹药箱是否作为弹药源参与供弹（完全替代 TACZ 本体弹药盒）。
	#其 36 格存储始终可被其他模组（如管道）读写，不受本开关影响。
	backpackUsable = false
	#Search radius (blocks) for placed ammo crates when resolving reload / belt-feed ammo.
	#放置态弹药箱作为换弹/弹链弹药源的查找半径（格）。
	#Range: 1.0 ~ 64.0
	radius = 4.0
	#true = ammo crates (placed / item form / cursor / hoppers) only accept TACZ ammo and ammo boxes.
	#true = 弹药箱（放置态/物品态/光标交互/漏斗）只允许装入 TACZ 弹药与弹药盒。
	crateAmmoOnly = false
	#Whether placed ammo crates also feed guns from container items inside them
	#(shulker boxes, backpacks - anything with internal storage).
	#Item-form ammo crates placed inside also feed guns (a crate inside a crate).
	#放置态弹药箱是否把内部容器物品（潜影盒、背包等一切自带存储格的物品）展开为弹药源。
	#放入箱内的物品态弹药箱同样参与供弹（箱中箱）。
	expandContainersInside = true

[sb_sync]
	#Push SB backpack contents to the carrier's client whenever they change, so ammo count / upgrade toggle stay in sync outside the SB GUI. Fixes: ghost firing after emptying, stale HUD, toggle not taking effect.
	#SB 背包内容变更时主动推送给携带者客户端，GUI 外弹药计数/升级开关保持同步。
	#修复：射空后假开枪、HUD 计数不刷新、GUI 内开关升级不生效（须重开背包才恢复）。
	enabled = true
	#Push interval in server ticks (1 = every tick, lowest latency; raise to save bandwidth on busy servers).
	#推送间隔（服务端 tick）：1 = 每 tick 同步一次，延迟最低；调大可节省带宽。
	#Range: 1 ~ 20
	intervalTicks = 1

[enchantments]
	#Enchantment cost curve: min cost = costBase + costStep * (level - 1), max = min + costSpan.
	#附魔经验费用曲线：min = costBase + costStep*(等级-1)，max = min + costSpan（全部附魔共享）。
	#Range: 0 ~ 100
	costBase = 5
	#Per-level cost increment.
	#每级费用增量。
	#Range: 0 ~ 100
	costStep = 8
	#Span between min and max cost at the same level.
	#同等级 min 与 max 的跨度。
	#Range: 0 ~ 100
	costSpan = 10

	[enchantments.cooling_device]
		#Max level. At 10 with default reduction the gun never overheats.
		#最大等级。默认削减下 10 级枪械免过热。
		#Range: 1 ~ 10
		maxLevel = 10
		#Heat-per-shot reduction per level (0.10 = -10%/level).
		#每级对单发热量增速的削减比例（0.10 = 每级 -10%）。
		#Range: 0.0 ~ 1.0
		perLevelReduction = 0.1

	[enchantments.air_cooling]
		#Max level.
		#最大等级。
		#Range: 1 ~ 10
		maxLevel = 5
		#RPM threshold above which the reduction kicks in.
		#射速超过该阈值（rpm）后开始产生减免。
		#Range: 0 ~ 1200
		thresholdRpm = 600
		#Per-level reduction per RPM over the threshold.
		#每级每超出 1 rpm 的减免比例。
		#Range: 0.0 ~ 1.0
		perRpm = 0.002
		#Lower bound of the heat multiplier. Negative = shooting actively cools the gun.
		#热量缩放系数下限。负值 = 射击时主动散热。
		#Range: -10.0 ~ 1.0
		floor = -1.0

	[enchantments.belt_feed]
		#Max level.
		#最大等级。
		#Range: 1 ~ 10
		maxLevel = 1

	[enchantments.preheat]
		#Max level.
		#最大等级。
		#Range: 1 ~ 10
		maxLevel = 5
		#RPM bonus per level at 100% heat (0.40 = +40%/level).
		#满热度时每级射速加成（0.40 = 每级 +40%）。
		#Range: 0.0 ~ 10.0
		perLevelBonus = 0.4

	[enchantments.progressive_trigger]
		#Max level.
		#最大等级。
		#Range: 1 ~ 10
		maxLevel = 5
		#RPM bonus per level at full ramp (0.15 = +15%/level, weaker than preheat).
		#满渐变时每级射速加成（0.15 = 每级 +15%，低于预热）。
		#Range: 0.0 ~ 10.0
		perLevelBonus = 0.15
		#Continuous-fire time (ms) to reach the full bonus.
		#连射积累到满加成所需时长（毫秒）。
		#Range: 100 ~ 600000
		rampUpTimeMs = 3000
		#Gap (ms) after which continuous fire is considered a new burst.
		#距上一发超过该时长（毫秒）视为新连射。
		#Range: 100 ~ 600000
		burstGapMs = 2000
```

### Technical notes

- Forge **1.20.1** (47.4.10), TACZ **1.1.8-hotfix2**. Sophisticated Backpacks and Curios are optional (mixin classes targeting them load conditionally).
- Zero NBT / data-pack tampering with gun items. Behavior is delivered by 10 mixin classes: 5 into TACZ — `AbstractGunItem` (4 injections), `GunData`, `GunHudOverlay`, `LivingEntityShoot`, `ModernKineticGunScriptAPI` (2 injections) — and 5 into SB (content-change push sync).
- The `AbstractGunItem` injections are `@Inject`-at-RETURN fallbacks rather than `@Redirect`s: they occupy the return instruction instead of the `getCapability` call site, so they are orthogonal to — and coexist with — third-party mods that redirect the same calls (e.g. taczaddon). Inventory hits short-circuit before any crate scan.
- Fire-session tracking is dual-side (client prediction + server authoritative) via `GunFireEvent`.

### Known limitations

- Guns whose Lua script overrides `handle_shoot_heat` skip cooling/air-cooling scaling (most guns use the default path).
- The reload-animation ammo check (`hasAmmoToConsume`) still looks at the bare inventory only — affects animation timing, not actual supply.
- COMMON config is not synced between client and server; keep both sides' files consistent on multiplayer servers.
- TACZ's engine fires at most one shot per tick, so all fire rates are capped at 1200 RPM — anything above is clamped to 1200.

### Build

```
./gradlew build
```

Requires Java 17 and the three dependency jars in `libs/` (TACZ, Sophisticated Backpacks, Sophisticated Core) — included in the repo.

### License

[MPL-2.0](LICENSE)
