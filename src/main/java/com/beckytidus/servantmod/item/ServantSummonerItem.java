package com.beckytidus.servantmod.item;

import com.beckytidus.servantmod.entity.ServantEntity;
import com.beckytidus.servantmod.registry.EntityRegistry;
import com.beckytidus.servantmod.registry.ServantNames;
import com.beckytidus.servantmod.registry.ServantSkins;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Hires a servant. Right-click to bind one to you. */
public class ServantSummonerItem extends Item {
    public ServantSummonerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient()) {
            ServantEntity servant = spawnServant(world, user);
            if (servant != null) {
                user.sendMessage(Text.literal("\u2692 " + servant.getServantName() + " has entered your service."), false);
                if (!user.isCreative()) user.getStackInHand(hand).decrement(1);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    @Nullable
    public static ServantEntity spawnServant(World world, PlayerEntity owner) {
        if (EntityRegistry.SERVANT == null) return null;
        ServantEntity servant = new ServantEntity(EntityRegistry.SERVANT, world);
        servant.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), 0.0f);
        servant.setOwner(owner);
        servant.setServantName(ServantNames.pick(world.getRandom()));
        servant.setTextureVariant(ServantSkins.pick(world.getRandom()));
        if (world instanceof ServerWorld) {
            world.spawnEntity(servant);
        }
        return servant;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> textConsumer, TooltipType type) {
        textConsumer.accept(Text.literal("Hires a servant bound to you."));
        textConsumer.accept(Text.literal("Give orders by right-click or by chat."));
    }
}
