package com.github.charlyb01.music_control.gui.components;

import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.config.FilterOperator;
import com.github.charlyb01.music_control.config.ModConfig;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unified identifier query language for music, events, biomes and dimensions.
 * Supports @namespace, #path, $case-sensitive display text, !negation and an
 * initial & to temporarily invert the configured AND/OR operator.
 */
public final class IdentifierSearchFilter implements SearchFilter<Identifier> {
    public static final IdentifierSearchFilter INSTANCE = new IdentifierSearchFilter();

    private IdentifierSearchFilter() {}

    @Override
    public boolean matches(String query, Identifier value) {
        return matches(query, value,
                ModConfig.get().cosmetics.gui.filterOperator == FilterOperator.AND);
    }

    static boolean matches(String query, Identifier value, boolean defaultAnd) {
        if (query == null || query.isBlank()) return true;

        boolean invertOperator = false;
        List<String> terms = new ArrayList<>();
        for (String raw : query.split(" ")) {
            if (raw.isEmpty()) continue;
            if (raw.charAt(0) == '&') {
                invertOperator = true;
                if (raw.length() > 1) terms.add(raw.substring(1));
            } else {
                terms.add(raw);
            }
        }
        if (terms.isEmpty()) return true;

        boolean and = defaultAnd ^ invertOperator;
        for (String term : terms) {
            boolean matched = matchesTerm(term, value);
            if (and && !matched) return false;
            if (!and && matched) return true;
        }
        return and;
    }

    private static boolean matchesTerm(String term, Identifier value) {
        boolean inverted = term.charAt(0) == '!';
        if (inverted) {
            term = term.substring(1);
            if (term.isEmpty()) return false;
        }

        char field = term.charAt(0);
        String needle = (field == '@' || field == '#' || field == '$')
                ? term.substring(1) : term;
        boolean matched = switch (field) {
            case '@' -> value.getNamespace().toLowerCase(Locale.ROOT)
                    .contains(needle.toLowerCase(Locale.ROOT));
            case '#' -> value.getPath().toLowerCase(Locale.ROOT)
                    .contains(needle.toLowerCase(Locale.ROOT));
            case '$' -> Music.getTranslatedText(value).getString().contains(needle);
            default -> Music.getTranslatedText(value).getString().toLowerCase(Locale.ROOT)
                    .contains(needle.toLowerCase(Locale.ROOT));
        };
        return matched ^ inverted;
    }
}
