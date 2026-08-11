package com.flwolfy.starrylist.modmenu;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.StringListListEntry;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Builds the local-only Cloth Config editor exposed through ModMenu. */
final class StarryListClothConfigGUI {

  private static final SystemToast.SystemToastId SAVE_RESULT = new SystemToast.SystemToastId();

  private StarryListClothConfigGUI() {}

  static Screen create(Screen parent) {
    StarryListConfigData current = StarryListConfigManager.getInstance().data();
    StarryListClothConfigBuilder values = new StarryListClothConfigBuilder(current);
    ConfigBuilder builder = ConfigBuilder.create()
        .setParentScreen(parent)
        .setTitle(Component.translatable("starrylist.config.title"))
        .setDoesConfirmSave(true);
    ConfigEntryBuilder entries = builder.entryBuilder();

    ConfigCategory all = builder.getOrCreateCategory(Component.translatable("starrylist.config.all"));
    all.addEntry(entries.startTextDescription(
        Component.translatable("starrylist.config.local_only")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC)
    ).build());

    ConfigCategory general = builder.getOrCreateCategory(
        Component.translatable("starrylist.config.general")
    );
    var languageEntry = entries.startEnumSelector(
        Component.translatable("starrylist.config.language"),
        StarryListLang.class,
        values.language
    ).setDefaultValue(StarryListConfigData.DEFAULT.general().language())
        .setTooltip(Component.translatable("starrylist.config.language.tooltip"))
        .setSaveConsumer(value -> values.language = value).build();
    var permissionEntry = entries.startIntSlider(
        Component.translatable("starrylist.config.admin_permission"),
        values.adminPermissionLevel,
        0,
        4
    ).setDefaultValue(2)
        .setTooltip(Component.translatable("starrylist.config.admin_permission.tooltip"))
        .setSaveConsumer(value -> values.adminPermissionLevel = value).build();
    List<AbstractConfigListEntry<?>> generalEntries = List.of(languageEntry, permissionEntry);
    generalEntries.forEach(general::addEntry);
    addAllSection(all, "starrylist.config.general", generalEntries);

    ConfigCategory display = builder.getOrCreateCategory(
        Component.translatable("starrylist.config.display")
    );
    var hiddenEntry = entries.startBooleanToggle(
        Component.translatable("starrylist.config.hidden_default"), values.hiddenByDefault
    ).setDefaultValue(false)
        .setTooltip(Component.translatable("starrylist.config.hidden_default.tooltip"))
        .setSaveConsumer(value -> values.hiddenByDefault = value).build();
    var rotationEntry = entries.startBooleanToggle(
        Component.translatable("starrylist.config.rotation"), values.rotationEnabled
    ).setDefaultValue(true)
        .setTooltip(Component.translatable("starrylist.config.rotation.tooltip"))
        .setSaveConsumer(value -> values.rotationEnabled = value).build();
    var intervalEntry = entries.startIntField(
        Component.translatable("starrylist.config.rotation_interval"), values.rotationIntervalSeconds
    ).setMin(1).setMax(3600).setDefaultValue(20)
        .setTooltip(Component.translatable("starrylist.config.rotation_interval.tooltip"))
        .setSaveConsumer(value -> values.rotationIntervalSeconds = value).build();
    List<AbstractConfigListEntry<?>> boardEntries = new ArrayList<>();
    for (var board : StarryListBoardRegistry.getInstance().all()) {
      String boardId = board.id();
      var boardEntry = entries.startBooleanToggle(
          Component.literal(board.presentationFor(
              Minecraft.getInstance().getLanguageManager().getSelected()
          ).title()),
          values.enabledBoards.contains(boardId)
      ).setDefaultValue(StarryListConfigData.DEFAULT.display().enabledBoards().contains(boardId))
          .setTooltip(Component.translatable("starrylist.config.enabled_boards.tooltip"))
          .setSaveConsumer(value -> values.setBoardEnabled(boardId, value))
          .build();
      boardEntries.add(boardEntry);
    }
    var boardSubcategoryBuilder = entries.startSubCategory(
        Component.translatable("starrylist.config.enabled_boards")
    ).setExpanded(false);
    boardSubcategoryBuilder.addAll(boardEntries);
    var boardsSubcategory = boardSubcategoryBuilder.build();
    List<AbstractConfigListEntry<?>> displayEntries = List.of(
        hiddenEntry, rotationEntry, intervalEntry, boardsSubcategory
    );
    displayEntries.forEach(display::addEntry);
    addAllSection(all, "starrylist.config.display", displayEntries);

