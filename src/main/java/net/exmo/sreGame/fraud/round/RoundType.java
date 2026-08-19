package net.exmo.sreGame.fraud.round;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public enum RoundType {
   SUM_GUESS("数字猜和", "信息差与谎报", SumGuessRound::new),
   PRISONER("囚徒困境", "信任与背叛", PrisonerRound::new),
   TRUST_CHAIN("信任链", "贪婪与接力", TrustChainRound::new),
   VOTE_DEDUCT("投票扣分", "拉帮结派", VoteDeductRound::new),
   AUCTION("暗标拍卖", "估价与虚张声势", AuctionRound::new),
   DRAW_LIE("你画我猜", "误导与反误导", DrawLieRound::new),
   INTEL("真假情报", "辨别谎言", IntelRound::new),
   ROULETTE("赌徒轮盘", "风险评估", RouletteRound::new),
   SPLIT_CLAIM("分赃", "贪婪与默契", SplitClaimRound::new),
   PASSWORD("口令", "真假信使", PasswordRound::new),
   SCAPEGOAT("替罪羊", "栽赃与自保", ScapegoatRound::new),
   BLUFF_CARD("梭哈", "虚张声势", BluffCardRound::new),
   SECRET_GIFT("暗礼", "人情与算计", SecretGiftRound::new),
   LONE_WOLF("独食", "从众与独行", LoneWolfRound::new),
   FINALE("终极诈骗", "集体翻盘", FinaleRound::new);

   private final String display;
   private final String theme;
   private final Supplier<RoundHandler> factory;

   RoundType(String display, String theme, Supplier<RoundHandler> factory) {
      this.display = display;
      this.theme = theme;
      this.factory = factory;
   }

   public String display() {
      return this.display;
   }

   public String theme() {
      return this.theme;
   }

   public RoundHandler create() {
      return this.factory.get();
   }

   public boolean standard() {
      return this != FINALE;
   }

   public static List<RoundType> shuffledStandard() {
      return shuffledStandard(8);
   }

   public static List<RoundType> shuffledStandard(int count) {
      List<RoundType> list = new ArrayList<>();
      for (RoundType type : values()) {
         if (type.standard()) {
            list.add(type);
         }
      }
      Collections.shuffle(list, ThreadLocalRandom.current());
      if (count > 0 && list.size() > count) {
         return new ArrayList<>(list.subList(0, count));
      }
      return list;
   }
}
