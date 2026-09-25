package dev.george.damagedisplay.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.george.damagedisplay.DamageDisplayConfig;
import dev.george.damagedisplay.DamageDisplayOfGeorge;
import dev.george.damagedisplay.ToggleState;
import dev.george.damagedisplay.network.TogglePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端：按键开关。
 *
 * <p>按键可以在「选项 → 控制 → 按键绑定」里的
 * <b>{@code Damage display of George}</b> 分类下改成任意键。
 *
 * <p>只做客户端该做的事：读按键 → 翻转本地状态 → 尽力通知服务端 → 提示玩家。
 * 真正的显示判定在 {@code DamageDisplayHandler} 里，服务端和客户端各判各的。
 */
@EventBusSubscriber(modid = DamageDisplayOfGeorge.MODID, value = Dist.CLIENT)
public final class KeyBindings {

    /** 按键分类，会显示在「按键绑定」界面的分组标题上。 */
    private static final String CATEGORY = "key.categories." + DamageDisplayOfGeorge.MODID;

    /** 切换伤害显示开关。默认绑定到 <b>右方括号 ]</b>（贴合本 mod 的 [] 风格，且不与原版冲突）。 */
    public static final KeyMapping TOGGLE_DISPLAY = new KeyMapping(
            "key." + DamageDisplayOfGeorge.MODID + ".toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            CATEGORY);

    private KeyBindings() {
    }

    /** 注册按键（模组事件总线）。 */
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_DISPLAY);
    }

    /**
     * 每客户端 tick 检查按键。
     *
     * <p>{@code consumeClick()} —— 用它而不是 {@code isDown()}，
     * 保证「按一下切一次」，按住不会疯狂翻转。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE_DISPLAY.consumeClick()) {
            handleToggle();
        }
    }

    private static void handleToggle() {
        Minecraft mc = Minecraft.getInstance();

        // 配置里被彻底关掉时，按键不生效（尊重配置文件的优先级）
        if (!DamageDisplayConfig.enabled()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("msg." + DamageDisplayOfGeorge.MODID + ".disabled_by_config"), true);
            }
            return;
        }

        // 翻转状态。键用玩家自己的 UUID（单人/联机都一致）；
        // 极少数拿不到玩家的情况退化成客户端哨兵键。
        java.util.UUID id = mc.player != null ? mc.player.getUUID() : ToggleState.CLIENT_KEY;
        boolean nowEnabled = ToggleState.toggle(id);

        // 尽力通知服务端。服务端没装本 mod 时包发不出去，
        // 那是预期行为：没装服务端就不会有伤害消息需要过滤。
        try {
            if (mc.getConnection() != null) {
                PacketDistributor.sendToServer(new TogglePayload(nowEnabled));
            }
        } catch (Throwable ignored) {
            // 服务端不认这个通道，忽略即可
        }

        if (mc.player != null) {
            // 用 actionBar（物品栏上方那行）提示，不刷聊天栏
            mc.player.displayClientMessage(
                    Component.translatable("msg." + DamageDisplayOfGeorge.MODID + (nowEnabled ? ".on" : ".off")),
                    true);
        }
    }
}
