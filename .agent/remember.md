今晚任务: TACZ: Sustained Fire (modid sustainedfire, D:/Documents/mcmod/TACZ-Sustained-Fire). GOAL-PLAN.md v2在项目根. Forge 1.20.1(47.4.10/Java17/official), tacz 1.1.8-hotfix2, SB+SC(已在libs/flatDir). 用户已睡全自动授权. 收尾: 全部干完后schtasks异步跑D:/Desktop/2小时后休眠.bat(不能用Start-Process,会被杀).
===build#6结果: 1错已修===
根因: 1.20.1 Forge LazyOptional.of(NonNullSupplier<T>)非of(T)(老版API). 修复(本轮): LazyOptional.of(() -> new CombinedView(...)). 到此全部编译错清零路径: remap=false×6→import修正(Rarity嵌套)→getReturnValue()泛型→CombinedView自实现→NonNullSupplier. build#7本轮已启动(sleep95+jar完整验收: MANIFEST MixinConfigs/mixins.json/lang在位/zh_cn回读).
===指令通道新实证(重要!)===
首条cmd块多行(>3行)会触发解析失败"第2行开启