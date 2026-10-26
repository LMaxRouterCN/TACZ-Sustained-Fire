<!-- ID:001 -->
[2026-10-11 v3终版,已推翻v1雨闸门模型] VS2 2.4.11 world_weather 哨兵坐标bug(全案报告:vs-td-investigation/CASE-REPORT.md):船坞区块ticking+任意镂空/悬空/地下ship→tickChunk每tick以1/16抽列做冰雪探测查getBiome(不问天气:晴天raining=0全速复现,level.dat字节级证实;/weather clear已执行且无效)→VS2 occlude对镂空列返回哨兵(0,minY-1,0)→useBiomeAtWorldPos无条件套shipToWorld→输出c_w+R·(-c_s)≈船坞模长(2400万格级)且随船摇晃逐tick移动→TD等同步推理型BiomeSource必miss→主线程每tick park 3.5-5.4s→TPS0.18,无自然终点(流停=退档[Stopping server前8秒签名]或船坞卸载)。修复:VS2侧两个wrap(useBiomeAtWorldPos/usePrecipitationAtWorldPos)加 if(pos.getY()<level.getMinBuildHeight()) return original.call(level,pos);;BiomeSource mod侧防御=Server thread且缓存miss直接走default/null fallback。诊断指纹:远点半径=某船坞中心模长+角度窄弧单调漂移+每tick恰1次miss+离群点模长=另一船坞。v1翻车教训:对MC源码的"记忆"未核对禁止当事实(唯一记错处=isRaining守卫);"雨停"从"流停"反推=循环论证。
tag: Minecraft, VS2, bug根因, mod兼容, 哨兵坐标
<!-- END:001 -->
<!-- ID:002 -->
[2026-10-12] 用户(max)对VS2(Valkyrien Skies)开发组强烈反感:更新不积极、文档不好好写、开发组小圈子各行其是。倾向避免与其交互、不想编译VS2本体。涉及VS2 bug修复路线决策时:优先本地mixin模组或下游防御,PR只作fire-and-forget;勿默认用户愿意等上游或升VS2版本(其整合包锁2.4.11)。
tag: Minecraft, VS2, 用户偏好, 修复路线
<!-- END:002 -->
<!-- ID:003 -->
Max 的待办/备忘录是单文件扁平记录,路径: D:\Documents\my\日常\!备忘录-剪贴板-随笔记-待办_不要删除这个文件.md (约5.6KB@2026-10-19,持续增长)。标记约定: 无前缀=未完成,勾=完成,叉=决定不做,半勾=进行中,圈=搁置/纠结,可多个按时间序。分类: 杂乱/整合包/pokeragent(本地D:\Documents\my\Poker Agent)/mc模组(本地D:\Documents\mcmod)/tht(TansHugeTrees)/emi(LMaxs-EMI-Fork)。统计基准(2026-10-19): 勾6项(下界合金掉落物、Log Filter注释、create覆盖挪回收站、旧记号转义迁移、ae2合成卡住破案=根因ae2vm、EmiScrollGridFix); 半勾3项(ae2/emi回退决策、pipez fork、td×vs2兼容半成); 叉1项(AppliedCannon)。条目无时间戳,除pokeragent分区两条(先create回收站后转义迁移)外完成先后不可考,用时重读文件为准,勿信统计快照。
tag: 待办文件, 备忘录路径
<!-- END:003 -->
<!-- ID:004 -->
2026-10-18 02:17 审查结论: 爆炸效果重写与大火燃烧重构在本地为纯绿地项目, mcmod全部项目过目加全盘检索AppliedCannon零命中, 无任何现有代码。可行性报告落盘于 D:\Documents\my\日常\爆炸大火-可行性审查\爆炸与大火-可行性审查报告.md。核心: 爆炸=射线改体积场, ExplosionEvent.Start取消加getToBlow填充, 零mixin, 中等难度, 建议先行; 大火=燃烧管线, 架构决策留给max: chunk级SavedData燃烧图(推荐) 对比 每方块BE, 中高难度; 两项目共享分帧调度器基建。许可证MPL-2.0, Forge 1.20.1。

