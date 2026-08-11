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

/** Model-bound single-line string configuration field. */
public final class StarryListStringEntry extends StarryListTooltipEntry<String>
    implements StarryListPendingEntry {

  private final StarryListEntryContext context;
  private final String original;
  private final String defaultValue;
  private final EditBox textField;
  private final Button resetButton;
  private long observedRevision;
  private String observedText;

  /**
   * Creates a shared-model string field.
   *
   * @param context field context
   */
  public StarryListStringEntry(StarryListEntryContext context) {
    super(context.label(), context::tooltipValue);
    this.context = context;
    original = (String) context.field().value();
    defaultValue = (String) context.field().defaultValue();
    textField = new EditBox(
        Minecraft.getInstance().font, 0, 0, 0, 20, context.label()
    );
    textField.setValue(original);
    resetButton = Button.builder(context.resetText(), ignored -> textField.setValue(defaultValue))
        .bounds(0, 0, Minecraft.getInstance().font.width(context.resetText()) + 6, 20)
        .build();
    observedRevision = context.model().revision();
    observedText = original;
  }

  @Override
  public String getValue() {
    return textField.getValue();
  }

  @Override
  public Optional<String> getDefaultValue() {
    return Optional.of(defaultValue);
  }

  @Override
  public Optional<Component> getError() {
    return context.model().error(context.field().path(), context.suppressErrors());
  }

  @Override
  public boolean isEdited() {
    return !getValue().equals(original);
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
    positionControls(graphics, y, x, entryWidth, mouseX, mouseY, delta);
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
    String current = textField.getValue();
    if (!current.equals(observedText)) {
      observedText = current;
      context.model().set(context.field().path(), current);
      observedRevision = context.model().revision();
    }
  }

  private void synchronize() {
    if (!textField.getValue().equals(observedText)) {
      flush();
    } else if (observedRevision != context.model().revision()) {
      textField.setValue(context.model().get(context.field().path(), String.class));
      observedText = textField.getValue();
      observedRevision = context.model().revision();
    }
  }

  private void positionControls(
      GuiGraphicsExtractor graphics, int y, int x, int entryWidth,
      int mouseX, int mouseY, float delta
  ) {
    graphics.text(
        Minecraft.getInstance().font, getDisplayedFieldName(), x, y + 6, getPreferredTextColor()
    );
    int resetX = StarryListControlLayout.resetX(
        x, entryWidth, resetButton.getWidth()
    );
    resetButton.setX(resetX);
    resetButton.setY(y);
    resetButton.active = isEditable() && !getValue().equals(defaultValue);
    textField.setX(StarryListControlLayout.valueX(x, entryWidth));
    textField.setY(y);
    textField.setWidth(StarryListControlLayout.valueWidth(resetButton.getWidth()));
    textField.setEditable(isEditable());
    textField.extractRenderState(graphics, mouseX, mouseY, delta);
    resetButton.extractRenderState(graphics, mouseX, mouseY, delta);
  }
}
