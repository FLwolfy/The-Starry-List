package com.flwolfy.starrylist.data.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.flwolfy.starrylist.data.config.StarryListConfigData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-scoped display preferences, travel remainders, and blacklisted score archives. */
public final class StarryListState extends SavedData {

  private static final Codec<StarryListState> CODEC = RecordCodecBuilder.create(instance ->
      instance.group(
          Codec.unboundedMap(Codec.STRING, StarryListDisplayProfile.CODEC)
              .optionalFieldOf("profiles", Map.of()).forGetter(state -> state.serializedProfiles()),
          Codec.unboundedMap(Codec.STRING, Codec.INT)
              .optionalFieldOf("travelRemainders", Map.of()).forGetter(state -> state.travelRemainders),
          Codec.unboundedMap(Codec.STRING, Codec.STRING)
              .optionalFieldOf("archivedPlayerNames", Map.of())
              .forGetter(state -> state.archivedPlayerNames),
          Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT))
              .optionalFieldOf("archivedScores", Map.of()).forGetter(state -> state.archivedScores)
      ).apply(instance, StarryListState::new)
  );

  /** SavedData type used to load and persist StarryList world state. */
  public static final SavedDataType<StarryListState> TYPE = new SavedDataType<>(
      Identifier.fromNamespaceAndPath("the-starry-list", "state"),
      StarryListState::new,
      CODEC,
      DataFixTypes.LEVEL
  );

  private final Map<UUID, StarryListDisplayProfile> profiles = new HashMap<>();
  private final Map<String, Integer> travelRemainders = new HashMap<>();
  private final Map<String, String> archivedPlayerNames = new HashMap<>();
  private final Map<String, Map<String, Integer>> archivedScores = new HashMap<>();

  StarryListState() {}

  private StarryListState(
      Map<String, StarryListDisplayProfile> profiles,
      Map<String, Integer> travelRemainders,
      Map<String, String> archivedPlayerNames,
      Map<String, Map<String, Integer>> archivedScores
  ) {
    profiles.forEach((key, value) -> {
      try {
        List<String> canonical = StarryListConfigData.normalizeIds(value.boards());
        this.profiles.put(UUID.fromString(key), new StarryListDisplayProfile(
            value.mode(), canonical, value.rotationEnabled(), value.rotationIntervalSeconds()
        ));
      } catch (IllegalArgumentException ignored) {}
    });
    this.travelRemainders.putAll(travelRemainders);
    this.archivedPlayerNames.putAll(archivedPlayerNames);
    archivedScores.forEach((key, value) -> this.archivedScores.put(key, new HashMap<>(value)));
  }

  private Map<String, StarryListDisplayProfile> serializedProfiles() {
    Map<String, StarryListDisplayProfile> serialized = new HashMap<>();
    profiles.forEach((key, value) -> serialized.put(key.toString(), value));
    return serialized;
  }

  /**
   * Returns a player's saved display profile or the implicit default profile.
   *
   * @param playerId player UUID
   * @return saved or default display profile
   */
  public StarryListDisplayProfile profile(UUID playerId) {
    return profiles.getOrDefault(playerId, StarryListDisplayProfile.defaultProfile());
  }

  /**
   * Stores a custom or hidden profile and removes explicit default profiles.
   *
   * @param playerId player UUID
   * @param profile profile to store
   */
  public void setProfile(UUID playerId, StarryListDisplayProfile profile) {
    if (profile.mode() == StarryListDisplayProfile.Mode.DEFAULT) {
      profiles.remove(playerId);
    } else {
      profiles.put(playerId, profile);
    }
    setDirty();
  }

  /**
   * Restores one player to the server default display settings.
   *
   * @param playerId player UUID
   */
  public void resetProfile(UUID playerId) {
    if (profiles.remove(playerId) != null) setDirty();
  }

  /**
   * Restores every player to the server default display settings.
   *
   * @return number of removed custom profiles
   */
  public int clearProfiles() {
    int size = profiles.size();
    profiles.clear();
    if (size > 0) setDirty();
    return size;
  }

  /**
   * Accumulates vanilla movement centimeters and returns newly completed whole blocks.
   *
   * @param playerId player UUID
   * @param centimeters positive movement increment
   * @return number of completed whole blocks
   */
  public int addTravel(UUID playerId, int centimeters) {
    String key = playerId.toString();
    int total = Math.max(0, travelRemainders.getOrDefault(key, 0)) + Math.max(0, centimeters);
    travelRemainders.put(key, total % 100);
    setDirty();
    return total / 100;
  }

  /** Stores one hidden score while a player is blacklisted. */
  public void archiveScore(UUID playerId, String playerName, String boardId, int value) {
    String key = playerId.toString();
    archivedPlayerNames.put(key, playerName);
    archivedScores.computeIfAbsent(key, ignored -> new HashMap<>()).put(boardId, value);
    setDirty();
  }

  /** Returns one archived score, or {@code null} when none exists. */
  public Integer archivedScore(UUID playerId, String boardId) {
    Map<String, Integer> scores = archivedScores.get(playerId.toString());
    return scores == null ? null : scores.get(boardId);
  }

  /** Returns an immutable snapshot of every archived player. */
  public Map<UUID, ArchivedScores> archivedPlayers() {
    Map<UUID, ArchivedScores> result = new HashMap<>();
    archivedScores.forEach((key, scores) -> {
      try {
        UUID playerId = UUID.fromString(key);
        result.put(playerId, new ArchivedScores(
            archivedPlayerNames.getOrDefault(key, key), Map.copyOf(scores)
        ));
      } catch (IllegalArgumentException ignored) {}
    });
    return Map.copyOf(result);
  }

  /** Removes one archived score and prunes empty player records. */
  public boolean removeArchivedScore(UUID playerId, String boardId) {
    String key = playerId.toString();
    Map<String, Integer> scores = archivedScores.get(key);
    if (scores == null || scores.remove(boardId) == null) return false;
    if (scores.isEmpty()) {
      archivedScores.remove(key);
      archivedPlayerNames.remove(key);
    }
    setDirty();
    return true;
  }

  /** Removes all archived values for one leaderboard. */
  public int clearArchivedBoard(String boardId) {
    int removed = 0;
    for (String key : List.copyOf(archivedScores.keySet())) {
      Map<String, Integer> scores = archivedScores.get(key);
      if (scores != null && scores.remove(boardId) != null) removed++;
      if (scores != null && scores.isEmpty()) {
        archivedScores.remove(key);
        archivedPlayerNames.remove(key);
      }
    }
    if (removed > 0) setDirty();
    return removed;
  }

  /** Immutable archived identity and per-board values. */
  public record ArchivedScores(String playerName, Map<String, Integer> scores) {}
}
