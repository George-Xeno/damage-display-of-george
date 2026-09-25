package dev.george.damagedisplay;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.neoforge.common.Tags;

/**
 * 把伤害归类成「物理 / 魔法 / 火焰 / …」以及「是否真实伤害（无视减免）」。
 *
 * <h2>为什么用标签而不是硬编码模组名</h2>
 * 原版和 NeoForge 早就定义了标准的伤害类型标签（{@code DamageTypeTags.IS_FIRE}、
 * {@code IS_PROJECTILE}、{@code Tags.DamageTypes.IS_MAGIC} 等）。
 * 成熟模组都是基于这套标签做分类的 —— 例如神化（Apotheosis）的
 * {@code DamageReductionAffix.DamageType} 枚举就是逐个 {@code source.is(标签)} 判断出来的。
 *
 * <p>所以这里也照同样的标准来判：<b>不需要依赖任何具体模组</b>，
 * 装了谁都能正确分类，自定义伤害类型只要打了标准标签也能被识别。
 *
 * <h2>关于「真实伤害」</h2>
 * 原版没有「真实伤害」这个标签，但它的语义是明确的：<b>无视护甲、附魔、抗性提升、
 * 无敌帧</b>的伤害。原版用 {@code BYPASSES_ARMOR}、{@code BYPASSES_INVULNERABILITY}
 * 等标签表达；莱特兰（l2hostility）则把这类性质直接编进伤害类型名
 * （如 {@code mob_attack-bypass_armor-bypass_magic}）。
 *
 * <p>这里综合两种表达方式判断：只要「无视护甲」且「无视魔法抗性」同时成立，
 * 就认为是真实伤害 —— 这与玩家直觉一致（完全不吃减伤）。
 */
public final class DamageClassifier {

    /**
     * 判定结果。
     *
     * @param categories 伤害类型分类（可能多个，如「物理」+「弹射物」）
     * @param bypasses   无视了哪些减免（可能多个）
     */
    public record Result(List<String> categories, List<String> bypasses) {

        /** 是否是「真实伤害」：同时无视护甲与魔法抗性。 */
        public boolean isTrueDamage() {
            return bypasses.contains("bypass.armor") && bypasses.contains("bypass.magic");
        }

        public boolean isEmpty() {
            return categories.isEmpty() && bypasses.isEmpty();
        }
    }

    private DamageClassifier() {
    }

    /**
     * 对伤害来源做分类。
     *
     * @param source 伤害来源
     */
    public static Result classify(DamageSource source) {
        List<String> categories = new ArrayList<>();
        List<String> bypasses = new ArrayList<>();

        // ── ① 主类型：按「能量/机制」归类 ──
        //    判定顺序有讲究：先判更具体的（闪电、摔落），再判更宽泛的（物理）。
        if (source.is(DamageTypeTags.IS_LIGHTNING)) {
            categories.add("cat.lightning");
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            categories.add("cat.fall");
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            categories.add("cat.drowning");
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            categories.add("cat.fire");
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            categories.add("cat.freeze");
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            categories.add("cat.explosion");
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            categories.add("cat.projectile");
        }
        if (source.is(Tags.DamageTypes.IS_POISON)) {
            categories.add("cat.poison");
        }
        if (source.is(Tags.DamageTypes.IS_WITHER)) {
            categories.add("cat.wither");
        }
        if (source.is(Tags.DamageTypes.IS_MAGIC)
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            categories.add("cat.magic");
        }

        // 物理：只认「近战攻击」。
        //
        // 注意：NeoForge 的 Tags.DamageTypes.IS_PHYSICAL 语义是「非魔法」，
        // 会把摔落、箭矢、仙人掌这些都算进去 —— 那不符合玩家直觉
        // （"摔落伤害是物理伤害"没意义，"箭矢"已经单独归到弹射物了）。
        // 所以这里只认原版的两个近战攻击标签，外加按注册名兜底判 mob_attack。
        if (source.is(DamageTypeTags.IS_PLAYER_ATTACK) || isMobAttack(source)) {
            categories.add("cat.physical");
        }

        // 去重（magic 可能被多条规则命中）
        categories = dedupe(categories);

        // 全都判不出来时给个「环境/其它」
        if (categories.isEmpty()) {
            categories.add("cat.other");
        }

        // ── ② 是否无视各种减免（用来识别"真实伤害"） ──
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            bypasses.add("bypass.armor");
        }
        if (source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            bypasses.add("bypass.enchantments");
        }
        if (source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            bypasses.add("bypass.resistance");
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            bypasses.add("bypass.invulnerability");
        }
        if (source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            bypasses.add("bypass.cooldown");
        }
        if (source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            bypasses.add("bypass.effects");
        }
        if (source.is(DamageTypeTags.BYPASSES_WOLF_ARMOR)) {
            bypasses.add("bypass.wolf_armor");
        }
        if (source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            bypasses.add("bypass.shield");
        }

        // ── ③ 莱特兰风格：性质编码在伤害类型名里 ──
        //    如 l2damagetracker:mob_attack-bypass_armor-bypass_magic
        //    这类自定义类型不一定打了标准标签，所以额外按名字兜底识别。
        String id = DamageDescriber.typeId(source);
        if (id.contains("bypass_armor") && !bypasses.contains("bypass.armor")) {
            bypasses.add("bypass.armor");
        }
        if (id.contains("bypass_magic") && !bypasses.contains("bypass.magic")) {
            bypasses.add("bypass.magic");
        }
        if (id.contains("bypass_cooldown") && !bypasses.contains("bypass.cooldown")) {
            bypasses.add("bypass.cooldown");
        }

        // 莱特兰的 bypass_magic 语义 = 无视魔法抗性，映射到标准的 resistance
        if (bypasses.contains("bypass.magic") && !bypasses.contains("bypass.resistance")) {
            bypasses.add("bypass.resistance");
        }

        // ── ④ 魔法类判定兜底：莱特兰的 -bypass_magic 系列属于魔法伤害 ──
        if (id.contains("magic") && !categories.contains("cat.magic")) {
            categories.add("cat.magic");
            categories = dedupe(categories);
        }

        return new Result(categories, dedupe(bypasses));
    }

    private static List<String> dedupe(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * 是否是生物近战攻击。
     *
     * <p>原版没有给 {@code mob_attack} 单独的标签（只有 {@code IS_PLAYER_ATTACK}），
     * 所以按伤害类型注册名判定 {@code minecraft:mob_attack} /
     * {@code minecraft:mob_attack_no_aggro}。
     *
     * <p>莱特兰那套 {@code mob_attack-bypass_armor} 之类的变体也算生物近战，
     * 所以用 {@code endsWith} 之外还判了前缀包含。
     */
    private static boolean isMobAttack(DamageSource source) {
        String path = pathOf(DamageDescriber.typeId(source));
        return path.equals("mob_attack")
                || path.equals("mob_attack_no_aggro")
                || path.startsWith("mob_attack-");
    }

    /** 取 id 里 {@code :} 后面的部分。 */
    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** 该伤害类型是否打了某个标签（调试/扩展用）。 */
    public static boolean hasTag(DamageSource source, TagKey<DamageType> tag) {
        return source.is(tag);
    }
}
