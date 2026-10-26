# TACZ: Sustained Fire / 持续火力

A Forge addon for [TACZ (Timeless and Classics Zero)](https://github.com/MCModderAnchor/TACZ) that adds 5 gun enchantments pushing sustained-fire gameplay to the limit.

TACZ（时代与经典 Zero）的 Forge 附属模组：5 个枪械附魔，把连射玩法推到极限。

## Enchantments / 附魔一览

| ID | Name / 名称 | Effect / 效果 |
|---|---|---|
| `sustainedfire:belt_feed` | Belt Feed / 弹链升级 | Shoot straight from inventory ammo — no magazine reload. / 射击直接消耗背包弹药，免换弹 |
| `sustainedfire:preheat` | Preheat / 预热 | Higher heat = faster fire rate (heat-based guns). / 过热值越高射速越快（有热量数据的枪） |
| `sustainedfire:progressive_trigger` | Progressive Trigger / 渐进射速 | Fire rate ramps up the longer you keep shooting. / 持续射击一段时间后射速渐增 |
| `sustainedfire:cooling_device` | Cooling Device / 冷却装置 | Each level: -10% heat per shot. / 每级降低 10% 单发热量 |
| `sustainedfire:air_cooling` | Air Cooling / 风冷 | Above 600 RPM, each level further reduces heat gain — can go negative (shooting cools the gun). / 射速超 600 RPM 部分线性减免热量，可为负（越打越冷） |

## How to obtain / 获取方式

Enchanted books + anvil on any TACZ gun. `/enchant` also works (enchantability is gate-kept to TACZ guns only).

对任意 TACZ 枪械使用附魔书 + 铁砧；`/enchant` 指令同样可用（仅对 TACZ 枪械生效）。

## Ammo source expansion / 弹药源扩展（Belt Feed 的弹药从哪来）

With `belt_feed`, consumption is resolved from a **combined view** of:

1. Player inventory (vanilla behavior) / 玩家背包（原版行为）
2. **Container items** placed in the inventory — Sophisticated Backpacks and anything else exposing `ITEM_HANDLER` / 背包内的容器物品（精妙背包等一切暴露 `ITEM_HANDLER` 的物品，无需专门适配）
3. Nearby ammo boxes (planned, milestone 6) / 周围弹药箱（规划中，里程碑 6）

## Technical notes / 技术说明

- Forge **1.20.1** (47.4.10), TACZ **1.1.8-hotfix2**. Sophisticated Backpacks optional.
- Zero NBT / data-pack tampering with gun items: all behavior via 4 mixins (`useInventoryAmmo`, `getShootInterval`, `consumeAmmoFromPlayer` ×2, `handleShootHeat`) + capability redirects.
- Fire-session tracking is dual-side (client prediction + server authoritative) via `GunFireEvent`.
- Injection points do not overlap with ragnarok-style enchantability mixins — both can coexist.

## Known limitations / 已知限制

- Guns whose Lua script overrides `handle_shoot_heat` skip cooling/air-cooling scaling (most guns use the default path). / Lua 脚本覆写 `handle_shoot_heat` 的枪不享受冷却/风冷缩放（绝大多数枪走默认路径）。
- Manual reload (`canReload`) still checks the bare inventory only — milestone 7. / 手动换弹的弹药判定仍只看裸背包（里程碑 7 处理）。
- Containers worn in Curios slots are not expanded (only items *inside* the inventory). / Curios 背槽佩戴的容器不在展开范围内。

## Build / 构建


gradlew build

Requires the three dependency jars in `libs/` (TACZ, Sophisticated Backpacks, Sophisticated Core) — included in the repo.

## License / 许可

[MPL-2.0](LICENSE)