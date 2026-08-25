package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public final class AutoHitCrystal extends Module implements TickListener, ItemUseListener, AttackListener {
	private final KeybindSetting activateKey = new KeybindSetting(EncryptedString.of("Activate Key"), GLFW.GLFW_MOUSE_BUTTON_RIGHT, false)
			.setDescription(EncryptedString.of("Key that does hit crystalling"));
	private final BooleanSetting checkPlace = new BooleanSetting(EncryptedString.of("Check Place"), false)
			.setDescription(EncryptedString.of("Checks if you can place the obsidian on that block"));
	private final NumberSetting switchDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 0, 20, 0, 1);
	private final NumberSetting switchChance = new NumberSetting(EncryptedString.of("Switch Chance"), 0, 100, 100, 1);
	private final NumberSetting placeDelay = new NumberSetting(EncryptedString.of("Place Delay"), 0, 20, 0, 1);
	private final NumberSetting placeChance = new NumberSetting(EncryptedString.of("Place Chance"), 0, 100, 100, 1).setDescription(EncryptedString.of("Randomization"));
	private final BooleanSetting workWithTotem = new BooleanSetting(EncryptedString.of("Work With Totem"), false);
	private final BooleanSetting workWithCrystal = new BooleanSetting(EncryptedString.of("Work With Crystal"), false);
	private final BooleanSetting clickSimulation = new BooleanSetting(EncryptedString.of("Click Simulation"), false)
			.setDescription(EncryptedString.of("Makes the CPS hud think you're legit"));
	private final BooleanSetting swordSwap = new BooleanSetting(EncryptedString.of("Sword Swap"), true);

	private int placeClock = 0;
	private int switchClock = 0;
	private boolean active;
	private boolean crystalling;
	private boolean crystalSelected;

	public AutoHitCrystal() {
		super(EncryptedString.of("Auto Hit Crystal"),
				EncryptedString.of("Automatically hit-crystals for you"),
				-1,
				Category.COMBAT);
		addSettings(activateKey, checkPlace, switchDelay, switchChance, placeDelay, placeChance, workWithTotem, workWithCrystal, clickSimulation, swordSwap);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(ItemUseListener.class, this);
		eventManager.add(AttackListener.class, this);
		reset();

		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(ItemUseListener.class, this);
		eventManager.remove(AttackListener.class, this);
		super.onDisable();
	}

	@Override
	public void onTick() {
		int randomNum = MathUtils.randomInt(1, 100);

		if (mc.gui.screen() != null)
			return;

		if (KeyUtils.isKeyPressed(activateKey.getKey())) {
			if(mc.hitResult instanceof BlockHitResult hitResult && mc.hitResult.getType() == HitResult.Type.BLOCK)
				if(!active && !BlockUtils.canPlaceBlockClient(hitResult.getBlockPos()) && checkPlace.getValue())
					return;

			ItemStack mainHandStack = mc.player.getMainHandItem();

			if (!(WorldUtils.isSword(mainHandStack) || (workWithTotem.getValue() && mainHandStack.is(Items.TOTEM_OF_UNDYING)) || workWithCrystal.getValue() && mainHandStack.is(Items.END_CRYSTAL)) && !active)
				return;
			else if(mc.hitResult instanceof BlockHitResult hitResult && !active) {
				if(swordSwap.getValue()) {
					if (mc.hitResult.getType() == HitResult.Type.BLOCK) {
						Block block = mc.level.getBlockState(hitResult.getBlockPos()).getBlock();

						crystalling = block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
					}
				}
			}

			active = true;

			if (!crystalling) {
				if (mc.hitResult instanceof BlockHitResult hit) {
					if (hit.getType() == HitResult.Type.MISS)
						return;

					if (!BlockUtils.isBlock(hit.getBlockPos(), Blocks.OBSIDIAN)) {
						if(BlockUtils.isBlock(hit.getBlockPos(), Blocks.RESPAWN_ANCHOR) && BlockUtils.isAnchorCharged(hit.getBlockPos()))
							return;

						mc.options.keyUse.setDown(false);

						if (!mc.player.isHolding(Items.OBSIDIAN)) {
							if (switchClock > 0) {
								switchClock--;
								return;
							}

							if (randomNum <= switchChance.getValueInt()) {
								switchClock = switchDelay.getValueInt();
								InventoryUtils.selectItemFromHotbar(Items.OBSIDIAN);
							}
						}

						if (mc.player.isHolding(Items.OBSIDIAN)) {
							if (placeClock > 0) {
								placeClock--;
								return;
							}

							if (clickSimulation.getValue())
								MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

							randomNum = MathUtils.randomInt(1, 100);

							if (randomNum <= placeChance.getValueInt()) {
								WorldUtils.placeBlock(hit, true);

								placeClock = placeDelay.getValueInt();
								crystalling = true;
							}
						}
					}
				}
			}

			if (crystalling) {
				if (!mc.player.isHolding(Items.END_CRYSTAL) && !crystalSelected) {
					if (switchClock > 0) {
						switchClock--;
						return;
					}

					randomNum = MathUtils.randomInt(1, 100);

					if (randomNum <= switchChance.getValueInt()) {
						crystalSelected = InventoryUtils.selectItemFromHotbar(Items.END_CRYSTAL);
						switchClock = switchDelay.getValueInt();
					}
				}

				if (mc.player.isHolding(Items.END_CRYSTAL)) {
					AutoCrystal autoCrystal = Argon.INSTANCE.getModuleManager().getModule(AutoCrystal.class);

					if (!autoCrystal.isEnabled())
						autoCrystal.onTick();
				}
			}
		} else reset();
	}

	@Override
	public void onItemUse(ItemUseEvent event) {
		ItemStack mainHandStack = mc.player.getMainHandItem();
		if ((mainHandStack.is(Items.END_CRYSTAL) || mainHandStack.is(Items.OBSIDIAN)) && GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS)
			event.cancel();
	}

	public void reset() {
		placeClock = placeDelay.getValueInt();
		switchClock = switchDelay.getValueInt();
		active = false;
		crystalling = false;
		crystalSelected = false;
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (mc.player.getMainHandItem().is(Items.END_CRYSTAL) && GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
			event.cancel();
	}
}
