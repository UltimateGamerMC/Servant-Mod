package com.beckytidus.servantmod.mixin;

import com.beckytidus.servantmod.entity.Duty;
import com.beckytidus.servantmod.entity.ServantEntity;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Locale;

/**
 * Voice orders. Say "servant mine", or address a servant by name -
 * "Alfred, chop" - and the nearest servant you own takes the order.
 */
@Mixin(PlayerManager.class)
public class PlayerManagerServantChatMixin {
    private static final double ORDER_RANGE = 24.0;

    @Inject(method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V", at = @At("TAIL"))
    private void servantMod$voiceOrders(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params, CallbackInfo ci) {
        if (sender == null) return;
        String content = message.getSignedContent().toLowerCase(Locale.ROOT).trim();
        if (content.isEmpty()) return;

        List<ServantEntity> nearby = sender.getEntityWorld().getEntitiesByClass(
                ServantEntity.class,
                sender.getBoundingBox().expand(ORDER_RANGE),
                s -> s.getOwner() == sender);
        if (nearby.isEmpty()) return;

        String[] words = content.split("\\s+");
        ServantEntity target = null;
        String orderWord = null;

        if (isCallWord(words[0])) {
            orderWord = words.length > 1 ? words[1] : null;
        } else {
            for (ServantEntity servant : nearby) {
                String name = servant.getServantName().toLowerCase(Locale.ROOT);
                if (content.startsWith(name + " ") || content.startsWith(name + ",")) {
                    target = servant;
                    orderWord = words.length > 1 ? strip(words[1]) : null;
                    break;
                }
            }
        }

        if (orderWord == null) return;
        Duty duty = Duty.tryParse(orderWord);
        if (duty == null) return;
        if (target == null) target = nearest(nearby, sender);
        if (target == null) return;
        target.setDuty(duty);
    }

    private static boolean isCallWord(String word) {
        String w = strip(word);
        return w.equals("servant") || w.equals("servants") || w.equals("butler")
                || w.equals("maid") || w.equals("staff") || w.equals("help");
    }

    private static String strip(String word) {
        return word.replace(",", "").replace("!", "").replace(".", "").trim();
    }

    private static ServantEntity nearest(List<ServantEntity> servants, ServerPlayerEntity player) {
        ServantEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServantEntity s : servants) {
            double d = s.squaredDistanceTo(player);
            if (d < bestDist) { bestDist = d; best = s; }
        }
        return best;
    }
}
