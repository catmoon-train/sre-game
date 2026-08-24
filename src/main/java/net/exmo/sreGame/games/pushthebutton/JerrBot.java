package net.exmo.sreGame.games.pushthebutton;

public final class JerrBot {
   private JerrBot() {
   }

   public static String say(String line) {
      return "&6<JerrBot> " + line;
   }

   public static String intro() {
      return say("&f欢迎登上 &bJerr 号&f。我是你们的舰载智能，请称呼我 JerrBot。");
   }

   public static String roles() {
      return say("&7船员中混入了外星人。完成测试、互相怀疑，拍下那个大红按钮。");
   }

   public static String captain(String name) {
      return say("&e船长席轮到 &f" + name + " &e——选一项测试，再点名受试者。");
   }

   public static String test(String type, String names) {
      return say("&f本轮：" + type + " &7受试者 &f" + names + "&7。请按你看到的提示作答。");
   }

   public static String compile() {
      return say("&7正在汇总答卷……请回到大厅观看展示墙。");
   }

   public static String view() {
      return say("&f答案已上墙。看仔细：人类和外星人拿到的题并不一样。");
   }

   public static String lounge() {
      return say("&7可以自由交谈。谁都可以拍按钮——但每人整局只有一次机会。");
   }

   public static String button(String name) {
      return say("&c" + name + " 拍下了按钮。气闸正在加压。");
   }

   public static String airlockPick(int aliens) {
      return say("&f拍钮者，指出恰好 &c" + aliens + " &f名外星人。选错一个，人类就完了。");
   }

   public static String vote(String names) {
      return say("&f是否把 &e" + names + " &f送出气闸？赞成释放，反对则取消。");
   }

   public static String votePass() {
      return say("&a赞成占优。气闸开启。愿真空对他们足够仁慈。");
   }

   public static String voteFail(String nos) {
      return say("&c提案被否决。" + (nos.isEmpty() ? "" : " 反对：&f" + nos));
   }

   public static String humanWin() {
      return say("&a气闸里全是外星人。人类暂时安全。恭喜，大概。");
   }

   public static String alienWin() {
      return say("&c你们放走了自己人。飞船易主。外星人获胜。");
   }

   public static String hack(String hacker, String target) {
      return say("&c" + hacker + " 入侵了 &f" + target + "&c 的题面。");
   }

   public static String sus(String actor, String target) {
      return say("&e" + actor + " 觉得 &f" + target + " &e很可疑。");
   }

   public static String hurry(String name) {
      return say("&b" + name + " 在催：&f倒计时加速了。");
   }

   public static String claim(String name) {
      return say("&c" + name + " 声称自己动过题面。信不信由你。");
   }
}
