package dev.keryeshka.voxyseeu.fabric.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class JsonConfigIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonConfigIO() {
    }

    public static <T> T load(Path path, Class<T> type, Supplier<T> defaultsFactory) {
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                T defaults = defaultsFactory.get();
                save(path, defaults);
                return defaults;
            }

            try (Reader reader = Files.newBufferedReader(path)) {
                T value = GSON.fromJson(reader, type);
                if (value == null) {
                    T defaults = defaultsFactory.get();
                    save(path, defaults);
                    return defaults;
                }
                return value;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load config: " + path, exception);
        }
    }

    public static void save(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(value, writer);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save config: " + path, exception);
        }
    }
}
