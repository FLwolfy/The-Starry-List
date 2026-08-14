package com.flwolfy.starrylist.command;

import com.flwolfy.starrylist.StarryListMod;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

/** Shared board-ID parsing and Brigadier suggestions. */
final class StarryListBoardArgument {

  private StarryListBoardArgument() {}

  static final SuggestionProvider<CommandSourceStack> ENABLED = (context, builder) -> {
    var runtime = StarryListMod.getRuntime();
    return SharedSuggestionProvider.suggest(
        runtime == null ? java.util.stream.Stream.empty()
            : runtime.registry().all().stream().map(board -> board.id()),
        builder
    );
  };

  static final SuggestionProvider<CommandSourceStack> RECALCULATABLE = (context, builder) -> {
    var runtime = StarryListMod.getRuntime();
    return SharedSuggestionProvider.suggest(
        runtime == null ? java.util.stream.Stream.empty()
            : runtime.registry().all().stream()
                .filter(board -> board.supportsRecalculation())
                .map(board -> board.id()),
        builder
    );
  };

  static String normalize(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
