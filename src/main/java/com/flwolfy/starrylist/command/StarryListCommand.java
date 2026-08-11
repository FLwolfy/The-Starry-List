package com.flwolfy.starrylist.command;

import static net.minecraft.commands.Commands.literal;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import com.flwolfy.starrylist.display.StarryListPlayerSGUI;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Opens the player-facing visual leaderboard settings menu. */
public final class StarryListCommand {

  private StarryListCommand() {}

  /**
   * Registers the parameterless {@code /starry} GUI command.
   *
   * @param dispatcher server command dispatcher
   */
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(literal("starry").executes(context -> {
      ServerPlayer player = context.getSource().getPlayer();
      if (player == null) {
        context.getSource().sendFailure(
            StarryListLangManager.getInstance().text("starrylist.command.player_only")
        );
        return 0;
      }
      var runtime = StarryListMod.getRuntime();
      if (runtime == null) {
        context.getSource().sendFailure(
            StarryListLangManager.getInstance().text("starrylist.command.not_ready")
        );
        return 0;
      }
      StarryListPlayerSGUI.open(player, runtime);
      return 1;
    }));
  }
}
