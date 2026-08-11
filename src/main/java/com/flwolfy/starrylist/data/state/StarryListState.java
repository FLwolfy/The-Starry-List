package com.flwolfy.starrylist.data.state;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-scoped display preferences, travel remainders, and blacklisted score archives. */
public final class StarryListState extends SavedData {

  private static final Codec<StarryListState> CODEC = RecordCodecBuilder.create(instance ->
      instance.group(
          Codec.unboundedMap(Codec.STRING, StarryListDisplayProfile.CODEC)
              .fieldOf("profiles").forGetter(state -> state.serializedProfiles()),
          Codec.unboundedMap(
              Codec.STRING,
              Codec.unboundedMap(Codec.STRING, net.minecraft.nbt.CompoundTag.CODEC)
          ).fieldOf("boardData").forGetter(state -> state.boardData),
          Codec.unboundedMap(Codec.STRING, Codec.STRING)
              .fieldOf("archivedPlayerNames")
              .forGetter(state -> state.archivedPlayerNames),
          Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT))
              .fieldOf("archivedScores").forGetter(state -> state.archivedScores)
      ).apply(instance, StarryListState::new)
  );

  public static final SavedDataType<StarryListState> TYPE = new SavedDataType<>(
      Identifier.fromNamespaceAndPath("the-starry-list", "state"),
      StarryListState::new,
      CODEC,
      DataFixTypes.LEVEL
  );

  private final Map<UUID, StarryListDisplayProfile> profiles = new HashMap<>();
  private final Map<String, Map<String, net.minecraft.nbt.CompoundTag>> boardData = new HashMap<>();
  private final Map<String, String> archivedPlayerNames = new HashMap<>();
  private final Map<String, Map<String, Integer>> archivedScores = new HashMap<>();

  StarryListState() {}

  private StarryListState(
      Map<String, StarryListDisplayProfile> profiles,
      Map<String, Map<String, net.minecraft.nbt.CompoundTag>> boardData,
      Map<String, String> archivedPlayerNames,
      Map<String, Map<String, Integer>> archivedScores
  ) {
    profiles.forEach((key, value) -> {
      try {
        List<String> canonical = StarryListConfigData.normalizeIds(value.boards());
        this.profiles.put(UUID.fromString(key), new StarryListDisplayProfile(
            value.mode(), canonical, value.rotationEnabled(), value.rotationIntervalSeconds()
        ));
      } catch (IllegalArgumentException ignored) {
      }
    });
    boardData.forEach((boardId, players) -> {
      Map<String, net.minecraft.nbt.CompoundTag> copied = new HashMap<>();
      players.forEach((playerId, tag) -> copied.put(playerId, tag.copy()));
      this.boardData.put(boardId, copied);
    });
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
    if (profiles.remove(playerId) != null) {
      setDirty();
    }
  }

  /**
   * Restores every player to the server default display settings.
   *
   * @return number of removed custom profiles
   */
  public int clearProfiles() {
    int size = profiles.size();
    profiles.clear();
    if (size > 0) {
      setDirty();
    }

    return size;
  }

  /**
   * Returns persistent state isolated by board identifier and player UUID.
   *
   * @param boardId the board identifier
   * @param playerId the player UUID
   * @return the board-specific player state
   */
  public StarryListBoardState boardState(String boardId, UUID playerId) {
    net.minecraft.nbt.CompoundTag data = boardData
        .computeIfAbsent(boardId, ignored -> new HashMap<>())
        .computeIfAbsent(playerId.toString(), ignored -> new net.minecraft.nbt.CompoundTag());
    return new StarryListBoardState(this, data);
  }

  void markBoardStateDirty() {
    setDirty();
  }

  /**
   * Stores one hidden score while a player is blacklisted.
   *
   * @param playerId the player UUID
   * @param playerName the last known player name
   * @param boardId the board identifier
   * @param value the archived score
   */
  public void archiveScore(UUID playerId, String playerName, String boardId, int value) {
    String key = playerId.toString();
    archivedPlayerNames.put(key, playerName);
    archivedScores.computeIfAbsent(key, ignored -> new HashMap<>()).put(boardId, value);
    setDirty();
  }

  /**
   * Returns one archived score.
   *
   * @param playerId the player UUID
   * @param boardId the board identifier
   * @return the archived score, or {@code null} when none exists
   */
  public Integer archivedScore(UUID playerId, String boardId) {
    Map<String, Integer> scores = archivedScores.get(playerId.toString());
    return scores == null ? null : scores.get(boardId);
  }

  /**
   * Returns an immutable snapshot of every archived player.
   *
   * @return archived identities and scores by player UUID
   */
  public Map<UUID, ArchivedScores> archivedPlayers() {
    Map<UUID, ArchivedScores> result = new HashMap<>();
    archivedScores.forEach((key, scores) -> {
      try {
        UUID playerId = UUID.fromString(key);
        result.put(playerId, new ArchivedScores(
            archivedPlayerNames.getOrDefault(key, key), Map.copyOf(scores)
        ));
      } catch (IllegalArgumentException ignored) {
      }
    });
    return Map.copyOf(result);
  }

  /**
   * Removes one archived score and prunes empty player records.
   *
   * @param playerId the player UUID
   * @param boardId the board identifier
   */
  public void removeArchivedScore(UUID playerId, String boardId) {
    String key = playerId.toString();
    Map<String, Integer> scores = archivedScores.get(key);
    if (scores == null || scores.remove(boardId) == null) {
      return;
    }

    if (scores.isEmpty()) {
      archivedScores.remove(key);
      archivedPlayerNames.remove(key);
    }
    setDirty();
  }

  /**
   * Removes all archived values for one leaderboard.
   *
   * @param boardId the board identifier
   * @return the number of removed scores
   */
  public int clearArchivedBoard(String boardId) {
    int removed = 0;
    for (String key : List.copyOf(archivedScores.keySet())) {
      Map<String, Integer> scores = archivedScores.get(key);
      if (scores != null && scores.remove(boardId) != null) {
        removed++;
      }

      if (scores != null && scores.isEmpty()) {
        archivedScores.remove(key);
        archivedPlayerNames.remove(key);
      }
    }
    if (removed > 0) {
      setDirty();
    }

    return removed;
  }

  /**
   * Removes unavailable board identifiers from every saved display profile.
   *
   * @param boardIds identifiers to remove
   * @return number of changed profiles
   */
  public int removeBoardsFromProfiles(Set<String> boardIds) {
    if (boardIds.isEmpty()) {
      return 0;
    }

    int changed = 0;
    for (Map.Entry<UUID, StarryListDisplayProfile> entry : profiles.entrySet()) {
      StarryListDisplayProfile profile = entry.getValue();
      List<String> boards = profile.boards().stream()
          .filter(id -> !boardIds.contains(id))
          .toList();
      if (!boards.equals(profile.boards())) {
        entry.setValue(new StarryListDisplayProfile(
            profile.mode(),
            boards,
            profile.rotationEnabled(),
            profile.rotationIntervalSeconds()
        ));
        changed++;
      }
    }
    if (changed > 0) {
      setDirty();
    }

    return changed;
  }

  /**
   * Permanently removes state belonging to board identifiers absent from the discovered catalog.
   *
   * @param retainedBoardIds board identifiers whose data must be retained
   * @return counts of removed board-state namespaces and archived player scores
   */
  public PruneResult pruneOrphanedBoards(Set<String> retainedBoardIds) {
    int namespaces = 0;
    for (String storageId : List.copyOf(boardData.keySet())) {
      String boardId = storageId.startsWith("__inactive__/")
          ? storageId.substring("__inactive__/".length()) : storageId;
      if (!retainedBoardIds.contains(boardId) && boardData.remove(storageId) != null) {
        namespaces++;
      }
    }

    int scores = 0;
    for (String playerId : List.copyOf(archivedScores.keySet())) {
      Map<String, Integer> values = archivedScores.get(playerId);
      if (values == null) {
        continue;
      }

      int before = values.size();
      values.keySet().removeIf(boardId -> !retainedBoardIds.contains(boardId));
      scores += before - values.size();
      if (values.isEmpty()) {
        archivedScores.remove(playerId);
        archivedPlayerNames.remove(playerId);
      }
    }
    if (namespaces > 0 || scores > 0) {
      setDirty();
    }

    return new PruneResult(namespaces, scores);
  }

  /**
   * Replaces the inactive-objective archive for one script board.
   *
   * @param boardId stable script board identifier
   * @param scores scores keyed by vanilla scoreboard owner
   */
  public void archiveInactiveBoard(String boardId, Map<String, InactiveScore> scores) {
    String storageId = inactiveStorageId(boardId);
    if (scores.isEmpty()) {
      if (boardData.remove(storageId) != null) {
        setDirty();
      }
      return;
    }

    Map<String, net.minecraft.nbt.CompoundTag> stored = new HashMap<>();
    scores.forEach((owner, score) -> {
      net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
      tag.putInt("value", score.value());
      tag.putString("displayName", score.displayName());
      stored.put(owner, tag);
    });
    boardData.put(storageId, stored);
    setDirty();
  }

  /**
   * Returns inactive scores previously archived for one script board.
   *
   * @param boardId stable script board identifier
   * @return immutable score archive keyed by vanilla scoreboard owner
   */
  public Map<String, InactiveScore> inactiveBoardScores(String boardId) {
    Map<String, net.minecraft.nbt.CompoundTag> stored = boardData.get(inactiveStorageId(boardId));
    if (stored == null) {
      return Map.of();
    }

    Map<String, InactiveScore> result = new HashMap<>();
    stored.forEach((owner, tag) -> result.put(owner, new InactiveScore(
        tag.getIntOr("value", 0),
        tag.getStringOr("displayName", owner)
    )));
    return Map.copyOf(result);
  }

  /**
   * Clears the inactive-objective archive for one restored script board.
   *
   * @param boardId stable script board identifier
   */
  public void clearInactiveBoard(String boardId) {
    if (boardData.remove(inactiveStorageId(boardId)) != null) {
      setDirty();
    }
  }

  private static String inactiveStorageId(String boardId) {
    return "__inactive__/" + boardId;
  }

  /**
   * Immutable archived identity and per-board values.
   *
   * @param playerName the last known player name
   * @param scores the archived values by board identifier
   */
  public record ArchivedScores(String playerName, Map<String, Integer> scores) {}

  /**
   * One score retained while a script objective is absent.
   *
   * @param value score value
   * @param displayName last visible owner name
   */
  public record InactiveScore(int value, String displayName) {
  }

  /**
   * Counts data permanently removed by orphan pruning.
   *
   * @param boardStateNamespaces removed board-state and inactive-objective namespaces
   * @param archivedScores removed blacklisted score values
   */
  public record PruneResult(int boardStateNamespaces, int archivedScores) {
  }
}
