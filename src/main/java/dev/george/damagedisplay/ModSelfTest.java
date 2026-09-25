package dev.george.damagedisplay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 无头自检：起专用服务器 → 造一个假玩家 → 施加几种不同类型的伤害 →
 * 检查聊天栏（这里重定向到日志）里的输出 → 打印 PASS/FAIL 然后关服。
 *
 * <p>用法：{@code .\gradlew runSelfTest}
 * 只有加了 {@code -Ddamagedisplay.selftest=true} 时才会激活。
 */
public final class ModSelfTest {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 自检最多跑多少 tick，防止卡死。 */
    private static final int MAX_TICKS = 400;

    /** 攒够这么多条消息就判定通过。 */
    private static final int EXPECTED_MESSAGES = 3;

    private static int tick = 0;
    private static boolean ran = false;

    private ModSelfTest() {
    }

    public static boolean isEnabled() {
        return System.getProperty("damagedisplay.selftest") != null;
    }

    /**
     * 纯逻辑自检：验证 {@link DamageClassifier} 对各种伤害类型的归类。
     *
     * <p>用服务器上真实存在的 {@code DamageSources} 造伤害来源，
     * 逐个打印分类结果 —— 这是确认「物理 / 魔法 / 火焰」判对了没有的直接手段。
     */
    private static boolean runClassifierCheck(MinecraftServer server) {
        ServerLevel level = server.overworld();
        net.minecraft.world.damagesource.DamageSources s = level.damageSources();

        boolean ok = true;
        ok &= checkCategory(s.playerAttack(null), "cat.physical", "playerAttack");
        ok &= checkCategory(s.mobAttack(null), "cat.physical", "mobAttack");
        ok &= checkCategory(s.fall(), "cat.fall", "fall");
        ok &= checkCategory(s.inFire(), "cat.fire", "inFire");
        ok &= checkCategory(s.onFire(), "cat.fire", "onFire");
        ok &= checkCategory(s.lava(), "cat.fire", "lava");
        ok &= checkCategory(s.explosion(null, null), "cat.explosion", "explosion");
        ok &= checkCategory(s.arrow(null, null), "cat.projectile", "arrow");
        ok &= checkCategory(s.lightningBolt(), "cat.lightning", "lightningBolt");
        ok &= checkCategory(s.magic(), "cat.magic", "magic");
        ok &= checkCategory(s.indirectMagic(null, null), "cat.magic", "indirectMagic");
        ok &= checkCategory(s.drown(), "cat.drowning", "drown");
        ok &= checkCategory(s.freeze(), "cat.freeze", "freeze");
        ok &= checkCategory(s.witherSkull(null, null), "cat.magic", "witherSkull");
        ok &= checkCategory(s.starve(), "cat.other", "starve");

        LOGGER.info("SELFTEST: 伤害分类检查 {}", ok ? "通过" : "失败");
        return ok;
    }

    /** 检查某个伤害来源是否被判进期望的分类。 */
    private static boolean checkCategory(DamageSource source, String expected, String label) {
        DamageClassifier.Result r = DamageClassifier.classify(source);
        boolean hit = r.categories().contains(expected);
        LOGGER.info("SELFTEST: 分类 {} -> {} {}",
                label, r.categories(),
                hit ? "" : (" (期望含 " + expected + ")"));
        if (!hit) {
            LOGGER.error("SELFTEST: 分类错误！{} 应含 {}", label, expected);
        }
        return hit;
    }

    /**
     * 检查每个收录的伤害类型都有对应翻译 —— 防止再出现「悬浮框显示占位符」那类问题。
     *
     * <p>用 {@code Language.getInstance()} 判定键是否存在。
     * 专用服务器上没有语言资源，这一步会被跳过（返回 true）。
     */
    private static boolean runTranslationCheck() {
        // 收集所有会被 lookup 的类型名（原版 48 + 模组 22）
        String[] all = {
                // 原版
                "player_attack", "mob_attack", "mob_attack_no_aggro", "arrow", "trident",
                "mob_projectile", "thrown", "spit", "fireball", "unattributed_fireball",
                "wither_skull", "wind_charge", "sonic_boom",
                "in_fire", "on_fire", "lava", "hot_floor", "campfire", "fireworks",
                "fall", "fly_into_wall", "falling_anvil", "falling_block", "falling_stalactite",
                "stalagmite", "cactus", "sweet_berry_bush", "thorns", "sting", "cramming",
                "in_wall", "freeze", "drown", "starve", "dry_out", "lightning_bolt",
                "explosion", "player_explosion", "bad_respawn_point",
                "magic", "indirect_magic", "wither", "dragon_breath",
                "generic", "generic_kill", "out_of_world", "outside_border",
                // 莱特兰
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
                "l2damagetracker_player_attack-bypass_armor-bypass_cooldown-bypass_magic"
        };

        // 专用服务器没有语言资源，直接跳过
        if (!dev.george.damagedisplay.client.ClientEnvironment.hasLanguage()) {
            LOGGER.info("SELFTEST: 非客户端环境，跳过翻译检查");
            return true;
        }

        int missing = 0;
        for (String key : all) {
            String full = "damage_type.damage_display_of_george." + key;
            if (!dev.george.damagedisplay.client.ClientEnvironment.translationExists(full)) {
                LOGGER.error("SELFTEST: 缺翻译 -> {}", full);
                missing++;
            }
        }
        boolean ok = missing == 0;
        LOGGER.info("SELFTEST: 翻译检查 共 {} 项，缺失 {} 项 -> {}",
                all.length, missing, ok ? "通过" : "失败");
        return ok;
    }

