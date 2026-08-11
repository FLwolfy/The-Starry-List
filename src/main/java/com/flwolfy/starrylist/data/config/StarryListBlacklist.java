package com.flwolfy.starrylist.data.config;

import java.util.List;
import java.util.regex.Pattern;

/** Compiled, immutable player-name blacklist used by server-side score services. */
public final class StarryListBlacklist {

  private volatile List<Pattern> patterns = List.of();

  /**
   * Recompiles all validated patterns from the active configuration.
   *
   * @param config the active configuration
   */
  public void apply(StarryListConfigData config) {
    patterns = config.blacklist().playerNamePatterns().stream()
        .map(expression -> Pattern.compile(
            expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ))
        .toList();
  }

  /**
   * Checks whether a full player name matches a configured expression.
   *
   * @param playerName the complete player name
   * @return whether the name is blacklisted
   */
  public boolean matches(String playerName) {
    if (playerName == null) {
      return false;
    }

    return patterns.stream().anyMatch(pattern -> pattern.matcher(playerName).matches());
  }
}
