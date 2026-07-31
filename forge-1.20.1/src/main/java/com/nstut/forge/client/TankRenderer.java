package com.nstut.forge.client;

import com.mojang.blaze3d.vertex.*;
import com.nstut.economy.blocks.TankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class TankRenderer implements BlockEntityRenderer<TankBlockEntity> {

    private static final float U_MIN = 5f / 16f;
    private static final float U_MAX = 11f / 16f;
    private static final float V_MIN = 1f / 16f;
    private static final float V_MAX = 15f / 16f;
    public TankRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(@NotNull TankBlockEntity tank, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        FluidStack fluidStack = tank.getFluid();
        if (fluidStack.isEmpty()) return;

        int amount = fluidStack.getAmount();
        int capacity = tank.getCapacity();
        float fillRatio = (float) amount / capacity;
        if (fillRatio <= 0) return;

        Direction facing = tank.getBlockState().getValue(DirectionalBlock.FACING);

        TextureAtlasSprite sprite = getFluidSprite(fluidStack.getFluid());

        poseStack.pushPose();

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer builder = buffer.getBuffer(RenderType.cutout());

        float vRange = V_MAX - V_MIN;
        float vFilled = V_MIN + vRange * fillRatio;

        // One complete fluid sprite represents one block. The tank window is
        // smaller than a block, so sample only the matching fraction instead
        // of stretching the full sprite over the window.
        float renderedWidth = U_MAX - U_MIN;
        float renderedHeight = vFilled - V_MIN;
        float u0 = sprite.getU0();
        float u1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * renderedWidth;
        float v0 = sprite.getV0();
        float v1 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * renderedHeight;

        int light = 0xF000F0;
        int color = IClientFluidTypeExtensions.of(fluidStack.getFluid()).getTintColor();

        switch (facing) {
            case NORTH -> {
                float z = -0.003f;
                float x0 = U_MIN, x1 = U_MAX;
                float y0 = V_MIN, y1 = vFilled;
                drawQuad(builder, matrix, x0, y0, z, x1, y1, z, u0, u1, v0, v1, light, color);
            }
            case SOUTH -> {
                float z = 1.003f;
                float x0 = U_MIN, x1 = U_MAX;
                float y0 = V_MIN, y1 = vFilled;
                drawQuadFlipped(builder, matrix, x0, y0, z, x1, y1, z, u0, u1, v0, v1, light, color);
            }
            case WEST -> {
                float x = -0.003f;
                float z0 = 1f - U_MAX, z1 = 1f - U_MIN;
                float y0 = V_MIN, y1 = vFilled;
                drawQuadZ(builder, matrix, x, y0, z0, x, y1, z1, u0, u1, v0, v1, light, color);
            }
            case EAST -> {
                float x = 1.003f;
                float z0 = U_MIN, z1 = U_MAX;
                float y0 = V_MIN, y1 = vFilled;
                drawQuadZFlipped(builder, matrix, x, y0, z0, x, y1, z1, u0, u1, v0, v1, light, color);
            }
            case UP -> {
                float y = 1.003f;
                float x0 = U_MIN, x1 = U_MAX;
                float z0 = V_MIN, z1 = vFilled;
                drawQuadTop(builder, matrix, x0, y, z0, x1, y, z1, u0, u1, v0, v1, light, color);
            }
            case DOWN -> {
                float y = -0.003f;
                float x0 = U_MIN, x1 = U_MAX;
                float z0 = V_MIN, z1 = vFilled;
                drawQuadBottom(builder, matrix, x0, y, z0, x1, y, z1, u0, u1, v0, v1, light, color);
            }
        }

        poseStack.popPose();
    }

    private static TextureAtlasSprite getFluidSprite(Fluid fluid) {
        ResourceLocation stillTexture = IClientFluidTypeExtensions.of(fluid).getStillTexture();
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
    }

    private static void drawQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z, float x1, float y1, float z2, float u0, float u1, float v0, float v1, int light, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1f;
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv(u0, v1).uv2(light).normal(0, 0, -1).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv(u1, v1).uv2(light).normal(0, 0, -1).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv(u1, v0).uv2(light).normal(0, 0, -1).endVertex();
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv(u0, v0).uv2(light).normal(0, 0, -1).endVertex();
    }

    private static void drawQuadFlipped(VertexConsumer vc, Matrix4f m, float x0, float y0, float z, float x1, float y1, float z2, float u0, float u1, float v0, float v1, int light, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1f;
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv(u1, v1).uv2(light).normal(0, 0, 1).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv(u0, v1).uv2(light).normal(0, 0, 1).endVertex();
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv(u0, v0).uv2(light).normal(0, 0, 1).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv(u1, v0).uv2(light).normal(0, 0, 1).endVertex();
    }

    private static void drawQuadZ(VertexConsumer vc, Matrix4f m, float x, float y0, float z0, float x2, float y1, float z1, float u0, float u1, float v0, float v1, int light, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1f;
        vc.vertex(m, x, y1, z1).color(r, g, b, a).uv(u1, v1).uv2(light).normal(-1, 0, 0).endVertex();
        vc.vertex(m, x, y1, z0).color(r, g, b, a).uv(u0, v1).uv2(light).normal(-1, 0, 0).endVertex();
        vc.vertex(m, x, y0, z0).color(r, g, b, a).uv(u0, v0).uv2(light).normal(-1, 0, 0).endVertex();
        vc.vertex(m, x, y0, z1).color(r, g, b, a).uv(u1, v0).uv2(light).normal(-1, 0, 0).endVertex();
    }

    private static void drawQuadZFlipped(VertexConsumer vc, Matrix4f m, float x, float y0, float z0, float x2, float y1, float z1, float u0, float u1, float v0, float v1, int light, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1f;
        vc.vertex(m, x, y1, z0).color(r, g, b, a).uv(u0, v1).uv2(light).normal(1, 0, 0).endVertex();
        vc.vertex(m, x, y1, z1).color(r, g, b, a).uv(u1, v1).uv2(light).normal(1, 0, 0).endVertex();
        vc.vertex(m, x, y0, z1).color(r, g, b, a).uv(u1, v0).uv2(light).normal(1, 0, 0).endVertex();
        vc.vertex(m, x, y0, z0).color(r, g, b, a).uv(u0, v0).uv2(light).normal(1, 0, 0).endVertex();
    }

    private static void drawQuadTop(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float y2, float z1, float u0, float u1, float v0, float v1, int light, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1f;
        vc.vertex(m, x1, y, z0).color(r, g, b, a).uv(u1, v1).uv2(light).normal(0, 1, 0).endVertex();
        vc.vertex(m, x0, y, z0).color(r, g, b, a).uv(u0, v1).uv2(light).normal(0, 1, 0).endVertex();
        vc.vertex(m, x0, y, z1).color(r, g, b, a).uv(u0, v0).uv2(light).normal(0, 1, 0).endVertex();
        vc.vertex(m, x1, y, z1).color(r, g, b, a).uv(u1, v0).uv2(light).normal(0, 1, 0).endVertex();
    }

    private static void drawQuadBottom(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float y2, float z1, float u0, float u1, float v0, float v1, int light, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1f;
        vc.vertex(m, x1, y, z1).color(r, g, b, a).uv(u1, v1).uv2(light).normal(0, -1, 0).endVertex();
        vc.vertex(m, x0, y, z1).color(r, g, b, a).uv(u0, v1).uv2(light).normal(0, -1, 0).endVertex();
        vc.vertex(m, x0, y, z0).color(r, g, b, a).uv(u0, v0).uv2(light).normal(0, -1, 0).endVertex();
        vc.vertex(m, x1, y, z0).color(r, g, b, a).uv(u1, v0).uv2(light).normal(0, -1, 0).endVertex();
    }
}
