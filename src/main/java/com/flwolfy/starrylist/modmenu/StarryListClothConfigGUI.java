package com.flwolfy.starrylist.modmenu;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.board.script.StarryListScriptBoard;
import com.flwolfy.starrylist.board.script.StarryListScriptManager;
import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.StringListListEntry;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Builds the local-only Cloth Config editor exposed through ModMenu. */
final class StarryListClothConfigGUI {

  private static final SystemToast.SystemToastId SAVE_RESULT = new SystemToast.SystemToastId();
  private static WeakReference<Screen> activeScreen = new WeakReference<>(null);
  private static WeakReference<ClothConfigScreen> pendingScrollScreen = new WeakReference<>(null);
  private static Screen activeParent;
  private static long activeRevision;
  private static double pendingScrollAmount;
  private static int pendingScrollAttempts;

  private StarryListClothConfigGUI() {}

  static Screen create(Screen parent) {
    try {
      inspectScripts();
    } catch (RuntimeException exception) {
      StarryListMod.LOGGER.error(
          "Failed to refresh Groovy board previews while opening Cloth Config",
          exception
      );
    }

    return create(parent, new StarryListClothConfigBuilder(
        StarryListConfigManager.getInstance().loadForEditing()
    ), 0);
  }

  private static Screen create(
      Screen parent,
      StarryListClothConfigBuilder values,
      int selectedCategory
  ) {
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
    List<AbstractConfigListEntry<?>> builtInBoardEntries = new ArrayList<>();
    List<AbstractConfigListEntry<?>> customBoardEntries = new ArrayList<>();
    List<BoardListEntry> boardControls = new ArrayList<>();
    StarryListBoardRegistry registry = StarryListBoardRegistry.getInstance();
    Runnable[] refreshAction = new Runnable[]{() -> {}};
    for (var board : registry.definitions()) {
      if (board instanceof StarryListScriptBoard) {
        continue;
      }

      String boardId = board.id();
      String locale = Minecraft.getInstance().getLanguageManager().getSelected();
      var boardEntry = new BoardListEntry(
          Component.literal(board.presentationFor(locale).title()),
          new BoardSettings(
              !values.disabledBoards.contains(boardId),
              values.enabledBoards.contains(boardId)
          ),
          new BoardSettings(
              true,
              StarryListConfigData.DEFAULT.display().enabledBoards().contains(boardId)
          ),
          setting -> {
            values.setBoardLoaded(boardId, setting.loaded());
            values.setBoardEnabled(boardId, setting.defaultEnabled());
          },
          entries.getResetButtonKey(),
          true,
          null
      );
      boardControls.add(boardEntry);
      builtInBoardEntries.add(boardEntry);
    }
    customBoardEntries.addAll(createCustomBoardEntries(
        entries, values, boardControls
    ));
    var builtInBoardsBuilder = entries.startSubCategory(
        Component.translatable("starrylist.config.boards.builtin")
    ).setExpanded(true);
    builtInBoardsBuilder.addAll(builtInBoardEntries);
    var builtInBoards = builtInBoardsBuilder.build();
    var customBoards = new RefreshableSubCategoryListEntry(
        Component.translatable("starrylist.config.boards.custom"),
        customBoardEntries,
        () -> refreshAction[0].run()
    );

    ConfigCategory boards = builder.getOrCreateCategory(
        Component.translatable("starrylist.config.boards")
    );
    List<AbstractConfigListEntry<?>> boardEntries = List.of(builtInBoards, customBoards);
    boardEntries.forEach(boards::addEntry);
    addAllSection(all, "starrylist.config.boards", boardEntries);

    List<AbstractConfigListEntry<?>> displayEntries = List.of(
        hiddenEntry, rotationEntry, intervalEntry
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
        entries.getResetButtonKey(),
        false
    );
    var allBlacklistEntry = new BlacklistListEntry(
        Component.translatable("starrylist.config.blacklist")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
        blacklistModel,
        entries.getResetButtonKey(),
        true
    );
    blacklist.addEntry(blacklistEntry);
    // StringListListEntry is already an expandable container. Wrapping it in another
    // SubCategoryListEntry makes Cloth render newly-added cells, but the outer container does not
    // reliably propagate selection/focus to those dynamic grandchildren. Keep it top-level in
    // All so its event path is identical to the working standalone Blacklist category.
    all.addEntry(allBlacklistEntry);

    refreshAction[0] = () -> {
      languageEntry.save();
      permissionEntry.save();
      hiddenEntry.save();
      rotationEntry.save();
      intervalEntry.save();
      boardControls.forEach(AbstractConfigListEntry::save);
      blacklistEntry.publishPendingChanges();
      allBlacklistEntry.publishPendingChanges();
      values.playerNamePatterns = blacklistModel.values();
      refreshCustomBoards(
          entries,
          values,
          customBoards,
          builtInBoardEntries,
          boardControls
      );
    };

    builder.setSavingRunnable(() -> {
      // A click on Save may happen before the next render pass. Flush both independent editors
      // here so the shared model always contains the last character typed or row added.
      blacklistEntry.publishPendingChanges();
      allBlacklistEntry.publishPendingChanges();
      values.playerNamePatterns = blacklistModel.values();
      save(values);
    });
    Screen screen = builder.build();
    if (screen instanceof AbstractConfigScreen configScreen) {
      configScreen.selectedCategoryIndex = selectedCategory;
    }
    activeScreen = new WeakReference<>(screen);
    activeParent = parent;
    activeRevision = StarryListBoardRegistry.getInstance().revision();
    return screen;
  }

