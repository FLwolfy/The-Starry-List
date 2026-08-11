package com.flwolfy.starrylist.modmenu.builder;

import com.flwolfy.starrylist.modmenu.model.StarryListConfigEditorModel;
import com.flwolfy.starrylist.modmenu.model.StarryListConfigRecordMapper;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Supplies metadata and shared state to one configuration entry builder. */
public final class StarryListEntryContext {

  private final StarryListConfigEditorModel model;
  private final StarryListConfigRecordMapper.Field field;
  private final Component label;
  private final Component[] tooltip;
  private final Component resetText;
  private final boolean suppressErrors;

  /**
   * Creates an entry-building context.
   *
   * @param model shared editor model
   * @param field flattened field metadata
   * @param label localized field label
   * @param tooltip localized tooltip lines
   * @param resetText Cloth Config reset button text
   * @param suppressErrors whether this duplicate view suppresses validation errors
   */
  public StarryListEntryContext(
      StarryListConfigEditorModel model,
      StarryListConfigRecordMapper.Field field,
      Component label,
      Component[] tooltip,
      Component resetText,
      boolean suppressErrors
  ) {
    this.model = model;
    this.field = field;
    this.label = label;
    this.tooltip = tooltip.clone();
    this.resetText = resetText;
    this.suppressErrors = suppressErrors;
  }

  /**
   * Returns the shared editor model.
   *
   * @return shared editor model
   */
  public StarryListConfigEditorModel model() {
    return model;
  }

  /**
   * Returns the flattened field metadata.
   *
   * @return flattened field metadata
   */
  public StarryListConfigRecordMapper.Field field() {
    return field;
  }

  /**
   * Returns the localized field label.
   *
   * @return localized field label
   */
  public Component label() {
    return label;
  }

  /**
   * Returns a defensive copy of the localized tooltip lines.
   *
   * @return localized tooltip lines
   */
  public Component[] tooltip() {
    return tooltip.clone();
  }

  /**
   * Returns the reset button text.
   *
   * @return reset button text
   */
  public Component resetText() {
    return resetText;
  }

  /**
   * Checks whether validation errors are suppressed in this duplicate view.
   *
   * @return whether validation errors are suppressed
   */
  public boolean suppressErrors() {
    return suppressErrors;
  }

  /**
   * Returns the tooltip value expected by Cloth Config entries.
   *
   * @return tooltip supplier value
   */
  public Optional<Component[]> tooltipValue() {
    return tooltip.length == 0 ? Optional.empty() : Optional.of(tooltip());
  }
}
