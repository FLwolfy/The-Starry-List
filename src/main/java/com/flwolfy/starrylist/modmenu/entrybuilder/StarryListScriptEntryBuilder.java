package com.flwolfy.starrylist.modmenu.entrybuilder;

import com.flwolfy.starrylist.data.script.StarryListScript;
import com.flwolfy.starrylist.data.script.StarryListScriptManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Multi-line JEXL editor with file import and compile-time validation. */
@SuppressWarnings("deprecation")
public final class StarryListScriptEntryBuilder
    extends StarryListEntryBuilderBase<StarryListScript> {

  @Override
  public AbstractFieldBuilder<StarryListScript, ?, ?> create(
      ConfigEntryBuilder builder,
      StarryListScript value,
      Component label
  ) {
    return new ScriptFieldBuilder(builder, value, label);
  }

  private static final class ScriptFieldBuilder extends AbstractFieldBuilder<
      StarryListScript,
      ScriptEntry,
      ScriptFieldBuilder
  > {

    private ScriptFieldBuilder(
        ConfigEntryBuilder builder,
        StarryListScript value,
        Component label
    ) {
      super(builder.getResetButtonKey(), label);
      this.value = Objects.requireNonNull(value);
    }

    @Override
    public ScriptEntry build() {
      return finishBuilding(new ScriptEntry(
          getFieldNameKey(),
          value,
          getDefaultValue().get(),
          getSaveConsumer(),
          getResetButtonKey(),
          getTooltipSupplier().apply(value).orElseGet(() -> new Component[0])
      ));
    }
  }

  private static final class ScriptEntry extends StringListEntry {

    private static final int BUTTON_SPACING = 4;
    private final Button editButton;
    private final Button importButton;

    private ScriptEntry(
        Component fieldName,
        StarryListScript script,
        StarryListScript defaultScript,
        Consumer<StarryListScript> saveConsumer,
        Component resetButton,
        Component... tooltip
    ) {
      super(
          fieldName,
          script.source(),
          resetButton,
          defaultScript::source,
          source -> saveConsumer.accept(new StarryListScript(source)),
          () -> Optional.of(tooltip),
          false
      );
      setErrorSupplier(() -> StarryListScriptManager.getInstance().validate(
          new StarryListScript(getValue())
      ) ? Optional.empty() : Optional.of(Component.translatable("starrylist.config.script.invalid")));

      Component edit = Component.translatable("starrylist.config.script.edit");
      editButton = Button.builder(edit, button -> editScript())
          .size(Minecraft.getInstance().font.width(edit) + 6, 20)
          .tooltip(Tooltip.create(Component.translatable("starrylist.config.script.edit.tooltip")))
          .build();
      Component importText = Component.translatable("starrylist.config.script.import");
      importButton = Button.builder(importText, button -> importScript())
          .size(Minecraft.getInstance().font.width(importText) + 6, 20)
          .tooltip(Tooltip.create(Component.translatable("starrylist.config.script.import.tooltip")))
          .build();
      textFieldWidget.visible = false;
      widgets.add(0, editButton);
      widgets.add(0, importButton);
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
        float tickDelta
    ) {
      resetButton.visible = false;
      textFieldWidget.visible = false;
      try {
        super.extractRenderState(
            graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta
        );
      } finally {
        resetButton.visible = true;
      }
      int width = (textFieldWidget.getWidth() - BUTTON_SPACING) / 2;
      editButton.setWidth(width);
      importButton.setWidth(width);
      editButton.setX(textFieldWidget.getX());
      importButton.setX(editButton.getX() + width + BUTTON_SPACING);
      editButton.setY(textFieldWidget.getY());
      importButton.setY(textFieldWidget.getY());
      editButton.active = isEditable();
      importButton.active = isEditable();
      editButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
      importButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
      resetButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    private void editScript() {
      Minecraft.getInstance().setScreenAndShow(
          new ScriptEditor(getConfigScreen(), getValue(), this::setValue)
      );
    }

    private void importScript() {
      String path;
      try (MemoryStack stack = MemoryStack.stackPush()) {
        PointerBuffer filters = stack.mallocPointer(1);
        filters.put(stack.UTF8("*.jexl")).flip();
        path = TinyFileDialogs.tinyfd_openFileDialog(
            "Import StarryList JEXL Script", "", filters, "JEXL scripts (*.jexl)", false
        );
      }
      if (path == null) return;
      try {
        setValue(Files.readString(Path.of(path)));
      } catch (Exception exception) {
        TinyFileDialogs.tinyfd_messageBox(
            "The-Starry-List",
            "Failed to import JEXL file:\n" + exception.getMessage(),
            "ok",
            "error",
            1
        );
      }
    }
  }

  private static final class ScriptEditor extends Screen {

    private static final int MARGIN = 20;
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 40;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_SPACING = 4;
    private final Screen parent;
    private final String initialSource;
    private final Consumer<String> confirmConsumer;
    private Button confirmButton;

    private ScriptEditor(Screen parent, String initialSource, Consumer<String> confirmConsumer) {
      super(Component.translatable("starrylist.config.script.editor.title"));
      this.parent = parent;
      this.initialSource = initialSource;
      this.confirmConsumer = confirmConsumer;
    }

    @Override
    protected void init() {
      MultiLineEditBox script = MultiLineEditBox.builder()
          .setX(MARGIN)
          .setY(HEADER_HEIGHT)
          .setShowBackground(true)
          .setShowDecorations(true)
          .build(font, width - MARGIN * 2, height - HEADER_HEIGHT - FOOTER_HEIGHT, title);
      script.setValue(initialSource);
      addRenderableWidget(script);
      addRenderableWidget(Button.builder(
          Component.translatable("starrylist.config.script.editor.cancel"),
          button -> onClose()
      ).bounds(width / 2 - BUTTON_WIDTH - BUTTON_SPACING / 2, height - 30, BUTTON_WIDTH, 20).build());
      confirmButton = addRenderableWidget(Button.builder(
          Component.translatable("starrylist.config.script.editor.confirm"),
          button -> {
            confirmConsumer.accept(script.getValue());
            onClose();
          }
      ).bounds(width / 2 + BUTTON_SPACING / 2, height - 30, BUTTON_WIDTH, 20).build());
      confirmButton.active = false;
      script.setValueListener(source -> confirmButton.active = !initialSource.equals(source)
          && StarryListScriptManager.getInstance().validate(new StarryListScript(source)));
      setInitialFocus(script);
    }

    @Override
    public void extractRenderState(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float tickDelta
    ) {
      super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
      graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
      minecraft.setScreenAndShow(parent);
    }
  }
}
