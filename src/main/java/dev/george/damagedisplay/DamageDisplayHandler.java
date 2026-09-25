package dev.george.damagedisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 伤害显示的核心逻辑。
 *
 * <p>挂了三个事件，分别拿「受伤前」「格挡」「结算后」三个阶段的数据：
 *
 * <ol>
 *   <li>{@link LivingIncomingDamageEvent} —— 减免开始前触发，记下原始伤害值。
 *       （原始伤害在 {@code LivingDamageEvent.Post} 里也能拿到，但这里额外记一份，
 *       是为了在 {@code Post} 不触发的情况下仍然有数据可用。）</li>
 *   <li>{@link LivingShieldBlockEvent} —— 盾牌格挡的量和耐久消耗。</li>
 *   <li>{@link LivingDamageEvent.Post} —— 全部减免算完、血量已经扣掉，
 *       这里能一次性读到各项减免明细和最终实际伤害。</li>
 * </ol>
 *
 * <p>消息通过 {@code ServerPlayer.sendSystemMessage} 直接发给对应玩家，
 * 所以无论模组装在服务端还是客户端（单人）都能工作。
 */
public final class DamageDisplayHandler {

    /**
     * 记录「本次伤害开始前」的血量，用来事后算真实掉血量。
     *
     * <p>键是实体 UUID，值是受伤前的血量。{@link LivingDamageEvent.Post} 里算完后会移除。
     */
    private static final java.util.Map<java.util.UUID, Float> HEALTH_BEFORE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 每次伤害的「改动链」，边采样边往里加。
     *
     * <p>同样在 {@link LivingDamageEvent.Post} 里用完就移除。
     */
    private static final java.util.Map<java.util.UUID, DamageTrace> TRACES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private DamageDisplayHandler() {
    }

    /** 这个实体本次伤害是否需要被追踪（配置开着 + 该显示 + 开关是开）。 */
    private static boolean isTracking(LivingEntity entity) {
        return DamageDisplayConfig.enabled()
                && shouldDisplay(entity)
                && isToggledOn(entity)
                && DamageDisplayConfig.showTrace();
    }

    // ------------------------------------------------------------------
    // 改动链：在伤害流程的各个节点采样
    // ------------------------------------------------------------------

    /**
     * 【采样点 1】伤害流程最开头 —— 记初始伤害，并快照血量。
     *
     * <p>用 {@link EventPriority#HIGHEST} 保证早于其它任何监听器的修改：
     * 血量快照要最原始才能算出真实掉血量；{@code getNewDamage()} 此刻就是「最初伤害」，
     * 作为改动链的起点。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!isTracking(event.getEntity())) {
            return;
        }
        LivingEntity entity = event.getEntity();

        HEALTH_BEFORE.put(entity.getUUID(), entity.getHealth());

        DamageTrace trace = new DamageTrace();
        trace.add("trace.initial", event.getAmount(), DamageTrace.Kind.INITIAL);

        // 记下攻击者与武器，供悬浮详情里说明「加成从哪来」
        trace.setAttackerInfo(describeAttacker(event.getSource()));

        TRACES.put(entity.getUUID(), trace);
    }

    /**
     * 【采样点 2】{@code LivingIncomingDamageEvent} 的末尾 —— 别的模组在这一阶段改完之后。
     *
     * <p>冒险类模组（莱特兰、神化等）加的「攻击力 / 伤害加成」大多发生在这里。
     * 这一段的差值就是它们的总贡献；如果能识别出攻击者/武器，会一并写进说明。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingTail(LivingIncomingDamageEvent event) {
        if (!isTracking(event.getEntity())) {
            return;
        }
        DamageTrace trace = TRACES.get(event.getEntity().getUUID());
        if (trace != null) {
            float now = event.getAmount();
            float before = trace.last();
            // 只有真的变了才记，避免刷一条「+0」
            if (Math.abs(now - before) > 1.0E-4f) {
                trace.add(now > before ? "trace.amplify" : "trace.weaken", now, DamageTrace.Kind.AMPLIFY);
            }
        }
    }

    /**
     * 【采样点 3】护甲 / 附魔 / 抗性提升算完之后（吸收尚未处理）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!isTracking(event.getEntity())) {
            return;
        }
        DamageTrace trace = TRACES.get(event.getEntity().getUUID());
        if (trace != null) {
            float now = event.getNewDamage();
            float before = trace.last();
            if (Math.abs(now - before) > 1.0E-4f) {
                trace.add("trace.afterReductions", now, DamageTrace.Kind.REDUCE);
            }
        }
    }

    /**
     * 描述攻击者与武器，用于悬浮详情里说明伤害来源。
     *
     * <p>例：{@code 僵尸 的 铁剑}、{@code 骷髅 的 弓}。
     */
    private static String describeAttacker(DamageSource source) {
        Component srcName = DamageDescriber.describeSource(source);
        if (srcName == null) {
            return null;
        }
        net.minecraft.world.item.ItemStack weapon = source.getWeaponItem();
        String base = srcName.getString();
        if (weapon != null && !weapon.isEmpty()) {
            return base + " · " + weapon.getHoverName().getString();
        }
        return base;
    }

