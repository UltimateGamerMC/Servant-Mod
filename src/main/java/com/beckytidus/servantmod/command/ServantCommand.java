package com.beckytidus.servantmod.command;

import com.beckytidus.servantmod.entity.Duty;
import com.beckytidus.servantmod.entity.ServantEntity;
import com.beckytidus.servantmod.item.ServantSummonerItem;
import com.beckytidus.servantmod.registry.ItemRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ServantCommand {
    private static final double FIND_RANGE = 128.0;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommand(dispatcher));
    }

    @Nullable
    private static ServantEntity closest(ServerCommandSource source) {
        PlayerEntity player = source.getPlayer();
        if (player == null) return null;
        ServerWorld world = source.getWorld();
        ServantEntity best = null;
        double bestSq = FIND_RANGE * FIND_RANGE;
        for (ServantEntity s : world.getEntitiesByClass(ServantEntity.class, player.getBoundingBox().expand(FIND_RANGE), s -> s.getOwner() == player)) {
            double d = s.squaredDistanceTo(player);
            if (d < bestSq) { bestSq = d; best = s; }
        }
        return best;
    }

    private static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("servant")
                .then(CommandManager.literal("summon")
                    .executes(ctx -> {
                        PlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        ServantEntity servant = ServantSummonerItem.spawnServant(ctx.getSource().getWorld(), player);
                        if (servant == null) {
                            ctx.getSource().sendError(Text.literal("Could not summon a servant."));
                            return 0;
                        }
                        ctx.getSource().sendMessage(Text.literal("\u2692 " + servant.getServantName() + " has entered your service."));
                        return 1;
                    })
                )
                .then(CommandManager.literal("duty")
                    .then(CommandManager.argument("duty", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Duty d : Duty.values()) builder.suggest(d.name().toLowerCase());
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ServantEntity servant = closest(ctx.getSource());
                            if (servant == null) {
                                ctx.getSource().sendError(Text.literal("No servant nearby."));
                                return 0;
                            }
                            String raw = StringArgumentType.getString(ctx, "duty");
                            Duty duty = Duty.tryParse(raw);
                            if (duty == null) {
                                ctx.getSource().sendError(Text.literal("Unknown duty: " + raw));
                                return 0;
                            }
                            servant.setDuty(duty);
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("list")
                    .executes(ctx -> {
                        PlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        ServerWorld world = ctx.getSource().getWorld();
                        List<ServantEntity> list = world.getEntitiesByClass(ServantEntity.class, player.getBoundingBox().expand(FIND_RANGE), s -> s.getOwner() == player);
                        ctx.getSource().sendMessage(Text.literal("Servants in your service: " + list.size()));
                        for (ServantEntity s : list) ctx.getSource().sendMessage(s.getStatusText());
                        return 1;
                    })
                )
                .then(CommandManager.literal("pack")
                    .executes(ctx -> {
                        ServantEntity servant = closest(ctx.getSource());
                        if (servant == null) {
                            ctx.getSource().sendError(Text.literal("No servant nearby."));
                            return 0;
                        }
                        Inventory pack = servant.getPack();
                        ctx.getSource().sendMessage(Text.literal("\u2692 " + servant.getServantName() + "'s pack:"));
                        for (int i = 0; i < pack.size(); i++) {
                            ItemStack stack = pack.getStack(i);
                            if (!stack.isEmpty()) ctx.getSource().sendMessage(Text.literal("  " + stack.getCount() + "x " + stack.getName().getString()));
                        }
                        return 1;
                    })
                )
                .then(CommandManager.literal("recall")
                    .executes(ctx -> {
                        PlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        ServerWorld world = ctx.getSource().getWorld();
                        List<ServantEntity> list = world.getEntitiesByClass(ServantEntity.class, player.getBoundingBox().expand(FIND_RANGE), s -> s.getOwner() == player);
                        for (ServantEntity s : list) {
                            s.setDuty(Duty.FOLLOW, false);
                            s.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0f);
                        }
                        ctx.getSource().sendMessage(Text.literal("\u2692 Recalled " + list.size() + " servant(s)."));
                        return 1;
                    })
                )
                .then(CommandManager.literal("dismiss")
                    .executes(ctx -> {
                        ServantEntity servant = closest(ctx.getSource());
                        if (servant == null) {
                            ctx.getSource().sendError(Text.literal("No servant nearby."));
                            return 0;
                        }
                        servant.speak("Very well. I'll take my leave.");
                        servant.discard();
                        ctx.getSource().sendMessage(Text.literal("\u2692 Servant dismissed."));
                        return 1;
                    })
                )
                .then(CommandManager.literal("give")
                    .executes(ctx -> give(ctx.getSource(), ItemRegistry.SERVANT_SUMMONER, "Servant Summoner"))
                    .then(CommandManager.literal("ledger").executes(ctx -> give(ctx.getSource(), ItemRegistry.SERVANT_LEDGER, "Servant Ledger")))
                )
        );
    }

    private static int give(ServerCommandSource source, net.minecraft.item.Item item, String label) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerEntity player = source.getPlayerOrThrow();
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
        source.sendMessage(Text.literal("Gave " + label + " to " + player.getName().getString()));
        return 1;
    }
}
