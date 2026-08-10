package com.flwolfy.starrylist.scoreboard;

import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Identifies one of the six fixed leaderboards and its vanilla scoreboard objective.
 *
 * @param id command and configuration identifier
 * @param objectiveName vanilla scoreboard objective name
 * @param translationKey bundled translation key
 * @param icon item used by visual configuration menus
 */
public record StarryListBoardDefinition(
    String id,
    String objectiveName,
    String translationKey,
    Item icon
) {

  /**
   * Resolves this board's name using the configured server language.
   *
   * @return translated board name
   */
  public Component displayName() {
    return StarryListLangManager.getInstance().text(translationKey);
  }

  /**
   * Resolves this board's name using a player's reported client language when available.
   *
   * @param player player viewing the name
   * @return translated board name
   */
  public Component displayName(ServerPlayer player) {
    return StarryListLangManager.getInstance().textFor(
        player.clientInformation().language(), translationKey
    );
  }
}
