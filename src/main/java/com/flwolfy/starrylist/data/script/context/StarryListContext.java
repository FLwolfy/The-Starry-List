package com.flwolfy.starrylist.data.script.context;

/** A typed, immutable JEXL event context. Exactly one nested event record is normally non-null. */
public record StarryListContext(
    BlockBreak blockBreak,
    BlockPlace blockPlace,
    MobKill mobKill,
    PlayerKill playerKill,
    PlayerDeath playerDeath,
    Travel travel,
    Presence join,
    Presence leave,
    Presence scheduled
) {

  public record BlockBreak(
      StarryListBlock block,
      StarryListPosition position,
      StarryListItem tool
  ) {}

  public record BlockPlace(
      StarryListBlock block,
      StarryListPosition position,
      StarryListItem item
  ) {}

  public record MobKill(StarryListEntity victim, String damageType) {}

  public record PlayerKill(StarryListPlayer victim, String damageType) {}

  public record PlayerDeath(StarryListPlayer killer, String damageType) {}

  public record Travel(
      int centimeters,
      double blocks,
      String movementType,
      StarryListPosition position
  ) {}

  public record Presence(StarryListPosition position) {}

  public static StarryListContext blockBreak(BlockBreak value) {
    return new StarryListContext(value, null, null, null, null, null, null, null, null);
  }

  public static StarryListContext blockPlace(BlockPlace value) {
    return new StarryListContext(null, value, null, null, null, null, null, null, null);
  }

  public static StarryListContext mobKill(MobKill value) {
    return new StarryListContext(null, null, value, null, null, null, null, null, null);
  }

  public static StarryListContext playerKill(PlayerKill value) {
    return new StarryListContext(null, null, null, value, null, null, null, null, null);
  }

  public static StarryListContext playerDeath(PlayerDeath value) {
    return new StarryListContext(null, null, null, null, value, null, null, null, null);
  }

  public static StarryListContext travel(Travel value) {
    return new StarryListContext(null, null, null, null, null, value, null, null, null);
  }

  public static StarryListContext join(Presence value) {
    return new StarryListContext(null, null, null, null, null, null, value, null, null);
  }

  public static StarryListContext leave(Presence value) {
    return new StarryListContext(null, null, null, null, null, null, null, value, null);
  }

  public static StarryListContext scheduled(Presence value) {
    return new StarryListContext(null, null, null, null, null, null, null, null, value);
  }
}
