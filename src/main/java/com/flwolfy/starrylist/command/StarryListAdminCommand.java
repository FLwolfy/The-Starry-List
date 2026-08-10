package com.flwolfy.starrylist.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.StarryListRuntime;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

/** Implements administrative reload, score, and profile commands. */
public final class StarryListAdminCommand {

  private StarryListAdminCommand() {}

  /**
   * Registers the administrative {@code /starryadmin} command tree.
   *
   * @param dispatcher server command dispatcher
   */
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(literal("starryadmin")
        .requires(StarryListAdminCommand::hasPermission)
        .executes(context -> success(context, "starrylist.admin.help"))
        .then(literal("reload").executes(StarryListAdminCommand::reload))
        .then(literal("score")
            .then(literal("get")
                .then(boardArgument()
                    .then(argument("player", EntityArgument.player())
                        .executes(StarryListAdminCommand::scoreGet))))
            .then(literal("set")
                .then(boardArgument()
                    .then(argument("targets", EntityArgument.players())
                        .then(argument("value", IntegerArgumentType.integer())
                            .executes(context -> mutateScores(context, Mutation.SET))))))
            .then(literal("add")
                .then(boardArgument()
                    .then(argument("targets", EntityArgument.players())
                        .then(argument("value", IntegerArgumentType.integer())
                            .executes(context -> mutateScores(context, Mutation.ADD))))))
            .then(literal("reset")
                .then(boardArgument()
                    .then(argument("targets", EntityArgument.players())
                        .executes(context -> mutateScores(context, Mutation.RESET)))))
            .then(literal("reset-all")
                .then(boardArgument().executes(StarryListAdminCommand::scoreResetAll))))
        .then(literal("profile")
            .then(literal("get")
                .then(argument("player", EntityArgument.player())
                    .executes(StarryListAdminCommand::profileGet)))
            .then(literal("reset")
                .then(argument("targets", EntityArgument.players())
                    .executes(StarryListAdminCommand::profileReset)))
            .then(literal("reset-all").executes(StarryListAdminCommand::profileResetAll))));
  }

  private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
      boardArgument() {
    return argument("boardId", StringArgumentType.word())
        .suggests(StarryListBoardArgument.ENABLED);
  }

  private static boolean hasPermission(CommandSourceStack source) {
    if (source.getEntity() == null) return true;
    PermissionSet permissions = source.permissions();
    if (permissions == PermissionSet.ALL_PERMISSIONS) return true;
    int required = StarryListConfigManager.getInstance().data().general().adminPermissionLevel();
    return permissions instanceof LevelBasedPermissionSet levels
        && levels.level().isEqualOrHigherThan(PermissionLevel.byId(required));
  }

  private static int reload(CommandContext<CommandSourceStack> context) {
    if (!StarryListConfigManager.getInstance().reload()) {
      return failure(context, "starrylist.admin.reload_failed");
    }
    return success(context, "starrylist.admin.reload_success");
  }

  private static int scoreGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    StarryListRuntime runtime = runtime();
    ServerPlayer player = EntityArgument.getPlayer(context, "player");
    String board = board(context);
    var definition = runtime.registry().get(board).orElse(null);
    if (definition == null) return failure(context, "starrylist.command.invalid_board");
    int score = runtime.scores().get(board, player.getUUID());
    context.getSource().sendSuccess(() -> text(
        "starrylist.admin.score_get",
        definition.objectiveName(),
        player.getUUID(),
        player.getGameProfile().name(),
        score
    ), false);
    return score;
  }

  private static int mutateScores(
      CommandContext<CommandSourceStack> context,
      Mutation mutation
  ) throws CommandSyntaxException {
    StarryListRuntime runtime = runtime();
    String board = board(context);
    if (runtime.registry().get(board).isEmpty()) {
      return failure(context, "starrylist.command.invalid_board");
    }
    Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
    int value = mutation == Mutation.RESET ? 0 : IntegerArgumentType.getInteger(context, "value");
    int affected = 0;
    for (ServerPlayer player : players) {
      if (mutation == Mutation.SET) runtime.scores().set(board, player, value);
      if (mutation == Mutation.ADD) runtime.scores().add(board, player, value);
      if (mutation == Mutation.RESET) runtime.scores().reset(board, player.getUUID());
      affected++;
    }
    int result = affected;
    context.getSource().sendSuccess(() -> text("starrylist.admin.score_updated", result), true);
    return affected;
  }

  private static int scoreResetAll(CommandContext<CommandSourceStack> context) {
    StarryListRuntime runtime = runtime();
    String board = board(context);
    if (runtime.registry().get(board).isEmpty()) {
      return failure(context, "starrylist.command.invalid_board");
    }
    int affected = runtime.scores().resetAll(board);
    context.getSource().sendSuccess(() -> text("starrylist.admin.score_updated", affected), true);
    return affected;
  }

  private static int profileGet(CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    StarryListRuntime runtime = runtime();
    ServerPlayer player = EntityArgument.getPlayer(context, "player");
    var profile = runtime.state().profile(player.getUUID());
    context.getSource().sendSuccess(() -> text(
        "starrylist.admin.profile_get",
        player.getGameProfile().name(),
        profile.mode(),
        String.join(", ", profile.boards()),
        profile.rotationEnabled(),
        profile.rotationIntervalSeconds()
    ), false);
    return 1;
  }

  private static int profileReset(CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    StarryListRuntime runtime = runtime();
    Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
    players.forEach(player -> {
      runtime.state().resetProfile(player.getUUID());
      runtime.display().update(player, true);
    });
    int affected = players.size();
    context.getSource().sendSuccess(() -> text("starrylist.admin.profile_reset", affected), true);
    return affected;
  }

  private static int profileResetAll(CommandContext<CommandSourceStack> context) {
    StarryListRuntime runtime = runtime();
    int affected = runtime.state().clearProfiles();
    runtime.display().updateAll(true);
    context.getSource().sendSuccess(() -> text("starrylist.admin.profile_reset", affected), true);
    return affected;
  }

  private static StarryListRuntime runtime() {
    StarryListRuntime runtime = StarryListMod.runtime();
    if (runtime == null) throw new IllegalStateException("StarryList server is not ready");
    return runtime;
  }

  private static String board(CommandContext<CommandSourceStack> context) {
    return StarryListBoardArgument.normalize(StringArgumentType.getString(context, "boardId"));
  }

  private static int success(CommandContext<CommandSourceStack> context, String key) {
    context.getSource().sendSuccess(() -> text(key), false);
    return 1;
  }

  private static int failure(CommandContext<CommandSourceStack> context, String key) {
    context.getSource().sendFailure(text(key));
    return 0;
  }

  private static Component text(String key, Object... arguments) {
    return StarryListLangManager.getInstance().text(key, arguments);
  }

  private enum Mutation {
    SET,
    ADD,
    RESET
  }
}
