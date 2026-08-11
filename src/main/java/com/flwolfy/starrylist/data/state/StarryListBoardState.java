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

  /** Reads an integer value with a caller-provided fallback. */
  public int getInt(String key, int fallback) {
    return data.getIntOr(key, fallback);
  }

  /** Writes an integer value and marks world SavedData dirty. */
  public void putInt(String key, int value) {
    data.putInt(key, value);
    owner.markBoardStateDirty();
  }

  /** Reads an arbitrary codec-backed value from this board namespace. */
  public <T> Optional<T> read(String key, Codec<T> codec) {
    return data.read(key, codec);
  }

  /** Stores an arbitrary codec-backed value and marks world SavedData dirty. */
  public <T> void put(String key, Codec<T> codec, T value) {
    data.store(key, codec, value);
    owner.markBoardStateDirty();
  }

  /** Returns a defensive NBT snapshot for diagnostics or compound reads. */
  public CompoundTag snapshot() {
    return data.copy();
  }

  /** Removes one value and marks world SavedData dirty when it existed. */
  public void remove(String key) {
    if (data.remove(key) != null) owner.markBoardStateDirty();
  }

  /** Accumulates positive sub-units and persists the remainder. */
  public int accumulate(String key, int amount, int unitsPerWhole) {
    if (amount <= 0 || unitsPerWhole <= 0) return 0;
    long total = Math.max(0, getInt(key, 0)) + (long) amount;
    putInt(key, (int) (total % unitsPerWhole));
    return (int) Math.min(Integer.MAX_VALUE, total / unitsPerWhole);
  }
}
