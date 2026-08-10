package com.flwolfy.starrylist.modmenu.entrybuilder;

import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;
import net.minecraft.network.chat.Component;

/** PayTp-style factory abstraction for custom Cloth Config fields. */
public abstract class StarryListEntryBuilderBase<T> {

  public abstract AbstractFieldBuilder<T, ?, ?> create(
      ConfigEntryBuilder builder,
      T value,
      Component label
  );
}
