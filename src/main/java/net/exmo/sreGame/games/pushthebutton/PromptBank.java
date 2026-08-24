package net.exmo.sreGame.games.pushthebutton;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class PromptBank {
   public record Pair(String human, String alien) {
   }

   public record Delib(String human, String a, String b, String c) {
   }

   private static final List<Pair> WRITING = List.of(
      new Pair("用一个词形容早餐。", "用一个词形容葬礼。"),
      new Pair("你最想养的宠物是？", "你最怕半夜遇见的东西是？"),
      new Pair("写出一种水果。", "写出一种武器。"),
      new Pair("童年最爱的零食。", "手术台上最怕看到的东西。"),
      new Pair("给一艘飞船起个好听的名字。", "给一场灾难起个好听的名字。"),
      new Pair("你理想的周末活动。", "你理想的审讯方式。"),
      new Pair("写出一个颜色。", "写出一种病。"),
      new Pair("最想去的旅游地。", "最不想被困的地方。"),
      new Pair("用三个字夸同事。", "用三个字骂老板。"),
      new Pair("今天的心情用食物来比喻。", "今天的心情用凶器来比喻。"),
      new Pair("写出一首你会唱的歌。", "写出一句临终遗言。"),
      new Pair("最讨厌的天气。", "最适合作案的天气。"),
      new Pair("如果中了彩票你先买什么。", "如果世界末日你先藏什么。"),
      new Pair("你的幸运数字。", "你的犯罪编号。"),
      new Pair("写出一种运动。", "写出一种酷刑。"),
      new Pair("给猫咪取名。", "给病毒取名。"),
      new Pair("最想吃的夜宵。", "最不想在冰箱里看到的。"),
      new Pair("用一句话介绍自己。", "用一句话伪装成人类。"),
      new Pair("你的口头禅。", "外星人的问候语。"),
      new Pair("写出一种乐器。", "写出一种警报声。"),
      new Pair("最想拥有的超能力。", "最想删除的人类记忆。"),
      new Pair("推荐一部电影。", "推荐一种消失方式。"),
      new Pair("写出一个节日。", "写出一个祭典。"),
      new Pair("你的手机壁纸是什么。", "你的真实面目是什么。"),
      new Pair("最拿手的家常菜。", "最拿手的实验配方。"),
      new Pair("用一个表情包形容现在。", "用一个尸检报告形容现在。"),
      new Pair("写出一种交通工具。", "写出一种逃生舱。"),
      new Pair("你最怕迟到的场合。", "你最怕被识破的场合。"),
      new Pair("给这艘船写一句广告词。", "给入侵写一句广告词。"),
      new Pair("童年外号。", "实验室编号。"),
      new Pair("写出一种花。", "写出一种毒。"),
      new Pair("周末想刷的剧。", "周末想解剖的东西。"),
      new Pair("你的星座。", "你的母星。"),
      new Pair("最想收到的礼物。", "最想偷走的器官。"),
      new Pair("写出一句安慰人的话。", "写出一句催眠人类的话。"),
      new Pair("你的早餐饮料。", "你的冷却液型号。"),
      new Pair("最想学的技能。", "最想植入的芯片。"),
      new Pair("写出一个城市。", "写出一个坐标。"),
      new Pair("如果可以隐身一天做什么。", "如果可以换皮一天做什么。"),
      new Pair("用两个字形容友谊。", "用两个字形容寄生。"),
      new Pair("你的闹钟铃声。", "母舰召唤频率。"),
      new Pair("最喜欢的季节。", "最适合孵化的季节。"),
      new Pair("写出一种职业。", "写出一种伪装身份。"),
      new Pair("你的口头禅第二个备选。", "入侵失败时的借口。"),
      new Pair("推荐一个旅游纪念品。", "推荐一件人体样本。"),
      new Pair("写出你的邮箱密码提示。", "写出你的飞船门禁口令。"),
      new Pair("最想养的植物。", "最想培育的菌株。"),
      new Pair("一句话吐槽星期一。", "一句话评价地球人。"),
      new Pair("你最怕迟到的场合。", "你最怕被识破的场合。"),
      new Pair("给这艘船写一句广告。", "给入侵写一句广告。"),
      new Pair("童年外号。", "实验室编号。"),
      new Pair("写出一种花。", "写出一种毒。"),
      new Pair("周末想刷的剧。", "周末想解剖的东西。")
   );

   private static final List<Pair> OPINION = List.of(
      new Pair("菠萝该放披萨上。", "人类该被做成披萨。"),
      new Pair("早起的人更幸福。", "早起的人更容易被抓走。"),
      new Pair("猫比狗聪明。", "克隆比原生更优越。"),
      new Pair("加班应该有加班费。", "实验体应该有加班费。"),
      new Pair("冬天必须喝热饮。", "飞船必须保持冰点。"),
      new Pair("手机不离身很正常。", "芯片不离身很正常。"),
      new Pair("排队加塞不可原谅。", "插队进化很合理。"),
      new Pair("甜豆腐脑完胜咸的。", "活体实验完胜模拟。"),
      new Pair("周一应该取消。", "人类应该取消。"),
      new Pair("电梯里应该聊天。", "电梯里应该扫描。"),
      new Pair("拼好饭可以接受。", "拼器官可以接受。"),
      new Pair("开会可以只听不说。", "渗透可以只看不说。"),
      new Pair("下雨天就该请假。", "日食天就该收割。"),
      new Pair("宠物可以上桌吃饭。", "样本可以上桌解剖。"),
      new Pair("熬夜是年轻人的权利。", "休眠是母舰的命令。"),
      new Pair("自拍太多很自恋。", "换皮太多很专业。"),
      new Pair("礼物要包装才有心意。", "人质要包装才便携。"),
      new Pair("咖啡必须加糖。", "冷却剂必须加血。"),
      new Pair("朋友迟到五分钟可以原谅。", "伪装破绽五分钟可以原谅。"),
      new Pair("综艺比纪录片好看。", "审讯比纪录片好看。"),
      new Pair("应该允许办公室午睡。", "应该允许办公室孵化。"),
      new Pair("红灯可以抢一点。", "警报可以抢一点。"),
      new Pair("人设崩了就该道歉。", "伪装崩了就该灭口。"),
      new Pair("冬天开暖气到28度合理。", "舰桥保持绝对零度合理。"),
      new Pair("带薪摸鱼是一种艺术。", "带薪渗透是一种艺术。"),
      new Pair("方便面可以当正餐。", "营养膏可以当正餐。"),
      new Pair("情侣应公开秀恩爱。", "族群应公开分享宿主。"),
      new Pair("考试可以开卷。", "扫描可以开胸。"),
      new Pair("节日必须放烟花。", "登陆必须放信号弹。"),
      new Pair("电梯关门键可以狂按。", "气闸释放键可以狂按。"),
      new Pair("别人的秘密可以听。", "别人的基因可以采。"),
      new Pair("迟到怪天气。", "暴露怪太阳风。"),
      new Pair("点外卖比做饭高级。", "点样本比培养高级。"),
      new Pair("会议可以没有结论。", "入侵可以没有证人。"),
      new Pair("周末就该躺平。", "周末就该同化。"),
      new Pair("袜子可以不配对。", "触手可以不对称。"),
      new Pair("音乐必须外放分享。", "脑波必须广播同步。"),
      new Pair("垃圾分类太麻烦。", "残骸分类太麻烦。"),
      new Pair("朋友圈点赞等于社交。", "神经连接等于社交。"),
      new Pair("加班到十点还能忍。", "潜伏到十年还能忍。")
   );

   private static final List<Delib> DELIBS = List.of(
      new Delib("飞船晚餐选哪样？", "意面", "汉堡", "沙拉"),
      new Delib("逃生时先救谁？", "小孩", "船长", "自己"),
      new Delib("空闲时你会？", "看书", "睡觉", "健身"),
      new Delib("报警器响了你？", "去看", "躲起来", "拍按钮"),
      new Delib("最信任的线索？", "笔迹", "口音", "眼神"),
      new Delib("休息日首选？", "逛街", "宅家", "出游"),
      new Delib("饮料选？", "咖啡", "茶", "汽水"),
      new Delib("发现队友撒谎？", "揭穿", "观察", "配合"),
      new Delib("礼物选？", "实用", "好看", "贵"),
      new Delib("迷路了？", "问路", "导航", "乱走"),
      new Delib("电影类型？", "喜剧", "恐怖", "科幻"),
      new Delib("座位偏好？", "靠窗", "靠过道", "随便"),
      new Delib("争吵后？", "先道歉", "冷处理", "讲道理"),
      new Delib("密码习惯？", "生日", "随机", "同一套"),
      new Delib("团队角色？", "指挥", "执行", "摸鱼"),
      new Delib("危险临近？", "冲上去", "求援", "装死"),
      new Delib("早餐？", "面包", "粥", "不吃"),
      new Delib("信息来源？", "新闻", "朋友", "直觉"),
      new Delib("压力大时？", "吃", "睡", "吼"),
      new Delib("新同事第一印象看？", "穿着", "说话", "眼神"),
      new Delib("假期长度？", "三天", "一周", "越长越好"),
      new Delib("被怀疑时？", "解释", "沉默", "反问"),
      new Delib("灯光偏好？", "亮", "暗", "闪"),
      new Delib("合作失败谁的锅？", "计划", "执行", "运气"),
      new Delib("机密文件？", "烧掉", "上交", "偷看"),
      new Delib("闹钟响了？", "马上起", "再五分钟", "砸了它"),
      new Delib("选队长看？", "资历", "人缘", "直觉"),
      new Delib("雨天出门？", "伞", "帽", "淋"),
      new Delib("发现异常声音？", "循声", "无视", "报告"),
      new Delib("最后一颗糖？", "让给别人", "自己吃", "掰开")
   );

   public static final String ALIEN_DELIB = "随便选一个看起来最像人类会选的。";

   private static final List<Pair> DRAWING = List.of(
      new Pair("画一只猫。", "画一颗眼球。"),
      new Pair("画太阳。", "画母舰。"),
      new Pair("画房子。", "画牢笼。"),
      new Pair("画花朵。", "画触手。"),
      new Pair("画汽车。", "画逃生舱。"),
      new Pair("画笑脸。", "画尖牙。"),
      new Pair("画树。", "画电极。"),
      new Pair("画蛋糕。", "画器官。"),
      new Pair("画鱼。", "画寄生虫。"),
      new Pair("画雨伞。", "画飞碟。"),
      new Pair("画杯子。", "画烧杯。"),
      new Pair("画月亮。", "画裂缝。"),
      new Pair("画帽子。", "画头盔。"),
      new Pair("画冰淇淋。", "画冻住的标本。"),
      new Pair("画桥。", "画气闸。"),
      new Pair("画鸟。", "画无人机。"),
      new Pair("画书。", "画实验日志。"),
      new Pair("画吉他。", "画声波武器。"),
      new Pair("画鞋。", "画爪印。"),
      new Pair("画气球。", "画头颅。"),
      new Pair("画自行车。", "画履带。"),
      new Pair("画云。", "画烟雾弹。"),
      new Pair("画蝴蝶。", "画飞虫群。"),
      new Pair("画灯塔。", "画信号塔。"),
      new Pair("画西瓜。", "画切片标本。"),
      new Pair("画时钟。", "画倒计时。"),
      new Pair("画钥匙。", "画门禁卡。"),
      new Pair("画船。", "画沉船。"),
      new Pair("画星星。", "画弹孔。"),
      new Pair("画兔子。", "画实验鼠。"),
      new Pair("画彩虹。", "画光谱。"),
      new Pair("画椅子。", "画审讯椅。"),
      new Pair("画苹果。", "画禁果。"),
      new Pair("画火箭。", "画导弹。"),
      new Pair("画企鹅。", "画低温舱。"),
      new Pair("画眼镜。", "画目镜。"),
      new Pair("画城堡。", "画堡垒。"),
      new Pair("画蜗牛。", "画蠕虫。"),
      new Pair("画火焰。", "画熔炉。"),
      new Pair("画爱心。", "画核心。")
   );

   private PromptBank() {
   }

   public static Pair writing() {
      return pick(WRITING);
   }

   public static Pair opinion() {
      return pick(OPINION);
   }

   public static Pair drawing() {
      return pick(DRAWING);
   }

   public static Delib delib() {
      return DELIBS.get(ThreadLocalRandom.current().nextInt(DELIBS.size()));
   }

   private static Pair pick(List<Pair> list) {
      return list.get(ThreadLocalRandom.current().nextInt(list.size()));
   }
}
