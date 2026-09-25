package dev.george.damagedisplay.client;

import dev.george.damagedisplay.DamageDisplayOfGeorge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 客户端自检：把收录的伤害类型逐个翻译出来打到日志里。
 *
 * <p>只有加了 {@code -Ddamagedisplay.selftest=true} 时才激活。
 * 跑法：{@code .\gradlew runClientCheck}
 *
 * <p>为什么需要这个：专用服务器没有语言资源，{@code runSelfTest} 里测不了翻译。
 * 而「悬浮框显示占位符」恰恰就是翻译键写错导致的 —— 必须在真客户端验证。
 *
 * <p><b>触发时机很关键</b>：必须等到语言资源加载完成之后才能查翻译。
 * {@code RegisterClientReloadListenersEvent} 之类的「重载注册」事件是在解析**之前**
 * 触发的，那时候查什么都是缺失（会得到假警报）。所以这里挂在
 * 「界面打开」和「进入世界」这两个必然在语言加载之后的事件上。
 */
@EventBusSubscriber(modid = DamageDisplayOfGeorge.MODID, value = Dist.CLIENT)
public final class ClientSelfTest {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean done = false;

    private ClientSelfTest() {
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        maybeDump("进入世界");
    }

    /** 打开任意界面时触发（标题界面的按钮也算），此时语言必定已加载。 */
    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Init.Post event) {
        maybeDump("界面打开");
    }

    private static void maybeDump(String trigger) {
        if (done || System.getProperty("damagedisplay.selftest") == null) {
            return;
        }
        // 语言还没加载完就等下一次触发（不设 done，下次还会试）
        if (!ClientEnvironment.hasLanguage()) {
            return;
        }
        done = true;
        LOGGER.info("SELFTEST-CLIENT: 触发时机 = {}", trigger);
        dumpTranslations();
    }

    /** 把每种伤害类型的中文名打出来，同时统计缺失。 */
    private static void dumpTranslations() {
        LOGGER.info("SELFTEST-CLIENT: ===== 伤害类型汉化核对开始 =====");

        String[] keys = {
                "player_attack", "mob_attack", "arrow", "trident", "fireball",
                "in_fire", "lava", "fall", "cactus", "drown", "freeze",
                "explosion", "lightning_bolt", "magic", "indirect_magic",
                "wither", "dragon_breath", "generic", "out_of_world",
                "l2complements_bleed", "l2complements_emerald", "l2complements_freeze",
                "l2complements_life_sync", "l2complements_soul_flame", "l2complements_void_eye",
                "l2hostility_killer_aura", "l2hostility_reflect",
                "l2damagetracker_mob_attack-bypass_armor",
                "l2damagetracker_mob_attack-bypass_magic",
                "l2damagetracker_mob_attack-bypass_cooldown",
                "l2damagetracker_mob_attack-bypass_armor-bypass_magic",
                "l2damagetracker_mob_attack-bypass_armor-bypass_cooldown",
                "l2damagetracker_mob_attack-bypass_cooldown-bypass_magic",
                "l2damagetracker_mob_attack-bypass_armor-bypass_cooldown-bypass_magic",
                "l2damagetracker_player_attack-bypass_armor",
                "l2damagetracker_player_attack-bypass_magic",
                "l2damagetracker_player_attack-bypass_cooldown",
                "l2damagetracker_player_attack-bypass_armor-bypass_magic",
                "l2damagetracker_player_attack-bypass_armor-bypass_cooldown",
                "l2damagetracker_player_attack-bypass_cooldown-bypass_magic",
                "l2damagetracker_player_attack-bypass_armor-bypass_cooldown-bypass_magic",
                // 悬浮框用的其它键
                "msg.trace.title", "trace.category", "trace.trueDamage",
                "trace.bypasses", "trace.attacker", "trace.initial",
                "trace.amplify", "trace.weaken", "trace.afterReductions",
                "trace.absorption", "trace.final",
                "cat.physical", "cat.magic", "cat.fire", "cat.projectile",
                "bypass.armor", "bypass.resistance", "bypass.magic"
        };

        int missing = 0;
        for (String k : keys) {
            String full = "damage_display_of_george." + k;
            // 伤害类型是 damage_type.* 前缀，其它是模组根前缀
            String t1 = ClientEnvironment.translationOrNull("damage_type." + full);
            String t2 = ClientEnvironment.translationOrNull(full);
            String val = t1 != null ? t1 : t2;
            if (val == null) {
                LOGGER.error("SELFTEST-CLIENT: 缺失 -> {}", k);
                missing++;
            } else {
                LOGGER.info("SELFTEST-CLIENT: {} = {}", k, val);
            }
        }

        LOGGER.info("SELFTEST-CLIENT: 共 {} 项，缺失 {} 项 -> {}",
                keys.length, missing, missing == 0 ? "全部汉化 OK" : "有缺失！");
    }
}
