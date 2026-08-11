package com.flwolfy.starrylist.modmenu.entry.blacklist;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import me.shedaniel.clothconfig2.gui.entries.AbstractTextFieldListListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Regex-aware blacklist cell that preserves platform IME composition while focused. */
public final class StarryListBlacklistPatternCell extends AbstractTextFieldListListEntry
    .AbstractTextFieldListCell<
        String,
        StarryListBlacklistPatternCell,
        StarryListBlacklistListEntry
    > {

  private static final int LINE_COLOR = 0x60FFFFFF;
  private static final int FOCUSED_LINE_COLOR = 0xFFFFFFFF;
  private static final int ERROR_LINE_COLOR = 0xFFFF5555;

  private final StarryListBlacklistListEntry entry;

  /**
   * Creates one regex input cell.
   *
   * @param value initial expression
   * @param entry owning blacklist entry
   */
  public StarryListBlacklistPatternCell(
      String value,
      StarryListBlacklistListEntry entry
  ) {
    super(value, entry);
    this.entry = entry;
    widget.setHint(Component.translatable("starrylist.config.blacklist.pattern.hint"));
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
        graphics,
        index,
        y,
        x,
        entryWidth,
        entryHeight,
        mouseX,
        mouseY,
        hovered,
        delta
    );
    Component number = Component.literal(entry.numberOf(this) + ".")
        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    int numberX = widget.getX()
        - Minecraft.getInstance().font.width(number)
        - 4;
    graphics.text(
        Minecraft.getInstance().font,
        number,
        numberX,
        widget.getY(),
        0xFFFFAA00
    );
    int lineColor = widget.isFocused()
        ? FOCUSED_LINE_COLOR
        : getError().isPresent() ? ERROR_LINE_COLOR : LINE_COLOR;
    graphics.fill(
        widget.getX(),
        widget.getY() + 11,
        widget.getX() + widget.getWidth(),
        widget.getY() + 12,
        lineColor
    );
  }

  @Override
  protected String substituteDefault(String value) {
    return value == null ? "" : value;
  }

  @Override
  protected boolean isValidText(String value) {
    return true;
  }

  @Override
  public String getValue() {
    return widget.getValue();
  }

  @Override
  public Optional<Component> getError() {
    if (widget.isFocused()) {
      return Optional.empty();
    }
    String expression = getValue();
    if (expression == null || expression.isBlank()) {
      return invalid();
    }
    try {
      Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      return Optional.empty();
    } catch (PatternSyntaxException exception) {
      return invalid();
    }
  }

  private static Optional<Component> invalid() {
    return Optional.of(Component.translatable(
        "starrylist.config.blacklist.pattern.invalid"
    ));
  }
}
