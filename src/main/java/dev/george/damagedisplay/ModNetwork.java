package dev.george.damagedisplay;

import dev.george.damagedisplay.network.TogglePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络：接收客户端传来的「开关」包。
 *
 * <p>用 {@code optional()} 注册 —— 这样即使客户端装了、服务端没装（或反过来），
 * 也不会因为通道不匹配而踢人/报错。这是纯客户端模组的正确做法。
 */
public final class ModNetwork {

    private ModNetwork() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToServer(
                TogglePayload.TYPE,
                TogglePayload.CODEC,
                ModNetwork::onToggle);
    }

    /**
     * 处理客户端发来的开关请求。
     *
     * <p>服务端只认「这个玩家自己的」开关，不去动别人的状态。
     */
    private static void onToggle(TogglePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ToggleState.setEnabled(player.getUUID(), payload.enabled());
        }
    }
}
