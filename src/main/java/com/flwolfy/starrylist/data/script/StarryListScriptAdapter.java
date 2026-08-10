package com.flwolfy.starrylist.data.script;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class StarryListScriptAdapter extends TypeAdapter<StarryListScript> {

  @Override
  public void write(JsonWriter writer, StarryListScript script) throws IOException {
    if (script == null) {
      writer.nullValue();
    } else {
      writer.value(script.source());
    }
  }

  @Override
  public StarryListScript read(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return StarryListScript.of("0");
    }
    return StarryListScript.of(reader.nextString());
  }
}
