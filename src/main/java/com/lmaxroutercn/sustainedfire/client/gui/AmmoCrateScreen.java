package com.lmaxroutercn.sustainedfire.client.gui;

import com.lmaxroutercn.sustainedfire.SustainedFire;
import com.lmaxroutercn.sustainedfire.menu.AmmoCrateMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 弹药箱 GUI：36 格布局（176x186），高度随容量扩展。
 * 贴图为占位（深色直角风格，跟随 TACZ 系 UI），整文件可替换——
 * 与方块贴图同一套"美术与逻辑解耦"管线。
 */
public class AmmoCrateScreen extends AbstractContainerScreen<AmmoCrateMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(SustainedFire.MODID, "textures/gui/ammo_crate.png");

    public AmmoCrateScreen(AmmoCrateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 186;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 的 AbstractContainerScreen.render 内部已调用 renderBackground，不重复调
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 176, 186);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 深色面板：标签改亮色（默认 0x404040 在深底上不可读）
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xE0E0E0, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xE0E0E0, false);
    }
}