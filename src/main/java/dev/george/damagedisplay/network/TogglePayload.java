package dev.george.damagedisplay.network;

import dev.george.damagedisplay.DamageDisplayOfGeorge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：切换伤害显示的开关状态。
 *
 * <p>只在「服务端也装了这个 mod」时才有用。纯客户端场景（服务器没装）下，
 * 客户端自己就能靠本地状态把消息过滤掉 —— 见 {@code ClientToggleState}。
 *
 * @param enabled 玩家希望的目标状态
 */
public record TogglePayload(boolean enabled) implements CustomPacketPayload {

    /** 包 id。 */
    public static final CustomPacketPayload.Type<TogglePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(DamageDisplayOfGeorge.MODID, "toggle"));

    /** 编解码：就一个 boolean。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, TogglePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, TogglePayload::enabled,
                    TogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
