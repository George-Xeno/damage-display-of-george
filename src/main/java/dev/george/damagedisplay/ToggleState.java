package dev.george.damagedisplay;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时的「显示开关」状态，由按键控制，独立于配置文件。
 *
 * <h2>为什么还要一个运行时开关</h2>
 * 配置文件（{@code enabled}）是「长期设置」，改一次要重启或重载；
 * 而按键是「临时看一眼 / 临时静音」的即时操作。两者语义不同，所以分开：
 * <ul>
 *   <li>配置文件 {@code enabled = false} → 彻底关闭，按键也救不回来（尊重配置）。</li>
 *   <li>按键开关 → 在配置允许的前提下，临时切换显示与否。</li>
 * </ul>
 *
 * <h2>三种场景下的行为</h2>
 * <ul>
 *   <li><b>单人 / 服务端也装了</b>：按键发出网络包，服务端保存该玩家的开关状态，
 *       服务端决定不发消息。</li>
 *   <li><b>纯客户端（服务器没装本 mod）</b>：服务端不知道这个 mod，也就根本不会发
 *       伤害消息 —— 那种情况下本 mod 本来就没有输出可过滤。开关只影响本地状态。</li>
 *   <li><b>集成服务器（单人）</b>：客户端和服务端在同一个进程里，走的是
 *       {@code ServerPlayer} 那条路。</li>
 * </ul>
 */
public final class ToggleState {

    /**
     * 每个玩家的开关状态。键是玩家 UUID。
     *
     * <p>服务端维护「每个玩家各自的开关」；客户端只用一个「本地玩家」的哨兵键。
     * 用 {@link ConcurrentHashMap} 是因为服务端可能在网络线程读写。
     */
    private static final Map<UUID, Boolean> PLAYER_ENABLED = new ConcurrentHashMap<>();

    /** 客户端侧「本地玩家」的哨兵键（客户端不知道自己的 UUID 时也能用）。 */
    public static final UUID CLIENT_KEY = new UUID(0L, 0L);

    private ToggleState() {
    }

    /**
     * 该玩家的显示是否打开。
     *
     * <p>没记录过的玩家默认「开」。
     */
    public static boolean isEnabled(UUID playerId) {
        return PLAYER_ENABLED.getOrDefault(playerId, Boolean.TRUE);
    }

    /** 设置开关，返回设置后的新状态。 */
    public static boolean setEnabled(UUID playerId, boolean enabled) {
        PLAYER_ENABLED.put(playerId, enabled);
        return enabled;
    }

    /**
     * 翻转开关，返回翻转后的新状态。
     *
     * <p>没记录过时按「当前是开」处理，所以第一次翻转结果是 {@code false}（关）。
     * 用 {@code compute} 保证并发下不会翻错。
     */
    public static boolean toggle(UUID playerId) {
        return PLAYER_ENABLED.compute(playerId, (k, old) -> !(old == null || old));
    }

    /** 玩家下线时清掉记录，避免长期运行堆积。 */
    public static void forget(UUID playerId) {
        PLAYER_ENABLED.remove(playerId);
    }
}
