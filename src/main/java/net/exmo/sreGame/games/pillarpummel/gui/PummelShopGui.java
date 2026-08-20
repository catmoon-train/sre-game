package net.exmo.sreGame.games.pillarpummel.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.games.pillarpummel.PillarPummelMatch;
import net.exmo.sreGame.games.pillarpummel.PummelShop;
import net.exmo.sreGame.games.pillarpummel.PummelTeam;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class PummelShopGui {
   private static final String[] IDS = {
      PummelShop.TNT, PummelShop.TNTPACK, PummelShop.FORT, PummelShop.TURRET, PummelShop.BLOCKGEN,
      PummelShop.BOW, PummelShop.REPAIR, PummelShop.NUKE,
      PummelShop.SHIELD, PummelShop.SPEED, PummelShop.JUMP, PummelShop.HEAL,
      PummelShop.GAPPLE, PummelShop.PEARL, PummelShop.WEB, PummelShop.POWDER,
      PummelShop.SNOWBALL, PummelShop.ARROWS, PummelShop.ROD,
      PummelShop.STONE, PummelShop.IRON, PummelShop.DIAMOND,
      PummelShop.WATER, PummelShop.MILK, PummelShop.WIND, PummelShop.TOTEM
   };
   private static final String[] ICONS = {
      "tnt", "tnt", "stone_bricks", "iron_block", "diamond_block",
      "bow", "snow_block", "carrot_on_a_stick",
      "shield", "potion", "potion", "potion",
      "golden_apple", "ender_pearl", "cobweb", "red_concrete_powder",
      "snowball", "arrow", "fishing_rod",
      "cobblestone", "iron_block", "obsidian",
      "water_bucket", "milk_bucket", "wind_charge", "totem_of_undying"
   };
   private static final String[] NAMES = {
      "&cTNT", "&cTNT ×3", "&d堡垒", "&d防御塔", "&a方块生成器",
      "&f弓", "&6维修工具盒", "&4☢ 核弹",
      "&f盾牌", "&b速度药水", "&a跳跃药水", "&c治疗药水",
      "&6金苹果", "&5末影珍珠 ×2", "&f蜘蛛网 ×4", "&f粉末 ×8",
      "&f雪球 ×8", "&f箭 ×8", "&b钓鱼竿",
      "&7圆石 ×16", "&f铁块 ×2", "&5黑曜石 ×4",
      "&9水桶", "&f牛奶", "&b风弹 ×4", "&6不死图腾"
   };

   private PummelShopGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, PillarPummelMatch match) {
      SimpleContainer container = new SimpleContainer(54);
      fill(player, container, match);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player, match),
         TextUtil.color("&6⌂ &f军火商")
      ));
   }

   private static void fill(ServerPlayer player, SimpleContainer container, PillarPummelMatch match) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      PummelTeam team = match.teamOf(player.getUUID());
      int wool = PummelShop.woolCount(player, team);
      container.setItem(4, GuiItems.named("red_concrete_powder", "&f可用粉末 &e" + wool,
         List.of("&7背包 + 团队箱", "&7白羊毛/敌方粉末会自动换成队色")));
      int[] slots = {
         10, 11, 12, 13, 14, 15, 16,
         19, 20, 21, 22, 23, 24, 25,
         28, 29, 30, 31, 32, 33, 34,
         37, 38, 39, 40, 41
      };
      for (int i = 0; i < IDS.length && i < slots.length; i++) {
         int price = PummelShop.price(IDS[i], match.settings());
         container.setItem(slots[i], GuiItems.action(ICONS[i], NAMES[i] + " &e" + price + " 粉末",
            List.of(wool >= price ? "&a点击兑换" : "&c粉末不足"), "buy", "id", IDS[i]));
      }
      container.setItem(49, GuiItems.action("barrier", "&c关闭", List.of(), "close"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;
      private final PillarPummelMatch match;

      Menu(int syncId, Inventory inv, SimpleContainer container, GameContext ctx, ServerPlayer viewer,
         PillarPummelMatch match) {
         super(syncId, inv, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
         this.match = match;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         if ("close".equals(action)) {
            player.closeContainer();
            return;
         }
         if ("buy".equals(action)) {
            String id = GuiItems.extraTag(this.getSlot(slotId).getItem(), "id");
            if (id != null) {
               this.match.buy(player, id);
               fill(player, this.container, this.match);
            }
         }
      }
   }
}