  static void refreshIfRegistryChanged() {
    restorePendingScroll();
    Minecraft client = Minecraft.getInstance();
    Screen screen = activeScreen.get();
    long revision = StarryListBoardRegistry.getInstance().revision();
    if (screen != null && client.screen == screen && activeRevision != revision) {
      int category = screen instanceof AbstractConfigScreen configScreen
          ? configScreen.selectedCategoryIndex : 0;
      double scroll = screen instanceof ClothConfigScreen configScreen
          && configScreen.listWidget != null ? configScreen.listWidget.getScroll() : 0.0;
      Screen replacement = create(
          activeParent,
          new StarryListClothConfigBuilder(
              StarryListConfigManager.getInstance().loadForEditing()
          ),
          category
      );
      showWithScroll(client, replacement, scroll);
    }
  }

  private static void refreshCustomBoards(
      ConfigEntryBuilder entries,
      StarryListClothConfigBuilder values,
      RefreshableSubCategoryListEntry customBoards,
      List<AbstractConfigListEntry<?>> builtInBoardEntries,
      List<BoardListEntry> boardControls
  ) {
    StarryListScriptManager.OperationResult result = inspectScripts();
    values.enabledBoards = StarryListConfigData.normalizeIds(values.enabledBoards);
    values.disabledBoards = StarryListConfigData.normalizeIds(values.disabledBoards);

    List<BoardListEntry> customControls = new ArrayList<>();
    List<AbstractConfigListEntry<?>> replacement = createCustomBoardEntries(
        entries, values, customControls
    );
    customBoards.replaceEntries(replacement);

    boardControls.clear();
    builtInBoardEntries.stream()
        .filter(BoardListEntry.class::isInstance)
        .map(BoardListEntry.class::cast)
        .forEach(boardControls::add);
    boardControls.addAll(customControls);
    showRefreshResult(result.success(), result.message());
  }

