package com.beckytidus.servantmod.item;

import com.beckytidus.servantmod.entity.Duty;
import com.beckytidus.servantmod.entity.ServantEntity;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Consumer;

/**
 * The order book. Right-click a servant to hand it the next duty;
 * right-click the air to recall every servant you own.
 */
public class ServantLedgerItem extends Item {
    private static final double RECALL_RANGE = 128.0;

    public ServantLedgerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return ActionResult.SUCCESS;
        List<ServantEntity> servants = world.getEntitiesByClass(ServantEntity.class,
                user.getBoundingBox().expand(RECALL_RANGE), s -> s.getOwner() == user);
        if (servants.isEmpty()) {
            user.sendMessage(Text.literal("\u2692 No servants in your service nearby."), false);
            return ActionResult.SUCCESS;
        }
        for (ServantEntity servant : servants) {
            servant.setDuty(Duty.FOLLOW, false);
            servant.refreshPositionAndAngles(user.getX(), user.getY(), user.getZ(), user.getYaw(), 0.0f);
        }
        user.sendMessage(Text.literal("\u2692 Recalled " + servants.size() + " servant(s)."), false);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> textConsumer, TooltipType type) {
        textConsumer.accept(Text.literal("Right-click a servant: next duty"));
        textConsumer.accept(Text.literal("Right-click the air: recall all servants"));
    }
}
