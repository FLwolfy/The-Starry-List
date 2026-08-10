package com.flwolfy.starrylist.data.lang;

public enum StarryListLang {
  ENGLISH("en_us", "English"),
  SIMPLIFIED_CHINESE("zh_cn", "简体中文");

  private final String key;
  private final String name;

  StarryListLang(String key, String name) {
    this.key = key;
    this.name = name;
  }

  public String getLangKey() {
    return key;
  }

  public static StarryListLang fromKey(String key) {
    if (key == null) return ENGLISH;
    for (StarryListLang lang : values()) {
      if (lang.key.equalsIgnoreCase(key)) return lang;
    }
    return ENGLISH;
  }

  @Override
  public String toString() {
    return name;
  }
}
