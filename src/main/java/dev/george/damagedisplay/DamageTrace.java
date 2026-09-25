package dev.george.damagedisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次伤害的「改动链」：从最初伤害到最终伤害，中间每一步是谁改的、改了多少。
 *
 * <p>这是给鼠标悬浮在 {@code [实际 x]} 上时看的详情。
 *
 * <h2>为什么要记录过程，而不是只看头尾</h2>
 * 装了冒险类模组（莱特兰、神化等）之后，伤害会被改好几次：怪物攻击力加成、
 * 暴击、玩家护甲、抗性提升、吸收……只看「原始 → 实际」两个端点，
 * 玩家会觉得「怎么 6 点变成 12 点了」，完全看不出是哪一步加上去的。
 *
 * <h2>怎么采样的</h2>
 * 在 NeoForge 伤害流程的几个固定节点读 {@code DamageContainer.getNewDamage()}，
 * 和上一个采样值比较，差值就是「这一阶段改了多少」：
 * <ol>
 *   <li>{@code LivingIncomingDamageEvent}（HIGHEST）—— 最初的原始伤害</li>
 *   <li>{@code LivingIncomingDamageEvent}（LOWEST）—— 其它模组在 Incoming 阶段改完之后</li>
 *   <li>{@code LivingDamageEvent.Pre} —— 护甲/附魔/抗性算完之后</li>
 *   <li>{@code LivingDamageEvent.Post} —— 全部算完</li>
 * </ol>
 *
 * <p>这套採样不依赖任何具体模组，所以装了谁都能反映出它的改动，
 * 没装也不会有额外开销。
 */
public final class DamageTrace {

    /**
     * 改动链里的一步。
     *
     * @param label  这一步的说明（如「原始伤害」「护甲减免」）
     * @param value  这一步**之后**的伤害值
     * @param kind   这一步的性质，决定用颜色/正负号
     */
    public record Step(String label, float value, Kind kind) {

        /** 相对上一步的变化量。 */
        public float delta(float previous) {
            return value - previous;
        }
    }

    /** 一步的性质。 */
    public enum Kind {
        /** 最初的伤害。 */
        INITIAL,
        /** 加成（伤害变高）。 */
        AMPLIFY,
        /** 减免（伤害变低）。 */
        REDUCE,
        /** 最终值。 */
        FINAL
    }

    private final List<Step> steps = new ArrayList<>();

    /** 攻击者与武器的描述（如「僵尸 · 铁剑」），可能为 null。 */
    private String attackerInfo;

    /** 记一步。值没变化的重复步骤会被忽略，避免链里塞一堆「+0」。 */
    public void add(String label, float value, Kind kind) {
        if (!steps.isEmpty()) {
            Step last = steps.get(steps.size() - 1);
            // 数值没变就跳过（除了最后要标记的 FINAL）
            if (Math.abs(last.value() - value) < 1.0E-4f && kind != Kind.FINAL) {
                return;
            }
            // 同一标签重复也不要
            if (last.label().equals(label)) {
                return;
            }
        }
        steps.add(new Step(label, value, kind));
    }

    public List<Step> steps() {
        return steps;
    }

    public void setAttackerInfo(String info) {
        this.attackerInfo = info;
    }

    public String attackerInfo() {
        return attackerInfo;
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** 最初的伤害值（链的第一项）。 */
    public float first() {
        return steps.isEmpty() ? 0f : steps.get(0).value();
    }

    /** 最终值（链的最后一项）。 */
    public float last() {
        return steps.isEmpty() ? 0f : steps.get(steps.size() - 1).value();
    }

    /** 整条链里伤害是否被改动过（头尾不同，或中间有加成）。 */
    public boolean hasChanges() {
        return steps.size() > 1;
    }
}
