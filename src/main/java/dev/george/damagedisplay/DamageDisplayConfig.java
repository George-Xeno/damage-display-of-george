package dev.george.damagedisplay;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置（生成在 {@code config/damage_display_of_george-common.toml}）。
 *
 * <p>用 COMMON 而不是 SERVER：这份配置要同时作用于「服务端计算伤害的那一刻」
 * 和「客户端把消息打进聊天栏的那一刻」。SERVER 类型的配置只在服务端加载，
 * 单机里没问题，但在纯客户端场景（客户端装了、服务器没装）会读不到值。
 */
public final class DamageDisplayConfig {

    public static final ModConfigSpec SPEC;

    /** 总开关。 */
    public static final ModConfigSpec.BooleanValue ENABLED;

    /** 只显示「自己」受到的伤害，还是显示所有玩家/生物的伤害。 */
    public static final ModConfigSpec.BooleanValue ONLY_SELF;

    /** 是否显示减免明细（护甲、附魔、抗性提升、吸收……）。 */
    public static final ModConfigSpec.BooleanValue SHOW_BREAKDOWN;

    /** 是否显示「原始伤害 → 最终伤害」的变化。 */
    public static final ModConfigSpec.BooleanValue SHOW_ORIGINAL;

    /**
     * 是否追踪并显示「改动链」：把鼠标放在 {@code [实际 x]} 上时，
     * 能看到最终伤害是怎么从最初伤害一步步变过来的。
     */
    public static final ModConfigSpec.BooleanValue SHOW_TRACE;

    /** 最终实际伤害为 0 时（完全免疫/被格挡）是否也显示。 */
    public static final ModConfigSpec.BooleanValue SHOW_ZERO_DAMAGE;

    /** 伤害类型 id 的黑名单关键词，命中则不显示。 */
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> IGNORED_TYPES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Damage display of George —— 在聊天栏显示受到的伤害类型与伤害量").push("display");

        ENABLED = builder
                .comment("总开关。false = 完全不输出任何伤害信息。")
                .define("enabled", true);

        ONLY_SELF = builder
                .comment("true（默认）= 只在【自己】受到伤害时显示；",
                        "false = 周围所有生物受到的伤害都显示（刷屏警告，调试用）。")
                .define("onlySelf", true);

        SHOW_ORIGINAL = builder
                .comment("是否显示「原始伤害 → 最终伤害」，即减免前 / 减免后分别是多少。")
                .define("showOriginal", true);

        SHOW_BREAKDOWN = builder
                .comment("是否显示减免明细（护甲减免、附魔减免、抗性提升减免、吸收吸收了多少……）。")
                .define("showBreakdown", true);

        SHOW_TRACE = builder
                .comment("是否记录「伤害改动链」：把鼠标放在 [实际 x] 上会显示一个悬浮详情，",
                        "列出最终伤害是怎么从最初伤害一步步变化过来的",
                        "（例如：最初 6 → 攻击力加成 +4 → 暴击 ×1.5 → 护甲 -3 → 实际 9）。",
                        "装了莱特兰、神化这类会改伤害的模组时特别有用。",
                        "false = 不记录也不显示（省掉一点点开销）。")
                .define("showTrace", true);

        SHOW_ZERO_DAMAGE = builder
                .comment("最终实际伤害为 0 时是否也显示（例如被盾牌完全挡下、免疫该伤害类型）。",
                        "false = 只显示真正扣了血的。")
                .define("showZeroDamage", true);

        IGNORED_TYPES = builder
                .comment("伤害类型黑名单：伤害类型 id（如 minecraft:fall）里包含任一关键词就不显示。",
                        "留空 = 不过滤。",
                        "例：填 \"fall\" 就不显示摔落伤害。")
                .defineListAllowEmpty("ignoredTypes", java.util.List.of(), () -> "", o -> o instanceof String);

        builder.pop();

        SPEC = builder.build();
    }

    private DamageDisplayConfig() {
    }

    /**
     * 运行时读取配置。配置还没加载（或读取失败）时全部按「开」处理，
     * 也就是默认行为最完整，避免因为配置系统时序问题导致功能看起来「坏了」。
     */
    public static boolean enabled() {
        return get(ENABLED, true);
    }

    public static boolean onlySelf() {
        return get(ONLY_SELF, true);
    }

    public static boolean showOriginal() {
        return get(SHOW_ORIGINAL, true);
    }

    public static boolean showBreakdown() {
        return get(SHOW_BREAKDOWN, true);
    }

    public static boolean showZeroDamage() {
        return get(SHOW_ZERO_DAMAGE, true);
    }

    public static boolean showTrace() {
        return get(SHOW_TRACE, true);
    }

    private static boolean get(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    /** 该伤害类型是否被黑名单过滤掉。 */
    public static boolean isIgnored(String typeId) {
        try {
            for (String keyword : IGNORED_TYPES.get()) {
                if (keyword != null && !keyword.isBlank() && typeId.contains(keyword)) {
                    return true;
                }
            }
        } catch (IllegalStateException e) {
            // 配置未加载，不过滤
        }
        return false;
    }
}
