package net.exmo.sreGame.games.fakehuman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class IdCard {
   private static final String[] NAMES = {
      "林晓", "周启明", "陈婉", "赵无野", "苏晚晴", "韩律", "方小满", "江北",
      "沈秋", "白浅", "顾深", "唐果", "梁知夏", "陆南星", "叶迟", "何暮",
      "宋辞", "许安", "冯路", "曹雪", "邓桥", "蒋平", "魏澄", "谢南",
      "罗晚", "秦川", "陶真", "袁野", "潘星", "丁荷", "任舟", "傅灯"
   };
   private static final String[] JOBS = {
      "快递员", "护士", "小学教师", "电工", "便利店店员", "司机", "会计",
      "记者", "厨师", "社工", "图书管理员", "维修工", "药师", "保安"
   };
   private static final String[] TRAITS = {
      "左耳有小痣", "右手拇指茧厚", "说话轻微口吃", "右眉有缺口",
      "戴旧铜戒", "后颈晒痕", "走路内八", "常清嗓子", "左腕旧疤"
   };
   private static final String[] FAKE_JOBS = {
      "FEMA 外勤", "第三区户籍员", "夜班核销员", "匿名测绘", "编外巡查"
   };

   private final String name;
   private final String job;
   private final String temperature;
   private final String trait;
   private final boolean tempAbnormal;
   private final boolean contradictory;

   public IdCard(String name, String job, String temperature, String trait, boolean tempAbnormal, boolean contradictory) {
      this.name = name;
      this.job = job;
      this.temperature = temperature;
      this.trait = trait;
      this.tempAbnormal = tempAbnormal;
      this.contradictory = contradictory;
   }

   public String name() {
      return this.name;
   }

   public String job() {
      return this.job;
   }

   public String temperature() {
      return this.temperature;
   }

   public String trait() {
      return this.trait;
   }

   public boolean tempAbnormal() {
      return this.tempAbnormal;
   }

   public boolean contradictory() {
      return this.contradictory;
   }

   public List<String> lore() {
      return List.of(
         "&7姓名： &f" + this.name,
         "&7职业： &f" + this.job,
         "&7体温： &f" + this.temperature,
         "&7特征： &f" + this.trait
      );
   }

   public static IdCard human(Set<String> usedNames) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      double temp = 36.2 + rng.nextDouble() * 1.1;
      return new IdCard(
         uniqueName(usedNames),
         pick(JOBS),
         String.format("%.1f℃", temp),
         pick(TRAITS),
         false,
         false
      );
   }

   public static IdCard impostor(List<IdCard> humanCards, Set<String> usedNames) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      IdCard cover = human(usedNames);
      String name = cover.name;
      String job = rng.nextBoolean() ? pick(FAKE_JOBS) : cover.job;
      String trait = cover.trait;
      double temp = rng.nextBoolean() ? 34.1 + rng.nextDouble() : 38.6 + rng.nextDouble();
      String temperature = String.format("%.1f℃", temp);
      if (!humanCards.isEmpty()) {
         IdCard donor = humanCards.get(rng.nextInt(humanCards.size()));
         int overlaps = 1 + rng.nextInt(2);
         List<Integer> fields = new ArrayList<>(List.of(1, 2, 3));
         for (int i = 0; i < overlaps && !fields.isEmpty(); i++) {
            int idx = fields.remove(rng.nextInt(fields.size()));
            switch (idx) {
               case 1 -> job = donor.job;
               case 2 -> temperature = donor.temperature;
               default -> trait = donor.trait;
            }
         }
      }
      return new IdCard(name, job, temperature, trait, true, true);
   }

   private static String uniqueName(Set<String> used) {
      List<String> pool = new ArrayList<>(List.of(NAMES));
      if (used != null) {
         pool.removeIf(used::contains);
      }
      if (pool.isEmpty()) {
         return pick(NAMES) + ThreadLocalRandom.current().nextInt(10, 99);
      }
      String name = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
      if (used != null) {
         used.add(name);
      }
      return name;
   }

   private static String pick(String[] pool) {
      return pool[ThreadLocalRandom.current().nextInt(pool.length)];
   }

   public static List<IdCard> livingHumans(Collection<FakeHumanPlayer> players) {
      List<IdCard> cards = new ArrayList<>();
      for (FakeHumanPlayer state : players) {
         if (state != null && state.alive() && !state.impostor() && state.card() != null) {
            cards.add(state.card());
         }
      }
      return cards;
   }
}
