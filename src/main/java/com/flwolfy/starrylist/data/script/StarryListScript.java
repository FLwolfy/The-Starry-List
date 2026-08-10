package com.flwolfy.starrylist.data.script;

import java.util.List;
import java.util.Objects;

public final class StarryListScript {

  private final String source;

  public StarryListScript(String source) {
    this.source = Objects.requireNonNull(source, "source");
  }

  public String source() {
    return source;
  }

  public List<String> lines() {
    return source.lines().toList();
  }

  public static StarryListScript of(String source) {
    return new StarryListScript(source == null ? "0" : source);
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof StarryListScript other && source.equals(other.source);
  }

  @Override
  public int hashCode() {
    return source.hashCode();
  }

  @Override
  public String toString() {
    return source;
  }
}
