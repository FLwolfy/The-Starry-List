package com.flwolfy.starrylist.event;

import com.google.gson.annotations.SerializedName;
import java.util.Locale;

public enum StarryListEventType {
  @SerializedName("block_break")
  BLOCK_BREAK,
  @SerializedName("block_place")
  BLOCK_PLACE,
  @SerializedName("mob_kill")
  MOB_KILL,
  @SerializedName("player_kill")
  PLAYER_KILL,
  @SerializedName("player_death")
  PLAYER_DEATH,
  @SerializedName("travel")
  TRAVEL,
  @SerializedName("player_join")
  PLAYER_JOIN,
  @SerializedName("player_leave")
  PLAYER_LEAVE,
  @SerializedName("scheduled")
  SCHEDULED;

  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static StarryListEventType fromKey(String key) {
    if (key == null) return null;
    try {
      return valueOf(key.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
