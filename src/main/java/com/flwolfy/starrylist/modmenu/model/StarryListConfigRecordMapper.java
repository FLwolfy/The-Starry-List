package com.flwolfy.starrylist.modmenu.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Recursively maps immutable configuration records to stable field paths and back. */
public final class StarryListConfigRecordMapper {

  private StarryListConfigRecordMapper() {}

  /**
   * Describes every non-record field in declaration order.
   *
   * @param current current configuration record
   * @param defaults matching default configuration record
   * @return immutable field descriptions keyed by dotted record path
   */
  public static Map<String, Field> describe(Record current, Record defaults) {
    Map<String, Field> fields = new LinkedHashMap<>();
    describeRecord("", current, defaults, fields);
    return Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }

  /**
   * Reconstructs a configuration record from flattened field values.
   *
   * @param recordType root record type
   * @param values flattened values keyed by dotted record path
   * @param <T> root record type
   * @return reconstructed immutable record
   */
  public static <T extends Record> T reconstruct(
      Class<T> recordType,
      Map<String, Object> values
  ) {
    return reconstructRecord(recordType, "", values);
  }

  private static void describeRecord(
      String prefix,
      Record current,
      Record defaults,
      Map<String, Field> fields
  ) {
    if (current == null || defaults == null || current.getClass() != defaults.getClass()) {
      throw new IllegalArgumentException("Configuration records and defaults must have equal types");
    }

    for (RecordComponent component : current.getClass().getRecordComponents()) {
      String path = prefix.isEmpty() ? component.getName() : prefix + "." + component.getName();
      try {
        Object value = component.getAccessor().invoke(current);
        Object defaultValue = component.getAccessor().invoke(defaults);
        if (component.getType().isRecord()) {
          describeRecord(path, (Record) value, (Record) defaultValue, fields);
        } else {
          fields.put(path, new Field(
              path,
              component.getType(),
              component.getGenericType(),
              value,
              defaultValue
          ));
        }
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Could not read configuration field " + path, exception);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Record> T reconstructRecord(
      Class<T> recordType,
      String prefix,
      Map<String, Object> values
  ) {
    RecordComponent[] components = recordType.getRecordComponents();
    Object[] arguments = new Object[components.length];
    Class<?>[] parameterTypes = new Class<?>[components.length];
    for (int index = 0; index < components.length; index++) {
      RecordComponent component = components[index];
      parameterTypes[index] = component.getType();
      String path = prefix.isEmpty() ? component.getName() : prefix + "." + component.getName();
      arguments[index] = component.getType().isRecord()
          ? reconstructRecord((Class<? extends Record>) component.getType(), path, values)
          : values.get(path);
    }

    try {
      Constructor<T> constructor = recordType.getDeclaredConstructor(parameterTypes);
      constructor.setAccessible(true);
      return constructor.newInstance(arguments);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Could not reconstruct " + recordType.getName(), exception);
    }
  }

  /**
   * Describes one flattened record field.
   *
   * @param path dotted record path
   * @param rawType declared raw Java type
   * @param genericType declared generic Java type
   * @param value current field value
   * @param defaultValue default field value
   */
  public record Field(
      String path,
      Class<?> rawType,
      Type genericType,
      Object value,
      Object defaultValue
  ) {}
}
