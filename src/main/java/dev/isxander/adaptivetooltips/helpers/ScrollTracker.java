package dev.isxander.adaptivetooltips.helpers;

import dev.isxander.adaptivetooltips.config.AdaptiveTooltipConfig;
import dev.isxander.adaptivetooltips.config.ScrollDirection;
import dev.isxander.adaptivetooltips.mixins.BundleTooltipComponentAccessor;
import dev.isxander.adaptivetooltips.mixins.OrderedTextTooltipComponentAccessor;
import dev.isxander.adaptivetooltips.utils.TextUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.BundleTooltipComponent;
import net.minecraft.client.gui.tooltip.OrderedTextTooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.util.Iterator;
import java.util.List;

public class ScrollTracker {
    private static int targetVerticalScroll = 0;
    private static int targetHorizontalScroll = 0;

    private static float currentVerticalScroll = 0f;
    private static float currentHorizontalScroll = 0f;

    private static List<TooltipComponent> trackedComponents = null;
    public static boolean renderedThisFrame = false;

    public static void addVerticalScroll(int amt) {
        if (AdaptiveTooltipConfig.INSTANCE.getConfig().scrollDirection == ScrollDirection.NATURAL)
            amt = -amt;
        targetVerticalScroll += amt * AdaptiveTooltipConfig.INSTANCE.getConfig().verticalScrollSensitivity;
    }

    public static void addHorizontalScroll(int amt) {
        if (AdaptiveTooltipConfig.INSTANCE.getConfig().scrollDirection == ScrollDirection.NATURAL)
            amt = -amt;
        targetHorizontalScroll += amt * AdaptiveTooltipConfig.INSTANCE.getConfig().horizontalScrollSensitivity;
    }

    public static float getVerticalScroll() {
        return currentVerticalScroll;
    }

    public static float getHorizontalScroll() {
        return currentHorizontalScroll;
    }

    public static void scroll(MatrixStack matrices, List<TooltipComponent> components, int x, int y, int width, int height, int screenWidth, int screenHeight) {
        tick(components, x, y, width, height, screenWidth, screenHeight, MinecraftClient.getInstance().getLastFrameDuration());

        // have to use a translate rather than moving the tooltip's x and y because int precision is too jittery
        matrices.translate(ScrollTracker.getHorizontalScroll(), ScrollTracker.getVerticalScroll(), 0);
    }

    private static void tick(List<TooltipComponent> components, int x, int y, int width, int height, int screenWidth, int screenHeight, float tickDelta) {
        renderedThisFrame = true;

        resetIfNeeded(components);

        // prevent scrolling if not needed, required for clamping to work without breaking every tooltip
        if (height < screenHeight)
            targetVerticalScroll = 0;
        if (width < screenWidth)
            targetHorizontalScroll = 0;

        // prevents scrolling too far up/down
        targetVerticalScroll = MathHelper.clamp(targetVerticalScroll, Math.min(screenHeight - (y + height) - 4, 0), Math.max(-y + 4, 0));
        targetHorizontalScroll = MathHelper.clamp(targetHorizontalScroll, Math.min(screenWidth - (x + width) - 4, 0), Math.max(-x + 4, 0));

        tickAnimation(tickDelta);
    }

    private static void tickAnimation(float tickDelta) {
        if (AdaptiveTooltipConfig.INSTANCE.getConfig().smoothScrolling) {
            currentVerticalScroll = MathHelper.lerp(tickDelta * 0.5f, currentVerticalScroll, targetVerticalScroll);
            currentHorizontalScroll = MathHelper.lerp(tickDelta * 0.5f, currentHorizontalScroll, targetHorizontalScroll);
        } else {
            currentVerticalScroll = targetVerticalScroll;
            currentHorizontalScroll = targetHorizontalScroll;
        }
    }

    private static void resetIfNeeded(List<TooltipComponent> components) {
        // if not the same component as last frame, reset the scrolling.
        if (!isEqual(components, trackedComponents)) {
            reset();
        }

        trackedComponents = components;
    }

    public static void reset() {
        targetVerticalScroll = targetHorizontalScroll = 0;
        // need to also reset the animation as it is funky upon next render
        currentVerticalScroll = currentHorizontalScroll = 0;
    }

    private static boolean isEqual(List<TooltipComponent> l1, List<TooltipComponent> l2) {
        if (l1 == null || l2 == null)
            return false;

        Iterator<TooltipComponent> iter1 = l1.iterator();
        Iterator<TooltipComponent> iter2 = l2.iterator();

        // loop through both lists until either ends
        while (iter1.hasNext() && iter2.hasNext()) {
            TooltipComponent c1 = iter1.next();
            TooltipComponent c2 = iter2.next();

            // if the components are same instance, they are the same, go to next element
            if (c1 == c2) continue;

            // no abstract way of comparing tooltip components so we have to check what implementation they are
            if (c1 instanceof OrderedTextTooltipComponent ot1 && c2 instanceof OrderedTextTooltipComponent ot2) {
                // OrderedText cannot be compared, MutableText can
                if (!TextUtil.toText(((OrderedTextTooltipComponentAccessor) ot1).getText()).equals(TextUtil.toText(((OrderedTextTooltipComponentAccessor) ot2).getText())))
                    return false;
            } else if (c1 instanceof BundleTooltipComponent bt1 && c2 instanceof BundleTooltipComponent bt2) {
                // gets the inventory of each bundle and loops through each stack
                
                Iterator<ItemStack> i1 = ((BundleTooltipComponentAccessor) bt1).getInventory().iterator();
                Iterator<ItemStack> i2 = ((BundleTooltipComponentAccessor) bt2).getInventory().iterator();

                // iterate through both bundle inventories until either runs out
                while (i1.hasNext() && i2.hasNext()) {
                    ItemStack stack1 = i1.next();
                    ItemStack stack2 = i2.next();

                    if (!ItemStack.areEqual(stack1, stack2))
                        return false;
                }
                
                // if either inventory has more items, we know they are not the same inventory
                if (i1.hasNext() || i2.hasNext())
                    return false;
            } else {
                // no other vanilla implementations of TooltipComponent or the two components are different to eachother
                return false;
            }
        }

        return !(iter1.hasNext() || iter2.hasNext());
    }
}
