package com.flwolfy.starrylist.modmenu;

import com.flwolfy.starrylist.scoreboard.StarryListBoardDefinition;
import com.flwolfy.starrylist.scoreboard.StarryListBoardRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** Cloth Config entry that edits the enabled order of all six fixed leaderboards. */
final class StarryListBoardListEntry extends AbstractConfigListEntry<List<String>> {

  private static final int ROW_HEIGHT = 24;

  private final List<String> initialValue;
  private final List<String> defaultValue;
  private final List<String> selected;
  private final List<StarryListBoardDefinition> definitions;
  private final BooleanSupplier hiddenByDefault;
  private final List<Button> buttons = new ArrayList<>();

  StarryListBoardListEntry(
      List<String> value,
      List<String> defaults,
      BooleanSupplier hiddenByDefault,
      Consumer<List<String>> saveConsumer
  ) {
    super(Component.translatable("starrylist.config.default_boards"), false);
    this.initialValue = List.copyOf(value);
    this.defaultValue = List.copyOf(defaults);
    this.selected = new ArrayList<>(value);
    this.definitions = new StarryListBoardRegistry().all();
    this.hiddenByDefault = hiddenByDefault;
    this.saveCallback = saveConsumer;

    for (int row = 0; row < definitions.size(); row++) {
      int selectedRow = row;
      buttons.add(Button.builder(Component.empty(), ignored -> toggle(selectedRow)).build());
      buttons.add(Button.builder(Component.literal("↑"), ignored -> move(selectedRow, -1)).build());
      buttons.add(Button.builder(Component.literal("↓"), ignored -> move(selectedRow, 1)).build());
    }
    buttons.add(Button.builder(
        Component.translatable("starrylist.config.default_boards.reset"),
        ignored -> {
          selected.clear();
          selected.addAll(defaultValue);
        }
    ).build());
  }

  /** {@inheritDoc} */
  @Override
  public List<String> getValue() {
    return List.copyOf(selected);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<List<String>> getDefaultValue() {
    return Optional.of(defaultValue);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEdited() {
    return !selected.equals(initialValue);
  }

  /** {@inheritDoc} */
  @Override
  public int getItemHeight() {
    return ROW_HEIGHT * (definitions.size() + 1);
  }

  /** {@inheritDoc} */
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
      float tickDelta
  ) {
    super.extractRenderState(
        graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta
    );
    List<StarryListBoardDefinition> ordered = orderedDefinitions();
    for (int row = 0; row < ordered.size(); row++) {
      StarryListBoardDefinition board = ordered.get(row);
      int selectedIndex = selected.indexOf(board.id());
      boolean enabled = selectedIndex >= 0;
      Button toggle = buttons.get(row * 3);
      Button up = buttons.get(row * 3 + 1);
      Button down = buttons.get(row * 3 + 2);
      int rowY = y + row * ROW_HEIGHT;
      toggle.setRectangle(entryWidth - 92, 20, x, rowY);
      up.setRectangle(40, 20, x + entryWidth - 88, rowY);
      down.setRectangle(40, 20, x + entryWidth - 44, rowY);
      toggle.setMessage(Component.translatable(
          enabled ? "starrylist.config.board.enabled" : "starrylist.config.board.disabled",
          Component.translatable(board.translationKey())
      ));
      boolean canDisable = !enabled || selected.size() > 1 || hiddenByDefault.getAsBoolean();
      toggle.active = canDisable;
      toggle.setTooltip(canDisable ? null : Tooltip.create(
          Component.translatable("starrylist.config.board.last_enabled")
      ));
      up.active = enabled && selectedIndex > 0;
      down.active = enabled && selectedIndex < selected.size() - 1;
      toggle.extractRenderState(graphics, mouseX, mouseY, tickDelta);
      up.extractRenderState(graphics, mouseX, mouseY, tickDelta);
      down.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }
    Button reset = buttons.get(buttons.size() - 1);
    reset.setRectangle(entryWidth, 20, x, y + definitions.size() * ROW_HEIGHT);
    reset.active = !selected.equals(defaultValue);
    reset.extractRenderState(graphics, mouseX, mouseY, tickDelta);
  }

  /** {@inheritDoc} */
  @Override
  public List<? extends GuiEventListener> children() {
    return buttons;
  }

  /** {@inheritDoc} */
  @Override
  public List<? extends NarratableEntry> narratables() {
    return buttons;
  }

  private void toggle(int row) {
    StarryListBoardDefinition board = orderedDefinitions().get(row);
    if (selected.remove(board.id())) return;
    selected.add(board.id());
  }

  private void move(int row, int offset) {
    StarryListBoardDefinition board = orderedDefinitions().get(row);
    int source = selected.indexOf(board.id());
    int target = source + offset;
    if (source < 0 || target < 0 || target >= selected.size()) return;
    selected.remove(source);
    selected.add(target, board.id());
  }

  private List<StarryListBoardDefinition> orderedDefinitions() {
    List<StarryListBoardDefinition> ordered = new ArrayList<>();
    for (String id : selected) {
      definitions.stream().filter(board -> board.id().equals(id)).findFirst().ifPresent(ordered::add);
    }
    definitions.stream().filter(board -> !selected.contains(board.id())).forEach(ordered::add);
    return ordered;
  }
}
