package net.exmo.sreGame.games.dodgeball;

import net.minecraft.ChatFormatting;
import org.joml.Vector3f;

public enum DodgeballTeam {
   RED("红", "&c", ChatFormatting.RED, 0xC62828, new Vector3f(1.0F, 0.18F, 0.18F)),
   BLUE("蓝", "&9", ChatFormatting.BLUE, 0x1565C0, new Vector3f(0.2F, 0.45F, 1.0F));

   private final String label;
   private final String code;
   private final ChatFormatting formatting;
   private final int leather;
   private final Vector3f dust;

   DodgeballTeam(String label, String code, ChatFormatting formatting, int leather, Vector3f dust) {
      this.label = label;
      this.code = code;
      this.formatting = formatting;
      this.leather = leather;
      this.dust = dust;
   }

   public String label() {
      return this.label;
   }

   public String code() {
      return this.code;
   }

   public String display() {
      return this.code + this.label + "队";
   }

   public ChatFormatting formatting() {
      return this.formatting;
   }

   public int leather() {
      return this.leather;
   }

   public Vector3f dust() {
      return this.dust;
   }

   public DodgeballTeam other() {
      return this == RED ? BLUE : RED;
   }
}
