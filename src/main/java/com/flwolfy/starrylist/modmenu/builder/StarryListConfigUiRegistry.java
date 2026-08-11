package com.flwolfy.starrylist.modmenu.builder;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.modmenu.entry.scalar.StarryListBooleanEntry;
import com.flwolfy.starrylist.modmenu.entry.scalar.StarryListEnumEntry;
import com.flwolfy.starrylist.modmenu.entry.scalar.StarryListIntegerEntry;
import com.flwolfy.starrylist.modmenu.entry.scalar.StarryListIntegerSliderEntry;
import com.flwolfy.starrylist.modmenu.entry.scalar.StarryListStringEntry;
import com.flwolfy.starrylist.modmenu.entry.scalar.StarryListUnsupportedEntry;
import com.flwolfy.starrylist.modmenu.model.StarryListConfigEditorModel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.network.chat.Component;

/** Resolves field-specific and type-based Cloth Config entry builders. */
public final class StarryListConfigUiRegistry {

  private final Map<Class<?>, StarryListConfigEntryBuilder> typeBuilders = new HashMap<>();
  private final Map<String, StarryListConfigEntryBuilder> pathBuilders = new HashMap<>();
  private final Map<String, Presentation> presentations = new HashMap<>();
  private final Map<String, StarryListConfigSectionBuilder> sectionBuilders = new HashMap<>();
  private final Set<String> hiddenPaths = new HashSet<>();

  /**
   * Creates the complete built-in UI registry.
   *
   * @return configured project-local registry
   */
  public static StarryListConfigUiRegistry createDefault() {
    StarryListConfigUiRegistry registry = new StarryListConfigUiRegistry();
    registry.registerType(boolean.class, context -> new StarryListBooleanEntry(context));
    registry.registerType(Boolean.class, context -> new StarryListBooleanEntry(context));
    registry.registerType(int.class, context -> new StarryListIntegerEntry(context, null, null));
    registry.registerType(Integer.class, context -> new StarryListIntegerEntry(context, null, null));
    registry.registerType(String.class, StarryListStringEntry::new);

    registry.registerPath(
        "general.adminPermissionLevel",
        context -> new StarryListIntegerSliderEntry(context, 0, 4)
    );
    registry.registerPath(
        "display.rotationIntervalSeconds",
        context -> new StarryListIntegerEntry(context, 1, 3600)
    );
    registry.hide("display.enabledBoards");
    registry.hide("boards.disabledBoards");
    registry.hide("blacklist.playerNamePatterns");

    registry.present("general.language", "language");
    registry.present("general.adminPermissionLevel", "admin_permission");
    registry.present("display.hiddenByDefault", "hidden_default");
    registry.present("display.rotationEnabled", "rotation");
    registry.present("display.rotationIntervalSeconds", "rotation_interval");
    return registry;
  }

  /**
   * Registers the fallback builder for an exact declared field type.
   *
   * @param type declared field type
   * @param builder entry builder
   */
  public void registerType(Class<?> type, StarryListConfigEntryBuilder builder) {
    typeBuilders.put(type, builder);
  }

  /**
   * Registers a builder override for one dotted field path.
   *
   * @param path dotted field path
   * @param builder entry builder
   */
  public void registerPath(String path, StarryListConfigEntryBuilder builder) {
    pathBuilders.put(path, builder);
  }

  /**
   * Registers a custom builder for one top-level configuration record.
   *
   * @param path top-level record path
   * @param sectionBuilder section builder
   */
  public void registerSection(
      String path,
      StarryListConfigSectionBuilder sectionBuilder
  ) {
    sectionBuilders.put(path, sectionBuilder);
  }

  /**
   * Returns a custom top-level section builder when registered.
   *
   * @param path top-level record path
   * @return registered section builder, or {@code null}
   */
  public StarryListConfigSectionBuilder section(String path) {
    return sectionBuilders.get(path);
  }

  /**
   * Hides a field consumed by a custom section.
   *
   * @param path dotted field path
   */
  public void hide(String path) {
    hiddenPaths.add(path);
  }

  /**
   * Registers existing translation-key aliases for one field.
   *
   * @param path dotted field path
   * @param translationSuffix suffix below {@code starrylist.config}
   */
  public void present(String path, String translationSuffix) {
    presentations.put(path, new Presentation(
        "starrylist.config." + translationSuffix,
        "starrylist.config." + translationSuffix + ".tooltip"
    ));
  }

  /**
   * Checks whether a custom section consumes a field.
   *
   * @param path dotted field path
   * @return whether generic generation should skip it
   */
  public boolean hidden(String path) {
    return hiddenPaths.contains(path);
  }

  /**
   * Builds one independent entry for a flattened field.
   *
   * @param model shared editor model
   * @param path dotted field path
   * @param resetText Cloth reset button text
   * @param suppressErrors whether duplicate validation errors are suppressed
   * @return model-bound entry
   */
  public AbstractConfigListEntry<?> build(
      StarryListConfigEditorModel model,
      String path,
      Component resetText,
      boolean suppressErrors
  ) {
    var field = model.field(path);
    Presentation presentation = presentations.getOrDefault(path, new Presentation(
        "starrylist.config." + path,
        "starrylist.config." + path + ".tooltip"
    ));
    StarryListEntryContext context = new StarryListEntryContext(
        model,
        field,
        Component.translatable(presentation.labelKey()),
        new Component[]{Component.translatable(presentation.tooltipKey())},
        resetText,
        suppressErrors
    );
    StarryListConfigEntryBuilder builder = pathBuilders.get(path);
    if (builder == null) {
      builder = typeBuilders.get(field.rawType());
    }
    if (builder == null && field.rawType().isEnum()) {
      builder = StarryListEnumEntry::new;
    }
    if (builder == null) {
      StarryListMod.LOGGER.error(
          "No Cloth Config entry builder for {} ({})",
          path,
          field.genericType().getTypeName()
      );
      return new StarryListUnsupportedEntry(context);
    }
    return builder.build(context);
  }

  private record Presentation(String labelKey, String tooltipKey) {}
}
