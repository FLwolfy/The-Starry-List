package com.flwolfy.starrylist.modmenu.entry.scalar;

import com.flwolfy.starrylist.modmenu.builder.StarryListEntryContext;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListControlLayout;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListTooltipEntry;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** Model-bound enum selector used by independently rendered configuration views. */
public final class StarryListEnumEntry extends StarryListTooltipEntry<Enum<?>> {

  private final StarryListEntryContext context;
  private final Enum<?> original;
  private final Enum<?> defaultValue;
  private final Enum<?>[] constants;
  private final Button valueButton;
  private final Button resetButton;

  /**
   * Creates a shared-model enum entry.
   *
   * @param context field context
   */
  public StarryListEnumEntry(StarryListEntryContext context) {
    super(context.label(), context::tooltipValue);
    this.context = context;
    original = (Enum<?>) context.field().value();
    defaultValue = (Enum<?>) context.field().defaultValue();
    constants = (Enum<?>[]) context.field().rawType().getEnumConstants();
    valueButton = Button.builder(Component.empty(), ignored -> advance())
        .bounds(0, 0, 0, 20).build();
    resetButton = Button.builder(context.resetText(), ignored -> {
      context.model().set(context.field().path(), defaultValue);
      updateLabel();
    }).bounds(
        0, 0, Minecraft.getInstance().font.width(context.resetText()) + 6, 20
    ).build();
    setErrorSupplier(() -> context.model().error(
        context.field().path(), context.suppressErrors()
    ));
    updateLabel();
  }

  @Override
  public Enum<?> getValue() {
    return (Enum<?>) context.model().get(context.field().path(), context.field().rawType());
  }

  @Override
  public Optional<Enum<?>> getDefaultValue() {
    return Optional.of(defaultValue);
  }

  @Override
  public boolean isEdited() {
    return getValue() != original;
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
    updateLabel();
    graphics.text(
        Minecraft.getInstance().font, getDisplayedFieldName(), x, y + 6, getPreferredTextColor()
    );
    int resetX = StarryListControlLayout.resetX(
        x, entryWidth, resetButton.getWidth()
    );
    resetButton.setX(resetX);
    resetButton.setY(y);
    resetButton.active = isEditable() && getValue() != defaultValue;
    valueButton.setX(StarryListControlLayout.valueX(x, entryWidth));
    valueButton.setY(y);
    valueButton.setWidth(StarryListControlLayout.valueWidth(resetButton.getWidth()));
    valueButton.active = isEditable();
    valueButton.extractRenderState(graphics, mouseX, mouseY, delta);
    resetButton.extractRenderState(graphics, mouseX, mouseY, delta);
  }

  @Override
  public List<? extends GuiEventListener> children() {
    return List.of(valueButton, resetButton);
  }

  @Override
  public List<? extends NarratableEntry> narratables() {
    return List.of(valueButton, resetButton);
  }

  private void advance() {
    Enum<?> current = getValue();
    int next = (current.ordinal() + 1) % constants.length;
    context.model().set(context.field().path(), constants[next]);
    updateLabel();
  }

  private void updateLabel() {
    valueButton.setMessage(Component.literal(getValue().toString()));
  }
}
