package com.flwolfy.starrylist.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.StarryListRuntime;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import com.flwolfy.starrylist.data.state.StarryListDisplayProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Implements the complete player-facing /starry display command tree. */
public final class StarryListCommand {

  private StarryListCommand() {}

  /**
   * Registers the player-facing {@code /starry} command tree.
   *
   * @param dispatcher server command dispatcher
   */
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(literal("starry")
        .executes(StarryListCommand::status)
        .then(literal("boards")
            .executes(context -> boards(context, 1))
            .then(argument("page", IntegerArgumentType.integer(1))
                .executes(context -> boards(context, IntegerArgumentType.getInteger(context, "page")))))
        .then(literal("display")
            .then(literal("status").executes(StarryListCommand::status))
            .then(literal("default").executes(StarryListCommand::resetDefault))
            .then(literal("hide").executes(StarryListCommand::hide))
            .then(literal("set")
                .then(argument("boardIds", StringArgumentType.greedyString())
                    .suggests(StarryListBoardArgument.ENABLED)
                    .executes(StarryListCommand::setBoards)))
            .then(literal("add")
                .then(argument("boardId", StringArgumentType.word())
                    .suggests(StarryListBoardArgument.ENABLED)
                    .executes(StarryListCommand::addBoard)))
            .then(literal("remove")
                .then(argument("boardId", StringArgumentType.word())
                    .suggests(StarryListBoardArgument.ENABLED)
                    .executes(StarryListCommand::removeBoard)))
            .then(literal("move")
                .then(argument("boardId", StringArgumentType.word())
                    .suggests(StarryListBoardArgument.ENABLED)
                    .then(argument("index", IntegerArgumentType.integer(1))
                        .executes(StarryListCommand::moveBoard))))
            .then(literal("rotation")
                .then(argument("enabled", BoolArgumentType.bool())
                    .executes(StarryListCommand::rotation)))
            .then(literal("interval")
                .then(argument("seconds", IntegerArgumentType.integer(1, 3600))
                    .executes(StarryListCommand::interval)))));
  }

  private static int status(CommandContext<CommandSourceStack> context) {
    StarryListRuntime runtime = requireRuntime(context);
    ServerPlayer player = context.getSource().getPlayer();
    if (player == null) {
      context.getSource().sendSuccess(() -> text("starrylist.command.console_help"), false);
      return 1;
    }
    var profile = runtime.state().profile(player.getUUID());
    var effective = runtime.display().effective(player.getUUID());
    context.getSource().sendSuccess(() -> text(
        "starrylist.command.status",
        profile.mode().name(),
        effective.hidden() ? "-" : String.join(", ", effective.boards()),
        effective.rotationEnabled(),
        effective.intervalSeconds()
    ), false);
    return 1;
  }

  private static int boards(CommandContext<CommandSourceStack> context, int page) {
    StarryListRuntime runtime = requireRuntime(context);
    List<?> all = runtime.registry().all().stream().toList();
    int pages = Math.max(1, (all.size() + 7) / 8);
    int selectedPage = Math.min(page, pages);
    int start = (selectedPage - 1) * 8;
    context.getSource().sendSuccess(() -> text(
        "starrylist.command.boards_header", selectedPage, pages
    ), false);
    runtime.registry().all().stream().skip(start).limit(8).forEach(board ->
        context.getSource().sendSuccess(() -> Component.literal(
            board.id() + " - " + board.displayName()
        ), false)
    );
    return 1;
  }

  private static int resetDefault(CommandContext<CommandSourceStack> context) {
    ServerPlayer player = player(context);
    StarryListRuntime runtime = requireRuntime(context);
    runtime.state().resetProfile(player.getUUID());
    runtime.display().update(player, true);
    return success(context, "starrylist.command.display_default");
  }

  private static int hide(CommandContext<CommandSourceStack> context) {
    ServerPlayer player = player(context);
    StarryListRuntime runtime = requireRuntime(context);
    runtime.state().setProfile(player.getUUID(), new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.HIDDEN, List.of(), false, 20
    ));
    runtime.display().update(player, true);
    return success(context, "starrylist.command.display_hidden");
  }

  private static int setBoards(CommandContext<CommandSourceStack> context) {
    List<String> requested = parseBoardList(StringArgumentType.getString(context, "boardIds"));
    if (requested.isEmpty() || !validBoards(requireRuntime(context), requested)) {
      return failure(context, "starrylist.command.invalid_board");
    }
    ServerPlayer player = player(context);
    StarryListRuntime runtime = requireRuntime(context);
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    save(runtime, player, new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.CUSTOM,
        requested,
        editable.rotationEnabled(),
        editable.rotationIntervalSeconds()
    ));
    return success(context, "starrylist.command.display_updated");
  }

  private static int addBoard(CommandContext<CommandSourceStack> context) {
    StarryListRuntime runtime = requireRuntime(context);
    ServerPlayer player = player(context);
    String board = StarryListBoardArgument.normalize(StringArgumentType.getString(context, "boardId"));
    if (!validBoards(runtime, List.of(board))) return failure(context, "starrylist.command.invalid_board");
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    List<String> boards = new ArrayList<>(editable.boards());
    if (!boards.contains(board)) boards.add(board);
    save(runtime, player, copy(editable, boards));
    return success(context, "starrylist.command.display_updated");
  }

  private static int removeBoard(CommandContext<CommandSourceStack> context) {
    StarryListRuntime runtime = requireRuntime(context);
    ServerPlayer player = player(context);
    String board = StarryListBoardArgument.normalize(StringArgumentType.getString(context, "boardId"));
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    List<String> boards = new ArrayList<>(editable.boards());
    if (!boards.remove(board)) return failure(context, "starrylist.command.invalid_board");
    if (boards.isEmpty()) return failure(context, "starrylist.command.cannot_remove_last");
    save(runtime, player, copy(editable, boards));
    return success(context, "starrylist.command.display_updated");
  }

  private static int moveBoard(CommandContext<CommandSourceStack> context) {
    StarryListRuntime runtime = requireRuntime(context);
    ServerPlayer player = player(context);
    String board = StarryListBoardArgument.normalize(StringArgumentType.getString(context, "boardId"));
    int requestedIndex = IntegerArgumentType.getInteger(context, "index");
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    List<String> boards = new ArrayList<>(editable.boards());
    if (!boards.remove(board) || requestedIndex > boards.size() + 1) {
      return failure(context, "starrylist.command.invalid_board_or_index");
    }
    boards.add(requestedIndex - 1, board);
    save(runtime, player, copy(editable, boards));
    return success(context, "starrylist.command.display_updated");
  }

  private static int rotation(CommandContext<CommandSourceStack> context) {
    StarryListRuntime runtime = requireRuntime(context);
    ServerPlayer player = player(context);
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    save(runtime, player, new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.CUSTOM,
        editable.boards(),
        BoolArgumentType.getBool(context, "enabled"),
        editable.rotationIntervalSeconds()
    ));
    return success(context, "starrylist.command.display_updated");
  }

  private static int interval(CommandContext<CommandSourceStack> context) {
    StarryListRuntime runtime = requireRuntime(context);
    ServerPlayer player = player(context);
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    save(runtime, player, new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.CUSTOM,
        editable.boards(),
        editable.rotationEnabled(),
        IntegerArgumentType.getInteger(context, "seconds")
    ));
    return success(context, "starrylist.command.display_updated");
  }

  private static void save(
      StarryListRuntime runtime,
      ServerPlayer player,
      StarryListDisplayProfile profile
  ) {
    runtime.state().setProfile(player.getUUID(), profile);
    runtime.display().update(player, true);
  }

  private static StarryListDisplayProfile copy(StarryListDisplayProfile source, List<String> boards) {
    return new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.CUSTOM,
        boards,
        source.rotationEnabled(),
        source.rotationIntervalSeconds()
    );
  }

  private static List<String> parseBoardList(String input) {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    for (String part : input.split("[,\\s]+")) {
      String normalized = StarryListBoardArgument.normalize(part);
      if (!normalized.isBlank()) ids.add(normalized);
    }
    return List.copyOf(ids);
  }

  private static boolean validBoards(StarryListRuntime runtime, List<String> ids) {
    return ids.stream().allMatch(id -> runtime.registry().get(id).isPresent());
  }

  private static ServerPlayer player(CommandContext<CommandSourceStack> context) {
    ServerPlayer player = context.getSource().getPlayer();
    if (player == null) throw new IllegalStateException("This command requires a player");
    return player;
  }

  private static StarryListRuntime requireRuntime(CommandContext<CommandSourceStack> context) {
    StarryListRuntime runtime = StarryListMod.runtime();
    if (runtime == null) throw new IllegalStateException("StarryList server is not ready");
    return runtime;
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
}
