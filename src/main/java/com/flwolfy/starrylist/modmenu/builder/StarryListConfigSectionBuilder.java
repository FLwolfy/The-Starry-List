package com.flwolfy.starrylist.modmenu.builder;

import me.shedaniel.clothconfig2.api.ConfigCategory;

/** Builds one custom top-level category and its independent All-category view. */
public interface StarryListConfigSectionBuilder {

  /**
   * Populates both independent views for a custom record section.
   *
   * @param category dedicated top-level category
   * @param all All category receiving the duplicate view
   */
  void build(ConfigCategory category, ConfigCategory all);
}
