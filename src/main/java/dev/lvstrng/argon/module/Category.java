package dev.lvstrng.argon.module;

import dev.lvstrng.argon.utils.EncryptedString;

public enum Category {
	COMBAT(EncryptedString.of("Combat")),
	MISC(EncryptedString.of("Misc")),
	RENDER(EncryptedString.of("Render")),
	BLATENT(EncryptedString.of("Blatent")),
	CLIENT(EncryptedString.of("Client")),
	DISABLED(EncryptedString.of("Disabled"));
	public final CharSequence name;

	Category(CharSequence name) {
		this.name = name;
	}
}
