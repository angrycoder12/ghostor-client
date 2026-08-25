package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.imixin.IKeyBinding;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

/** Edge-aware bridging assistance using the regular 26.2 key/input pipeline. */
public final class SafeWalk extends Module implements TickListener, HudListener {
    public enum BlockDisplay {
        BLOCKS_IN_TOTAL,
        BLOCKS_IN_CURRENT_STACK
    }

    private final BooleanSetting shift = new BooleanSetting(EncryptedString.of("Shift"), false);
    private final BooleanSetting shiftDuringJumps = new BooleanSetting(EncryptedString.of("Shift during jumps"), false);
    private final MinMaxSetting shiftTime = new MinMaxSetting(
            EncryptedString.of("Shift time"), 0, 280, 5, 140, 200);
    private final BooleanSetting onShiftHold = new BooleanSetting(EncryptedString.of("On shift hold"), false);
    private final BooleanSetting blocksOnly = new BooleanSetting(EncryptedString.of("Blocks only"), true);
    private final BooleanSetting showBlockAmount = new BooleanSetting(EncryptedString.of("Show amount of blocks"), true);
    private final ModeSetting<BlockDisplay> blockDisplay = new ModeSetting<>(
            EncryptedString.of("Block display info"), BlockDisplay.BLOCKS_IN_CURRENT_STACK, BlockDisplay.class);
    private final BooleanSetting lookDown = new BooleanSetting(EncryptedString.of("Only when looking down"), true);
    private final MinMaxSetting pitchRange = new MinMaxSetting(
            EncryptedString.of("Pitch min range"), 0, 90, 1, 70, 85);
    private final BooleanSetting shawtyMoment = new BooleanSetting(EncryptedString.of("Shawty Moment"), true);

    private boolean shouldBridge;
    private long releaseAfter;

    public SafeWalk() {
        super(EncryptedString.of("SafeWalk"), EncryptedString.of("Helps prevent walking off block edges"), -1, Category.MISC);
        addSettings(shift, shiftDuringJumps, shiftTime, onShiftHold, blocksOnly, showBlockAmount,
                blockDisplay, lookDown, pitchRange, shawtyMoment);
    }

    @Override
    public void onEnable() {
        resetState();
        eventManager.add(TickListener.class, this);
        eventManager.add(HudListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(HudListener.class, this);
        resetState();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (!shouldPreventEdge()) {
            resetState();
            return;
        }

        boolean overAir = isOverAir();
        if (!shift.getValue()) {
            shouldBridge = mc.player.onGround() && overAir;
            setModuleShift(false);
            return;
        }

        long now = System.currentTimeMillis();
        if (mc.player.onGround()) {
            if (overAir) {
                // Match the reference timer: release delay begins after the
                // player has moved back onto a supported block.
                releaseAfter = now + randomShiftTime();
                shouldBridge = true;
                setModuleShift(true);
            } else if (shouldBridge && now < releaseAfter) {
                setModuleShift(true);
            } else {
                clearBridgeState();
            }
        } else if (shouldBridge && mc.player.getAbilities().flying) {
            clearBridgeState();
        } else if (shouldBridge && overAir && shiftDuringJumps.getValue()) {
            setModuleShift(true);
        } else {
            setModuleShift(false);
        }
    }

    /** Conditions shared by the vanilla edge-clamping hook and auto-shift UI behavior. */
    public boolean shouldPreventEdge() {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.player.isDeadOrDying()
                || mc.gui.screen() != null || mc.player.getAbilities().flying) {
            return false;
        }
        if (onShiftHold.getValue() && !((IKeyBinding) mc.options.keyShift).isActuallyPressed()) {
            return false;
        }
        if (blocksOnly.getValue() && !(mc.player.getMainHandItem().getItem() instanceof BlockItem)) {
            return false;
        }
        float pitch = mc.player.getXRot();
        if (lookDown.getValue() && (pitch < pitchRange.getMinValue() || pitch > pitchRange.getMaxValue())) {
            return false;
        }
        return !shawtyMoment.getValue() || mc.player.input.keyPresses.backward();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (!showBlockAmount.getValue() || !shouldBridge || mc.player == null || mc.gui.screen() != null) {
            return;
        }

        int blocks = countBlocks();
        if (blocks <= 0) {
            return;
        }
        int color = blocks < 16 ? Color.RED.getRGB()
                : blocks < 32 ? Color.ORANGE.getRGB()
                : blocks < 128 ? Color.YELLOW.getRGB()
                : blocks > 128 ? Color.GREEN.getRGB()
                : Color.BLACK.getRGB();
        String text = blocks + " blocks";
        GuiGraphicsExtractor context = event.context;
        int x = (context.guiWidth() - mc.font.width(text)) / 2;
        int y = context.guiHeight() / 2 + 17;
        context.fill(x - 4, y - 3, x + mc.font.width(text) + 4, y + mc.font.lineHeight + 3, 0x88000000);
        context.text(mc.font, text, x, y, color, false);
    }

    private boolean isOverAir() {
        return mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(0, -.08, 0));
    }

    private int countBlocks() {
        if (blockDisplay.isMode(BlockDisplay.BLOCKS_IN_CURRENT_STACK)) {
            ItemStack stack = mc.player.getMainHandItem();
            return stack.getItem() instanceof BlockItem ? stack.getCount() : 0;
        }

        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof BlockItem) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private long randomShiftTime() {
        long min = shiftTime.getMinLong();
        long max = shiftTime.getMaxLong();
        return max > min ? ThreadLocalRandom.current().nextLong(min, max + 1) : min;
    }

    private void setModuleShift(boolean pressed) {
        boolean physical = ((IKeyBinding) mc.options.keyShift).isActuallyPressed();
        mc.options.keyShift.setDown(pressed || physical);
    }

    private void clearBridgeState() {
        shouldBridge = false;
        releaseAfter = 0;
        setModuleShift(false);
    }

    private void resetState() {
        shouldBridge = false;
        releaseAfter = 0;
        if (mc.options != null) {
            setModuleShift(false);
        }
    }
}
