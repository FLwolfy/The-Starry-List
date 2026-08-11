package com.flwolfy.starrylist.modmenu.builder;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;

/** Creates one model-bound Cloth Config entry for a supported field type. */
public interface StarryListConfigEntryBuilder {

  /**
   * Creates an independent widget tree bound to the shared editor model.
   *
   * @param context field metadata and editor binding
   * @return model-bound Cloth Config entry
   */
  AbstractConfigListEntry<?> build(StarryListEntryContext context);
}
