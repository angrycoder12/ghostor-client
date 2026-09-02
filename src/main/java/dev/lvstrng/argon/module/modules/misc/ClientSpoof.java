package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.StringSetting;
import dev.lvstrng.argon.utils.EncryptedString;

/** Supplies the brand string used by Minecraft's normal brand handshake. */
public final class ClientSpoof extends Module {
    public enum Mode {
        Lunar,
        Vanilla,
        Fabric,
        Custom
    }

    private static final int MAX_BRAND_LENGTH = 256;
    private static final String LUNAR_BRAND = "lunarclient:v2.12.0-2629";

    private final ModeSetting<Mode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), Mode.Lunar, Mode.class);
    private final StringSetting customBrand = new StringSetting(
            EncryptedString.of("Custom Brand"), "vanilla")
            .visibleWhen(() -> mode.isMode(Mode.Custom));

    public ClientSpoof() {
        super(EncryptedString.of("Client Spoof"),
                EncryptedString.of("Changes the client brand reported to servers"),
                -1,
                Category.MISC);
        addSettings(mode, customBrand);
        // Match the reference's first-run default without repeatedly overriding a
        // saved disabled state during profile loading.
        setEnabledStatus(true);
    }

    public String getSpoofedBrand() {
        return switch (mode.getMode()) {
            case Lunar -> LUNAR_BRAND;
            case Vanilla -> "vanilla";
            case Fabric -> "fabric";
            case Custom -> safeCustomBrand();
        };
    }

    private String safeCustomBrand() {
        String value = customBrand.getValue();
        if (value == null || value.isBlank()) return "vanilla";
        return value.length() <= MAX_BRAND_LENGTH ? value : value.substring(0, MAX_BRAND_LENGTH);
    }
}
