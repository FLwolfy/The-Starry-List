package com.flwolfy.starrylist.modmenu.entry.board;

import com.flwolfy.starrylist.modmenu.entry.common.StarryListTooltipEntry;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** Three-button board control bound to a shared board settings model. */
public final class StarryListBoardListEntry
    extends StarryListTooltipEntry<StarryListBoardSettings> {

  private static final int GAP = 4;
  private static final int LOAD_WIDTH = 68;
  private static final int DEFAULT_WIDTH = 104;

  private final Supplier<StarryListBoardSettings> valueSupplier;
  private final Consumer<StarryListBoardSettings> valueConsumer;
  private final StarryListBoardSettings originalValue;
  private final StarryListBoardSettings defaultValue;
  private final Button loadedButton;
  private final Button defaultButton;
  private final Button resetButton;
  private final boolean valid;

  /**
   * Creates a board control entry.
   *
   * @param title localized board title
   * @param valueSupplier shared current value supplier
   * @param valueConsumer shared value replacement consumer
   * @param defaultValue reset value
   * @param resetText reset button text
   * @param valid whether the board passed preview validation
   * @param diagnostic validation or informational tooltip
   */
  public StarryListBoardListEntry(
      Component title,
      Supplier<StarryListBoardSettings> valueSupplier,
      Consumer<StarryListBoardSettings> valueConsumer,
      StarryListBoardSettings defaultValue,
      Component resetText,
      boolean valid,
      Component diagnostic
  ) {
    super(
        valid ? title : title.copy().withStyle(ChatFormatting.RED, ChatFormatting.STRIKETHROUGH),
        () -> Optional.of(new Component[]{diagnostic == null
            ? Component.translatable("starrylist.config.enabled_boards.tooltip")
            : diagnostic})
    );
    this.valueSupplier = valueSupplier;
    this.valueConsumer = valueConsumer;
    this.originalValue = valueSupplier.get();
    this.defaultValue = defaultValue;
    this.valid = valid;
    loadedButton = Button.builder(Component.empty(), ignored -> {
      StarryListBoardSettings value = getValue();
      valueConsumer.accept(new StarryListBoardSettings(!value.loaded(), value.defaultEnabled()));
      updateLabels();
    }).bounds(0, 0, LOAD_WIDTH, 20).build();
    defaultButton = Button.builder(Component.empty(), ignored -> {
      StarryListBoardSettings value = getValue();
      valueConsumer.accept(new StarryListBoardSettings(value.loaded(), !value.defaultEnabled()));
      updateLabels();
    }).bounds(0, 0, DEFAULT_WIDTH, 20).build();
    resetButton = Button.builder(resetText, ignored -> {
      valueConsumer.accept(defaultValue);
      updateLabels();
    }).bounds(0, 0, Minecraft.getInstance().font.width(resetText) + 6, 20).build();
    updateLabels();
  }

  @Override
  public StarryListBoardSettings getValue() {
    return valueSupplier.get();
  }

  @Override
  public Optional<StarryListBoardSettings> getDefaultValue() {
    return Optional.of(defaultValue);
  }

  @Override
  public boolean isEdited() {
    return !getValue().equals(originalValue);
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
    updateLabels();
    graphics.text(
        Minecraft.getInstance().font, getDisplayedFieldName(), x, y + 6, getPreferredTextColor()
    );
    int resetX = x + entryWidth - resetButton.getWidth();
    int defaultX = resetX - GAP - DEFAULT_WIDTH;
    int loadedX = defaultX - GAP - LOAD_WIDTH;
    boolean active = valid && isEditable();
    loadedButton.active = active;
    defaultButton.active = active;
    resetButton.active = active && !getValue().equals(defaultValue);
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
    StarryListBoardSettings value = getValue();
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
