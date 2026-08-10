package com.flwolfy.starrylist.data.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

class StarryListLangAdapterTest {

  private final Gson gson = new GsonBuilder()
      .registerTypeAdapter(StarryListLang.class, new StarryListLangAdapter())
      .create();

  @Test
  void readsAndWritesSupportedLanguageKeys() {
    assertEquals(StarryListLang.ENGLISH, gson.fromJson("\"en_us\"", StarryListLang.class));
    assertEquals("\"zh_cn\"", gson.toJson(StarryListLang.SIMPLIFIED_CHINESE));
  }

  @Test
  void rejectsUnsupportedLanguageKeys() {
    assertThrows(
        JsonParseException.class,
        () -> gson.fromJson("\"unsupported\"", StarryListLang.class)
    );
  }
}
