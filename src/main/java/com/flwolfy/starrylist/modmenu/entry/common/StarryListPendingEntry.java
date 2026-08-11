package com.flwolfy.starrylist.modmenu.entry.common;

/** Exposes widget-local edits that must be flushed before save or refresh. */
public interface StarryListPendingEntry {

  /**
   * Publishes the widget's latest local value to the shared editor model.
   */
  void flush();
}
