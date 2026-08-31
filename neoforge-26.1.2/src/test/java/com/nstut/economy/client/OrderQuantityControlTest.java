package com.nstut.economy.client;

import com.nstut.openui.api.Ui;
import com.nstut.openui.runtime.NativeWidgetHost;
import com.nstut.openui.runtime.UiRuntime;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.state.Subscription;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderQuantityControlTest {
    private final NativeWidgetHost dummyHost = new NativeWidgetHost() {
        @Override public void add(AbstractWidget widget) { }
        @Override public void remove(AbstractWidget widget) { }
    };

    @Test
    void sellBuyInfiniteSellTransitionsShowTheCorrectControls() {
        Signal<String> quantity = Signals.of("64");
        Signal<Boolean> sellMode = Signals.of(true);
        Signal<Boolean> infinite = Signals.of(false);
        Subscription resetInfiniteForSell = sellMode.subscribe(sell -> {
            if (sell) infinite.set(false);
        });
        OrderQuantityControl control = new OrderQuantityControl(
                quantity, sellMode, infinite, () -> quantity.set("128"),
                "Quantity", "∞ Unlimited", "MAX", "∞", ignored -> Ui.spacer());
        UiRuntime runtime = new UiRuntime(testFont(), dummyHost);
        runtime.setRoot(control);

        assertEquals(OrderQuantityControl.Mode.SELL_QUANTITY, control.displayedMode());
        assertTrue(control.isMaximumVisible());
        assertTrue(control.isQuantityInputVisible());
        assertFalse(control.isInfiniteActive());

        sellMode.set(false);
        assertEquals(OrderQuantityControl.Mode.BUY_QUANTITY, control.displayedMode());
        assertFalse(control.isMaximumVisible());
        assertTrue(control.isQuantityInputVisible());
        assertFalse(control.isInfiniteActive());

        infinite.set(true);
        assertEquals(OrderQuantityControl.Mode.BUY_INFINITE, control.displayedMode());
        assertFalse(control.isMaximumVisible());
        assertFalse(control.isQuantityInputVisible());
        assertTrue(control.isUnlimitedVisible());
        assertTrue(control.isInfiniteActive());

        sellMode.set(true);
        assertEquals(OrderQuantityControl.Mode.SELL_QUANTITY, control.displayedMode());
        assertTrue(control.isMaximumVisible());
        assertTrue(control.isQuantityInputVisible());
        assertFalse(infinite.get());

        resetInfiniteForSell.close();
        runtime.close();
    }

    private static Font testFont() {
        try {
            for (Constructor<?> constructor : Font.class.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 1) {
                    return (Font) constructor.newInstance(new Object[]{null});
                }
                if (constructor.getParameterCount() == 2) {
                    return (Font) constructor.newInstance(null, false);
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to construct test font", exception);
        }
        throw new AssertionError("Unsupported Font constructor");
    }
}
