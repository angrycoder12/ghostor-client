package dev.lvstrng.argon.module;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.ButtonListener;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.modules.client.Friends;
import dev.lvstrng.argon.module.modules.client.SelfDestruct;
import dev.lvstrng.argon.module.modules.blatant.BoatFly;
import dev.lvstrng.argon.module.modules.blatant.Fly;
import dev.lvstrng.argon.module.modules.blatant.Speed;
import dev.lvstrng.argon.module.modules.blatant.LegitSpeed;
import dev.lvstrng.argon.module.modules.combat.*;
import dev.lvstrng.argon.module.modules.misc.*;
import dev.lvstrng.argon.module.modules.render.*;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.module.setting.ActionSetting;
import dev.lvstrng.argon.utils.EncryptedString;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager implements ButtonListener {
	private final List<Module> modules = new ArrayList<>();
	private final Map<Category, List<Module>> modulesByCategory = new EnumMap<>(Category.class);

	public ModuleManager() {
		addModules();
		addKeybinds();
		// Append this stateless GUI action after the legacy module keybind so old
		// Friends profile setting indexes remain unchanged.
		getModule(Friends.class).addManagerAction();
		addDisableActions();
		modules.forEach(Module::captureDefaultState);
	}

	public void addModules() {
		//Combat
		add(new AimAssist());
		add(new AnchorMacro());
		add(new AutoCrystal());
		add(new AutoDoubleHand());
		add(new AutoHitCrystal());
		add(new AutoInventoryTotem());
		add(new TriggerBot());
		add(new AutoPot());
		add(new AutoPotRefill());
		add(new AutoWTap());
		add(new CrystalOptimizer());
		add(new DoubleAnchor());
		add(new HoverTotem());
		add(new NoMissDelay());
		add(new ShieldDisabler());
		add(new TotemOffhand());
		add(new AutoJumpReset());

		//Misc
		add(new Prevent());
		add(new AutoXP());
		add(new NoJumpDelay());
		add(new PingSpoof());
		add(new FakeLag());
		add(new AutoClicker());
		add(new KeyPearl());
		add(new NoBreakDelay());
		add(new Freecam());
		add(new PackSpoof());
		add(new Sprint());

		//Render
		add(new HUD());
		add(new NoBounce());
		add(new PlayerESP());
		add(new StorageEsp());
		add(new TargetHud());

		//Client
		add(new ClickGUI());
		add(new Friends());
		add(new SelfDestruct());

		// Keep new modules at the end because legacy profiles use list indexes.
		add(new NoFall());
		add(new Reach());
		add(new ChestStealer());
		add(new SafeWalk());
		add(new Nametags());
		add(new MapTooltip());
		add(new ShulkerBoxTooltip());
		add(new Fullbright());
		add(new AntiAFK());
		add(new Scaffold());
		add(new Clutch());
		add(new AutoTool());
		add(new BlockESP());
		add(new ArmorHUD());
		add(new AutoGap());
		add(new ClientSpoof());
		add(new Backtrack());
		add(new FastPlace());
		add(new Velocity());
		add(new Fly());
		add(new BoatFly());
		add(new Speed());
		add(new Trajectories());
		add(new PumpkinVision());
		add(new MobESP());
		add(new BlockIn());
		add(new LegitSpeed());
		add(new WaterBucket());
		add(new AntiBot());
	}

	public List<Module> getEnabledModules() {
		return modules.stream()
				.filter(Module::isEnabled)
				.toList();
	}


	public List<Module> getModules() {
		return modules;
	}

	public void addKeybinds() {
		Argon.INSTANCE.getEventManager().add(ButtonListener.class, this);

		for (Module module : modules)
			module.addSetting(new KeybindSetting(EncryptedString.of("Keybind"), module.getKey(), true).setDescription(EncryptedString.of("Key to enabled the module")));
	}

	private void addDisableActions() {
		for (Module module : modules) {
			if (module.getOriginalCategory() == Category.CLIENT) continue;
			module.addSetting(new ActionSetting("Disable Module", () -> module.setClientDisabled(true))
					.visibleWhen(() -> !module.isClientDisabled()));
			module.addSetting(new ActionSetting("Enable Module", () -> module.setClientDisabled(false))
					.visibleWhen(module::isClientDisabled));
		}
	}

	public List<Module> getModulesInCategory(Category category) {
		return modulesByCategory.computeIfAbsent(category, requested -> modules.stream()
				.filter(module -> module.getCategory() == requested)
				.sorted(java.util.Comparator.comparing(
						module -> module.getName().toString(), String.CASE_INSENSITIVE_ORDER))
				.toList());
	}

	/** Invalidates the small GUI index after a module is renamed or moved. */
	public void invalidateCategoryIndex() {
		modulesByCategory.clear();
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T getModule(Class<T> moduleClass) {
		return (T) modules.stream()
				.filter(moduleClass::isInstance)
				.findFirst()
				.orElse(null);
	}

	public void add(Module module) {
		modules.add(module);
		invalidateCategoryIndex();
	}

	@Override
	public void onButtonPress(ButtonEvent event) {
		if (SelfDestruct.destruct)
			return;

		// Do not let module keybinds leak through screens or focused text fields.
		// The ClickGUI key remains a close shortcut when no input has focus.
		if (Argon.mc != null && Argon.mc.gui.screen() != null) {
			if (Argon.mc.gui.screen() instanceof dev.lvstrng.argon.gui.ClickGui gui
					&& !gui.isTextInputFocused()) {
				ClickGUI clickGUI = getModule(ClickGUI.class);
				if (clickGUI.getKey() == event.button && event.action == GLFW.GLFW_PRESS)
					clickGUI.toggle();
			}
			return;
		}

		modules.forEach(module -> {
			if(!module.isClientDisabled() && module.getKey() == event.button && event.action == GLFW.GLFW_PRESS)
				module.toggle();
		});
	}
}
