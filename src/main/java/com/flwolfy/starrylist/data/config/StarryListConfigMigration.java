package com.flwolfy.starrylist.data.config;

import com.google.gson.JsonObject;

/** Pure JSON migrations applied before a configuration record is decoded. */
final class StarryListConfigMigration {

  private StarryListConfigMigration() {}

  /** Migrates the legacy ordered board list to the fixed-order enabled-board field. */
  static boolean migrateLegacy(JsonObject root) {
    if (!root.has("display") || !root.get("display").isJsonObject()) return false;
    JsonObject display = root.getAsJsonObject("display");
    if (!display.has("defaultBoards")) return false;
    if (!display.has("enabledBoards") || display.get("enabledBoards").isJsonNull()) {
      display.add("enabledBoards", display.get("defaultBoards").deepCopy());
    }
    display.remove("defaultBoards");
    return true;
  }
}
