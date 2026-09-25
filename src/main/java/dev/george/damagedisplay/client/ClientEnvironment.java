package dev.george.damagedisplay.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * 客户端环境的薄封装，用来安全地访问「只有客户端才有」的语言资源。
 *
 * <p>{@code net.minecraft.client.resources.language.I18n} 和 {@code Language} 都只存在于
 * 客户端发行版里，专用服务器上引用会直接 {@code NoClassDefFoundError}。
 *
 * <p>所以真正的调用被塞进一个独立的静态嵌套类 {@link ClientOnly} 里 ——
 * JVM 只在真的走到那一行时才加载它，服务端永远不会加载。
 */
public final class ClientEnvironment {

    /** 客户端专属实现；只有 Dist.CLIENT 时才会被类加载。 */
    private static final class ClientOnly {
        static boolean hasLanguage() {
            try {
                return net.minecraft.locale.Language.getInstance() != null;
            } catch (Throwable t) {
                return false;
            }
        }

        static boolean translationExists(String key) {
            try {
                return net.minecraft.locale.Language.getInstance().has(key);
            } catch (Throwable t) {
                return false;
            }
        }

        static String translationOrNull(String key) {
            try {
                net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
                // 用 has() 判存在性；getOrDefault(key, null) 在处理缺失键时行为不可靠
                return lang.has(key) ? lang.getOrDefault(key) : null;
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private ClientEnvironment() {
    }

    /** 当前是不是客户端。 */
    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    /** 语言资源是否已加载（专用服务器上恒为 false）。 */
    public static boolean hasLanguage() {
        if (!isClient()) {
            return false;
        }
        try {
            return ClientOnly.hasLanguage();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 某翻译键是否存在。 */
    public static boolean translationExists(String key) {
        if (!isClient()) {
            return false;
        }
        try {
            return ClientOnly.translationExists(key);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 取某翻译键的译文（不存在时返回 null）。 */
    public static String translationOrNull(String key) {
        if (!isClient()) {
            return null;
        }
        try {
            return ClientOnly.translationOrNull(key);
        } catch (Throwable t) {
            return null;
        }
    }
}
