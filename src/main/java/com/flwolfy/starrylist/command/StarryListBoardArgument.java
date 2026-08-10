package com.flwolfy.starrylist.command;

import com.flwolfy.starrylist.StarryListMod;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

/** Shared board-id parsing and Brigadier suggestions. */
public final class StarryListBoardArgument {

  private StarryListBoardArgument() {}

  public static final SuggestionProvider<CommandSourceStack> ENABLED = (context, builder) -> {
    var runtime = StarryListMod.runtime();
    return SharedSuggestionProvider.suggest(
        runtime == null ? java.util.stream.Stream.empty()
            : runtime.registry().enabled().stream().map(board -> board.id()),
        builder
    );
  };

  public static String normalize(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