环境事实: max真实桌面路径为 D:\Desktop 而非C盘用户目录下Desktop; 后端终端为PowerShell。

bug待max定夺: D:\Desktop\30分钟后休眠.bat 实际注册+2小时计划任务AutoHibernate2h, 窗口内30分钟倒计时纯装饰不触发任何动作, 疑似从2小时版复制漏改AddHours(2)。修复点: AddHours改AddMinutes(30), 或直接用桌面现成2小时版。已验证休眠时刻04:16:43。
tag: 爆炸, 大火, 绿地项目, 可行性审查, 桌面路径, 休眠bat
<!-- END:004 -->
<!-- ID:005 -->
LLMwc4n是max的LLM提示词/破限分享仓库,用户常来开issue求助模型拒绝问题. 2026-09-25时max自述要忙一两个月(约到12月). 5个open issue全是用户求助: #2(2025-11),#4(gemini敏感词),#5(ds已"打穿",用的claude-fable-5.1改的提示词),#6(捧场),#8(2026-10-13,v4/v4.1被拒,等max回复). #7编号空缺(404).
tag: LLMwc4n项目, 用户求助, max忙期
<!-- END:005 -->
<!-- ID:006 -->
LMaxs-Pipez-fork(本地D:/Documents/mcmod/LMaxs-Pipez-fork)未推送到GitHub:remote只有upstream(henkelmax/pipez),无origin. 分支lmaxs/1.20.1的BFS改动等全部无云端备份,刚经历搬家,风险敞口. 2026-10-18经gh api确认仓库不存在. 已向max建议推送.
tag: LMaxs-Pipez-fork, git远程, 未推送
<!-- END:006 -->
<!-- ID:007 -->
GitHub Search API(gh search / search/issues)索引滞后且分页排序不稳定: 近期创建的issue可能完全不在结果(实例:LLMwc4n#8和WindowTextExtractor#1存在于GraphQL直查但搜索无),total_count与实际可有±几条抖动(实例:BCLib#172在第2页和第3页重复出现). 权威做法:名下仓库用GraphQL批量查,具体issue用repos API直查. 2026-10-18实测.
tag: gh-cli, 搜索索引坑, 数据可信度
<!-- END:007 -->
<!-- ID:008 -->
gh api -f key=true 发送的是字符串"true"而非JSON布尔,subscription端点(PUT /repos/{owner}/{repo}/subscription)不认就静默空转:HTTP 2xx退出码0但订阅不生效,2026-10-19实证(failed:0但27仓未建订阅). 正确做法:JSON body管道喂gh api --input -. 回读验证必须独立GET,不能信PUT退出码.
tag: gh-cli, API坑, 布尔参数, subscription
<!-- END:008 -->
<!-- ID:009 -->
exec已在PowerShell中运行,不要再套powershell -Command "..."双层:外层引号会对$变量先行插值,内层收到的脚本$n/$fail全成空壳,foreach直接ParserError. 直接裸写循环/管道即可. 2026-10-19实证.
tag: exec指令, PowerShell嵌套, 引号插值坑
<!-- END:009 -->
<!-- ID:010 -->
2026-10-19 IR2040启动卡死定案闭环: Ixeris 4.6.9+1.20.1-forge线程接管与FML早显窗口互锁死锁. 机制: 原main线程被改名Ixeris Event Polling Thread,卡在MainThreadDispatcher.await不泵Win32消息,Ixeris另开线程跑Minecraft构造,fmlearlydisplay对该早显窗口glfwSetWindowTitle跨线程同步SendMessage需等待消息泵→环死锁. max回退Ixeris旧版后启动成功=根因实证. 证据: jstack dump在$env:TEMP\ir2040_td.txt(另存D:\Documents\my\ir2040-thread-dump.txt待确认),日志冻结15:59:14.920,Render thread卡937s仅耗2.1s cpu,已排除磁盘/Defender/堆(Xmx14336m)/双显卡. 后续: 给Ixeris仓库开issue,草稿证据链齐. 该包mods在版本隔离目录,E盘机械盘与本案无关.
tag: bug根因, Minecraft, Ixeris
<!-- END:010 -->
<!-- ID:011 -->
2026-10-19 create等写入载荷的转义坑: 运行中后端会把载荷里的```替换为三反引号; 需要在目标文件保留字面```时(如给源码写断言), 用字符串拼接(TICK+3)绕开替换. 三反引号直接写载荷会原样落盘但破坏聊天端markdown渲染, 应写```让其转换. 自2026-10-19起后端转义新约定: 载荷中的反斜杠+反引号由后端还原为字面反引号, 旧记号退役(后端重启后生效, 重启前旧记号仍有效).
tag: API坑, PokerAgent
<!-- END:011 -->
<!-- ID:012 -->
2026-10-19 终版勘误(覆盖旧版): (1)双禁铁律: 指令标记序列(方括号包裹的cmd/code那两对)不得以字面形式出现在载荷与正文任何位置(注释/引用/示例不豁免), 讨论时用口述或码位描述. (2)码位铁律: 凡引用文件已有特殊字符一律从文件自适应提取(探针)或\uXXXX写法, 永不凭记忆心算(两连错: 12300=角括号, 12336=U+3030波浪线; 方头括号=U+3010/U+3011). (3)GBK控制台: python脚本print非GBK字符会UnicodeEncodeError吞诊断, 开头sys.stdout.reconfigure(encoding='utf-8', errors='replace'). (4)剪贴板read经展示层转换不保真, 取证一律行号模式. (5)旧记号TICK+3字面写入remember/memory载荷会被运行中旧后端替换成三反引号存进记忆文本, 记忆里提及该记号用TICK+3拼接口述. (6)转义语义生效条件(实测修正): commit 72a761a后实测运行中后端仍为旧语义(反斜杠加反引号原样落盘, 旧记号替换仍生效)——备份通知不能证明重启加载了新代码(该功能4ad4306就有); 转义还原需max重启后端后生效, 重启前载荷继续用旧记号, 重启后切反斜杠转义, 要字面反斜杠加反引号则双写反斜杠. 关联011.
tag: API坑, PokerAgent
<!-- END:012 -->
<!-- ID:013 -->
[2026-10-26深夜终态] IR2040客户端冻结案→已发布GitHub issue: https://github.com/Antikythera-Studios/epicfight/issues/2625 (title带[1.20.1/20.14.17]前缀,body 8523字符,OPEN).根因链:Render thread钉死在EpicFight 20.14.17 AnimationClip.getPoseInTime:100(NaN毒化二分搜索,三个分支全false区间永不收缩)←SPChangeLivingMotion包处理→ClientAnimator.offAllLayers→Layer.off→getEnabledPose→ConcurrentLinkAnimation.getPoseByTime:72→NaN elapsedTime源自AnimationPlayer.tick 48-51(EMPTY_ANIMATION totalTime=0且isRepeat=true,elapsedTime%=0→NaN,任何曾被禁用的composite层一tick带毒).NaN出生:Animations:143匿名DirectStaticAnimation→StaticAnimation()无参构造99行super(0.0F,true)→EMPTY_CLIP(AnimationClip:14)clipTime默认0.修复建议三条已在issue:tick守卫getTotalTime()>0/getPoseByTime洗NaN/animationClip加NaN守卫.验证闭环:dump栈23帧epicfight侧9帧与本地源码双向锁死,线上body每行断言有实锤.完整案卷D:\Documents\my\日常\epicfight-bug-case\(body.md/issue-draft.md v2/evidence两dump).dump原件%TEMP%\mc_td.txt与td2.txt.后续:max网页审+拖dump附件,可插桩build定位具体entity/animation对(需复现).次生案:DH-Regen Queue烧核非死锁,零BLOCKED,不另报.教训:jcmd的nid是十进制(Zulu21).附:StaticAnimation.java:101上游typo"emtpy"无关本bug.
tag: bug根因，Minecraft, EpicFight
<!-- END:013 -->
<!-- ID:014 -->
2026-10-26 PokerAgent框架坑(v2修正): ①create指令写入内容约4000字符处静默截断,回执仍报"已创建"——大文件必须分块(每块≤1500字符)+append+每块read校验,严禁单次写长文档. ②append会剥掉新块开头的全部空行(不是只吞一个!前缀空行防吞策略无效),接缝补空行只能用replace -l. ③replace默认整块匹配,行内片段搜不到;精确改行用-l行号模式,支持2行扩3行的插入. ④同一轮连续两个行号replace:先执行行号大的(下游)再小的(上游),否则前面插入导致行号漂移会打错位置.
tag: API坑, PokerAgent
<!-- END:014 -->
<!-- ID:015 -->
2026-10-26 gh api + PokerAgent exec通道实测谱系(修正:推翻早前"含管道必挂"的过度概括):
[安全] 裸URL零flag的gh api;简单jq字符串路径(--jq ".workflows[].path" / --jq ".content");exec内含PowerShell管道的纯本地命令(Get-ChildItem | Select-Object 实测通过).
[挂起无回执] gh api加自定义Accept头(-H "Accept: application/vnd.github.raw");gh api --jq ".content" 后接PowerShell解码管道(ForEach-Object内嵌套中括号圆括号).
[疑似毒,未干净验证] jq表达式内转义嵌套双引号——今晚样本被正文标签污染混杂,早前轮次有独立案底,标记存疑.
[立即报错] gh api -o 不存在(那是curl的flag).
[框架解析铁律] 解释性正文严禁字面出现中文方括号形式的cmd或code标签对——后端会从假标签起抓,把后续散文连同同批真指令一起解析成垃圾,连环报"未知指令".引用标签一律用英文方括号描述.
[输出纪律] 今晚三次消息停在"即将发指令"的冒号处然后直接结束,指令块从未生成.铁律:宣布与指令块必须同条消息,消息不得以预告冒号收尾.
[取文件内容标准姿势] contents端点用--jq ".content"拿base64,回执按宽度折行,去换行再解码;验标签或操作真伪,直接打事件API看actor字段.
[批量解析观察] 深夜四指令批(2×memory+remember+read):remember碎裂出2条未知指令噪声(正文首行+闭合code标签被当指令名),016回执出现两次,疑似碎裂触发批内重放(幂等无害),最终全对(remember新内容经注入回执自验).推论:批内多行指令可能碎裂并引发重放,非幂等变更(gh issue comment/add-label等)忌混批,单独成条发送.
tag: gh-cli, API坑, exec指令, PokerAgent
<!-- END:015 -->
<!-- ID:016 -->
2026-10-26 实锤版(覆盖疑似稿): epicfight上游(Antikythera-Studios/epicfight)自动打标机制已由events API验证. issue#2625唯一labeled事件: actor=github-actions[bot](type:Bot),时间2026-10-26T12:53:13Z(贴issue创建后约1分钟). 机制全貌: 仓库.github/workflows/issue-label.yml监听issues opened,按author_association分流——OWNER/MEMBER/COLLABORATOR打"core contributor",其余所有人(含max/LMaxRouterCN)一律自动打"triage",由workflow内gh issue edit --add-label执行. 结论: 该仓库所有外来issue出生即带triage,与人工关注零关联. 通用方法论: 判断issue标签是否人工,查events API对应事件的actor字段,bot即自动化. 附注: 事件流无edited/commented记录,但max确于13:01:55Z挂上双dump附件——附件挂载是否留事件未考证(GitHub events语义不确定),不影响主线. 转写件中GITHUB_VTOKEN错字确认是base64转写噪声,workflow文件本体完好(bot实际执行成功).
tag: EpicFight, gh-cli, 数据可信度
<!-- END:016 -->
<!-- ID:017 -->
[2026-10-27] 许可证兼容性硬约束: MPL-2.0 与 GPL-3.0 是单向兼容(MPL文件可并入GPL项目,GPL代码不能进MPL项目). TACZ 源码是 GPL-3.0, 因此 TACZ: Sustained Fire(MPL-2.0) 中禁止复制任何 TACZ 源码文本, 只能通过 mixin/事件/API 调用其运行时逻辑. mixin 手法: 附加式(@Inject at RETURN 补充逻辑)而非复制原逻辑重写. 需求3弹链的寻弹补充即按此设计(原方法跑完→补充扫SB背包).
tag: 许可证, mod兼容, 绿地项目
<!-- END:017 -->
<!-- ID:018 -->
2026-10-27: max的测试实例 E:/MC/.minecraft/versions/Industrial Revolution 2040/mods/ 里所有jar文件名都带中文前缀(如"tacz 永恒枪械工坊：零 tacz-1.20.1-1.1.8-hotfix2.jar","精妙背包 sophisticatedbackpacks-...jar"),裸英文名复制会WinError 2. 从该目录取jar时先按list输出的完整文件名取,复制到别处时剥掉中文前缀恢复标准名(flatDir maven坐标要求name-version格式). 另: TACZ-Sustained-Fire项目libs已放好三jar(tacz-1.20.1-1.1.8-hotfix2 / sophisticatedbackpacks-3.26.14.2226 / sophisticatedcore-1.5.15.2427).
tag: 搜索索引坑, Minecraft, mod兼容
<!-- END:018 -->
<!-- ID:019 -->
2026-10-27: PokerAgent多行写文件(create/remember/memory)时,指令行之后的正文内容必须包在code标签+三反引号代码块里,否则后端把内容逐行解析为指令(大量"未知指令"报错),目标文件写成0字节. 单行指令(mkdir/list/copy等)可在同一cmd块内按行罗列,无需code标签. 另: 服务商工具解析器会劫持类工具调用语法并返回伪造回执,唯一真实通道是cmd标签.
tag: PokerAgent, API坑
<!-- END:019 -->
<!-- ID:020 -->
[2026-10-27实证] PokerAgent框架"任务结束销毁残留进程"机制会杀死Start-Process拉起的异步子进程(证据: gradlew异步构建0字节日志即死). 逃逸方案: schtasks /create+/run(进程父级是svchost,不在agent进程树内,完整跑完1m14s构建). 结论: 一切异步长任务/延时任务(含收尾休眠bat)一律走schtasks,禁用Start-Process.
tag: API坑, 休眠bat, PokerAgent
<!-- END:020 -->
<!-- ID:021 -->
[2026-10-27实证] Forge mod 对 tacz 等第三方mod类做 mixin: 注解必须显式 remap=false(所有指向mod类成员的@Inject/@Redirect/@ModifyArg的method目标、@At(INVOKE)的target、@Shadow字段), 否则Mixin AP 0.8.5报"Unable to locate obfuscation mapping"致命编译错(mod类不在SRG映射内,AP默认remap=true). 例外: MC原生类成员(如ItemStack)不需要且不能加remap=false. Forge patch方法(如LivingEntity.getCapability)名字生产不混淆,@At target加remap=false后字面匹配成立. 另: javac错误输出到stderr日志是GBK编码,PowerShell读需[Text.Encoding]::GetEncoding(936).GetString(ReadAllBytes())否则中文部分显示??????.
tag: Minecraft, API坑
<!-- END:021 -->
