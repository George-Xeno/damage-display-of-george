package dev.george.damagedisplay;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Damage display of George。
 *
 * <p>作用：在聊天栏输出玩家当前受到的伤害类型、伤害量，以及最终造成的实际伤害。
 *
 * <p>实现要点：
 * <ul>
 *   <li>监听 {@code LivingIncomingDamageEvent} / {@code LivingShieldBlockEvent} /
 *       {@code LivingDamageEvent.Post} 三个 NeoForge 事件，覆盖「减免前 → 格挡 → 结算后」全过程。</li>
 *   <li>原始伤害取 {@code LivingDamageEvent.Post#getOriginalDamage()}，
 *       最终实际伤害取 {@code getNewDamage()}，各项减免取 {@code getReduction(...)}。</li>
 *   <li>伤害类型名复用原版的死亡消息语言键（{@code death.attack.<msgId>}），
 *       自动跟随玩家语言，也兼容其它模组自定义的伤害类型。</li>
 *   <li>纯服务端事件驱动 + 配置用 COMMON，单人 / 专用服务器 / 纯客户端都能用。</li>
 * </ul>
 */
@Mod(DamageDisplayOfGeorge.MODID)
public class DamageDisplayOfGeorge {

    public static final String MODID = "damage_display_of_george";

    private static final Logger LOGGER = LogUtils.getLogger();

    public DamageDisplayOfGeorge(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, DamageDisplayConfig.SPEC);
        NeoForge.EVENT_BUS.register(DamageDisplayHandler.class);
        modBus.addListener(ModNetwork::onRegisterPayloads);
        if (ModSelfTest.isEnabled()) {
            NeoForge.EVENT_BUS.register(ModSelfTest.class);
        }
        LOGGER.debug("Damage display of George loaded");
    }
}
