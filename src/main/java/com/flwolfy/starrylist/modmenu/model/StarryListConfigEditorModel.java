package com.flwolfy.starrylist.modmenu.model;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Owns the shared editable values used by independent Cloth Config widget trees. */
public final class StarryListConfigEditorModel {

  private final Map<String, StarryListConfigRecordMapper.Field> fields;
  private final Map<String, Object> values = new LinkedHashMap<>();
  private long revision;
  private long validatedRevision = -1;
  private List<String> invalidFields = List.of();

  /**
   * Creates an editor model from current and default configuration records.
   *
   * @param current current file configuration
   * @param defaults default configuration
   */
  public StarryListConfigEditorModel(
      StarryListConfigData current,
      StarryListConfigData defaults
  ) {
    fields = StarryListConfigRecordMapper.describe(current, defaults);
    fields.forEach((path, field) -> values.put(path, copy(field.value())));
  }

  /**
   * Returns one field description.
   *
   * @param path dotted record path
   * @return field metadata
   */
  public StarryListConfigRecordMapper.Field field(String path) {
    StarryListConfigRecordMapper.Field field = fields.get(path);
    if (field == null) {
      throw new IllegalArgumentException("Unknown configuration field " + path);
    }
    return field;
  }

  /**
   * Returns all flattened fields in record declaration order.
   *
   * @return immutable ordered field map
   */
  public Map<String, StarryListConfigRecordMapper.Field> fields() {
    return fields;
  }

  /**
   * Reads one current field value.
   *
   * @param path dotted record path
   * @param type expected value type
   * @param <T> value type
   * @return current value
   */
  public <T> T get(String path, Class<T> type) {
    return type.cast(values.get(path));
  }

  /**
   * Replaces one field value and advances the model revision when it changed.
   *
   * @param path dotted record path
   * @param value replacement value
   */
  public void set(String path, Object value) {
    if (!fields.containsKey(path)) {
      throw new IllegalArgumentException("Unknown configuration field " + path);
    }
    Object copied = copy(value);
    if (!Objects.equals(values.get(path), copied)) {
      values.put(path, copied);
      revision++;
    }
  }

  /**
   * Returns the current model revision.
   *
   * @return monotonically increasing edit revision
   */
  public long revision() {
    return revision;
  }

  /**
   * Reconstructs the complete immutable configuration.
   *
   * @return current configuration snapshot
   */
  public StarryListConfigData build() {
    return StarryListConfigRecordMapper.reconstruct(StarryListConfigData.class, values);
  }

  /**
   * Resolves the cached complete-validation error for one field.
   *
   * @param path dotted record path
   * @param suppress whether this duplicate view suppresses its error
   * @return localized validation error when invalid
   */
  public Optional<Component> error(String path, boolean suppress) {
    if (suppress) {
      return Optional.empty();
    }
    validateIfChanged();
    return invalidFields.contains(path)
        ? Optional.of(Component.translatable("starrylist.config." + path + ".invalid"))
        : Optional.empty();
  }

  /**
   * Returns every currently invalid field path.
   *
   * @return immutable validation result
   */
  public List<String> invalidFields() {
    validateIfChanged();
    return invalidFields;
  }

  private void validateIfChanged() {
    if (validatedRevision == revision) {
      return;
    }
    invalidFields = build().validate();
    validatedRevision = revision;
  }

  private static Object copy(Object value) {
    if (value instanceof List<?> list) {
      return List.copyOf(new ArrayList<>(list));
    }
    return value;
  }
}