  private static List<AbstractConfigListEntry<?>> createCustomBoardEntries(
      ConfigEntryBuilder entries,
      StarryListClothConfigBuilder values,
      List<BoardListEntry> boardControls
  ) {
    List<AbstractConfigListEntry<?>> result = new ArrayList<>();
    StarryListBoardRegistry registry = StarryListBoardRegistry.getInstance();
    boolean previewed = StarryListScriptManager.getInstance().hasPreview();
    Map<String, StarryListScriptManager.ScriptInspection> inspections = new LinkedHashMap<>();
    StarryListScriptManager.getInstance().inspections().forEach(
        value -> inspections.put(value.sourceFile(), value)
    );
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

      String boardId = board.id();
      boolean valid = inspection == null || inspection.valid();
      String locale = Minecraft.getInstance().getLanguageManager().getSelected();
      Component title = inspection != null && previewed
          ? Component.literal(inspection.title(locale))
          : Component.literal(board.presentationFor(locale).title());
      BoardListEntry boardEntry = new BoardListEntry(
          title,
          new BoardSettings(
              !values.disabledBoards.contains(boardId),
              values.enabledBoards.contains(boardId)
          ),
          new BoardSettings(true, false),
          setting -> {
            values.setBoardLoaded(boardId, setting.loaded());
            values.setBoardEnabled(boardId, setting.defaultEnabled());
          },
          entries.getResetButtonKey(),
          valid,
          valid ? null : Component.literal(inspection.message())
      );
      result.add(boardEntry);
      boardControls.add(boardEntry);
    }

    for (StarryListScriptManager.ScriptInspection inspection : inspections.values()) {
      String boardId = inspection.id();
      BoardListEntry boardEntry = new BoardListEntry(
          Component.literal(inspection.title(
              Minecraft.getInstance().getLanguageManager().getSelected()
          )),
          new BoardSettings(
              boardId != null && !values.disabledBoards.contains(boardId),
              boardId != null && values.enabledBoards.contains(boardId)
          ),
          new BoardSettings(true, false),
          setting -> {
            if (boardId != null) {
              values.setBoardLoaded(boardId, setting.loaded());
              values.setBoardEnabled(boardId, setting.defaultEnabled());
            }
          },
          entries.getResetButtonKey(),
          inspection.valid(),
          inspection.valid()
              ? Component.translatable("starrylist.config.boards.custom.pending")
              : Component.literal(inspection.message())
      );
      boardEntry.setEditable(inspection.valid() && boardId != null);
      result.add(boardEntry);
      boardControls.add(boardEntry);
    }