    // ------------------------------------------------------------------
    // 事件：结算后，输出
    // ------------------------------------------------------------------

    /**
     * 伤害完全结算后输出详情。
     *
     * <p>用 {@link EventPriority#LOWEST}：等别的模组（减伤、免伤、吸血之类）都改完了再读，
     * 拿到的是最终的、玩家真正掉的血。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        UUID id = entity.getUUID();
        Float before = HEALTH_BEFORE.remove(id);
        DamageTrace trace = TRACES.remove(id);

        if (!DamageDisplayConfig.enabled()) {
            return;
        }
        if (!shouldDisplay(entity)) {
            return;
        }
        if (!isToggledOn(entity)) {
            return;
        }

        DamageSource source = event.getSource();

        // 黑名单过滤
        if (DamageDisplayConfig.isIgnored(DamageDescriber.typeId(source))) {
            return;
        }

        float original = event.getOriginalDamage();

        // 真实掉血量 = 受伤前血量 - 受伤后血量（clamp 到 >= 0）
        // 拿不到快照（极罕见）时退回 newDamage
        float actual = before != null
                ? Math.max(0f, before - entity.getHealth())
                : Math.max(0f, event.getNewDamage());

        if (actual <= 0f && !DamageDisplayConfig.showZeroDamage()) {
            return;
        }

        // 改动链的最后一步：吸收（Post 阶段才算完），终点用真实掉血量
        if (trace != null) {
            trace.add("trace.absorption", event.getNewDamage() - event.getReduction(DamageContainer.Reduction.ABSORPTION),
                    DamageTrace.Kind.REDUCE);
            trace.add("trace.final", actual, DamageTrace.Kind.FINAL);
        }

        Component message = buildMessage(entity, source, event, actual, trace);

        // 改动链里的最大伤害值，供自检判断「实际掉血是否离谱」
        float peak = actual;
        if (trace != null) {
            for (DamageTrace.Step s : trace.steps()) {
                peak = Math.max(peak, s.value());
            }
        }
        MessageCapture.recordAmounts(original, actual, peak);

        // 自检时把 container 的原始数值全打出来，便于对照真实掉血量找差异
        if (ModSelfTest.isEnabled()) {
            org.slf4j.LoggerFactory.getLogger("SelfTest").info(
                    "SELFTEST-DETAIL: original={} newDamage={} absorption={} blocked={} | 真实掉血={}",
                    original, event.getNewDamage(),
                    event.getReduction(DamageContainer.Reduction.ABSORPTION),
                    event.getBlockedDamage(), actual);
            if (trace != null) {
                StringBuilder sb = new StringBuilder();
                float prev = 0f;
                for (int i = 0; i < trace.steps().size(); i++) {
                    DamageTrace.Step s = trace.steps().get(i);
                    if (i > 0) {
                        sb.append(" -> ");
                    }
                    sb.append(s.label()).append("=").append(DamageDescriber.num(s.value()));
                    prev = s.value();
                }
                org.slf4j.LoggerFactory.getLogger("SelfTest").info("SELFTEST-TRACE: {}", sb);
            }
        }

        send(entity, message);
    }

    // ------------------------------------------------------------------
    // 消息拼装
    // ------------------------------------------------------------------

    /**
     * 拼装聊天栏那一行。
     *
     * <p>统一用方括号分段，视觉上更整齐、也更容易一眼扫到重点：
     * <pre>
     * [伤害] [类型 摔落] [实际 7.5]
     * [伤害] [类型 生物攻击] [来源 僵尸] [原始 6] [实际 3.4] [护甲 -2.6]
     * </pre>
     *
     * <p>{@code [实际 x]} 上挂着悬浮详情，鼠标放上去能看到完整的伤害改动链。
     */
    private static Component buildMessage(LivingEntity entity, DamageSource source,
                                          LivingDamageEvent.Post event, float actual, DamageTrace trace) {
        float original = event.getOriginalDamage();
        float blocked = event.getBlockedDamage();
        boolean showOriginal = DamageDisplayConfig.showOriginal() && Math.abs(original - actual) > 1.0E-4f;

        List<Component> segments = new ArrayList<>();

        // ── ① 谁受伤（只有非玩家受伤时才需要写名字，避免自己受伤刷自己名字） ──
        if (!(entity instanceof Player)) {
            segments.add(seg(entity.getDisplayName().copy().withStyle(ChatFormatting.GRAY)));
        }

        // ── ② 伤害类型 ──
        segments.add(seg(Component.empty()
                .append(label("msg.type", ChatFormatting.GRAY))
                .append(DamageDescriber.describeType(source).copy().withStyle(ChatFormatting.AQUA))));

        // ── ③ 来源实体 ──
        Component src = DamageDescriber.describeSource(source);
        if (src != null) {
            segments.add(seg(Component.empty()
                    .append(label("msg.source", ChatFormatting.GRAY))
                    .append(src.copy().withStyle(ChatFormatting.YELLOW))));
        }

        // ── ④ 原始伤害（只有和最终值不同时才显示，避免"原始 5 实际 5"这种废话） ──
        if (showOriginal) {
            ChatFormatting color = original > actual ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE;
            segments.add(seg(Component.empty()
                    .append(label("msg.original", ChatFormatting.DARK_GRAY))
                    .append(Component.literal(DamageDescriber.num(original)).withStyle(color))));
        }

        // ── ⑤ 最终实际伤害（重点，高亮加粗；伤害越高颜色越"危险"） ──
        //     这里挂上「伤害改动链」的悬浮详情，鼠标放上去能看到全过程。
        ChatFormatting finalColor = actual <= 0f ? ChatFormatting.GREEN
                : actual >= 6f ? ChatFormatting.RED
                : ChatFormatting.GOLD;
        MutableComponent actualValue = Component.literal(DamageDescriber.num(actual))
                .withStyle(finalColor, ChatFormatting.BOLD);

        if (trace != null && trace.hasChanges()) {
            actualValue.withStyle(style -> style
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            buildTraceTooltip(trace, source)))
                    // 有悬浮详情时给个下划线，提示"这里可以看更多"
                    .withUnderlined(true));
        }

        segments.add(seg(Component.empty()
                .append(label("msg.actual", ChatFormatting.GRAY))
                .append(actualValue)));

        // ── ⑥ 减免明细（每项单独一个方括号） ──
        if (DamageDisplayConfig.showBreakdown()) {
            if (blocked > 0f) {
                segments.add(detail("reduction.blocked", blocked, ChatFormatting.BLUE));
            }
            addDetail(segments, "reduction.armor", event.getReduction(DamageContainer.Reduction.ARMOR));
            addDetail(segments, "reduction.enchantments", event.getReduction(DamageContainer.Reduction.ENCHANTMENTS));
            addDetail(segments, "reduction.mob_effects", event.getReduction(DamageContainer.Reduction.MOB_EFFECTS));
            addDetail(segments, "reduction.absorption", event.getReduction(DamageContainer.Reduction.ABSORPTION));
            addDetail(segments, "reduction.innate", event.getReduction(DamageContainer.Reduction.INNATE_RESISTANCE));
            // 注意：故意不列 Reduction.INVULNERABILITY（无敌帧）。
            // 连续受击时它几乎每次都会触发，列出来只会刷屏，
            // 而且那部分本来就没真的打到玩家身上，「原始 → 实际」已经体现了。
        }

        // ── 拼成一行：[伤害] [A] [B] ... ──
        MutableComponent line = Component.empty();
        line.append(tr("msg.prefix", ChatFormatting.DARK_GRAY));
        for (Component c : segments) {
            line.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY)).append(c);
        }
        return line;
    }

    /** 把内容包成一对深灰色方括号：{@code [内容]}。 */
    private static Component seg(Component content) {
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(content)
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 拼装「伤害改动链」悬浮详情 —— 鼠标放在 {@code [实际 x]} 上时显示。
     *
     * <p>输出形如：
     * <pre>
     * 伤害改动过程
     *   最初伤害        6
     *   伤害加成       +4      ← 莱特兰/神化等模组在这一步加
     *   减免前         10
     *   护甲/抗性/附魔  -3.2
     *   吸收            -1
     *   ────────────────
     *   实际掉血        5.8
     * </pre>
     *
     * <p>每一步的差值来自各阶段对 {@code getNewDamage()} 的采样（见 {@link DamageTrace}），
     * 不依赖任何具体模组，所以装了谁都能体现出来。
     */
    private static Component buildTraceTooltip(DamageTrace trace, DamageSource source) {
        MutableComponent out = Component.empty();

        // ── 标题 ──
        out.append(tr("msg.trace.title", ChatFormatting.WHITE)
                .withStyle(ChatFormatting.BOLD));

        // ── 伤害分类：物理 / 魔法 / 火焰 …… 以及是否真实伤害 ──
        DamageClassifier.Result cls = DamageClassifier.classify(source);
        out.append("\n").append(Component.literal("  " + labelText("trace.category") + " ")
                        .withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < cls.categories().size(); i++) {
            if (i > 0) {
                out.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY));
            }
            out.append(Component.translatable(DamageDisplayOfGeorge.MODID + "." + cls.categories().get(i))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (cls.isTrueDamage()) {
            out.append(Component.literal("  ")).append(
                    tr("trace.trueDamage", ChatFormatting.RED).withStyle(ChatFormatting.BOLD));
        }

        // ── 无视了哪些减免（真实伤害的构成） ──
        if (!cls.bypasses().isEmpty()) {
            out.append("\n").append(Component.literal("  " + labelText("trace.bypasses") + " ")
                            .withStyle(ChatFormatting.GRAY));
            for (int i = 0; i < cls.bypasses().size(); i++) {
                if (i > 0) {
                    out.append(Component.literal("、").withStyle(ChatFormatting.DARK_GRAY));
                }
                out.append(Component.translatable(DamageDisplayOfGeorge.MODID + "." + cls.bypasses().get(i))
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        // ── 攻击者与武器 ──
        if (trace.attackerInfo() != null) {
            out.append("\n").append(Component.literal("  " + labelText("trace.attacker") + " ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(trace.attackerInfo()).withStyle(ChatFormatting.YELLOW));
        }

        // ── 改动链正文 ──
        out.append("\n");
        out.append(Component.literal("  ──────────────").withStyle(ChatFormatting.DARK_GRAY));

        List<DamageTrace.Step> steps = trace.steps();
        float prev = steps.isEmpty() ? 0f : steps.get(0).value();

        for (int i = 0; i < steps.size(); i++) {
            DamageTrace.Step step = steps.get(i);
            out.append("\n");

            if (i == 0) {
                // 起点：只显示数值
                out.append(Component.literal("  " + labelText(step.label()) + "  ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(DamageDescriber.num(step.value()))
                                .withStyle(ChatFormatting.WHITE));
            } else {
                float delta = step.value() - prev;

                // 差值符号与颜色：涨了=红（更痛），降了=绿（减伤）
                String sign = delta >= 0 ? "+" : "";
                ChatFormatting deltaColor = delta > 0f ? ChatFormatting.RED
                        : delta < 0f ? ChatFormatting.GREEN
                        : ChatFormatting.DARK_GRAY;

                out.append(Component.literal("  " + labelText(step.label()) + "  ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(sign + DamageDescriber.num(delta))
                                .withStyle(deltaColor))
                        .append(Component.literal("  → " + DamageDescriber.num(step.value()))
                                .withStyle(ChatFormatting.DARK_GRAY));
            }
            prev = step.value();
        }

        // ── 结论：最初 vs 最终 ──
        out.append("\n").append(Component.literal("  ──────────────").withStyle(ChatFormatting.DARK_GRAY));
        out.append("\n").append(Component.literal("  " + labelText("trace.initial") + " ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(DamageDescriber.num(trace.first())).withStyle(ChatFormatting.WHITE));
        out.append(Component.literal("   " + labelText("trace.final") + " ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(DamageDescriber.num(trace.last()))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        return out;
    }

    /** 取翻译键对应的纯文本（用于悬浮提示里拼字符串）。 */
    private static String labelText(String key) {
        return Component.translatable(DamageDisplayOfGeorge.MODID + "." + key).getString();
    }

    /** 取一条模组自己的翻译键。 */
    private static MutableComponent tr(String key, ChatFormatting color) {
        return Component.translatable(DamageDisplayOfGeorge.MODID + "." + key).withStyle(color);
    }

    /** 标签 + 空格，用于 {@code [标签 值]} 这种「标签在前」的分段。 */
    private static MutableComponent label(String key, ChatFormatting color) {
        return tr(key, color).append(Component.literal(" ").withStyle(color));
    }

    /** 一条减免明细，形如 {@code [护甲 -2.6]}。 */
    private static Component detail(String labelKey, float value, ChatFormatting color) {
        return seg(Component.empty()
                .append(label(labelKey, color))
                .append(Component.literal("-" + DamageDescriber.num(value)).withStyle(color)));
    }

    private static void addDetail(List<Component> segments, String labelKey, float value) {
        if (value > 0f) {
            segments.add(detail(labelKey, value, ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 这个生物受到的伤害是否需要显示。 */
    private static boolean shouldDisplay(LivingEntity entity) {
        // 自检时无论配置如何都放行，否则用僵尸当靶子会一条都抓不到
        if (ModSelfTest.isEnabled()) {
            return true;
        }
        if (DamageDisplayConfig.onlySelf()) {
            return entity instanceof Player;
        }
        return true;
    }

    /**
     * 按键开关是否处于「开」。
     *
     * <p>玩家受伤 → 看该玩家自己的开关；非玩家生物受伤 → 看「附近有没有开着开关的玩家」，
     * 只要有一个人开着就还发得出去（{@link #send} 会再按距离筛）。
     */
    private static boolean isToggledOn(LivingEntity entity) {
        // 自检时始终放行，不受开关影响
        if (ModSelfTest.isEnabled()) {
            return true;
        }
        return ToggleState.isEnabled(entity.getUUID());
    }

    /**
     * 把消息发给该看见的人。
     *
     * <p>自己受伤 → 发给自己；其它生物受伤（onlySelf=false 时）→ 发包给附近所有玩家。
     */
    private static void send(LivingEntity entity, Component message) {
        // 自检模式下顺便留一份纯文本副本（非自检时是一次空调用）
        MessageCapture.record(message.getString());

        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        if (entity instanceof ServerPlayer player) {
            player.sendSystemMessage(message);
            return;
        }

        // 非玩家生物：发给 32 格内的玩家（调试用）
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(entity) <= 32.0 * 32.0) {
                player.sendSystemMessage(message);
            }
        }
    }
}
