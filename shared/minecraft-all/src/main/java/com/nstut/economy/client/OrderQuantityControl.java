package com.nstut.economy.client;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.Ui;
import com.nstut.openui.state.ReadableSignal;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Subscription;

import java.util.Objects;
import java.util.function.Function;

/** Reactive quantity editor shared by the new-order screen and edit dialog. */
public final class OrderQuantityControl extends HStack {
    public enum Mode { SELL_QUANTITY, BUY_QUANTITY, BUY_INFINITE }

    private final Signal<String> quantity;
    private final ReadableSignal<Boolean> sellMode;
    private final Signal<Boolean> infinite;
    private final Runnable maximumAction;
    private final String unlimitedLabel;
    private final String maximumLabel;
    private final String infiniteLabel;
    private final Function<Signal<String>, UIComponent> quantityFieldFactory;
    private ButtonWidget infiniteButton;
    private Subscription sellSubscription = Subscription.EMPTY;
    private Subscription infiniteSubscription = Subscription.EMPTY;
    private Mode displayedMode;
    private boolean maximumVisible;
    private boolean quantityInputVisible;
    private boolean unlimitedVisible;

    public OrderQuantityControl(Signal<String> quantity,
                                ReadableSignal<Boolean> sellMode,
                                Signal<Boolean> infinite,
                                Runnable maximumAction,
                                String quantityPlaceholder,
                                String unlimitedLabel,
                                String maximumLabel,
                                String infiniteLabel) {
        this(quantity, sellMode, infinite, maximumAction, quantityPlaceholder, unlimitedLabel,
                maximumLabel, infiniteLabel, value -> Ui.textField(value).placeholder(quantityPlaceholder));
    }

    OrderQuantityControl(Signal<String> quantity,
                         ReadableSignal<Boolean> sellMode,
                         Signal<Boolean> infinite,
                         Runnable maximumAction,
                         String quantityPlaceholder,
                         String unlimitedLabel,
                         String maximumLabel,
                         String infiniteLabel,
                         Function<Signal<String>, UIComponent> quantityFieldFactory) {
        this.quantity = Objects.requireNonNull(quantity);
        this.sellMode = Objects.requireNonNull(sellMode);
        this.infinite = Objects.requireNonNull(infinite);
        this.maximumAction = maximumAction;
        Objects.requireNonNull(quantityPlaceholder);
        this.unlimitedLabel = Objects.requireNonNull(unlimitedLabel);
        this.maximumLabel = Objects.requireNonNull(maximumLabel);
        this.infiniteLabel = Objects.requireNonNull(infiniteLabel);
        this.quantityFieldFactory = Objects.requireNonNull(quantityFieldFactory);
        gap(4);
        refresh();
    }

    @Override
    protected void onMount() {
        sellSubscription = sellMode.subscribe(ignored -> refresh());
        infiniteSubscription = infinite.subscribe(ignored -> refresh());
        refresh();
    }

    @Override
    protected void onUnmount() {
        sellSubscription.close();
        infiniteSubscription.close();
        sellSubscription = Subscription.EMPTY;
        infiniteSubscription = Subscription.EMPTY;
    }

    private void refresh() {
        Mode nextMode = sellMode.get()
                ? Mode.SELL_QUANTITY
                : infinite.get() ? Mode.BUY_INFINITE : Mode.BUY_QUANTITY;
        if (nextMode == displayedMode && childCount() > 0) return;

        displayedMode = nextMode;
        maximumVisible = nextMode == Mode.SELL_QUANTITY && maximumAction != null;
        quantityInputVisible = nextMode != Mode.BUY_INFINITE;
        unlimitedVisible = nextMode == Mode.BUY_INFINITE;
        for (UIComponent child : children()) {
            if (child.isFocused()) child.clearFocus();
        }
        clearChildren();

        UIComponent quantityDisplay;
        if (unlimitedVisible) {
            ButtonWidget unlimitedButton = Ui.button(unlimitedLabel, () -> { }).outline();
            unlimitedButton.enabled(false);
            unlimitedButton.focusable(false);
            unlimitedButton.alignLeft();
            quantityDisplay = unlimitedButton;
        } else {
            quantityDisplay = quantityFieldFactory.apply(quantity);
        }
        quantityDisplay.flex();
        addChild(quantityDisplay);

        if (maximumVisible) {
            addChild(Ui.button(maximumLabel, maximumAction).ghost());
        } else if (nextMode != Mode.SELL_QUANTITY) {
            if (infiniteButton == null) {
                infiniteButton = Ui.button(infiniteLabel, () -> infinite.set(!infinite.get())).ghost();
            }
            infiniteButton.setActive(nextMode == Mode.BUY_INFINITE);
            addChild(infiniteButton);
        }
    }

    public Mode displayedMode() { return displayedMode; }
    public boolean isMaximumVisible() { return maximumVisible; }
    public boolean isQuantityInputVisible() { return quantityInputVisible; }
    public boolean isUnlimitedVisible() { return unlimitedVisible; }
    public boolean isInfiniteActive() { return displayedMode == Mode.BUY_INFINITE; }
}
