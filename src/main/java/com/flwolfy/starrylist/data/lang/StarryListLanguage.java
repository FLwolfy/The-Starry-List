package com.flwolfy.starrylist.data.lang;

import java.util.Map;

/** One immutable server language discovered from the mod's bundled resources. */
record StarryListLanguage(String locale, String name, Map<String, String> translations) {

  StarryListLanguage {
    translations = Map.copyOf(translations);
  }
}
