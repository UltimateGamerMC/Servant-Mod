package com.beckytidus.servantmod.client.render;

import com.beckytidus.servantmod.entity.ServantEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class ServantEntityRenderer extends BipedEntityRenderer<ServantEntity, ServantEntityRenderState, ServantEntityModel> {
    private static final Identifier TEXTURE_DEFAULT = Identifier.of("servant-mod", "entity/servant_1");
    private static final int TEXTURE_COUNT = 6;

    public ServantEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new ServantEntityModel(context.getPart(EntityModelLayers.PLAYER), true), 0.5f);
    }

    @Override
    public ServantEntityRenderState createRenderState() {
        return new ServantEntityRenderState();
    }

    @Override
    protected boolean hasLabel(ServantEntity entity, double squaredDistanceToCamera) {
        if (squaredDistanceToCamera >= 4096.0) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;
        return !entity.isInvisibleTo(player);
    }

    @Override
    public void updateRenderState(ServantEntity entity, ServantEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        if (state.nameLabelPos == null) {
            state.nameLabelPos = new Vec3d(0, entity.getHeight(), 0);
        }
        state.statusLabel = net.minecraft.text.Text.literal(entity.getShortStatus());
        Identifier texture = textureFor(entity.getTextureVariant());
        state.skinTextures = new SkinTextures(
                new AssetInfo.TextureAssetInfo(texture),
                null,
                null,
                PlayerSkinType.SLIM,
                false
        );
    }

    private static Identifier textureFor(String variant) {
        if (variant != null && !variant.isEmpty()) {
            try {
                int n = Integer.parseInt(variant);
                if (n >= 1 && n <= TEXTURE_COUNT) {
                    return Identifier.of("servant-mod", "entity/servant_" + n);
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return TEXTURE_DEFAULT;
    }

    @Override
    protected void renderLabelIfPresent(ServantEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        if (state.displayName != null) {
            queue.submitLabel(matrices, state.nameLabelPos, 0, state.displayName, !state.sneaking, state.light, state.squaredDistanceToCamera, cameraState);
        }
        if (state.statusLabel != null) {
            queue.submitLabel(matrices, state.nameLabelPos, 9, state.statusLabel, !state.sneaking, state.light, state.squaredDistanceToCamera, cameraState);
        }
    }

    @Override
    public Identifier getTexture(ServantEntityRenderState state) {
        return state.skinTextures != null ? state.skinTextures.body().texturePath() : TEXTURE_DEFAULT;
    }
}
