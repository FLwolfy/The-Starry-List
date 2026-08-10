package com.flwolfy.starrylist.data.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;

public record StarryListDisplayProfile(
    Mode mode,
    List<String> boards,
    boolean rotationEnabled,
    int rotationIntervalSeconds
) {

  public enum Mode {
    DEFAULT,
    CUSTOM,
    HIDDEN
  }

  public static final Codec<StarryListDisplayProfile> CODEC = RecordCodecBuilder.create(instance ->
      instance.group(
          Codec.STRING.xmap(
              value -> Mode.valueOf(value.toUpperCase(Locale.ROOT)),
              value -> value.name().toLowerCase(Locale.ROOT)
          ).fieldOf("mode").forGetter(StarryListDisplayProfile::mode),
          Codec.STRING.listOf().fieldOf("boards").forGetter(StarryListDisplayProfile::boards),
          Codec.BOOL.fieldOf("rotationEnabled").forGetter(StarryListDisplayProfile::rotationEnabled),
          Codec.INT.fieldOf("rotationIntervalSeconds").forGetter(
              StarryListDisplayProfile::rotationIntervalSeconds
          )
      ).apply(instance, StarryListDisplayProfile::new)
  );

  public StarryListDisplayProfile {
    boards = List.copyOf(boards);
  }

  public static StarryListDisplayProfile defaultProfile() {
    return new StarryListDisplayProfile(Mode.DEFAULT, List.of(), true, 20);
  }
}
