package com.yuan.live2d.client.live2d;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

final class Live2DTextureRenderer {
    private Live2DTextureRenderer() {}

    /**
     * Draw the FBO into the rectangle described by {@code bounds}. The quad is sized to
     * the model's visible content so pixels outside the bounds are never touched. The
     * native side guarantees the FBO is transparent outside {@code Bounds()}, so
     * premultiplied-alpha blending leaves the destination untouched there.
     */
    static void draw(GuiGraphics graphics, Live2DClientState.DrawResult frame,
                     Live2DHudRenderer.DrawBounds bounds) {
        if (!frame.hasTexture() || bounds == null) return;
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, frame.textureId());
            RenderSystem.setShaderColor(1, 1, 1, 1);

            Matrix4f matrix = graphics.pose().last().pose();
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(matrix, bounds.left(),  bounds.bottom(), 0).uv(bounds.u0(), bounds.v0()).endVertex();
            buffer.vertex(matrix, bounds.right(), bounds.bottom(), 0).uv(bounds.u1(), bounds.v0()).endVertex();
            buffer.vertex(matrix, bounds.right(), bounds.top(),    0).uv(bounds.u1(), bounds.v1()).endVertex();
            buffer.vertex(matrix, bounds.left(),  bounds.top(),    0).uv(bounds.u0(), bounds.v1()).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest();
        }
    }
}
