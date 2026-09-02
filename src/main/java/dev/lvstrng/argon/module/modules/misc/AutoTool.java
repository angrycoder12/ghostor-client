package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.event.events.BlockBreakingListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.mixin.MinecraftClientAccessor;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

public final class AutoTool extends Module implements TickListener, AttackListener, BlockBreakingListener {
	private static final double DAMAGE_EPSILON = 0.0001D;
	private static final long NANOS_PER_MILLISECOND = 1_000_000L;

	private final BooleanSetting combatSwitching = new BooleanSetting(
			EncryptedString.of("Combat Switching"), false)
			.setDescription(EncryptedString.of("Switches to the highest-damage hotbar item before attacking"));
	private final MinMaxSetting switchDelay = new MinMaxSetting(
			EncryptedString.of("Switch Delay (ms)"), 0, 500, 1, 10, 50)
			.setDescription(EncryptedString.of("Random delay before changing hotbar slots"));
	private final BooleanSetting useSwords = new BooleanSetting(
			EncryptedString.of("Use Swords"), false)
			.setDescription(EncryptedString.of("Allows swords when they are the fastest item for a block"));
	private final BooleanSetting useHands = new BooleanSetting(
			EncryptedString.of("Use Hands"), true)
			.setDescription(EncryptedString.of("Avoids damaging a tool when no useful mining tool is available"));
	private final NumberSetting repairMode = new NumberSetting(
			EncryptedString.of("Repair Mode"), 0, 100, 0, 1)
			.setDescription(EncryptedString.of("Avoids tools at or below this many remaining uses"));
	private final BooleanSetting switchBack = new BooleanSetting(
			EncryptedString.of("Switch Back"), false)
			.setDescription(EncryptedString.of("Returns to the previous slot after mining stops"));

	private ClientLevel trackedLevel;
	private BlockPos pendingBlockPos;
	private int pendingBlockSlot = -1;
	private long blockSwitchAt;
	private int previousMiningSlot = -1;
	private int pendingCombatEntityId = -1;
	private int pendingCombatSlot = -1;
	private long combatSwitchAt;
	private boolean replayingAttack;

	public AutoTool() {
		super(EncryptedString.of("Auto-Tool"),
				EncryptedString.of("Automatically switches to the best hotbar tool or weapon"),
				-1,
				Category.MISC);
		addSettings(combatSwitching, switchDelay, useSwords, useHands, repairMode, switchBack);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(AttackListener.class, this);
		eventManager.add(BlockBreakingListener.class, this);
		trackedLevel = mc.level;
		clearPending();
		previousMiningSlot = -1;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(AttackListener.class, this);
		eventManager.remove(BlockBreakingListener.class, this);
		finishMining();
		clearPendingCombat();
		trackedLevel = null;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (!canSwitch()) {
			finishMining();
			clearPendingCombat();
			trackedLevel = mc.level;
			return;
		}

		if (trackedLevel != mc.level) {
			clearPending();
			previousMiningSlot = -1;
			trackedLevel = mc.level;
		}

		if (processPendingCombatSwitch()) {
			clearPendingBlock();
			return;
		}

		if (!isMiningActive()) {
			finishMining();
			return;
		}

		processPendingMiningSwitch();
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (replayingAttack || event.isCancelled() || !canSwitch()) {
			return;
		}

		if (mc.hitResult instanceof BlockHitResult blockHit
				&& blockHit.getType() == HitResult.Type.BLOCK) {
			queueMiningSwitch(blockHit.getBlockPos());
			return;
		}

		if (!combatSwitching.getValue()) {
			return;
		}

		if (!(mc.hitResult instanceof EntityHitResult hit)
				|| !(hit.getEntity() instanceof LivingEntity target)
				|| !isValidCombatTarget(target)) {
			clearPendingCombat();
			return;
		}

		if (trackedLevel != mc.level) {
			clearPending();
			trackedLevel = mc.level;
		}

		int bestSlot = findBestCombatSlot();
		int selectedSlot = mc.player.getInventory().getSelectedSlot();
		if (bestSlot == selectedSlot) {
			clearPendingCombat();
			return;
		}

		long now = System.nanoTime();
		if (pendingCombatEntityId == target.getId() && pendingCombatSlot == bestSlot) {
			if (now >= combatSwitchAt) {
				InventoryUtils.setInvSlot(bestSlot);
				clearPendingCombat();
				return;
			}

			event.cancel();
			return;
		}

		long delay = randomDelayNanos();
		if (delay == 0L) {
			InventoryUtils.setInvSlot(bestSlot);
			clearPendingCombat();
			return;
		}

		pendingCombatEntityId = target.getId();
		pendingCombatSlot = bestSlot;
		combatSwitchAt = now + delay;
		event.cancel();
	}

