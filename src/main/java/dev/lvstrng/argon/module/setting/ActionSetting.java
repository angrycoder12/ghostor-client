package dev.lvstrng.argon.module.setting;

import java.util.Objects;

/** A stateless GUI action. Action settings are intentionally not persisted. */
public final class ActionSetting extends Setting<ActionSetting> {
    private final Runnable action;

    public ActionSetting(CharSequence name, Runnable action) {
        super(name);
        this.action = Objects.requireNonNull(action, "action");
    }

    public void run() {
        action.run();
    }
}
