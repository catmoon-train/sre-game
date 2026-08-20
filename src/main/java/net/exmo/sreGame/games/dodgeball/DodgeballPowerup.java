package net.exmo.sreGame.games.dodgeball;

import java.util.List;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.world.item.ItemStack;

public enum DodgeballPowerup {
   SPEED("加速", "golden_boots", "&e拾取：移动速度 +40%，5 秒"),
   SHIELD("护盾", "shield", "&b拾取：免疫下一次雪球，10 秒"),
   TRIPLE("双倍球", "fire_charge", "&6拾取：下次投掷 3 个雪球"),
   JUMP("跳跃提升", "slime_ball", "&a拾取：跳跃高度提升，5 秒"),
   HOMING("追踪球", "ender_eye", "&d拾取：下次雪球轻微追踪敌人");

   private final String label;
   private final String icon;
   private final String lore;

   DodgeballPowerup(String label, String icon, String lore) {
      this.label = label;
      this.icon = icon;
      this.lore = lore;
   }

   public String label() {
      return this.label;
   }

   public String icon() {
      return this.icon;
   }

   public ItemStack stack() {
      ItemStack item = GuiItems.named(this.icon, "&e" + this.label, List.of(this.lore, "&7场地道具"));
      item.setCount(1);
      return item;
   }
}
