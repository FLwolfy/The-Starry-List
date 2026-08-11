package com.flwolfy.starrylist.modmenu.entry.blacklist;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import me.shedaniel.clothconfig2.gui.entries.AbstractTextFieldListListEntry;
import net.minecraft.network.chat.Component;

/** Regex-aware blacklist cell that preserves platform IME composition while focused. */
public final class StarryListBlacklistPatternCell extends AbstractTextFieldListListEntry
    .AbstractTextFieldListCell<
        String,
        StarryListBlacklistPatternCell,
        StarryListBlacklistListEntry
    > {

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
    widget.setHint(Component.translatable("starrylist.config.blacklist.pattern.hint"));
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
