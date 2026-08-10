package com.flwolfy.starrylist.data.lang;

/** Languages bundled for server-rendered command feedback. */
public enum StarryListLang {
  /** English server messages. */
  ENGLISH("en_us", "English"),
  /** Simplified Chinese server messages. */
  SIMPLIFIED_CHINESE("zh_cn", "简体中文");

  private final String key;
  private final String name;

  StarryListLang(String key, String name) {
    this.key = key;
    this.name = name;
  }

  /**
   * Returns the locale key used to locate bundled translations.
   *
   * @return resource-pack locale key
   */
  public String getLangKey() {
    return key;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return name;
  }
}
