package dev.george.damagedisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * 自检用的消息捕获器。
 *
 * <p>{@link DamageDisplayHandler} 输出消息时会调用 {@link #record}，
 * 这样子检既能看到内容，也能统计条数。
 *
 * <p>平时（非自检）这个类的开销就是一次空方法调用 / 一次 if 判断，
 * 不影响正常游戏。
 */
public final class MessageCapture {

    /** 自检开关。只有 -Ddamagedisplay.selftest=true 时才缓存消息。 */
    private static final boolean ACTIVE = System.getProperty("damagedisplay.selftest") != null;

    private static final List<String> MESSAGES = new ArrayList<>();

    /** 每次伤害的 [原始, 实际] 数值对。 */
    private static final List<float[]> AMOUNTS = new ArrayList<>();

    private MessageCapture() {
    }

    /** 记一条即将发出的伤害消息。 */
    public static void record(String plainText) {
        if (ACTIVE) {
            MESSAGES.add(plainText);
        }
    }

    /**
     * 记一条「原始伤害 / 实际伤害 / 改动链峰值」数据，供自检校验。
     *
     * @param original 最初的伤害
     * @param actual   真实掉血量
     * @param peak     改动链里的最大值（没有链时传 actual）
     */
    public static void recordAmounts(float original, float actual, float peak) {
        if (ACTIVE) {
            AMOUNTS.add(new float[]{original, actual, peak});
        }
    }

    /**
     * 检查「实际伤害」是否超过「改动链里的最大伤害」。
     *
     * <h2>为什么不能直接断言「实际 &le; 原始」</h2>
     * 装了莱特兰、神化这类会**加成**伤害的模组时，「实际 &gt; 原始」是完全正常的
     * ——敌人攻击力被放大了，掉的血当然比最初伤害多。
     *
     * <p>真正该判定的是：实际掉血不能超过「伤害流程中出现过的最大值」。
     * 比如最初 6、被加成到 10，那实际掉血最多就是 10，绝不可能是 12。
     * 这既能抓住第一版那个「把 newDamage 当实际伤害」的 bug
     * （那里会出现实际值超过链上任何一步的数值），又不会误伤加成类模组。
     */
    public static int countInversions() {
        int bad = 0;
        for (float[] pair : AMOUNTS) {
            float original = pair[0];
            float actual = pair[1];
            float peak = pair.length > 2 ? pair[2] : Math.max(original, actual);
            // 允许一点点浮点误差
            float ceiling = Math.max(peak, original) + 1.0E-3f;
            if (actual > ceiling) {
                bad++;
                org.slf4j.LoggerFactory.getLogger("SelfTest").error(
                        "SELFTEST: 异常！实际掉血({}) 超过了改动链最大值({})，原始={}",
                        actual, peak, original);
            }
        }
        return bad;
    }

    public static int count() {
        return MESSAGES.size();
    }

    public static void reset() {
        MESSAGES.clear();
        AMOUNTS.clear();
    }

    /** 把抓到的消息打到日志里。 */
    public static void dump() {
        if (MESSAGES.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger("SelfTest").warn("SELFTEST: 没有抓到任何伤害消息");
            return;
        }
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("SelfTest");
        for (int i = 0; i < MESSAGES.size(); i++) {
            logger.info("SELFTEST: 消息[{}] {}", i, MESSAGES.get(i));
        }
    }
}
