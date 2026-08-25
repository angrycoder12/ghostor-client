package dev.lvstrng.argon.utils;

import dev.lvstrng.argon.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SplashPotionItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import static dev.lvstrng.argon.Argon.mc;

public final class InventoryUtils {

	public static void setInvSlot(int slot) {
		mc.player.getInventory().setSelectedSlot(slot);
		((ClientPlayerInteractionManagerAccessor) mc.gameMode).syncSlot();
	}

	public static boolean selectItemFromHotbar(Predicate<Item> item) {
		Inventory inv = mc.player.getInventory();

		for (int i = 0; i < 9; i++) {
			ItemStack itemStack = inv.getItem(i);
			if (!item.test(itemStack.getItem()))
				continue;

			inv.setSelectedSlot(i);
			return true;
		}

		return false;
	}

	public static boolean selectItemFromHotbar(Item item) {
		return selectItemFromHotbar(i -> i == item);
	}

	public static boolean hasItemInHotbar(Predicate<Item> item) {
		Inventory inv = mc.player.getInventory();

		for (int i = 0; i < 9; i++) {
			ItemStack itemStack = inv.getItem(i);
			if (item.test(itemStack.getItem()))
				return true;
		}
		return false;
	}

	public static int countItem(Predicate<Item> item) {
		Inventory inv = mc.player.getInventory();

		int count = 0;

		for (int i = 0; i < 36; i++) {
			ItemStack itemStack = inv.getItem(i);
			if (item.test(itemStack.getItem()))
				count += itemStack.getCount();
		}

		return count;
	}

	public static int countItemExceptHotbar(Predicate<Item> item) {
		Inventory inv = mc.player.getInventory();

		int count = 0;

		for (int i = 9; i < 36; i++) {
			ItemStack itemStack = inv.getItem(i);
			if (item.test(itemStack.getItem()))
				count += itemStack.getCount();
		}

		return count;
	}

	public static int getSwordSlot() {
		Container playerInventory = mc.player.getInventory();

		for (int itemIndex = 0; itemIndex < 9; itemIndex++) {
			if (WorldUtils.isSword(playerInventory.getItem(itemIndex)))
				return itemIndex;
		}

		return -1;
	}

	public static boolean selectSword() {
		int itemIndex = getSwordSlot();

		if (itemIndex != -1) {
			InventoryUtils.setInvSlot(itemIndex);
			return true;
		} else return false;
	}

	public static int findSplash(MobEffect type, int duration, int amplifier) {
		Inventory inv = mc.player.getInventory();
		MobEffectInstance potion = new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(type), duration, amplifier);

		for (int i = 0; i < 9; i++) {
			ItemStack itemStack = inv.getItem(i);

			if (!(itemStack.getItem() instanceof SplashPotionItem))
				continue;

			//String s = PotionUtil.getPotion(itemStack).getEffects().toString();
			String s = itemStack.get(DataComponents.POTION_CONTENTS).getAllEffects().toString();
			if (s.contains(potion.toString())) {
				return i;
			}
		}

		return -1;
	}

	public static boolean isThatSplash(MobEffect type, int duration, int amplifier, ItemStack itemStack) {
		MobEffectInstance potion = new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(type), duration, amplifier);

		return itemStack.getItem() instanceof SplashPotionItem &&
				itemStack.get(DataComponents.POTION_CONTENTS).getAllEffects().toString().contains(potion.toString());
	}

	public static int findTotemSlot() {
		assert mc.player != null;
		Inventory inv = mc.player.getInventory();
		for (int index = 9; index < 36; index++) {
			if (inv.getItem(index).getItem() == Items.TOTEM_OF_UNDYING)
				return index;
		}
		return -1;
	}

	public static boolean selectAxe() {
		int itemIndex = getAxeSlot();

		if (itemIndex != -1) {
			mc.player.getInventory().setSelectedSlot(itemIndex);
			return true;
		} else return false;
	}

	public static int findRandomTotemSlot() {
		Inventory inventory = mc.player.getInventory();
		Random random = new Random();
		List<Integer> totemIndexes = new ArrayList<>();

		for(int i = 9; i < 36; i++) {
			if(inventory.getItem(i).getItem() == Items.TOTEM_OF_UNDYING)
				totemIndexes.add(i);
		}

		if(!totemIndexes.isEmpty()) {
			return totemIndexes.get(random.nextInt(totemIndexes.size()));
		} else return -1;
	}

	//effect.minecraft.instant_health
	//effect.minecraft.strength
	//effect.minecraft.speed
	public static int findRandomPot(String potion) {
		Inventory inventory = mc.player.getInventory();
		Random random = new Random();

		int slotIndex = random.nextInt(27) + 9;
		for (int i = 0; i < 27; i++) {
			int index = (slotIndex + i) % 36;
			ItemStack itemStack = inventory.getItem(index);
			if (itemStack.getItem() instanceof SplashPotionItem && (index != 36 || index != 37 || index != 38 || index != 39)) {
				if (!itemStack.get(DataComponents.POTION_CONTENTS).getAllEffects().toString().contains(potion.toString()))
					return -1;

				return index;
			}
		}
		return -1;
	}

	public static int findPot(MobEffect effect, int duration, int amplifier) {
		assert mc.player != null;
		Inventory inv = mc.player.getInventory();
		MobEffectInstance instance = new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier);

		for (int index = 9; index < 34; index++)
			if (inv.getItem(index).getItem() instanceof SplashPotionItem)
				if (inv.getItem(index).get(DataComponents.POTION_CONTENTS).getAllEffects().toString().contains(instance.toString()))
					return index;

		return -1;
	}

	public static List<Integer> getEmptyHotbarSlots() {
		Inventory inventory = mc.player.getInventory();
		List<Integer> slots = new ArrayList<>();

		for (int i = 0; i < 9; i++) {
			if (inventory.getItem(i).isEmpty())
				slots.add(i);
			else if (slots.contains(i) && !inventory.getItem(i).isEmpty())
				slots.remove(i);
		}

		return slots;
	}

	public static int getAxeSlot() {
		Container playerInventory = mc.player.getInventory();

		for (int itemIndex = 0; itemIndex < 9; itemIndex++) {
			if (WorldUtils.isAxe(playerInventory.getItem(itemIndex)))
				return itemIndex;
		}

		return -1;
	}

	public static int countItem(Item item) {
		return countItem(i -> i == item);
	}
}
