package dev.lvstrng.argon.config;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Runs native dialogs off the render thread and returns results on it. */
public final class NativeFileDialogs {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Ghostor File Dialog");
        thread.setDaemon(true);
        return thread;
    });

    private NativeFileDialogs() {
    }

    public static void openConfig(Path initialDirectory, Consumer<Path> callback) {
        EXECUTOR.execute(() -> {
            String result;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var patterns = stack.mallocPointer(1);
                patterns.put(stack.UTF8("*.ghostorconfig"));
                patterns.flip();
                result = TinyFileDialogs.tinyfd_openFileDialog(
                        "Import Ghostor Config", initialDirectory.toString(), patterns,
                        "Ghostor configs (*.ghostorconfig)", false);
            } catch (Throwable ignored) {
                result = null;
            }
            dispatch(result, callback);
        });
    }

    public static void saveConfig(Path suggestedPath, Consumer<Path> callback) {
        EXECUTOR.execute(() -> {
            String result;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var patterns = stack.mallocPointer(1);
                patterns.put(stack.UTF8("*.ghostorconfig"));
                patterns.flip();
                result = TinyFileDialogs.tinyfd_saveFileDialog(
                        "Export Ghostor Config", suggestedPath.toString(), patterns,
                        "Ghostor configs (*.ghostorconfig)");
            } catch (Throwable ignored) {
                result = null;
            }
            dispatch(result, callback);
        });
    }

    private static void dispatch(String result, Consumer<Path> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        minecraft.execute(() -> callback.accept(result == null || result.isBlank() ? null : Path.of(result)));
    }
}
