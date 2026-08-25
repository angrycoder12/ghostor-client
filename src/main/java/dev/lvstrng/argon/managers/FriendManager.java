package dev.lvstrng.argon.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.lvstrng.argon.Argon.mc;

public final class FriendManager {
    private final Gson gson = new Gson();
    private final Map<String, String> friends = new LinkedHashMap<>();
    private final Path friendsPath = FabricLoader.getInstance().getConfigDir()
            .resolve("ghostor")
            .resolve("friends.json");

    public FriendManager() {
        load();
    }

    public boolean addFriend(Player player) {
        return addFriend(player.getName().getString());
    }

    public boolean addFriend(String name) {
        String displayName = sanitize(name);
        if (displayName.isEmpty() || friends.containsKey(normalize(displayName))) {
            return false;
        }

        friends.put(normalize(displayName), displayName);
        save();
        sendStatus(displayName + " was added to friends");
        return true;
    }

    public boolean removeFriend(Player player) {
        return removeFriend(player.getName().getString());
    }

    public boolean removeFriend(String name) {
        String removed = friends.remove(normalize(name));
        if (removed == null) {
            return false;
        }

        save();
        sendStatus(removed + " was removed from friends");
        return true;
    }

    public boolean isFriend(Player player) {
        return player != null && isFriend(player.getName().getString());
    }

    public boolean isFriend(String name) {
        return friends.containsKey(normalize(name));
    }

    public List<String> getFriends() {
        ArrayList<String> result = new ArrayList<>(friends.values());
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(result);
    }

    public boolean isAimingOverFriend() {
        if(mc.hitResult instanceof EntityHitResult hitResult) {
            Entity entity = hitResult.getEntity();

            if(entity instanceof Player player) {
                return isFriend(player);
            }
        }

        return false;
    }

    private void load() {
        if (!Files.isRegularFile(friendsPath)) {
            return;
        }

        try {
            JsonArray array = gson.fromJson(Files.readString(friendsPath), JsonArray.class);
            if (array == null) {
                return;
            }
            for (JsonElement element : array) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                String name = sanitize(element.getAsString());
                if (!name.isEmpty()) {
                    friends.putIfAbsent(normalize(name), name);
                }
            }
        } catch (Exception ignored) {
            // A malformed optional friends file must not prevent the client from starting.
        }
    }

    private void save() {
        try {
            Files.createDirectories(friendsPath.getParent());
            JsonArray array = new JsonArray();
            friends.values().stream()
                    .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                    .forEach(array::add);

            Path temporary = friendsPath.resolveSibling(friendsPath.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(array));
            try {
                Files.move(temporary, friendsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, friendsPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Friendship still works for this session if persistence is unavailable.
        }
    }

    private void sendStatus(String message) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.strip();
    }

    private static String normalize(String name) {
        return sanitize(name).toLowerCase(Locale.ROOT);
    }
}
