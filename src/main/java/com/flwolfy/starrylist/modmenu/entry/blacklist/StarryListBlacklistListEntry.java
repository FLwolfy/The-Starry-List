package com.flwolfy.starrylist.modmenu.entry.blacklist;

import com.flwolfy.starrylist.data.config.StarryListConfigData;
import java.util.List;
import java.util.Optional;
import me.shedaniel.clothconfig2.gui.entries.AbstractTextFieldListListEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/** Category-local regex list whose initial and dynamic rows use the same custom cell type. */
public final class StarryListBlacklistListEntry extends AbstractTextFieldListListEntry<
    String,
    StarryListBlacklistPatternCell,
    StarryListBlacklistListEntry
> {

  private final StarryListBlacklistEditorModel model;
  private final boolean suppressErrors;
  private List<String> observedValues;

  /**
   * Creates an independently rendered blacklist list.
   *
   * @param title localized list title
   * @param model shared blacklist model
   * @param resetButtonKey Cloth Config reset button text
   * @param suppressErrors whether this All-category copy suppresses aggregate errors
   */
  public StarryListBlacklistListEntry(
      Component title,
      StarryListBlacklistEditorModel model,
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
        model::publish,
        () -> StarryListConfigData.DEFAULT.blacklist().playerNamePatterns(),
        resetButtonKey,
        false,
        true,
        true,
        StarryListBlacklistPatternCell::new
    );
    this.model = model;
    this.suppressErrors = suppressErrors;
    observedValues = model.values();
    model.register(this);
  }

  @Override
  public StarryListBlacklistListEntry self() {
    return this;
  }

  @Override
  public Optional<Component> getError() {
    return suppressErrors ? Optional.empty() : super.getError();
  }

  @Override
  public boolean isMouseOver(double mouseX, double mouseY) {
    if (super.isMouseOver(mouseX, mouseY)) {
      return true;
    }
    for (GuiEventListener child : children()) {
      if (child.isMouseOver(mouseX, mouseY)) {
        return true;
      }
    }
    return false;
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
    for (StarryListBlacklistPatternCell cell : cells) {
      if (!narratables.contains(cell)) {
        narratables.add(cell);
      }
    }
    super.extractRenderState(
        graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta
    );
  }

  /**
   * Publishes this widget tree's pending cell values to the shared model.
   */
  public void publishPendingChanges() {
    List<String> current = List.copyOf(getValue());
    if (current.equals(observedValues)) {
      return;
    }
    observedValues = current;
    model.publish(this, current);
  }

  /**
   * Receives values published by another independent blacklist view.
   *
   * @param replacement replacement expressions
   */
  public void receive(List<String> replacement) {
    if (!List.copyOf(getValue()).equals(observedValues)) {
      return;
    }
    replaceCells(replacement);
    observedValues = List.copyOf(replacement);
  }

  int numberOf(StarryListBlacklistPatternCell cell) {
    return cells.indexOf(cell) + 1;
  }

  private void replaceCells(List<String> replacement) {
    widgets.removeAll(cells);
    narratables.removeAll(cells);
    for (StarryListBlacklistPatternCell cell : cells) {
      cell.onDelete();
    }
    cells.clear();
    for (String value : replacement) {
      StarryListBlacklistPatternCell cell = getFromValue(value);
      cells.add(cell);
      widgets.add(cell);
      narratables.add(cell);
    }
  }
}
