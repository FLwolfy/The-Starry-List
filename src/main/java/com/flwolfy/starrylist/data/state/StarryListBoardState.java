package com.flwolfy.starrylist.data.state;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

/** Mutable, automatically-dirtied persistent state scoped to one board and player. */
public final class StarryListBoardState {

  private final StarryListState owner;
  private final CompoundTag data;

  StarryListBoardState(StarryListState owner, CompoundTag data) {
    this.owner = owner;
    this.data = data;
  }

  /**
   * Reads an integer value with a caller-provided fallback.
   *
   * @param key the value key
   * @param fallback the value returned when the key is absent
   * @return the stored or fallback value
   */
  public int getInt(String key, int fallback) {
    return data.getIntOr(key, fallback);
  }

  /**
   * Writes an integer value and marks the world data dirty.
   *
   * @param key the value key
   * @param value the value to store
   */
  public void putInt(String key, int value) {
    data.putInt(key, value);
    owner.markBoardStateDirty();
  }

  /**
   * Reads an arbitrary codec-backed value from this board namespace.
   *
   * @param <T> the decoded value type
   * @param key the value key
   * @param codec the value codec
   * @return the decoded value, if present and valid
   */
  public <T> Optional<T> read(String key, Codec<T> codec) {
    return data.read(key, codec);
  }

  /**
   * Stores an arbitrary codec-backed value and marks the world data dirty.
   *
   * @param <T> the encoded value type
   * @param key the value key
   * @param codec the value codec
   * @param value the value to store
   */
  public <T> void put(String key, Codec<T> codec, T value) {
    data.store(key, codec, value);
    owner.markBoardStateDirty();
  }

  /**
   * Returns a defensive NBT snapshot for diagnostics or compound reads.
   *
   * @return a copy of the state namespace
   */
  public CompoundTag snapshot() {
    return data.copy();
  }

  /**
   * Removes one value and marks the world data dirty when it existed.
   *
   * @param key the value key
   */
  public void remove(String key) {
    if (data.remove(key) != null) {
      owner.markBoardStateDirty();
    }
  }

  /**
   * Accumulates positive sub-units and persists the remainder.
   *
   * @param key the remainder key
   * @param amount the number of sub-units to add
   * @param unitsPerWhole the number of sub-units in one whole unit
   * @return the number of completed whole units
   */
  public int accumulate(String key, int amount, int unitsPerWhole) {
    if (amount <= 0 || unitsPerWhole <= 0) {
      return 0;
    }

    long total = Math.max(0, getInt(key, 0)) + (long) amount;
    putInt(key, (int) (total % unitsPerWhole));
    return (int) Math.min(Integer.MAX_VALUE, total / unitsPerWhole);
  }
}
