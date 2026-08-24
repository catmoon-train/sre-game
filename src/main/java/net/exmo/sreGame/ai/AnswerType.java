package net.exmo.sreGame.ai;

/**
 * 海龟汤 AI 裁判的回答类型。放在 ai 包作为共享枚举，便于 AiService 与各小游戏复用。
 */
public enum AnswerType {
   YES,
   NO,
   YES_OR_NO,
   IRRELEVANT;

   public String label() {
      return switch (this) {
         case YES -> "是";
         case NO -> "不是";
         case YES_OR_NO -> "是或不是";
         case IRRELEVANT -> "无关";
      };
   }

   /** 解析聊天/指令输入为回答类型；识别中文标签、数字 1/2/3/4 与英文。无法识别返回 null。 */
   public static AnswerType fromLabel(String text) {
      if (text == null) {
         return null;
      }
      String t = text.trim();
      if ("1".equals(t) || "是".equals(t) || "yes".equalsIgnoreCase(t)) return YES;
      if ("2".equals(t) || "不是".equals(t) || "否".equals(t) || "no".equalsIgnoreCase(t)) return NO;
      if ("4".equals(t) || "是或不是".equals(t) || "是或者不是".equals(t)
            || "maybe".equalsIgnoreCase(t) || "yes or no".equalsIgnoreCase(t)
            || "yesorno".equalsIgnoreCase(t)) return YES_OR_NO;
      if ("3".equals(t) || "无关".equals(t) || "不相关".equals(t)
            || "irrelevant".equalsIgnoreCase(t)) return IRRELEVANT;
      return null;
   }
}
