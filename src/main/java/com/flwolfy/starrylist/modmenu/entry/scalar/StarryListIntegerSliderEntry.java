package com.flwolfy.starrylist.modmenu.entry.scalar;

import com.flwolfy.starrylist.modmenu.builder.StarryListEntryContext;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListControlLayout;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListPendingEntry;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListTooltipEntry;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Model-bound bounded integer slider configuration field. */
public final class StarryListIntegerSliderEntry extends StarryListTooltipEntry<Integer>
    implements StarryListPendingEntry {

  private final StarryListEntryContext context;
  private final int minimum;
  private final int maximum;
  private final int original;
  private final int defaultValue;
  private final Slider slider;
  private final Button resetButton;
  private int value;

  /**
   * Creates a shared-model integer slider.
   *
   * @param context field context
   * @param minimum inclusive minimum
   * @param maximum inclusive maximum
   */
  public StarryListIntegerSliderEntry(
      StarryListEntryContext context,
      int minimum,
      int maximum
  ) {
    super(context.label(), context::tooltipValue);
    this.context = context;
    this.minimum = minimum;
    this.maximum = maximum;
    original = (Integer) context.field().value();
    defaultValue = (Integer) context.field().defaultValue();
    value = original;
    slider = new Slider(progress(value));
    resetButton = Button.builder(context.resetText(), ignored -> setValue(defaultValue))
        .bounds(
            0,
            0,
            Minecraft.getInstance().font.width(context.resetText()) + 6,
            20
        ).build();
    setErrorSupplier(() -> context.model().error(
        context.field().path(), context.suppressErrors()
    ));
  }

  @Override
  public Integer getValue() {
    return value;
  }

  @Override
  public Optional<Integer> getDefaultValue() {
    return Optional.of(defaultValue);
  }

  @Override
  public boolean isEdited() {
    return value != original;
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
    int shared = context.model().get(context.field().path(), Integer.class);
    if (shared != value) {
      setValue(shared);
    }
    super.extractRenderState(
        graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta
    );

    Component title = getDisplayedFieldName();
    boolean bidirectional = Minecraft.getInstance().font.isBidirectional();
    int resetX = bidirectional ? x : StarryListControlLayout.resetX(
        x, entryWidth, resetButton.getWidth()
    );
    int sliderX = bidirectional
        ? resetX + resetButton.getWidth() + 1
        : StarryListControlLayout.valueX(x, entryWidth);
    graphics.text(
        Minecraft.getInstance().font,
        title.getVisualOrderText(),
        bidirectional
            ? Minecraft.getInstance().getWindow().getGuiScaledWidth() - x
                - Minecraft.getInstance().font.width(title)
            : x,
        y + 6,
        getPreferredTextColor()
    );

    resetButton.setX(resetX);
    resetButton.setY(y);
    resetButton.active = isEditable() && value != defaultValue;
    slider.setX(sliderX);
    slider.setY(y);
    slider.setWidth(StarryListControlLayout.valueWidth(resetButton.getWidth()));
    slider.active = isEditable();
    resetButton.extractRenderState(graphics, mouseX, mouseY, delta);
    slider.extractRenderState(graphics, mouseX, mouseY, delta);
  }

  @Override
  public List<? extends GuiEventListener> children() {
    return List.of(slider, resetButton);
  }

  @Override
  public List<? extends NarratableEntry> narratables() {
    return List.of(slider, resetButton);
  }

  @Override
  public void flush() {
    context.model().set(context.field().path(), value);
  }

  private void setValue(int replacement) {
    value = Mth.clamp(replacement, minimum, maximum);
    slider.setProgress(progress(value));
    context.model().set(context.field().path(), value);
  }

  private double progress(int current) {
    return (double) (current - minimum) / Math.abs(maximum - minimum);
  }

  private final class Slider extends AbstractSliderButton {

    private Slider(double progress) {
      super(0, 0, 0, 20, Component.empty(), progress);
      updateMessage();
    }

    @Override
    protected void updateMessage() {
      setMessage(Component.literal(Integer.toString(StarryListIntegerSliderEntry.this.value)));
    }

    @Override
    protected void applyValue() {
      value = minimum + (int) (Math.abs(maximum - minimum) * this.value);
      context.model().set(context.field().path(), value);
      updateMessage();
    }

    private void setProgress(double progress) {
      setValue(progress);
      updateMessage();
    }
  }
}
