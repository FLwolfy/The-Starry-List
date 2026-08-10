package com.flwolfy.starrylist.data.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-scoped player display preferences and StarryList bookkeeping. */
public final class StarryListState extends SavedData {

  private static final Codec<StarryListState> CODEC = RecordCodecBuilder.create(instance ->
      instance.group(
          Codec.unboundedMap(Codec.STRING, StarryListDisplayProfile.CODEC)
              .optionalFieldOf("profiles", Map.of()).forGetter(state -> state.serializedProfiles()),
          Codec.unboundedMap(Codec.STRING, Codec.INT)
              .optionalFieldOf("travelRemainders", Map.of()).forGetter(state -> state.travelRemainders),
          Codec.unboundedMap(Codec.STRING, Codec.STRING)
              .optionalFieldOf("lastKnownNames", Map.of()).forGetter(state -> state.lastKnownNames),
          Codec.unboundedMap(Codec.STRING, Codec.STRING)
              .optionalFieldOf("managedObjectives", Map.of()).forGetter(state -> state.managedObjectives),
          Codec.STRING.listOf().optionalFieldOf("orphanObjectives", List.of())
              .forGetter(state -> List.copyOf(state.orphanObjectives))
      ).apply(instance, StarryListState::new)
  );

  public static final SavedDataType<StarryListState> TYPE = new SavedDataType<>(
      Identifier.fromNamespaceAndPath("the-starry-list", "state"),
      StarryListState::new,
      CODEC,
      DataFixTypes.LEVEL
  );

  private final Map<UUID, StarryListDisplayProfile> profiles = new HashMap<>();
  private final Map<String, Integer> travelRemainders = new HashMap<>();
  private final Map<String, String> lastKnownNames = new HashMap<>();
  private final Map<String, String> managedObjectives = new HashMap<>();
  private final Set<String> orphanObjectives = new LinkedHashSet<>();

  public StarryListState() {}

  private StarryListState(
      Map<String, StarryListDisplayProfile> profiles,
      Map<String, Integer> travelRemainders,
      Map<String, String> lastKnownNames,
      Map<String, String> managedObjectives,
      List<String> orphanObjectives
  ) {
    profiles.forEach((key, value) -> {
      try {
        this.profiles.put(UUID.fromString(key), value);
      } catch (IllegalArgumentException ignored) {}
    });
    this.travelRemainders.putAll(travelRemainders);
    this.lastKnownNames.putAll(lastKnownNames);
    this.managedObjectives.putAll(managedObjectives);
    this.orphanObjectives.addAll(orphanObjectives);
  }

  private Map<String, StarryListDisplayProfile> serializedProfiles() {
    Map<String, StarryListDisplayProfile> serialized = new HashMap<>();
    profiles.forEach((key, value) -> serialized.put(key.toString(), value));
    return serialized;
  }

  public StarryListDisplayProfile profile(UUID playerId) {
    return profiles.getOrDefault(playerId, StarryListDisplayProfile.defaultProfile());
  }

  public void setProfile(UUID playerId, StarryListDisplayProfile profile) {
    if (profile.mode() == StarryListDisplayProfile.Mode.DEFAULT) {
      profiles.remove(playerId);
    } else {
      profiles.put(playerId, profile);
    }
    setDirty();
  }

  public void resetProfile(UUID playerId) {
    if (profiles.remove(playerId) != null) setDirty();
  }

  public int clearProfiles() {
    int size = profiles.size();
    profiles.clear();
    if (size > 0) setDirty();
    return size;
  }

  public int addTravel(UUID playerId, int centimeters) {
    String key = playerId.toString();
    int total = Math.max(0, travelRemainders.getOrDefault(key, 0)) + Math.max(0, centimeters);
    travelRemainders.put(key, total % 100);
    setDirty();
    return total / 100;
  }

  public void rememberPlayer(UUID playerId, String name) {
    if (!name.equals(lastKnownNames.put(playerId.toString(), name))) setDirty();
  }

  public String lastKnownName(UUID playerId) {
    return lastKnownNames.getOrDefault(playerId.toString(), playerId.toString());
  }

  public Map<String, String> managedObjectives() {
    return Map.copyOf(managedObjectives);
  }

  public void manageObjective(String boardId, String objectiveName) {
    String previous = managedObjectives.put(boardId, objectiveName);
    if (previous != null && !previous.equals(objectiveName)) orphanObjectives.add(previous);
    orphanObjectives.remove(objectiveName);
    setDirty();
  }

  public void removeManagedBoard(String boardId) {
    String previous = managedObjectives.remove(boardId);
    if (previous != null) {
      orphanObjectives.add(previous);
      setDirty();
    }
  }

  public List<String> orphanObjectives() {
    return List.copyOf(orphanObjectives);
  }

  public void clearOrphans() {
    if (!orphanObjectives.isEmpty()) {
      orphanObjectives.clear();
      setDirty();
    }
  }
}