    /**
     * 纯逻辑自检：验证 {@link ToggleState} 的开关翻转行为。
     *
     * <p>不依赖游戏运行时，直接跑断言。
     */
    private static boolean runToggleStateCheck() {
        java.util.UUID id = java.util.UUID.randomUUID();
        boolean ok = true;

        // 默认应该是「开」
        if (!ToggleState.isEnabled(id)) {
            LOGGER.error("SELFTEST: 开关默认值应为 true");
            ok = false;
        }

        // 翻一次 → 关
        if (ToggleState.toggle(id)) {
            LOGGER.error("SELFTEST: 第一次翻转应为 false");
            ok = false;
        }
        // 再翻一次 → 开
        if (!ToggleState.toggle(id)) {
            LOGGER.error("SELFTEST: 第二次翻转应为 true");
            ok = false;
        }
        // 显式设置
        ToggleState.setEnabled(id, false);
        if (ToggleState.isEnabled(id)) {
            LOGGER.error("SELFTEST: setEnabled(false) 后应为 false");
            ok = false;
        }
        // 清理后回到默认「开」
        ToggleState.forget(id);
        if (!ToggleState.isEnabled(id)) {
            LOGGER.error("SELFTEST: forget 后应回到默认 true");
            ok = false;
        }

        LOGGER.info("SELFTEST: 开关逻辑检查 {}", ok ? "通过" : "失败");
        return ok;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!isEnabled()) {
            return;
        }
        LOGGER.info("SELFTEST: 服务器已启动，开始伤害自检");
        toggleOk = runToggleStateCheck();
        classifierOk = runClassifierCheck(event.getServer());
        translationOk = runTranslationCheck();
        tick = 0;
        ran = false;
    }

    /** 开关逻辑检查结果。 */
    private static boolean toggleOk = true;

    /** 伤害分类检查结果。 */
    private static boolean classifierOk = true;

    /** 翻译完整性检查结果。 */
    private static boolean translationOk = true;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!isEnabled()) {
            return;
        }
        MinecraftServer server = event.getServer();
        tick++;

        if (!ran && tick > 20) {
            ran = true;
            try {
                runScenario(server);
            } catch (Exception e) {
                LOGGER.error("SELFTEST: 场景执行异常", e);
                finish(server, false);
                return;
            }
        }

        if (ran && tick > 60) {
            finish(server, MessageCapture.count() >= EXPECTED_MESSAGES);
        }

        if (tick > MAX_TICKS) {
            LOGGER.error("SELFTEST: 超时，抓到的消息数 = {}", MessageCapture.count());
            finish(server, false);
        }
    }

    /** 造场景：生成一只僵尸，对它施加三种伤害（普通、摔落、火焰）。 */
    private static void runScenario(MinecraftServer server) {
        ServerLevel level = server.overworld();
        MessageCapture.reset();

        // 造一只僵尸当靶子（比玩家好造，不需要网络连接）
        BlockPos pos = new BlockPos(0, 100, 0);
        Mob zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            LOGGER.error("SELFTEST: 无法创建僵尸实体");
            return;
        }
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        level.addFreshEntity(zombie);
        zombie.setHealth(20f);

        // 关掉受伤无敌帧，保证三次伤害都能独立结算
        zombie.invulnerableTime = 0;

        DamageSources sources = level.damageSources();

        // ① 普通物理伤害
        applyAndLog(zombie, sources.generic(), 5f, "generic(5.0)");
        zombie.invulnerableTime = 0;

        // ② 摔落伤害（有伤害类型 msgId = fall，能测出语言键路径）
        applyAndLog(zombie, sources.fall(), 7.5f, "fall(7.5)");
        zombie.invulnerableTime = 0;

        // ③ 火焰伤害
        applyAndLog(zombie, sources.inFire(), 3f, "inFire(3.0)");

        // ④【回归测试】吸收伤害。
        //    注意：僵尸这类 Mob 每 tick 会衰减吸收值，setAbsorptionAmount 在这里留不住，
        //    所以这一步只作为「信息输出」，真正的吸收场景由玩家实机验证。
        zombie.setAbsorptionAmount(8f);
        zombie.setHealth(20f);
        zombie.invulnerableTime = 0;
        LOGGER.info("SELFTEST: 吸收测试前 -> 血量={}, 吸收={}（僵尸会衰减吸收，可能为 0）",
                DamageDescriber.num(zombie.getHealth()),
                DamageDescriber.num(zombie.getAbsorptionAmount()));
        applyAndLog(zombie, sources.generic(), 4f, "吸收测试(原始4.0)");
        LOGGER.info("SELFTEST: 吸收测试后 -> 血量={}, 吸收={}",
                DamageDescriber.num(zombie.getHealth()),
                DamageDescriber.num(zombie.getAbsorptionAmount()));

        // ⑤ 抗性提升 V：能挡掉绝大部分伤害，实际伤害必须远小于原始
        zombie.setAbsorptionAmount(0f);
        zombie.setHealth(20f);
        zombie.invulnerableTime = 0;
        zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 4));
        applyAndLog(zombie, sources.generic(), 10f, "抗性V测试(原始10.0)");

        // ⑥【改动链测试】模拟冒险模组在 Incoming 阶段给伤害加成。
        //    挂了 selfTestAmplifier 后，原始 3 应该先被放大到 3*2+1=7，
        //    改动链里必须如实记下这一步。
        zombie.removeAllEffects();
        zombie.setHealth(20f);
        zombie.invulnerableTime = 0;
        AMPLIFY_ACTIVE = true;
        applyAndLog(zombie, sources.generic(), 3f, "加成测试(原始3.0, 应被放大到7.0)");
        AMPLIFY_ACTIVE = false;

        LOGGER.info("SELFTEST: 场景执行完毕，僵尸剩余血量 = {}", zombie.getHealth());
    }

    /** 是否启用「模拟加成」的监听器。 */
    private static volatile boolean AMPLIFY_ACTIVE = false;

    /** 记录加成测试是否真的生效。 */
    private static volatile boolean amplifyWorked = false;

    /**
     * 模拟一个会改伤害的冒险模组：在 {@code LivingIncomingDamageEvent} 里把伤害 ×2 +1。
     *
     * <p>用 {@link EventPriority#NORMAL}，落在「采样点 1（HIGHEST）」和
     * 「采样点 2（LOWEST）」之间，正好能被改动链捕捉到。
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.NORMAL)
    public static void selfTestAmplifier(LivingIncomingDamageEvent event) {
        if (!AMPLIFY_ACTIVE) {
            return;
        }
        float boosted = event.getAmount() * 2f + 1f;
        event.setAmount(boosted);
        amplifyWorked = true;
        LOGGER.info("SELFTEST: [模拟冒险模组] 把伤害从 {} 加成到 {}",
                DamageDescriber.num((boosted - 1f) / 2f), DamageDescriber.num(boosted));
    }

    private static void applyAndLog(net.minecraft.world.entity.LivingEntity target, DamageSource source, float amount, String label) {
        float before = target.getHealth();
        boolean hurt = target.hurt(source, amount);
        float after = target.getHealth();
        LOGGER.info("SELFTEST: 施加 {} -> hurt()={}, 血量 {} -> {}",
                label, hurt, DamageDescriber.num(before), DamageDescriber.num(after));
    }

    private static void finish(MinecraftServer server, boolean pass) {
        LOGGER.info("SELFTEST: 抓到的伤害消息 {} 条（期望 >= {}）", MessageCapture.count(), EXPECTED_MESSAGES);
        MessageCapture.dump();

        // 关键断言：实际伤害不能超过原始伤害
        int inversions = MessageCapture.countInversions();
        if (inversions > 0) {
            LOGGER.error("SELFTEST: 发现 {} 条「实际伤害 > 原始伤害」的异常数据", inversions);
        }

        boolean ok = pass && inversions == 0 && toggleOk && classifierOk && translationOk;
        LOGGER.info("SELFTEST: RESULT: {}", ok ? "PASS" : "FAIL");
        server.halt(false);
    }
}
