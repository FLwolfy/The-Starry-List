package com.flwolfy.starrylist.data.lang;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class StarryListLangAdapter
    implements JsonSerializer<StarryListLang>, JsonDeserializer<StarryListLang> {

  @Override
  public JsonElement serialize(
      StarryListLang source,
      Type type,
      JsonSerializationContext context
  ) {
    return new JsonPrimitive(source.getLangKey());
  }

  @Override
  public StarryListLang deserialize(
      JsonElement json,
      Type type,
      JsonDeserializationContext context
  ) throws JsonParseException {
    String key = json.getAsString();
    for (StarryListLang language : StarryListLang.values()) {
      if (language.getLangKey().equalsIgnoreCase(key)) return language;
    }
    throw new JsonParseException("Unsupported StarryList language: " + key);
  }
}