    return result;
  }

  private static void showWithScroll(Minecraft client, Screen screen, double scrollAmount) {
    client.setScreen(screen);
    if (screen instanceof ClothConfigScreen configScreen) {
      pendingScrollScreen = new WeakReference<>(configScreen);
      pendingScrollAmount = scrollAmount;
      pendingScrollAttempts = 4;
      if (configScreen.listWidget != null) {
        configScreen.listWidget.scrollTo(scrollAmount, false);
      }
    }
  }

  private static void restorePendingScroll() {
    ClothConfigScreen screen = pendingScrollScreen.get();
    if (screen == null || Minecraft.getInstance().screen != screen
        || pendingScrollAttempts <= 0) {
      pendingScrollScreen = new WeakReference<>(null);
      return;
    }
    if (screen.listWidget == null) {
      return;
    }

    if (Math.abs(screen.listWidget.getScroll() - pendingScrollAmount) < 0.5) {
      pendingScrollScreen = new WeakReference<>(null);
      return;
    }
    screen.listWidget.scrollTo(pendingScrollAmount, false);
    pendingScrollAttempts--;
  }

  private static StarryListScriptManager.OperationResult inspectScripts() {
    return StarryListScriptManager.getInstance().preview(
        StarryListBoardRegistry.getInstance(),
        Minecraft.getInstance().getSingleplayerServer() != null
    );
  }

  private static void showRefreshResult(boolean success, String detail) {
    SystemToast.add(
        Minecraft.getInstance().getToastManager(),
        SAVE_RESULT,
        Component.translatable(success
            ? "starrylist.config.boards.custom.refresh.success"
            : "starrylist.config.boards.custom.refresh.failed"),
        Component.literal(detail)
    );
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
      if (!errors.isEmpty()) {
        throw new IllegalArgumentException(String.join(", ", errors));
      }

      if (!savePending(replacement)) {
        throw new IllegalStateException("The configuration could not be saved");
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

  private static final class RefreshableSubCategoryListEntry extends SubCategoryListEntry {

    private static final int BUTTON_WIDTH = 80;

    private final Button refreshButton;

    @SuppressWarnings({"deprecation", "rawtypes", "unchecked"})
    private RefreshableSubCategoryListEntry(
        Component title,
        List<AbstractConfigListEntry<?>> entries,
        Runnable refresh
    ) {
      super(title, new ArrayList((List) entries), true);
      refreshButton = Button.builder(
          Component.translatable("starrylist.config.boards.custom.refresh.button"),
          ignored -> refresh.run()
      ).bounds(0, 0, BUTTON_WIDTH, 20).build();
      refreshButton.setTooltip(Tooltip.create(Component.translatable(
          "starrylist.config.boards.custom.refresh.tooltip"
      )));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void replaceEntries(List<AbstractConfigListEntry<?>> replacement) {
      List entries = getValue();
      entries.clear();
      entries.addAll(replacement);
      setReferenceProviderEntries((List) replacement);
      requestReferenceRebuilding();
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
      super.extractRenderState(
          graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta
      );
      refreshButton.active = isEditable();
      refreshButton.setX(x + entryWidth - BUTTON_WIDTH);
      refreshButton.setY(y);
      refreshButton.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      List<GuiEventListener> children = new ArrayList<>();
      children.add(super.children().getFirst());
      if (isExpanded()) {
        children.addAll(getValue());
      }
      children.add(refreshButton);
      return children;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (refreshButton.mouseClicked(event, doubleClick)) {
        return true;
      }

      return super.mouseClicked(event, doubleClick);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      List<NarratableEntry> entries = new ArrayList<>();
      entries.add(super.narratables().getFirst());
      if (isExpanded()) {
        entries.addAll(getValue());
      }
      entries.add(refreshButton);
      return entries;
    }
  }

  private record BoardSettings(boolean loaded, boolean defaultEnabled) {
  }

  private static final class BoardListEntry extends TooltipListEntry<BoardSettings> {

    private static final int GAP = 4;
    private static final int LOAD_WIDTH = 68;
    private static final int DEFAULT_WIDTH = 104;
    private static final int RESET_WIDTH = 50;

    private final BoardSettings defaultValue;
    private final Button loadedButton;
    private final Button defaultButton;
    private final Button resetButton;
    private final boolean valid;
    private BoardSettings value;

    @SuppressWarnings("deprecation")
    private BoardListEntry(
        Component title,
        BoardSettings value,
        BoardSettings defaultValue,
        Consumer<BoardSettings> saveConsumer,
        Component resetText,
        boolean valid,
        Component diagnostic
    ) {
      super(
          valid ? title : title.copy().withStyle(ChatFormatting.RED, ChatFormatting.STRIKETHROUGH),
          () -> Optional.of(new Component[]{diagnostic == null
              ? Component.translatable("starrylist.config.enabled_boards.tooltip")
              : diagnostic}),
          false
      );
      this.value = value;
      this.defaultValue = defaultValue;
      this.valid = valid;
      this.saveCallback = saveConsumer;
      loadedButton = Button.builder(Component.empty(), ignored -> {
        this.value = new BoardSettings(!this.value.loaded(), this.value.defaultEnabled());
        updateLabels();
      }).bounds(0, 0, LOAD_WIDTH, 20).build();
      defaultButton = Button.builder(Component.empty(), ignored -> {
        this.value = new BoardSettings(this.value.loaded(), !this.value.defaultEnabled());
        updateLabels();
      }).bounds(0, 0, DEFAULT_WIDTH, 20).build();
      resetButton = Button.builder(resetText, ignored -> {
        this.value = this.defaultValue;
        updateLabels();
      }).bounds(0, 0, RESET_WIDTH, 20).build();
      updateLabels();
    }

    @Override
    public BoardSettings getValue() {
      return value;
    }

    @Override
    public Optional<BoardSettings> getDefaultValue() {
      return Optional.of(defaultValue);
    }

    @Override
    public boolean isEdited() {
      return !value.equals(defaultValue);
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
      super.extractRenderState(
          graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta
      );
      graphics.text(
          Minecraft.getInstance().font,
          getDisplayedFieldName(),
          x,
          y + 6,
          getPreferredTextColor()
      );
      int resetX = x + entryWidth - RESET_WIDTH;
      int defaultX = resetX - GAP - DEFAULT_WIDTH;
      int loadedX = defaultX - GAP - LOAD_WIDTH;
      boolean active = valid && isEditable();
      loadedButton.active = active;
      defaultButton.active = active;
      resetButton.active = active && isEdited();
      position(loadedButton, graphics, loadedX, y, mouseX, mouseY, delta);
      position(defaultButton, graphics, defaultX, y, mouseX, mouseY, delta);
      position(resetButton, graphics, resetX, y, mouseX, mouseY, delta);
      if (!valid) {
        graphics.horizontalLine(x, x + entryWidth, y + 10, 0xFFFF0000);
      }
    }

    @Override
    public List<? extends GuiEventListener> children() {
      return List.of(loadedButton, defaultButton, resetButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      return List.of(loadedButton, defaultButton, resetButton);
    }

    private void updateLabels() {
      loadedButton.setMessage(Component.translatable(value.loaded()
          ? "starrylist.config.board.loaded" : "starrylist.config.board.unloaded")
          .withStyle(value.loaded() ? ChatFormatting.GREEN : ChatFormatting.RED));
      loadedButton.setTooltip(Tooltip.create(Component.translatable(value.loaded()
          ? "starrylist.config.board.loaded.tooltip"
          : "starrylist.config.board.unloaded.tooltip")));
      defaultButton.setMessage(Component.translatable(value.defaultEnabled()
          ? "starrylist.config.board.default_enabled"
          : "starrylist.config.board.default_disabled")
          .withStyle(value.defaultEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED));
      defaultButton.setTooltip(Tooltip.create(Component.translatable(value.defaultEnabled()
          ? "starrylist.config.board.default_enabled.tooltip"
          : "starrylist.config.board.default_disabled.tooltip")));
      resetButton.setTooltip(Tooltip.create(Component.translatable(
          "starrylist.config.board.reset.tooltip"
      )));
    }

    private static void position(
        Button button,
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int mouseX,
        int mouseY,
        float delta
    ) {
      button.setX(x);
      button.setY(y);
      button.extractRenderState(graphics, mouseX, mouseY, delta);
    }
  }

  private static boolean savePending(StarryListConfigData replacement) {
    return StarryListConfigManager.getInstance().savePending(replacement);
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
      if (copied.equals(values)) {
        return;
      }

      values = copied;
      for (BlacklistListEntry editor : editors) {
        if (editor != source) {
          editor.receive(values);
        }
      }
    }

    private List<String> values() {
      return List.copyOf(values);
    }
  }

  /** A category-local widget tree backed by the shared blacklist value model. */
  private static final class BlacklistListEntry extends StringListListEntry {
    private final BlacklistEditorModel model;
    private final boolean suppressErrors;
    private List<String> observedValues;

    @SuppressWarnings("deprecation")
    private BlacklistListEntry(
        Component title,
        BlacklistEditorModel model,
        Component resetButtonKey,
        boolean suppressErrors
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
      this.suppressErrors = suppressErrors;
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
    public Optional<Component> getError() {
      return suppressErrors ? Optional.empty() : super.getError();
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
        if (!narratables.contains(cell)) {
          narratables.add(cell);
        }
      }
      super.extractRenderState(
          graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta
      );
    }

    private void publishPendingChanges() {
      List<String> current = List.copyOf(getValue());
      if (current.equals(observedValues)) {
        return;
      }

      observedValues = current;
      model.publish(this, current);
    }

    private void receive(List<String> replacement) {
      // Do not overwrite a local edit which has not reached its next render pass yet.
      if (!List.copyOf(getValue()).equals(observedValues)) {
        return;
      }

      replaceCells(replacement);
      observedValues = List.copyOf(replacement);
    }

    private void replaceCells(List<String> replacement) {
      widgets.removeAll(cells);
      narratables.removeAll(cells);
      for (StringListCell cell : cells) {
        cell.onDelete();
      }

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
