package com.flwolfy.starrylist.modmenu.entry.scalar;

import com.flwolfy.starrylist.modmenu.builder.StarryListEntryContext;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListControlLayout;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListPendingEntry;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListTooltipEntry;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** Model-bound integer configuration field. */
public final class StarryListIntegerEntry extends StarryListTooltipEntry<Integer>
    implements StarryListPendingEntry {

  private final StarryListEntryContext context;
  private final Integer minimum;
  private final Integer maximum;
  private final int original;
  private final int defaultValue;
  private final EditBox textField;
  private final Button resetButton;
  private long observedRevision;
  private String observedText;

  /**
   * Creates a shared-model integer field.
   *
   * @param context field context
   * @param minimum optional inclusive minimum
   * @param maximum optional inclusive maximum
   */
  public StarryListIntegerEntry(
      StarryListEntryContext context,
      Integer minimum,
      Integer maximum
  ) {
    super(context.label(), context::tooltipValue);
    this.context = context;
    this.minimum = minimum;
    this.maximum = maximum;
    original = (Integer) context.field().value();
    defaultValue = (Integer) context.field().defaultValue();

    int resetWidth = Minecraft.getInstance().font.width(context.resetText()) + 6;
    textField = new EditBox(
        Minecraft.getInstance().font,
        0,
        0,
        StarryListControlLayout.valueWidth(resetWidth),
        20,
        context.label()
    );
    textField.setValue(Integer.toString(original));
    resetButton = Button.builder(
        context.resetText(), ignored -> textField.setValue(Integer.toString(defaultValue))
    ).bounds(0, 0, resetWidth, 20).build();

    observedRevision = context.model().revision();
    observedText = textField.getValue();
  }

  @Override
  public Integer getValue() {
    return parse().orElse(context.model().get(context.field().path(), Integer.class));
  }

  @Override
  public Optional<Integer> getDefaultValue() {
    return Optional.of(defaultValue);
  }

  @Override
  public Optional<Component> getError() {
    if (context.suppressErrors()) {
      return Optional.empty();
    }
    Optional<Integer> parsed = parse();
    if (parsed.isEmpty()) {
      return Optional.of(Component.translatable("starrylist.config.integer.invalid"));
    }
    if (minimum != null && parsed.get() < minimum
        || maximum != null && parsed.get() > maximum) {
      return Optional.of(Component.translatable(
          "starrylist.config." + context.field().path() + ".invalid"
      ));
    }
    return context.model().error(context.field().path(), false);
  }

  @Override
  public boolean isEdited() {
    return parse().map(value -> value != original).orElse(true);
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth,
      int entryHeight, int mouseX, int mouseY, boolean hovered, float delta
  ) {
    synchronize();
    super.extractRenderState(
        graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta
    );
    graphics.text(
        Minecraft.getInstance().font, getDisplayedFieldName(), x, y + 6, getPreferredTextColor()
    );
    int resetX = StarryListControlLayout.resetX(
        x, entryWidth, resetButton.getWidth()
    );
    resetButton.setX(resetX);
    resetButton.setY(y);
    resetButton.active = isEditable() && !textField.getValue().equals(Integer.toString(defaultValue));
    textField.setX(StarryListControlLayout.valueX(x, entryWidth));
    textField.setY(y);
    textField.setWidth(StarryListControlLayout.valueWidth(resetButton.getWidth()));
    textField.setEditable(isEditable());
    textField.extractRenderState(graphics, mouseX, mouseY, delta);
    resetButton.extractRenderState(graphics, mouseX, mouseY, delta);
  }

  @Override
  public List<? extends GuiEventListener> children() {
    return List.of(textField, resetButton);
  }

  @Override
  public List<? extends NarratableEntry> narratables() {
    return List.of(textField, resetButton);
  }

  @Override
  public void flush() {
    if (textField.getValue().equals(observedText)) {
      return;
    }
    observedText = textField.getValue();
    parse().ifPresent(value -> context.model().set(context.field().path(), value));
    observedRevision = context.model().revision();
  }

  private void synchronize() {
    if (!textField.getValue().equals(observedText)) {
      flush();
    } else if (observedRevision != context.model().revision()) {
      textField.setValue(Integer.toString(
          context.model().get(context.field().path(), Integer.class)
      ));
      observedText = textField.getValue();
      observedRevision = context.model().revision();
    }
  }

  private Optional<Integer> parse() {
    try {
      return Optional.of(Integer.parseInt(textField.getValue()));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }
}
