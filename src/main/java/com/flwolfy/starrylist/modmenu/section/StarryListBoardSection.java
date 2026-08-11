package com.flwolfy.starrylist.modmenu.section;

import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.board.script.StarryListScriptBoard;
import com.flwolfy.starrylist.board.script.StarryListScriptManager;
import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.modmenu.entry.board.StarryListBoardListEntry;
import com.flwolfy.starrylist.modmenu.entry.board.StarryListBoardSettings;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListRefreshableSubCategoryEntry;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListSubCategoryEntry;
import com.flwolfy.starrylist.modmenu.model.StarryListConfigEditorModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Builds and synchronizes independent Built-in and Custom board configuration views. */
public final class StarryListBoardSection {

  private static final String ENABLED_PATH = "display.enabledBoards";
  private static final String DISABLED_PATH = "boards.disabledBoards";

  private final StarryListConfigEditorModel model;
  private final ConfigEntryBuilder entries;
  private final Runnable beforeRefresh;
  private final Consumer<StarryListScriptManager.OperationResult> refreshResult;
  private final List<View> views = new ArrayList<>();

  /**
   * Creates a board section controller.
   *
   * @param model shared configuration model
   * @param entries Cloth Config entry builder
   * @param beforeRefresh action that flushes other mutable views before script inspection
   * @param refreshResult refresh result consumer
   */
  public StarryListBoardSection(
      StarryListConfigEditorModel model,
      ConfigEntryBuilder entries,
      Runnable beforeRefresh,
      Consumer<StarryListScriptManager.OperationResult> refreshResult
  ) {
    this.model = model;
    this.entries = entries;
    this.beforeRefresh = beforeRefresh;
    this.refreshResult = refreshResult;
  }

  /**
   * Creates one independent Boards section bound to the shared model.
   *
   * @return Built-in and Custom subcategory entries
   */
  public List<AbstractConfigListEntry<?>> createView() {
    List<AbstractConfigListEntry<?>> builtInEntries = createBuiltInEntries();
    List<AbstractConfigListEntry<?>> customEntries = createCustomEntries();
    var builtIn = new StarryListSubCategoryEntry(
        entries,
        Component.translatable("starrylist.config.boards.builtin"),
        builtInEntries,
        true,
        false
    );
    var custom = new StarryListRefreshableSubCategoryEntry(
        entries,
        Component.translatable("starrylist.config.boards.custom"),
        customEntries,
        this::refresh
    );
    views.add(new View(custom));
    return List.of(builtIn, custom);
  }

  /**
   * Recompiles script previews and updates every Custom view in place.
   */
  public void refresh() {
    beforeRefresh.run();
    StarryListScriptManager.OperationResult result = inspectScripts();
    normalizeLists();
    views.forEach(view -> view.custom().replaceEntries(createCustomEntries()));
    refreshResult.accept(result);
  }

  private List<AbstractConfigListEntry<?>> createBuiltInEntries() {
    List<AbstractConfigListEntry<?>> result = new ArrayList<>();
    for (var board : StarryListBoardRegistry.getInstance().definitions()) {
      if (!(board instanceof StarryListScriptBoard)) {
        result.add(createBoardEntry(
            board.id(),
            Component.literal(board.presentationFor(locale()).title()),
            new StarryListBoardSettings(
                true,
                StarryListConfigData.DEFAULT.display().enabledBoards().contains(board.id())
            ),
            true,
            null
        ));
      }
    }
    return result;
  }

  private List<AbstractConfigListEntry<?>> createCustomEntries() {
    List<AbstractConfigListEntry<?>> result = new ArrayList<>();
    StarryListBoardRegistry registry = StarryListBoardRegistry.getInstance();
    StarryListScriptManager scripts = StarryListScriptManager.getInstance();
    boolean previewed = scripts.hasPreview();
    Map<String, StarryListScriptManager.ScriptInspection> inspections = new LinkedHashMap<>();
    scripts.inspections().forEach(value -> inspections.put(value.sourceFile(), value));

    for (var board : registry.definitions()) {
      if (!(board instanceof StarryListScriptBoard scriptBoard)) {
        continue;
      }
      StarryListScriptManager.ScriptInspection inspection = inspections.get(
          scriptBoard.sourceFile()
      );
      if (previewed && (inspection == null || !Objects.equals(board.id(), inspection.id()))) {
        continue;
      }
      inspections.remove(scriptBoard.sourceFile());
      boolean valid = inspection == null || inspection.valid();
      Component title = inspection != null && previewed
          ? Component.literal(inspection.title(locale()))
          : Component.literal(board.presentationFor(locale()).title());
      result.add(createBoardEntry(
          board.id(),
          title,
          new StarryListBoardSettings(true, false),
          valid,
          valid ? null : Component.literal(inspection.message())
      ));
    }

    for (StarryListScriptManager.ScriptInspection inspection : inspections.values()) {
      String boardId = inspection.id();
      StarryListBoardListEntry entry = createBoardEntry(
          boardId,
          Component.literal(inspection.title(locale())),
          new StarryListBoardSettings(true, false),
          inspection.valid(),
          inspection.valid()
              ? Component.translatable("starrylist.config.boards.custom.pending")
              : Component.literal(inspection.message())
      );
      entry.setEditable(inspection.valid() && boardId != null);
      result.add(entry);
    }
    return result;
  }

  private StarryListBoardListEntry createBoardEntry(
      String boardId,
      Component title,
      StarryListBoardSettings defaultValue,
      boolean valid,
      Component diagnostic
  ) {
    return new StarryListBoardListEntry(
        title,
        () -> settings(boardId),
        value -> setSettings(boardId, value),
        defaultValue,
        entries.getResetButtonKey(),
        valid,
        diagnostic
    );
  }

  private StarryListBoardSettings settings(String boardId) {
    if (boardId == null) {
      return new StarryListBoardSettings(false, false);
    }
    return new StarryListBoardSettings(
        !strings(DISABLED_PATH).contains(boardId),
        strings(ENABLED_PATH).contains(boardId)
    );
  }

  private void setSettings(String boardId, StarryListBoardSettings settings) {
    if (boardId == null) {
      return;
    }
    List<String> disabled = new ArrayList<>(strings(DISABLED_PATH));
    if (settings.loaded()) {
      disabled.remove(boardId);
    } else if (!disabled.contains(boardId)) {
      disabled.add(boardId);
    }
    List<String> enabled = new ArrayList<>(strings(ENABLED_PATH));
    if (settings.defaultEnabled()) {
      if (!enabled.contains(boardId)) {
        enabled.add(boardId);
      }
    } else {
      enabled.remove(boardId);
    }
    model.set(DISABLED_PATH, StarryListConfigData.normalizeIds(disabled));
    model.set(ENABLED_PATH, StarryListConfigData.normalizeIds(enabled));
  }

  private void normalizeLists() {
    model.set(ENABLED_PATH, StarryListConfigData.normalizeIds(strings(ENABLED_PATH)));
    model.set(DISABLED_PATH, StarryListConfigData.normalizeIds(strings(DISABLED_PATH)));
  }

  @SuppressWarnings("unchecked")
  private List<String> strings(String path) {
    return (List<String>) model.get(path, List.class);
  }

  private static String locale() {
    return Minecraft.getInstance().getLanguageManager().getSelected();
  }

  private static StarryListScriptManager.OperationResult inspectScripts() {
    return StarryListScriptManager.getInstance().preview(
        StarryListBoardRegistry.getInstance(),
        Minecraft.getInstance().getSingleplayerServer() != null
    );
  }

  private record View(StarryListRefreshableSubCategoryEntry custom) {}
}
