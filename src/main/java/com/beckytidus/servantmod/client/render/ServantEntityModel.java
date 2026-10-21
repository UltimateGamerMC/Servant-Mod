package com.beckytidus.servantmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

@Environment(EnvType.CLIENT)
public class ServantEntityModel extends BipedEntityModel<ServantEntityRenderState> {
    private final PlayerEntityModel inner;

    public ServantEntityModel(ModelPart root, boolean thinArms) {
        super(root);
        this.inner = new PlayerEntityModel(root, thinArms);
    }

    @Override
    public void setAngles(ServantEntityRenderState state) {
        inner.setAngles(state);
    }
}
