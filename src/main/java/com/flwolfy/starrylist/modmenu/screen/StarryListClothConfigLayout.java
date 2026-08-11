package com.flwolfy.starrylist.modmenu.screen;

import com.flwolfy.starrylist.board.script.StarryListScriptManager;
import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.modmenu.builder.StarryListConfigUiRegistry;
import com.flwolfy.starrylist.modmenu.entry.blacklist.StarryListBlacklistEditorModel;
import com.flwolfy.starrylist.modmenu.entry.blacklist.StarryListBlacklistListEntry;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListAllCategoryEntry;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListPendingEntry;
import com.flwolfy.starrylist.modmenu.model.StarryListConfigEditorModel;
import com.flwolfy.starrylist.modmenu.section.StarryListBoardSection;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Reflectively builds the complete Cloth Config category layout. */
public final class StarryListClothConfigLayout {

  private static final String BASE_KEY = "starrylist.config.";

  private final ConfigBuilder builder;
  private final ConfigEntryBuilder entries;
  private final StarryListConfigEditorModel model;
  private final StarryListConfigUiRegistry registry;
  private final StarryListBlacklistEditorModel blacklist;
  private final StarryListBoardSection boards;
  private final List<StarryListPendingEntry> pendingEntries = new ArrayList<>();

  /**
   * Creates a reflective layout builder.
   *
   * @param builder owning screen builder
   * @param current current editable file configuration
   * @param refreshResult script refresh result consumer
   */
  public StarryListClothConfigLayout(
      ConfigBuilder builder,
      StarryListConfigData current,
      Consumer<StarryListScriptManager.OperationResult> refreshResult
  ) {
    this.builder = builder;
    entries = builder.entryBuilder();
    model = new StarryListConfigEditorModel(current, StarryListConfigData.DEFAULT);
    registry = StarryListConfigUiRegistry.createDefault();
    blacklist = new StarryListBlacklistEditorModel(model);
    boards = new StarryListBoardSection(model, entries, blacklist::flush, refreshResult);
    registry.registerSection("boards", this::buildBoards);
    registry.registerSection("blacklist", this::buildBlacklist);
  }

  /**
   * Populates all categories in configuration-record declaration order.
   */
  public void build() {
    ConfigCategory all = builder.getOrCreateCategory(Component.translatable(BASE_KEY + "all"));
    all.addEntry(entries.startTextDescription(
        Component.translatable(BASE_KEY + "local_only")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC)
    ).build());

    ConfigCategory other = null;
    List<AbstractConfigListEntry<?>> otherAllEntries = new ArrayList<>();
    for (RecordComponent component : StarryListConfigData.class.getRecordComponents()) {
      String path = component.getName();
      var section = registry.section(path);
      if (section != null) {
        ConfigCategory category = builder.getOrCreateCategory(
            Component.translatable(BASE_KEY + path)
        );
        section.build(category, all);
      } else if (component.getType().isRecord()) {
        ConfigCategory category = builder.getOrCreateCategory(
            Component.translatable(BASE_KEY + path)
        );
        buildRecordCategory(category, all, component.getType(), path);
      } else {
        if (other == null) {
          other = builder.getOrCreateCategory(Component.translatable(BASE_KEY + "other"));
        }
        other.addEntry(buildEntry(path, false));
        otherAllEntries.add(buildEntry(path, true));
      }
    }

    if (!otherAllEntries.isEmpty()) {
      all.addEntry(allSection(
          Component.translatable(BASE_KEY + "other")
              .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
          otherAllEntries
      ));
    }
  }

  /**
   * Returns the shared editor model reconstructed during save.
   *
   * @return editor model
   */
  public StarryListConfigEditorModel model() {
    return model;
  }

  /**
   * Flushes dynamic list cells into the shared model before refresh or save.
   */
  public void flush() {
    pendingEntries.forEach(StarryListPendingEntry::flush);
    blacklist.flush();
  }

  private void buildRecordCategory(
      ConfigCategory category,
      ConfigCategory all,
      Class<?> recordType,
      String path
  ) {
    List<AbstractConfigListEntry<?>> categoryEntries = recordEntries(
        recordType, path, false, false
    );
    categoryEntries.forEach(category::addEntry);
    List<AbstractConfigListEntry<?>> allEntries = recordEntries(
        recordType, path, true, true
    );
    all.addEntry(allSection(
        Component.translatable(BASE_KEY + path)
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
        allEntries
    ));
  }

  private List<AbstractConfigListEntry<?>> recordEntries(
      Class<?> recordType,
      String prefix,
      boolean suppressErrors,
      boolean flattenNested
  ) {
    List<AbstractConfigListEntry<?>> result = new ArrayList<>();
    for (RecordComponent component : recordType.getRecordComponents()) {
      String path = prefix + "." + component.getName();
      if (component.getType().isRecord()) {
        List<AbstractConfigListEntry<?>> nested = recordEntries(
            component.getType(), path, suppressErrors, flattenNested
        );
        if (flattenNested) {
          result.add(entries.startTextDescription(
              Component.translatable(BASE_KEY + path)
                  .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
          ).build());
          result.addAll(nested);
        } else {
          var subcategory = entries.startSubCategory(
              Component.translatable(BASE_KEY + path)
          ).setExpanded(true);
          subcategory.addAll(nested);
          result.add(subcategory.build());
        }
      } else if (!registry.hidden(path)) {
        result.add(buildEntry(path, suppressErrors));
      }
    }
    return result;
  }

  private void buildBoards(ConfigCategory category, ConfigCategory all) {
    boards.createView().forEach(category::addEntry);
    all.addEntry(allSection(
        Component.translatable(BASE_KEY + "boards")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
        boards.createView()
    ));
  }

  private void buildBlacklist(ConfigCategory category, ConfigCategory all) {
    category.addEntry(new StarryListBlacklistListEntry(
        Component.translatable(BASE_KEY + "blacklist.patterns"),
        blacklist,
        entries.getResetButtonKey(),
        false
    ));
    StarryListBlacklistListEntry allBlacklist = new StarryListBlacklistListEntry(
        Component.translatable(BASE_KEY + "blacklist.patterns"),
        blacklist,
        entries.getResetButtonKey(),
        true
    );
    all.addEntry(allSection(
        Component.translatable(BASE_KEY + "blacklist")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
        List.of(allBlacklist)
    ));
  }

  private AbstractConfigListEntry<?> allSection(
      Component title,
      List<AbstractConfigListEntry<?>> sectionEntries
  ) {
    return new StarryListAllCategoryEntry(entries, title, sectionEntries);
  }

  private AbstractConfigListEntry<?> buildEntry(String path, boolean suppressErrors) {
    AbstractConfigListEntry<?> entry = registry.build(
        model, path, entries.getResetButtonKey(), suppressErrors
    );
    if (entry instanceof StarryListPendingEntry pending) {
      pendingEntries.add(pending);
    }
    return entry;
  }
}
