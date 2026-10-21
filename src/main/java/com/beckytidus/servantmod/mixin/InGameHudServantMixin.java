package com.beckytidus.servantmod.mixin;

import com.beckytidus.servantmod.entity.ServantEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shows a servant's duty card when you look at them. */
@Mixin(InGameHud.class)
public abstract class InGameHudServantMixin {
    private static final int SERVANT_LOOK_RANGE = 10;

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "render", at = @At("TAIL"))
    private void servantMod$renderServantStatus(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Entity lookedAt = client.targetedEntity;
        if (!(lookedAt instanceof ServantEntity) && client.getCameraEntity() != null) {
            lookedAt = DebugRenderer.getTargetedEntity(client.getCameraEntity(), SERVANT_LOOK_RANGE).orElse(null);
        }
        if (lookedAt instanceof ServantEntity servant && servant.isAlive()) {
            int w = context.getScaledWindowWidth();
            int h = context.getScaledWindowHeight();
            int y = h / 2 - 60;
            String name = servant.getCustomName() != null ? servant.getCustomName().getString() : "Servant";
            String status = "\u2692 " + name + "  \u2022 " + servant.getDuty().label() + "  \u2022 Energy " + servant.getEnergy() + "/100";
            context.drawTextWithShadow(client.textRenderer, status, (w - client.textRenderer.getWidth(status)) / 2, y, 0xFFD766);
        }
    }
}
