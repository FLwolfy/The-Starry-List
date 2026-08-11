package com.flwolfy.starrylist.modmenu.entry.scalar;

import com.flwolfy.starrylist.modmenu.builder.StarryListEntryContext;
import com.flwolfy.starrylist.modmenu.entry.common.StarryListTooltipEntry;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** Visible non-editable diagnostic for an unregistered configuration field type. */
public final class StarryListUnsupportedEntry extends StarryListTooltipEntry<Object> {

  private final Object value;

  /**
   * Creates an unsupported-field diagnostic entry.
   *
   * @param context field context
   */
  public StarryListUnsupportedEntry(StarryListEntryContext context) {
    super(
        context.label().copy().withStyle(ChatFormatting.RED),
        () -> Optional.of(new Component[]{Component.literal(
            "No Cloth Config builder for " + context.field().genericType().getTypeName()
        )})
    );
    value = context.field().value();
    setEditable(false);
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public Optional<Object> getDefaultValue() {
    return Optional.empty();
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
    graphics.text(
        Minecraft.getInstance().font,
        getDisplayedFieldName(),
        x,
        y + 6,
        getPreferredTextColor()
    );
    graphics.text(
        Minecraft.getInstance().font,
        Component.literal("Unsupported").withStyle(ChatFormatting.RED),
        x + entryWidth - 70,
        y + 6,
        0xFFFF5555
    );
  }

  @Override
  public List<? extends GuiEventListener> children() {
    return List.of();
  }

  @Override
  public List<? extends NarratableEntry> narratables() {
    return List.of();
  }
}
