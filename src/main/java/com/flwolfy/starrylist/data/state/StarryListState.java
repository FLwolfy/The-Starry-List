package com.flwolfy.starrylist.data.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-scoped player display preferences and travel-distance remainders. */
public final class StarryListState extends SavedData {

  private static final Codec<StarryListState> CODEC = RecordCodecBuilder.create(instance ->
      instance.group(
          Codec.unboundedMap(Codec.STRING, StarryListDisplayProfile.CODEC)
              .optionalFieldOf("profiles", Map.of()).forGetter(state -> state.serializedProfiles()),
          Codec.unboundedMap(Codec.STRING, Codec.INT)
              .optionalFieldOf("travelRemainders", Map.of()).forGetter(state -> state.travelRemainders)
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

  private StarryListState() {}

  private StarryListState(
      Map<String, StarryListDisplayProfile> profiles,
      Map<String, Integer> travelRemainders
  ) {
    profiles.forEach((key, value) -> {
      try {
        this.profiles.put(UUID.fromString(key), value);
      } catch (IllegalArgumentException ignored) {}
    });
    this.travelRemainders.putAll(travelRemainders);
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
}
