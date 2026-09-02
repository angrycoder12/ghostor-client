package dev.lvstrng.argon.gui.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/** Persists only ClickGUI layout, independently of index-based module profiles. */
public final class GuiLayoutState {
    private static final int MAX_SAVED_DIMENSION = 16_384;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("ghostor")
            .resolve("gui-layout.json");

    public SavedLayout load() {
        if (!Files.isRegularFile(path)) {
            return new SavedLayout();
        }
        try {
            SavedLayout layout = gson.fromJson(Files.readString(path), SavedLayout.class);
            return layout == null ? new SavedLayout() : layout;
        } catch (Exception ignored) {
            // A malformed optional layout file must never prevent client startup.
            return new SavedLayout();
        }
    }

    public void save(GuiBounds main, GuiBounds friends, GuiBounds blockSelector,
            GuiBounds configs, GuiBounds configForm) {
		SavedLayout layout = capture(main, friends, blockSelector, configs, configForm);
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(layout));
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Dragging/resizing remains available for this session if saving fails.
        }
    }

    public SavedLayout capture(GuiBounds main, GuiBounds friends, GuiBounds blockSelector,
            GuiBounds configs, GuiBounds configForm) {
		SavedLayout layout = new SavedLayout();
		layout.main = PanelState.from(main);
		layout.friends = PanelState.from(friends);
		layout.blockSelector = PanelState.from(blockSelector);
		layout.configs = PanelState.from(configs);
		layout.configForm = PanelState.from(configForm);
		return layout;
	}

    public static final class SavedLayout {
        public PanelState main;
        public PanelState friends;
        public PanelState blockSelector;
		public PanelState configs;
		public PanelState configForm;
    }

    public static final class PanelState {
        public Integer x;
        public Integer y;
        public Integer width;
        public Integer height;

        public boolean isValid() {
            return x != null && y != null && width != null && height != null
                    && Math.abs((long) x) <= MAX_SAVED_DIMENSION
                    && Math.abs((long) y) <= MAX_SAVED_DIMENSION
                    && width > 0 && width <= MAX_SAVED_DIMENSION
                    && height > 0 && height <= MAX_SAVED_DIMENSION;
        }

        public void applyTo(GuiBounds bounds) {
            if (isValid()) {
                bounds.initialize(x, y, width, height);
            }
        }

        private static PanelState from(GuiBounds bounds) {
            PanelState state = new PanelState();
            state.x = bounds.x();
            state.y = bounds.y();
            state.width = bounds.width();
            state.height = bounds.height();
            return state;
        }
    }
}
