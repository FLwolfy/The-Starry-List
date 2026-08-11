package com.flwolfy.starrylist.modmenu.entry.common;

import java.util.List;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

/** Expanded All-category section with stable nested focus handling. */
public final class StarryListAllCategoryEntry extends StarryListSubCategoryEntry {

  /**
   * Creates an expanded duplicate section.
   *
   * @param builder Cloth Config entry builder
   * @param title localized section title
   * @param entries independent entries bound to the shared model
   */
  public StarryListAllCategoryEntry(
      ConfigEntryBuilder builder,
      Component title,
      List<AbstractConfigListEntry<?>> entries
  ) {
    super(builder, title, entries, true, true);
  }
}
