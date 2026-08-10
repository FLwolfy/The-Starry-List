package com.flwolfy.starrylist.data.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

public final class StarryListConfigMapper {

  private StarryListConfigMapper() {}

  public static Map<String, Object> flattenData(Record record) {
    Map<String, Object> result = new HashMap<>();
    flatten("", record, result);
    return result;
  }

  private static void flatten(String prefix, Record record, Map<String, Object> result) {
    if (record == null) return;
    for (RecordComponent component : record.getClass().getRecordComponents()) {
      try {
        Object value = component.getAccessor().invoke(record);
        String path = prefix.isEmpty() ? component.getName() : prefix + "." + component.getName();
        if (value != null && value.getClass().isRecord()) {
          flatten(path, (Record) value, result);
        } else {
          result.put(path, value);
        }
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Failed to flatten config record", exception);
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends Record> T unflattenData(Map<String, Object> values, Class<T> type) {
    try {
      RecordComponent[] components = type.getRecordComponents();
      Object[] arguments = new Object[components.length];
      for (int index = 0; index < components.length; index++) {
        RecordComponent component = components[index];
        if (component.getType().isRecord()) {
          Map<String, Object> nested = new HashMap<>();
          String prefix = component.getName() + ".";
          values.forEach((key, value) -> {
            if (key.startsWith(prefix)) nested.put(key.substring(prefix.length()), value);
          });
          arguments[index] = unflattenData(nested, (Class<? extends Record>) component.getType());
        } else {
          arguments[index] = values.get(component.getName());
        }
      }
      Constructor<T> constructor = type.getDeclaredConstructor(
          java.util.Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new)
      );
      constructor.setAccessible(true);
      return constructor.newInstance(arguments);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Failed to rebuild config record", exception);
    }
  }
}
