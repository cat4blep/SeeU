package dev.keryeshka.voxyseeu.fabric.client;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.fabric.client.config.VoxySeeUClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class SeeUConfigScreen extends Screen {
    private final Screen parent;
    private final VoxySeeUClientConfig draft;
    private final Consumer<VoxySeeUClientConfig> onSave;

    private DistanceSlider renderDistanceSlider;
    private DistanceSlider minDistanceSlider;
    private DistanceSlider shareDistanceSlider;
    private Button renderToggleButton;
    private Button nameTagsButton;
    private Button shareSelfButton;

    SeeUConfigScreen(Screen parent, VoxySeeUClientConfig draft, Consumer<VoxySeeUClientConfig> onSave) {
        super(Component.translatable("screen.seeu.title"));
        this.parent = parent;
        this.draft = draft;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        int contentWidth = 320;
        int x = (this.width - contentWidth) / 2;
        int y = Math.max(30, this.height / 6);
        int rowHeight = 24;

        renderToggleButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            draft.enabled = !draft.enabled;
            refreshButtons();
        }).bounds(x, y, contentWidth, 20).build());

        renderDistanceSlider = addRenderableWidget(new DistanceSlider(
                x,
                y + rowHeight,
                contentWidth,
                "screen.seeu.render_distance",
                64,
                SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS,
                draft.maximumRenderDistanceBlocks,
                value -> {
                    draft.maximumRenderDistanceBlocks = value;
                    if (draft.minimumProxyDistanceBlocks > value) {
                        draft.minimumProxyDistanceBlocks = value;
                        minDistanceSlider.setExternalValue(value);
                    }
                }
        ));

        minDistanceSlider = addRenderableWidget(new DistanceSlider(
                x,
                y + rowHeight * 2,
                contentWidth,
                "screen.seeu.min_distance",
                0,
                SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS,
                draft.minimumProxyDistanceBlocks,
                value -> draft.minimumProxyDistanceBlocks = Math.min(value, draft.maximumRenderDistanceBlocks)
        ));

        nameTagsButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            draft.renderNameTags = !draft.renderNameTags;
            refreshButtons();
        }).bounds(x, y + rowHeight * 3, contentWidth, 20).build());

        shareSelfButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            draft.shareSelf = !draft.shareSelf;
            refreshButtons();
        }).bounds(x, y + rowHeight * 4, contentWidth, 20).build());

        shareDistanceSlider = addRenderableWidget(new DistanceSlider(
                x,
                y + rowHeight * 5,
                contentWidth,
                "screen.seeu.share_distance",
                64,
                SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS,
                draft.shareMaximumDistanceBlocks,
                value -> draft.shareMaximumDistanceBlocks = value
        ));

        int buttonY = y + rowHeight * 7;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            draft.clamp();
            onSave.accept(draft.copy());
            onClose();
        }).bounds(x, buttonY, (contentWidth - 10) / 2, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x + (contentWidth + 10) / 2, buttonY, (contentWidth - 10) / 2, 20)
                .build());

        refreshButtons();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    private void refreshButtons() {
        renderToggleButton.setMessage(toggleLabel("screen.seeu.render_enabled", draft.enabled));
        nameTagsButton.setMessage(toggleLabel("screen.seeu.name_tags", draft.renderNameTags));
        shareSelfButton.setMessage(toggleLabel("screen.seeu.share_self", draft.shareSelf));
        renderDistanceSlider.active = draft.enabled;
        minDistanceSlider.active = draft.enabled;
        nameTagsButton.active = draft.enabled;
        shareDistanceSlider.active = draft.shareSelf;
        minDistanceSlider.setMaximum(draft.maximumRenderDistanceBlocks);
        if (draft.minimumProxyDistanceBlocks > draft.maximumRenderDistanceBlocks) {
            draft.minimumProxyDistanceBlocks = draft.maximumRenderDistanceBlocks;
            minDistanceSlider.setExternalValue(draft.minimumProxyDistanceBlocks);
        }
    }

    private static Component toggleLabel(String key, boolean enabled) {
        return Component.translatable(key, Component.translatable(enabled ? "options.on" : "options.off"));
    }

    private static final class DistanceSlider extends AbstractSliderButton {
        private final String labelKey;
        private final int minimum;
        private int maximum;
        private final IntConsumer onValueChanged;

        private DistanceSlider(int x, int y, int width, String labelKey, int minimum, int maximum, int value, IntConsumer onValueChanged) {
            super(x, y, width, 20, Component.empty(), 0.0D);
            this.labelKey = labelKey;
            this.minimum = minimum;
            this.maximum = Math.max(minimum, maximum);
            this.onValueChanged = onValueChanged;
            setExternalValue(value);
        }

        private void setMaximum(int maximum) {
            this.maximum = Math.max(minimum, maximum);
            setExternalValue(currentValue());
        }

        private void setExternalValue(int value) {
            this.value = toSliderValue(value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(labelKey, currentValue()));
        }

        @Override
        protected void applyValue() {
            onValueChanged.accept(currentValue());
            updateMessage();
        }

        private int currentValue() {
            int rawValue = minimum + Mth.floor(this.value * (maximum - minimum));
            return Mth.clamp(rawValue, minimum, maximum);
        }

        private double toSliderValue(int value) {
            if (maximum <= minimum) {
                return 0.0D;
            }
            return Mth.clamp((double) (value - minimum) / (double) (maximum - minimum), 0.0D, 1.0D);
        }
    }
}
