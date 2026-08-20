package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.input.ChatPrompt;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomChatMode;
import net.exmo.sreGame.room.RoomState;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class RoomSettingsGui {
   private RoomSettingsGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player) {
      GameRoom room = ctx.rooms().getByPlayer(player.getUUID());
      if (room == null || !room.isHost(player.getUUID())) {
         ctx.send(player, "&c只有房主可以改房间设置。");
         RoomPanelGui.open(ctx, player);
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&e⚙ &f房间设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      RoomChatMode chat = room.chatMode();
      container.setItem(10, GuiItems.action("name_tag", "&f房间名称", List.of("&7当前： &f" + room.displayName(), "&e点击后在聊天框输入"), "name"));
      container.setItem(12, GuiItems.action(room.hasPassword() ? "iron_door" : "tripwire_hook",
         room.hasPassword() ? "&c已设密码" : "&7无密码",
         List.of("&e点击后在聊天框输入", "&7输入空内容可清除"), "password"));
      container.setItem(14, GuiItems.action(room.publicRoom() ? "lime_dye" : "gray_dye",
         room.publicRoom() ? "&a公开房间" : "&8私密房间",
         List.of("&7公开会出现在房间列表", "&e点击切换"), "visibility"));
      container.setItem(16, GuiItems.action(chat.icon(), "&f聊天： &e" + chat.label(), List.of(
         chat.lore1(),
         chat.lore2(),
         "&e点击切换 只收房间 / 不外放 / 全部"
      ), "chat"));
      container.setItem(22, GuiItems.action(room.autoReady() ? "lime_dye" : "gray_dye",
         room.autoReady() ? "&a自动准备：开" : "&8自动准备：关",
         List.of("&7加入房间时自动准备", "&7默认开启，仍可手动取消准备", "&e点击切换"), "autoready"));
      container.setItem(31, GuiItems.action("arrow", "&7返回房间", List.of(), "back"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, GameContext ctx, ServerPlayer viewer) {
         super(syncId, playerInv, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
         if (action == null || room == null || !room.isHost(player.getUUID())) {
            return;
         }
         switch (action) {
            case "back" -> RoomPanelGui.open(this.ctx, player);
            case "name" -> {
               player.closeContainer();
               ChatPrompt.await(player, ChatPrompt.Kind.ROOM_NAME, room.id(), "&a请在聊天框输入新的房间名称");
            }
            case "password" -> {
               player.closeContainer();
               ChatPrompt.await(player, ChatPrompt.Kind.ROOM_PASSWORD, room.id(), "&a请在聊天框输入新密码（空内容清除）");
            }
            case "visibility" -> {
               if (room.state() == RoomState.WAITING) {
                  room.setPublicRoom(!room.publicRoom());
                  fill(this.container, room);
               }
            }
            case "chat" -> {
               room.setChatMode(room.chatMode().next());
               this.ctx.broadcast(room, "&7聊天范围改为 &e" + room.chatMode().label());
               fill(this.container, room);
            }
            case "autoready" -> {
               if (room.state() == RoomState.WAITING) {
                  room.setAutoReady(!room.autoReady());
                  this.ctx.broadcast(room, room.autoReady() ? "&a已开启自动准备。" : "&7已关闭自动准备。");
                  fill(this.container, room);
               }
            }
            default -> {
            }
         }
      }
   }
}
