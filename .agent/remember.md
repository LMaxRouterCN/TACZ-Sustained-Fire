【热修中】TACZ: Sustained Fire 游戏内首崩(2026-10-27 13:13 AnvilScreen): CancellationException "The call useInventoryAmmo is not cancellable".
根因: sf$beltFeed/sf$shootIntervalBoost两个@Inject回调调用cir.setReturnValue但注解未写cancellable=true. Mixin运行期才查: 编译不报错,apply时仅日志warning,首次调用即崩. 触发路径: 铁砧悬停已附弹链升级的枪→TACZ tooltip(ClientGunTooltip:154)→useInventoryAmmo→handler→setReturnValue→崩. 铁砧附魔操作本身成功; 不修则服务端射击扣弹路径同样炸(每发都过此方法).
修复(本轮): 两处@Inject补cancellable=true(全项目仅这2处setReturnValue,已核对)→schtasks重构建→BUILD SUCCESSFUL则新commit(不amend)→删sfsleep+sfbuild计划任务(两个都挂在/sc once /st 23:59,今晚23:59会自动重触发,sfsleep重跑会把用户游戏中机器休眠).
重测点(装新jar,时间戳应为修复后): 1)悬停带弹链枪不崩 2)射击直读背包弹药(含SB包内弹药) 3)预热/渐进/冷却/风冷照旧 4)无附魔枪tooltip正常.
后续如再崩: 用户给crash log→直接读文本(这次不是GBK日志是crash report)→优先查mixin运行期问题: @Redirect目标匹配/ACL/@Shadow实际类型.