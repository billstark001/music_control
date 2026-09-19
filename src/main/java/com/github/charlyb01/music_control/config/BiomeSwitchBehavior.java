package com.github.charlyb01.music_control.config;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Locale;

/** Determines whether an already-playing track survives a biome music-context change. */
@JsonAdapter(BiomeSwitchBehavior.Adapter.class)
public enum BiomeSwitchBehavior {
    ALWAYS("always"),
    IF_INCOMPATIBLE("if_incompatible"),
    NEVER("never");

    private final String key;

    BiomeSwitchBehavior(String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return "text.autoconfig.music_control.option.general.timer.changeMusicOnBiomeSwitch.value." + key;
    }

    /** Reads both the current enum format and the boolean used before 1.10.1. */
    public static final class Adapter extends TypeAdapter<BiomeSwitchBehavior> {
        @Override
        public void write(JsonWriter output, BiomeSwitchBehavior value) throws IOException {
            if (value == null) {
                output.nullValue();
            } else {
                output.value(value.name());
            }
        }

        @Override
        public BiomeSwitchBehavior read(JsonReader input) throws IOException {
            JsonToken token = input.peek();
            if (token == JsonToken.BOOLEAN) {
                return input.nextBoolean() ? ALWAYS : NEVER;
            }
            if (token == JsonToken.NULL) {
                input.nextNull();
                return null;
            }
            if (token != JsonToken.STRING) {
                throw new JsonParseException("Expected a biome-switch behavior string or legacy boolean");
            }

            String value = input.nextString().trim()
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT);
            try {
                return valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new JsonParseException("Unknown biome-switch behavior: " + value, exception);
            }
        }
    }
}
