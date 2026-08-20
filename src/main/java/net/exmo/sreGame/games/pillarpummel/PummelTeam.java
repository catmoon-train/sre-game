package net.exmo.sreGame.games.pillarpummel;

import net.minecraft.world.SimpleContainer;

public final class PummelTeam {
   final int id;
   final PummelColor color;
   int score;
   int kills;
   int occupyBoostTicks;
   final SimpleContainer storage = new SimpleContainer(27);

   PummelTeam(int id) {
      this.id = id;
      this.color = PummelColor.of(id);
   }

   public String display() {
      return this.color.code() + this.color.label() + "队";
   }

   int woolStored() {
      int total = 0;
      for (int i = 0; i < this.storage.getContainerSize(); i++) {
         if (PummelShop.isWool(this.storage.getItem(i))) {
            total += this.storage.getItem(i).getCount();
         }
      }
      return total;
   }
}
