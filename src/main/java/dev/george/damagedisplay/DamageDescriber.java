package dev.george.damagedisplay;

import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 把 {@link DamageSource} 翻译成人类可读的「伤害类型」与「来源」文本。
 *
 * <h2>为什么不用现成的翻译键</h2>
 * 原版只有 {@code death.attack.<msgId>} 这一套键，而它们是**死亡消息**：
 * <ul>
 *   <li>{@code death.attack.fall} = "X hit the ground too hard"（X 重重摔在地上）</li>
 *   <li>{@code death.attack.generic} = "X died"（X 死了）</li>
 * </ul>
 * 拿来当「伤害类型」显示会变成 "Zombie died" 这种读起来像结果的句子，很别扭。
 * 原版**没有**「伤害类型通用名」的翻译键，所以这里自带一份中英对照表，
 * 让输出是干净的「摔落」「火焰」这类类型名。
 *
 * <p>遇到对照表里没有的自定义伤害类型（其它模组加的），回退顺序是：
 * 伤害类型注册表 id → {@code msgId}。不会崩，也不会显示成乱码。
 */
public final class DamageDescriber {

    /**
     * 有对应语言键的伤害类型（值 = 语言键里的名字段）。
     *
     * <p>分两类：
     * <ul>
     *   <li><b>原版 48 种</b>：直接用注册名（{@code fall} → {@code minecraft:fall}）。</li>
     *   <li><b>常见模组的自定义类型</b>：用「命名空间_注册名」形式，
     *       避免不同模组用了同一个注册名时互相覆盖
     *       （例如莱特兰和张三都注册了 {@code freeze}）。</li>
     * </ul>
     *
     * <p>名字本身放在语言文件里（{@code assets/damage_display_of_george/lang/}），
     * 这里的集合只用来判断「要不要走翻译键」。表里没有的类型会回退显示原始 id，
     * 不会显示成 {@code damage_type.damage_display_of_george.xxx} 这种没翻译的原文。
     */
    private static final Set<String> NAMES = Set.of(
            // ── 原版：攻击类 ──
            "player_attack", "mob_attack", "mob_attack_no_aggro", "arrow", "trident",
            "mob_projectile", "thrown", "spit", "fireball", "unattributed_fireball",
            "wither_skull", "wind_charge", "sonic_boom",
            // ── 原版：火焰 / 高温 ──
            "in_fire", "on_fire", "lava", "hot_floor", "campfire", "fireworks",
            // ── 原版：环境 ──
            "fall", "fly_into_wall", "falling_anvil", "falling_block", "falling_stalactite",
            "stalagmite", "cactus", "sweet_berry_bush", "thorns", "sting", "cramming",
            "in_wall", "freeze", "drown", "starve", "dry_out", "lightning_bolt",
            "explosion", "player_explosion", "bad_respawn_point",
            // ── 原版：魔法 / 状态 ──
            "magic", "indirect_magic", "wither", "dragon_breath",
            // ── 原版：特殊 ──
            "generic", "generic_kill", "out_of_world", "outside_border",

            // ── 莱特兰-扩充（l2complements）──
            "l2complements_bleed",            // 流血
            "l2complements_emerald",          // 绿宝石
            "l2complements_freeze",           // 冰封
            "l2complements_life_sync",        // 生命同步
            "l2complements_soul_flame",       // 灵魂烈焰
            "l2complements_void_eye",         // 虚空之眼

            // ── 莱特兰-恶意（l2hostility）──
            "l2hostility_killer_aura",        // 杀戮光环
            "l2hostility_reflect",            // 伤害反弹

            // ── 莱特兰伤害追踪框架（l2damagetracker）的 14 种组合 ──
            // 结构是「基础攻击类型 + 若干 bypass 后缀」
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
    );

    /** 翻译键前缀，对应语言文件里的 {@code damage_type.damage_display_of_george.<名字段>}。 */
    private static final String KEY_PREFIX = "damage_type.damage_display_of_george.";

    private DamageDescriber() {
    }

    /**
     * 伤害类型的显示名。
     *
     * <p>查表拿到「摔落」这种干净的类型名；表里没有就用伤害类型 id 兜底，
     * 并在悬浮提示里给出完整 id 方便排查。
     *
     * <p>查找顺序：
     * <ol>
     *   <li>「命名空间_注册名」—— 给模组自定义类型用，避免同名冲突</li>
     *   <li>纯注册名 —— 给原版类型用（{@code minecraft:} 命名空间）</li>
     * </ol>
     */
    public static Component describeType(DamageSource source) {
        String id = typeId(source);
        String namespace = namespaceOf(id);
        String path = pathOf(id);

        // ① 先试「命名空间_注册名」（模组自定义类型）
        String namespaced = namespace.replace('.', '_') + "_" + path;
        if (!namespace.equals("minecraft") && NAMES.contains(namespaced)) {
            return Component.translatable(KEY_PREFIX + namespaced)
                    .withStyle(style -> style.withHoverEvent(hover(id)));
        }

        // ② 再试纯注册名（原版类型）
        if (NAMES.contains(path)) {
            return Component.translatable(KEY_PREFIX + path)
                    .withStyle(style -> style.withHoverEvent(hover(id)));
        }

        // ③ 未知类型：直接显示 id，悬浮提示给出 msgId
        return Component.literal(id)
                .withStyle(style -> style.withHoverEvent(hover(id + "  msgId=" + source.type().msgId())));
    }

    private static net.minecraft.network.chat.HoverEvent hover(String text) {
        return new net.minecraft.network.chat.HoverEvent(
                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(text));
    }

    /** 伤害类型的注册表 id，例如 {@code minecraft:fall}。 */
    public static String typeId(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElseGet(() -> source.type().msgId());
    }

    /** 取 id 里 {@code :} 后面的部分（{@code minecraft:fall} → {@code fall}）。 */
    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** 取 id 里 {@code :} 前面的命名空间（{@code minecraft:fall} → {@code minecraft}）。 */
    private static String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(0, colon) : "minecraft";
    }

    /**
     * 伤害来源描述：攻击者是谁、直接来源是什么。
     *
     * <p>例：僵尸射出的箭 → attacker = 僵尸，direct = 箭，
     * 显示成「僵尸（箭）」；只有直接实体时显示「箭」。
     */
    public static Component describeSource(DamageSource source) {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();

        if (attacker instanceof LivingEntity living) {
            MutableComponent name = living.getDisplayName().copy();
            if (direct != null && direct != attacker) {
                name.append(Component.literal("（")).append(direct.getDisplayName()).append(Component.literal("）"));
            }
            return name;
        }

        if (direct != null) {
            return direct.getDisplayName().copy();
        }

        if (attacker != null) {
            return attacker.getDisplayName().copy();
        }

        return null;
    }

    /** 格式化成一位小数，整数则不显示 {@code .0}（8.0 → "8"，7.5 → "7.5"）。 */
    public static String num(float value) {
        if (Math.abs(value - Math.round(value)) < 1.0E-4f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
