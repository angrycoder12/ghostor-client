package dev.lvstrng.argon.config;

import com.google.gson.JsonObject;

/** Optional module-owned data that is not represented by normal settings. */
public interface ConfigStateProvider {
    JsonObject saveConfigState();

    JsonObject defaultConfigState();

    void validateConfigState(JsonObject state);

    void loadConfigState(JsonObject state);
}
