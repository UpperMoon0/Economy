package com.nstut.economy.client;
import com.nstut.economy.platform.Services;

import com.nstut.economy.blocks.TankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.Fluid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nstut.economy.trading.EconomyFluidStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class TankRenderer implements BlockEntityRenderer<TankBlockEntity, TankRenderer.TankRenderState> {

    private static final float U_MIN = 5f / 16f;
    private static final float U_MAX = 11f / 16f;
    private static final float V_MIN = 1f / 16f;
    private static final float V_MAX = 15f / 16f;

    public static class TankRenderState extends BlockEntityRenderState {
        public EconomyFluidStack fluid = EconomyFluidStack.EMPTY;
        public int capacity = 1;
        public Direction facing = Direction.NORTH;
    }

    public TankRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public TankRenderState createRenderState() {
        return new TankRenderState();
    }

    @Override
    public void extractRenderState(@NotNull TankBlockEntity tank, @NotNull TankRenderState state, float partialTick,
                                   @NotNull Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(tank, state, crumblingOverlay);
        EconomyFluidStack stack = tank.getFluid();
        state.fluid = stack == null ? EconomyFluidStack.EMPTY : stack;
        state.capacity = Math.max(1, tank.getCapacity());
        state.facing = tank.getBlockState().getValue(DirectionalBlock.FACING);
    }

    @Override
    public void submit(@NotNull TankRenderState state, @NotNull PoseStack poseStack,
                       @NotNull SubmitNodeCollector collector,
                       @NotNull net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
        EconomyFluidStack stack = state.fluid;
        if (stack.isEmpty()) return;

        float fillRatio = (float) stack.getAmount() / Math.max(1, state.capacity);
        if (fillRatio <= 0) return;

        Direction facing = state.facing;
        TextureAtlasSprite sprite = getFluidSprite(stack.getFluid());

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
        int color = Services.FLUID.tint(stack.getFluid());

        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, vc) -> {
            Matrix4f matrix = pose.pose();
            switch (facing) {
                case NORTH -> {
                    float z = -0.003f;
                    drawQuad(vc, matrix, U_MIN, V_MIN, z, U_MAX, vFilled, z, u0, u1, v0, v1, light, color);
                }
                case SOUTH -> {
                    float z = 1.003f;
                    drawQuadFlipped(vc, matrix, U_MIN, V_MIN, z, U_MAX, vFilled, z, u0, u1, v0, v1, light, color);
                }
                case WEST -> {
                    float x = -0.003f;
                    float z0 = 1f - U_MAX, z1 = 1f - U_MIN;
                    drawQuadZ(vc, matrix, x, V_MIN, z0, x, vFilled, z1, u0, u1, v0, v1, light, color);
                }
                case EAST -> {
                    float x = 1.003f;
                    float z0 = U_MIN, z1 = U_MAX;
                    drawQuadZFlipped(vc, matrix, x, V_MIN, z0, x, vFilled, z1, u0, u1, v0, v1, light, color);
                }
                case UP -> {
                    float y = 1.003f;
                    drawQuadTop(vc, matrix, U_MIN, y, V_MIN, U_MAX, y, vFilled, u0, u1, v0, v1, light, color);
                }
                case DOWN -> {
                    float y = -0.003f;
                    drawQuadBottom(vc, matrix, U_MIN, y, V_MIN, U_MAX, y, vFilled, u0, u1, v0, v1, light, color);
                }
            }
        });
    }

    private static TextureAtlasSprite getFluidSprite(Fluid fluid) {
        Identifier stillTexture = Services.FLUID.stillTexture(fluid);
        return Minecraft.getInstance()
                .getAtlasManager()
                .get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, stillTexture));
    }

    private static void drawQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z, float x1, float y1, float z2, float u0, float u1, float v0, float v1, int light, int color) {
        int rgba = argb(color);
        vc.addVertex(m, x0, y1, z).setColor(rgba).setUv(u0, v1).setLight(light).setNormal(0f, 0f, -1f);
        vc.addVertex(m, x1, y1, z).setColor(rgba).setUv(u1, v1).setLight(light).setNormal(0f, 0f, -1f);
        vc.addVertex(m, x1, y0, z).setColor(rgba).setUv(u1, v0).setLight(light).setNormal(0f, 0f, -1f);
        vc.addVertex(m, x0, y0, z).setColor(rgba).setUv(u0, v0).setLight(light).setNormal(0f, 0f, -1f);
    }

    private static void drawQuadFlipped(VertexConsumer vc, Matrix4f m, float x0, float y0, float z, float x1, float y1, float z2, float u0, float u1, float v0, float v1, int light, int color) {
        int rgba = argb(color);
        vc.addVertex(m, x1, y1, z).setColor(rgba).setUv(u1, v1).setLight(light).setNormal(0f, 0f, 1f);
        vc.addVertex(m, x0, y1, z).setColor(rgba).setUv(u0, v1).setLight(light).setNormal(0f, 0f, 1f);
        vc.addVertex(m, x0, y0, z).setColor(rgba).setUv(u0, v0).setLight(light).setNormal(0f, 0f, 1f);
        vc.addVertex(m, x1, y0, z).setColor(rgba).setUv(u1, v0).setLight(light).setNormal(0f, 0f, 1f);
    }

    private static void drawQuadZ(VertexConsumer vc, Matrix4f m, float x, float y0, float z0, float x2, float y1, float z1, float u0, float u1, float v0, float v1, int light, int color) {
        int rgba = argb(color);
        vc.addVertex(m, x, y1, z1).setColor(rgba).setUv(u1, v1).setLight(light).setNormal(-1f, 0f, 0f);
        vc.addVertex(m, x, y1, z0).setColor(rgba).setUv(u0, v1).setLight(light).setNormal(-1f, 0f, 0f);
        vc.addVertex(m, x, y0, z0).setColor(rgba).setUv(u0, v0).setLight(light).setNormal(-1f, 0f, 0f);
        vc.addVertex(m, x, y0, z1).setColor(rgba).setUv(u1, v0).setLight(light).setNormal(-1f, 0f, 0f);
    }

    private static void drawQuadZFlipped(VertexConsumer vc, Matrix4f m, float x, float y0, float z0, float x2, float y1, float z1, float u0, float u1, float v0, float v1, int light, int color) {
        int rgba = argb(color);
        vc.addVertex(m, x, y1, z0).setColor(rgba).setUv(u0, v1).setLight(light).setNormal(1f, 0f, 0f);
        vc.addVertex(m, x, y1, z1).setColor(rgba).setUv(u1, v1).setLight(light).setNormal(1f, 0f, 0f);
        vc.addVertex(m, x, y0, z1).setColor(rgba).setUv(u1, v0).setLight(light).setNormal(1f, 0f, 0f);
        vc.addVertex(m, x, y0, z0).setColor(rgba).setUv(u0, v0).setLight(light).setNormal(1f, 0f, 0f);
    }

    private static void drawQuadTop(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float y2, float z1, float u0, float u1, float v0, float v1, int light, int color) {
        int rgba = argb(color);
        vc.addVertex(m, x1, y, z0).setColor(rgba).setUv(u1, v1).setLight(light).setNormal(0f, 1f, 0f);
        vc.addVertex(m, x0, y, z0).setColor(rgba).setUv(u0, v1).setLight(light).setNormal(0f, 1f, 0f);
        vc.addVertex(m, x0, y, z1).setColor(rgba).setUv(u0, v0).setLight(light).setNormal(0f, 1f, 0f);
        vc.addVertex(m, x1, y, z1).setColor(rgba).setUv(u1, v0).setLight(light).setNormal(0f, 1f, 0f);
    }

    private static void drawQuadBottom(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float y2, float z1, float u0, float u1, float v0, float v1, int light, int color) {
        int rgba = argb(color);
        vc.addVertex(m, x1, y, z1).setColor(rgba).setUv(u1, v1).setLight(light).setNormal(0f, -1f, 0f);
        vc.addVertex(m, x0, y, z1).setColor(rgba).setUv(u0, v1).setLight(light).setNormal(0f, -1f, 0f);
        vc.addVertex(m, x0, y, z0).setColor(rgba).setUv(u0, v0).setLight(light).setNormal(0f, -1f, 0f);
        vc.addVertex(m, x1, y, z0).setColor(rgba).setUv(u1, v0).setLight(light).setNormal(0f, -1f, 0f);
    }

    private static int argb(int tint) {
        int c = tint;
        if ((c >>> 24) == 0) {
            c |= 0xFF000000;
        }
        return c;
    }
}