	@Override
	public void onBlockBreaking(BlockBreakingEvent event) {
		if (event.isCancelled() || !canSwitch() || !mc.options.keyAttack.isDown()) {
			return;
		}

		if (mc.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK) {
			queueMiningSwitch(hit.getBlockPos());
		}
	}

	private void queueMiningSwitch(BlockPos pos) {
		if (mc.player.getAbilities().instabuild) {
			return;
		}

		BlockState state = mc.level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}

		int bestSlot = findBestMiningSlot(state);
		if (bestSlot == mc.player.getInventory().getSelectedSlot()) {
			return;
		}

		if (previousMiningSlot < 0) {
			previousMiningSlot = mc.player.getInventory().getSelectedSlot();
		}

		long now = System.nanoTime();
		if (!pos.equals(pendingBlockPos) || bestSlot != pendingBlockSlot) {
			pendingBlockPos = pos.immutable();
			pendingBlockSlot = bestSlot;
			blockSwitchAt = now + randomDelayNanos();
		}

		if (now >= blockSwitchAt) {
			InventoryUtils.setInvSlot(bestSlot);
			clearPendingBlock();
		}
	}

	private void processPendingMiningSwitch() {
		if (pendingBlockPos == null || System.nanoTime() < blockSwitchAt) {
			return;
		}

		if (!(mc.hitResult instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK
				|| !hit.getBlockPos().equals(pendingBlockPos)) {
			clearPendingBlock();
			return;
		}

		BlockState state = mc.level.getBlockState(pendingBlockPos);
		if (state.isAir()) {
			clearPendingBlock();
			return;
		}

		int bestSlot = findBestMiningSlot(state);
		if (bestSlot != mc.player.getInventory().getSelectedSlot()) {
			InventoryUtils.setInvSlot(bestSlot);
		}
		clearPendingBlock();
	}

	private boolean processPendingCombatSwitch() {
		if (pendingCombatEntityId < 0) {
			return false;
		}

		if (!combatSwitching.getValue()
				|| !(mc.hitResult instanceof EntityHitResult hit)
				|| hit.getEntity().getId() != pendingCombatEntityId
				|| !(hit.getEntity() instanceof LivingEntity target)
				|| !isValidCombatTarget(target)) {
			clearPendingCombat();
			return false;
		}

		if (System.nanoTime() < combatSwitchAt) {
			return true;
		}

		int bestSlot = findBestCombatSlot();
		if (bestSlot != mc.player.getInventory().getSelectedSlot()) {
			InventoryUtils.setInvSlot(bestSlot);
		}

		clearPendingCombat();
		replayingAttack = true;
		try {
			((MinecraftClientAccessor) mc).invokeStartAttack();
		} finally {
			replayingAttack = false;
		}
		return true;
	}

	private int findBestMiningSlot(BlockState state) {
		int selectedSlot = mc.player.getInventory().getSelectedSlot();
		ItemStack heldStack = mc.player.getInventory().getItem(selectedSlot);
		float bestSpeed = getMiningSpeed(heldStack, state);
		if (isTooDamaged(heldStack) || (!useSwords.getValue() && heldStack.is(ItemTags.SWORDS))) {
			bestSpeed = 1.0F;
		}
		int bestSlot = -1;

		for (int slot = 0; slot < 9; slot++) {
			if (slot == selectedSlot) {
				continue;
			}

			ItemStack stack = mc.player.getInventory().getItem(slot);
			if (isTooDamaged(stack) || (!useSwords.getValue() && stack.is(ItemTags.SWORDS))) {
				continue;
			}

			float speed = getMiningSpeed(stack, state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = slot;
			}
		}

		if (bestSlot >= 0) {
			return bestSlot;
		}

		if (useHands.getValue() && isDamageable(heldStack)
				&& (getMiningSpeed(heldStack, state) <= 1.0F || isTooDamaged(heldStack))) {
			int fallbackSlot = findNonDamageableSlot(selectedSlot);
			if (fallbackSlot >= 0) {
				return fallbackSlot;
			}
		}

		return selectedSlot;
	}

	private float getMiningSpeed(ItemStack stack, BlockState state) {
		float speed = stack.getDestroySpeed(state);
		if (speed > 1.0F) {
			Registry<Enchantment> enchantments = mc.level.registryAccess()
					.lookupOrThrow(Registries.ENCHANTMENT);
			Optional<Reference<Enchantment>> efficiency = enchantments.get(Enchantments.EFFICIENCY);
			int level = efficiency
					.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
					.orElse(0);
			if (level > 0 && !stack.isEmpty()) {
				speed += level * level + 1;
			}
		}
		return speed;
	}

	private int findNonDamageableSlot(int selectedSlot) {
		for (int slot = 0; slot < 9; slot++) {
			if (slot != selectedSlot && !isDamageable(mc.player.getInventory().getItem(slot))) {
				return slot;
			}
		}
		return -1;
	}

	private boolean isDamageable(ItemStack stack) {
		return !stack.isEmpty() && stack.isDamageableItem();
	}

	private boolean isTooDamaged(ItemStack stack) {
		return isDamageable(stack)
				&& stack.getMaxDamage() - stack.getDamageValue() <= repairMode.getValueInt();
	}

	private int findBestCombatSlot() {
		int selectedSlot = mc.player.getInventory().getSelectedSlot();
		int bestSlot = selectedSlot;
		double bestDamage = getAttackDamage(mc.player.getInventory().getItem(selectedSlot));

		for (int slot = 0; slot < 9; slot++) {
			if (slot == selectedSlot) {
				continue;
			}

			double damage = getAttackDamage(mc.player.getInventory().getItem(slot));
			if (damage > bestDamage + DAMAGE_EPSILON) {
				bestDamage = damage;
				bestSlot = slot;
			}
		}

		return bestSlot;
	}

	private double getAttackDamage(ItemStack stack) {
		double baseDamage = mc.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		return getStackAttributeValue(stack, Attributes.ATTACK_DAMAGE, baseDamage);
	}

	private double getStackAttributeValue(ItemStack stack, Holder<Attribute> target, double baseValue) {
		double[] addValue = {0.0D};
		double[] addBase = {0.0D};
		double[] multiplyTotal = {1.0D};

		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (!attribute.equals(target)) {
				return;
			}

			switch (modifier.operation()) {
				case ADD_VALUE -> addValue[0] += modifier.amount();
				case ADD_MULTIPLIED_BASE -> addBase[0] += modifier.amount();
				case ADD_MULTIPLIED_TOTAL -> multiplyTotal[0] *= 1.0D + modifier.amount();
			}
		});

		return (baseValue + addValue[0] + baseValue * addBase[0]) * multiplyTotal[0];
	}

	private boolean isValidCombatTarget(LivingEntity target) {
		return target != mc.player && target.isAlive() && !target.isRemoved() && target.isAttackable();
	}

	private boolean canSwitch() {
		return mc.player != null
				&& mc.level != null
				&& mc.gameMode != null
				&& mc.gui.screen() == null
				&& !mc.player.isSpectator();
	}

	private boolean isMiningActive() {
		if (!mc.options.keyAttack.isDown()
				|| !(mc.hitResult instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}

		return !mc.level.getBlockState(hit.getBlockPos()).isAir()
				&& mc.gameMode.isDestroying();
	}

	private long randomDelayNanos() {
		return Math.max(0L, (long) switchDelay.getRandomValue()) * NANOS_PER_MILLISECOND;
	}

	private void clearPending() {
		clearPendingBlock();
		clearPendingCombat();
		replayingAttack = false;
	}

	private void finishMining() {
		clearPendingBlock();
		if (switchBack.getValue() && mc.player != null
				&& previousMiningSlot >= 0 && previousMiningSlot < 9
				&& previousMiningSlot != mc.player.getInventory().getSelectedSlot()) {
			InventoryUtils.setInvSlot(previousMiningSlot);
		}
		previousMiningSlot = -1;
	}

	private void clearPendingBlock() {
		pendingBlockPos = null;
		pendingBlockSlot = -1;
		blockSwitchAt = 0L;
	}

	private void clearPendingCombat() {
		pendingCombatEntityId = -1;
		pendingCombatSlot = -1;
		combatSwitchAt = 0L;
	}

}