    ConfigCategory blacklist = builder.getOrCreateCategory(
        Component.translatable("starrylist.config.blacklist")
    );
    BlacklistEditorModel blacklistModel = new BlacklistEditorModel(values.playerNamePatterns);
    var blacklistEntry = new BlacklistListEntry(
        Component.translatable("starrylist.config.blacklist.patterns"),
        blacklistModel,
        entries.getResetButtonKey()
    );
    var allBlacklistEntry = new BlacklistListEntry(
        Component.translatable("starrylist.config.blacklist")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
        blacklistModel,
        entries.getResetButtonKey()
    );
    blacklist.addEntry(blacklistEntry);
    // StringListListEntry is already an expandable container. Wrapping it in another
    // SubCategoryListEntry makes Cloth render newly-added cells, but the outer container does not
    // reliably propagate selection/focus to those dynamic grandchildren. Keep it top-level in
    // All so its event path is identical to the working standalone Blacklist category.
    all.addEntry(allBlacklistEntry);

    builder.setSavingRunnable(() -> {
      // A click on Save may happen before the next render pass. Flush both independent editors
      // here so the shared model always contains the last character typed or row added.
      blacklistEntry.publishPendingChanges();
      allBlacklistEntry.publishPendingChanges();
      values.playerNamePatterns = blacklistModel.values();
      save(values);
    });
    return builder.build();
  }

  private static Optional<Component> regexError(String expression) {
    if (expression == null || expression.isBlank()) {
      return Optional.of(Component.translatable("starrylist.config.blacklist.pattern.invalid"));
    }
    try {
      Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      return Optional.empty();
    } catch (PatternSyntaxException exception) {
      return Optional.of(Component.translatable("starrylist.config.blacklist.pattern.invalid"));
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void addAllSection(
      ConfigCategory all,
      String translationKey,
      List<AbstractConfigListEntry<?>> sectionEntries
  ) {
    Component title = Component.translatable(translationKey)
        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    all.addEntry(new AllCategoryEntry(title, new ArrayList((List) sectionEntries)));
  }

  private static void save(StarryListClothConfigBuilder values) {
    try {
      StarryListConfigData replacement = values.build();
      List<String> errors = replacement.validate();
      if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(", ", errors));
      if (!applyUpdate(replacement)) {
        throw new IllegalStateException("The configuration could not be applied");
      }
      SystemToast.add(
          Minecraft.getInstance().getToastManager(),
          SAVE_RESULT,
          Component.translatable("starrylist.config.save_success"),
          Component.translatable("starrylist.config.save_success.detail")
      );
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to save StarryList client configuration", exception);
      SystemToast.add(
          Minecraft.getInstance().getToastManager(),
          SAVE_RESULT,
          Component.translatable("starrylist.config.save_failed"),
          Component.literal(exception.getMessage() == null
              ? exception.getClass().getSimpleName() : exception.getMessage())
      );
    }
  }

  private static boolean applyUpdate(StarryListConfigData replacement) {
    var integratedServer = Minecraft.getInstance().getSingleplayerServer();
    if (integratedServer == null || integratedServer.isSameThread()) {
      return StarryListConfigManager.getInstance().update(replacement);
    }
    CompletableFuture<Boolean> result = new CompletableFuture<>();
    integratedServer.execute(() -> result.complete(
        StarryListConfigManager.getInstance().update(replacement)
    ));
    return result.join();
  }

  /** Prevents duplicate validation messages for entries also present in their own category. */
  @SuppressWarnings("deprecation")
  private static final class AllCategoryEntry extends SubCategoryListEntry {
    private AllCategoryEntry(Component title, List<AbstractConfigListEntry> entries) {
      super(title, entries, true);
    }

    @Override
    public Optional<Component> getError() {
      return Optional.empty();
    }
  }

  /**
   * Owns the editable blacklist value without owning any GUI widgets.
   *
   * <p>The All and Blacklist categories must not share a {@link StringListListEntry}: Cloth
   * assigns each entry a single parent/focus chain, while a string list mutates its child widget
   * collection when + or - is clicked. Sharing that mutable entry left new rows outside the
   * active focus chain and also invalidated IME composition. Each category now has its own entry
   * and this model synchronizes only their string values.</p>
   */
  private static final class BlacklistEditorModel {
    private final List<BlacklistListEntry> editors = new ArrayList<>();
    private List<String> values;

    private BlacklistEditorModel(List<String> values) {
      this.values = List.copyOf(values);
    }

    private void register(BlacklistListEntry editor) {
      editors.add(editor);
    }

    private void publish(BlacklistListEntry source, List<String> replacement) {
      List<String> copied = List.copyOf(replacement);
      if (copied.equals(values)) return;
      values = copied;
      for (BlacklistListEntry editor : editors) {
        if (editor != source) editor.receive(values);
      }
    }

    private List<String> values() {
      return List.copyOf(values);
    }
  }

  /** A category-local widget tree backed by the shared blacklist value model. */
  private static final class BlacklistListEntry extends StringListListEntry {
    private final BlacklistEditorModel model;
    private List<String> observedValues;

    @SuppressWarnings("deprecation")
    private BlacklistListEntry(
        Component title,
        BlacklistEditorModel model,
        Component resetButtonKey
    ) {
      super(
          title,
          model.values(),
          true,
          () -> Optional.of(new Component[]{Component.translatable(
              "starrylist.config.blacklist.patterns.tooltip"
          )}),
          ignored -> {},
          () -> StarryListConfigData.DEFAULT.blacklist().playerNamePatterns(),
          resetButtonKey,
          false,
          true,
          true
      );
      this.model = model;
      this.observedValues = model.values();
      replaceCells(observedValues);
      setCreateNewInstance(entry -> new BlacklistPatternCell("", entry));
      model.register(this);
    }

    @Override
    protected StringListCell getFromValue(String value) {
      return new BlacklistPatternCell(value, this);
    }

    @Override
    public void extractRenderState(
        GuiGraphicsExtractor graphics,
        int index,
        int y,
        int x,
        int entryWidth,
        int entryHeight,
        int mouseX,
        int mouseY,
        boolean hovered,
        float delta
    ) {
      publishPendingChanges();
      // Cloth omits dynamically-added cells from its narration/focus traversal collection.
      // Keep that collection in lockstep as well as the mouse-event widget collection.
      for (StringListCell cell : cells) {
        if (!narratables.contains(cell)) narratables.add(cell);
      }
      super.extractRenderState(
          graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta
      );
    }

    private void publishPendingChanges() {
      List<String> current = List.copyOf(getValue());
      if (current.equals(observedValues)) return;
      observedValues = current;
      model.publish(this, current);
    }

    private void receive(List<String> replacement) {
      // Do not overwrite a local edit which has not reached its next render pass yet.
      if (!List.copyOf(getValue()).equals(observedValues)) return;
      replaceCells(replacement);
      observedValues = List.copyOf(replacement);
    }

    private void replaceCells(List<String> replacement) {
      widgets.removeAll(cells);
      narratables.removeAll(cells);
      for (StringListCell cell : cells) cell.onDelete();
      cells.clear();
      for (String value : replacement) {
        StringListCell cell = new BlacklistPatternCell(value, this);
        cells.add(cell);
        widgets.add(cell);
        narratables.add(cell);
      }
    }
  }

  private static final class BlacklistPatternCell extends StringListListEntry.StringListCell {
    private BlacklistPatternCell(String value, StringListListEntry entry) {
      super(value, entry);
      // AbstractTextFieldListCell already renders Cloth's own background/underline. Enabling the
      // vanilla EditBox border here draws a second rectangle through that underline.
      widget.setHint(Component.translatable("starrylist.config.blacklist.pattern.hint"));
    }

    @Override
    public Optional<Component> getError() {
      // IMEs update the EditBox with intermediate composition strings. Validating those transient
      // fragments forces Cloth to rebuild its error render state while composing, which can cancel
      // the platform composition session. Validate as soon as focus leaves the field instead.
      return widget.isFocused() ? Optional.empty() : regexError(getValue());
    }
  }
}
