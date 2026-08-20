package net.exmo.sreGame.games.fakehuman;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.gui.GuiItems;
import net.minecraft.world.item.ItemStack;

public final class FakeHumanKits {
   public static final String ADMIT = "fh_admit";
   public static final String REFUSE = "fh_refuse";
   public static final String STONE = "fh_stone";
   public static final String GUN = "fh_gun";
   public static final String ROPE = "fh_rope";
   public static final String INSPECT = "fh_inspect";
   public static final String ID_ASK = "fh_id_ask";
   public static final String NIGHT = "fh_night";
   public static final String KNOCK = "fh_knock";
   public static final String ID = "fh_id";
   public static final String ID_SHOW = "fh_id_show";
   public static final String ID_REFUSE = "fh_id_refuse";
   public static final String ACCUSE = "fh_accuse";
   public static final String VOUCH = "fh_vouch";

   private FakeHumanKits() {
   }

   public static List<ItemStack> keeperDoor(boolean canNight) {
      List<ItemStack> items = new ArrayList<>();
      items.add(GuiItems.action("oak_door", "&a请进", List.of("&7让门口访客进屋"), ADMIT));
      items.add(GuiItems.action("barrier", "&c拒之门外", List.of("&7请回未到访池，以后还能再来"), REFUSE));
      items.add(GuiItems.action("name_tag", "&f要求出示证件", List.of("&7对方可拒绝"), ID_ASK));
      items.add(GuiItems.action("clock", canNight ? "&6进入夜晚" : "&8进入夜晚",
         List.of(canNight ? "&7今日访客已处理" : "&7先处理完门口的人"), NIGHT));
      return items;
   }

   public static void addSupplies(List<ItemStack> items, int stones, int ammo, int ropes, int inspects) {
      if (stones > 0) {
         items.add(GuiItems.action("ender_eye", "&6驱逐之石 &e×" + stones, List.of("&7永久驱逐并揭晓", "&a伪人 +1 信任  &c真人 -2"), STONE));
      }
      if (ammo > 0) {
         items.add(GuiItems.action("crossbow", "&c击毙 &e弹药 " + ammo, List.of("&7击毙并显形", "&a伪人 +1 信任  &c真人 -3"), GUN));
      }
      if (ropes > 0) {
         items.add(GuiItems.action("lead", "&e绳子 &e×" + ropes, List.of("&7捆绑屋内访客", "&7他人靠近交互 10 秒可解"), ROPE));
      }
      if (inspects > 0) {
         items.add(GuiItems.action("spyglass", "&b查验药剂 &e×" + inspects, List.of("&7每次查验消耗 1", "&7结果仅你可见"), INSPECT));
      }
   }

   public static List<ItemStack> arriver(FakeHumanPlayer state) {
      List<ItemStack> items = new ArrayList<>();
      items.add(GuiItems.action("oak_door", "&e敲门", List.of("&7提醒屋主你在门外"), KNOCK));
      items.add(GuiItems.action("written_book", "&f证件 · " + (state == null ? "?" : state.alias()),
         state == null || state.card() == null ? List.of("&e右键自己看") : state.card().lore(), ID));
      items.add(GuiItems.action("paper", "&a出示证件", List.of("&7屋主要求后点此出示"), ID_SHOW));
      items.add(GuiItems.action("barrier", "&c拒绝出示", List.of("&7会留下嫌疑"), ID_REFUSE));
      if (state != null && state.supply() != null) {
         items.add(GuiItems.named(state.supply().icon(), "&7随身补给 · " + state.supply().display(),
            List.of("&8进屋后交给屋主", "&7现在不能使用")));
      }
      return items;
   }

   public static List<ItemStack> insideGuest(FakeHumanPlayer state) {
      List<ItemStack> items = new ArrayList<>();
      items.add(GuiItems.action("written_book", "&f证件 · " + (state == null ? "?" : state.alias()),
         state == null || state.card() == null ? List.of() : state.card().lore(), ID));
      items.add(GuiItems.action("redstone", "&c指认", List.of("&7标记可疑的人"), ACCUSE));
      items.add(GuiItems.action("emerald", "&a担保", List.of("&7为某人作保"), VOUCH));
      return items;
   }

   public static List<ItemStack> spectator() {
      return List.of(GuiItems.named("snowball", "&7旁观门口", List.of(
         "&7今日还没轮到你", "&7能听见隔门对话，不能说话"
      )));
   }

   public static List<ItemStack> deadWatch() {
      return List.of(GuiItems.named("skeleton_skull", "&8等待再访", List.of("&7今夜之后将以新身份上门")));
   }
}
