package com.beckytidus.servantmod.interaction;

import com.beckytidus.servantmod.entity.ServantEntity;
import com.beckytidus.servantmod.registry.ItemRegistry;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Right-clicking a servant. No menus, no hearts - you issue orders and hand over
 * tools and food, the way you would to staff.
 */
public class EntityInteractionHandler {
    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity instanceof ServantEntity servant) {
                return handle(player, world, hand, servant);
            }
            return ActionResult.PASS;
        });
    }

    private static ActionResult handle(PlayerEntity player, World world, Hand hand, ServantEntity servant) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        boolean isOwner = servant.getOwner() == player;
        ItemStack stack = player.getStackInHand(hand);

        if (stack.isEmpty()) {
            if (player instanceof ServerPlayerEntity spe) {
                spe.sendMessage(servant.getStatusText(), true);
            }
            if (player.isSneaking() && isOwner) {
                servant.setDuty(servant.getDuty().next());
            } else if (isOwner) {
                servant.speak(com.beckytidus.servantmod.entity.ServantSpeech.idle(world.getRandom()));
            }
            return ActionResult.SUCCESS;
        }

        if (stack.isOf(ItemRegistry.SERVANT_LEDGER) && isOwner) {
            servant.setDuty(servant.getDuty().next());
            return ActionResult.SUCCESS;
        }

        if (servant.isFoodItem(stack)) {
            servant.setEnergy(servant.getEnergy() + 35);
            servant.setHealth(Math.min(servant.getHealth() + 4.0f, servant.getMaxHealth()));
            player.sendMessage(Text.literal("\u2692 " + servant.getServantName() + ": Thank you, master."), false);
            if (!player.isCreative()) stack.decrement(1);
            return ActionResult.SUCCESS;
        }

        if (isTool(stack) && isOwner) {
            if (!world.isClient() && world instanceof ServerWorld sw) {
                ItemStack old = servant.getMainHandStack();
                servant.equipStack(EquipmentSlot.MAINHAND, stack.copy());
                if (!old.isEmpty()) servant.dropStack(sw, old);
            }
            player.sendMessage(Text.literal("\u2692 " + servant.getServantName() + ": I'll work with this."), false);
            if (!player.isCreative()) stack.decrement(1);
            return ActionResult.SUCCESS;
        }

        if (stack.isOf(Items.NAME_TAG) && isOwner) {
            String name = stack.getName().getString();
            if (name.isEmpty() || name.equals("Name Tag")) name = "Servant";
            servant.setServantName(name);
            player.sendMessage(Text.literal("\u2692 Servant renamed to " + name + "."), false);
            if (!player.isCreative()) stack.decrement(1);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    private static boolean isTool(ItemStack stack) {
        return stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SHOVELS)
                || stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.HOES);
    }
}
