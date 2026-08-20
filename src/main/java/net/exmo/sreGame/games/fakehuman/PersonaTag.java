package net.exmo.sreGame.games.fakehuman;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public enum PersonaTag {
   GAYMER("给佬", "阴柔、调侃男性", "不懂具体梗、对女性也感兴趣"),
   LOLI("萝莉", "幼态、依赖、叠词", "过度装嫩、无直觉、编不圆来源"),
   OFFICIAL("官方腔", "书面语、敬语、套话", "机构名不存在、编号前后不一"),
   SHY("社恐", "简短、不敢对视、慌张", "过度沉默、无情绪波动、靠近他人"),
   LOYAL("忠诚", "担保他人、愿自绑、保护", "忠诚对象不稳、嘴上说不行动"),
   CLOWN("小丑", "段子、不正经、模仿", "梗重复、一直回避不切换正经"),
   DETECTIVE("推理狂", "分析、记笔记、审讯", "逻辑有漏洞、总带节奏指向真人"),
   MISER("守财奴", "关心物资、谈条件", "不懂物资价值、不翻箱子"),
   PARENT("带娃者", "护犊子、孩子优先", "对孩子态度不稳、答不出孩子信息"),
   SILENT("沉默寡言", "单字、手势、行动表达", "该行动不行动、行为随机"),
   PIOUS("虔诚者", "经文、祝福、祈祷", "经文编造、信仰体系不固定"),
   DRAMA("戏精", "情绪夸张、小剧场", "核心信息前后矛盾、为演而演"),
   DYING("半死不活", "咳嗽、虚弱、移动慢", "虚弱程度不稳、听到物资突然精神"),
   COP("前警员", "命令式、专业术语、查证件", "不懂术语、跳过程序、过度暴力"),
   DRUNK("醉汉", "大舌头、逻辑混乱、唱歌", "醉意不稳、名字说错、走路不晃");

   private final String display;
   private final String trait;
   private final String tell;

   PersonaTag(String display, String trait, String tell) {
      this.display = display;
      this.trait = trait;
      this.tell = tell;
   }

   public String display() {
      return this.display;
   }

   public String trait() {
      return this.trait;
   }

   public String tell() {
      return this.tell;
   }

   public boolean mutexWith(PersonaTag other) {
      if (other == null || other == this) {
         return false;
      }
      return pair(this, other, SHY, DRAMA)
         || pair(this, other, SILENT, CLOWN)
         || pair(this, other, OFFICIAL, DRUNK);
   }

   private static boolean pair(PersonaTag a, PersonaTag b, PersonaTag x, PersonaTag y) {
      return a == x && b == y || a == y && b == x;
   }

   public static List<PersonaTag> pick() {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      PersonaTag first = values()[rng.nextInt(values().length)];
      List<PersonaTag> out = new ArrayList<>();
      out.add(first);
      if (rng.nextBoolean()) {
         for (int i = 0; i < 12; i++) {
            PersonaTag second = values()[rng.nextInt(values().length)];
            if (second != first && !first.mutexWith(second)) {
               out.add(second);
               break;
            }
         }
      }
      return out;
   }
}
