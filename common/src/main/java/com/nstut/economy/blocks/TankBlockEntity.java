package com.nstut.economy.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import com.nstut.economy.config.EconomyConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;

@ParametersAreNonnullByDefault
public class TankBlockEntity extends BlockEntity implements WorldlyContainer {

    public enum TankMode {
        BOTH(0, "Both (Input & Output)"),
        INPUT(1, "Input Only (Sell Orders)"),
        OUTPUT(2, "Output Only (Bought Fluids)");

        public final int id;
        public final String displayName;

        TankMode(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public static TankMode byId(int id) {
            for (TankMode m : values()) if (m.id == id) return m;
            return BOTH;
        }

        public boolean canSupplyMarket() {
            return this == BOTH || this == INPUT;
        }

        public boolean canReceiveMarket() {
            return this == BOTH || this == OUTPUT;
        }
    }

    private static final int CONTAINER_SIZE = 1;
    public static final int DEFAULT_CAPACITY = 128000;

    private int capacity = DEFAULT_CAPACITY;
    private FluidStack fluid = FluidStack.EMPTY;
    private UUID owner;
    private TankMode mode = TankMode.BOTH;
    private NonNullList<ItemStack> items;
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public @NotNull FluidStack getFluidInTank(int tank) {
            return tank == 0 ? fluid.copy() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? capacity : 0; }
        @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 && !stack.isEmpty() && (fluid.isEmpty() || fluid.isFluidEqual(stack));
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return fillInternal(resource, action);
        }
        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return drainInternal(resource, action);
        }
        @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return drainInternal(maxDrain, action);
        }
    };
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> fluidHandler);

    IFluidHandler fluidHandlerForTesting() { return fluidHandler; }

    public TankBlockEntity(BlockPos pos, BlockState state) {
        this(BlockRegistries.TANK_BE.get(), pos, state);
    }

    TankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        if (this.fluid.getAmount() > this.capacity) {
            this.fluid.setAmount(this.capacity);
        }
        syncStateToClients();
    }

    public int getCapacity() {
        return capacity;
    }

    public FluidStack getFluid() {
        return fluid;
    }

    public int getFluidAmount() {
        return fluid.getAmount();
    }

    public void setFluid(FluidStack stack) {
        this.fluid = stack.copy();
        if (this.fluid.getAmount() > capacity) {
            this.fluid.setAmount(capacity);
        }
        syncStateToClients();
    }

    public int fill(FluidStack resource) {
        return fillInternal(resource, IFluidHandler.FluidAction.EXECUTE);
    }

    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        return fillInternal(resource, action);
    }

    private int fillInternal(FluidStack resource, IFluidHandler.FluidAction action) {
        if (resource.isEmpty()) return 0;
        if (!fluid.isEmpty() && !fluid.isFluidEqual(resource)) return 0;
        int amount = Math.min(resource.getAmount(), capacity - fluid.getAmount());
        if (amount <= 0 || action.simulate()) return Math.max(0, amount);
        if (fluid.isEmpty()) {
            fluid = resource.copy();
            fluid.setAmount(amount);
            syncStateToClients();
            return amount;
        }
        fluid.grow(amount);
        syncStateToClients();
        return amount;
    }

    public FluidStack drain(int maxDrain) {
        return drainInternal(maxDrain, IFluidHandler.FluidAction.EXECUTE);
    }

    private FluidStack drainInternal(int maxDrain, IFluidHandler.FluidAction action) {
        if (fluid.isEmpty() || maxDrain <= 0) return FluidStack.EMPTY;
        int drained = Math.min(fluid.getAmount(), maxDrain);
        FluidStack result = fluid.copy();
        result.setAmount(drained);
        if (action.execute()) {
            fluid.shrink(drained);
            if (fluid.getAmount() <= 0) fluid = FluidStack.EMPTY;
            syncStateToClients();
        }
        return result;
    }

    public FluidStack drain(FluidStack resource) {
        return drainInternal(resource, IFluidHandler.FluidAction.EXECUTE);
    }

    private FluidStack drainInternal(FluidStack resource, IFluidHandler.FluidAction action) {
        if (resource.isEmpty() || !resource.isFluidEqual(fluid)) return FluidStack.EMPTY;
        return drainInternal(resource.getAmount(), action);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (!EconomyConfig.getInstance().isExternalAutomationAllowed()) {
                return LazyOptional.empty();
            }
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluidCapability = LazyOptional.of(() -> fluidHandler);
    }

    public void handleBucketTransfer() {
        ItemStack bucketStack = items.get(0);
        if (level == null || level.isClientSide || bucketStack.isEmpty()) return;

        net.minecraftforge.fluids.capability.IFluidHandlerItem itemHandler = net.minecraftforge.fluids.FluidUtil.getFluidHandler(bucketStack).orElse(null);
        if (itemHandler == null) return;

        net.minecraftforge.fluids.capability.templates.FluidTank tankHandler =
                new net.minecraftforge.fluids.capability.templates.FluidTank(capacity);
        tankHandler.setFluid(fluid.copy());

        net.minecraftforge.fluids.FluidActionResult emptyResult =
                net.minecraftforge.fluids.FluidUtil.tryEmptyContainer(
                        bucketStack.copy(), tankHandler, Integer.MAX_VALUE, null, true);
        if (emptyResult.isSuccess()) {
            commitContainerTransfer(emptyResult.getResult(), tankHandler.getFluid());
            return;
        }

        if (!fluid.isEmpty()) {
            tankHandler = new net.minecraftforge.fluids.capability.templates.FluidTank(capacity);
            tankHandler.setFluid(fluid.copy());
            net.minecraftforge.fluids.FluidActionResult fillResult =
                    net.minecraftforge.fluids.FluidUtil.tryFillContainer(
                            bucketStack.copy(), tankHandler, Integer.MAX_VALUE, null, true);
            if (fillResult.isSuccess()) {
                commitContainerTransfer(fillResult.getResult(), tankHandler.getFluid());
            }
        }
    }

    private void commitContainerTransfer(ItemStack resultContainer, FluidStack resultingFluid) {
        fluid = resultingFluid.copy();
        items.set(0, resultContainer.copy());
        syncStateToClients();
    }

    private void syncStateToClients() {
        setChanged();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public TankMode getMode() {
        return mode != null ? mode : TankMode.BOTH;
    }

    public void setMode(TankMode mode) {
        this.mode = mode != null ? mode : TankMode.BOTH;
        syncStateToClients();
    }

    public void cycleMode() {
        setMode(TankMode.byId((getMode().id + 1) % TankMode.values().length));
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
        if (level != null && !level.isClientSide) {
            TankManager.register(owner, worldPosition, level.dimension().location().toString());
        }
    }

    @Override
    public int getContainerSize() { return CONTAINER_SIZE; }

    @Override
    public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }

    @Override
    public @NotNull ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
        handleBucketTransfer();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && owner != null) {
            TankManager.register(owner, worldPosition, level.dimension().location().toString());
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && owner != null) {
            TankManager.unregister(owner, worldPosition, level.dimension().location().toString());
        }
        super.setRemoved();
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        fluid = loadFluidFromTag(tag);
        if (tag.contains("Capacity")) {
            capacity = tag.getInt("Capacity");
        }
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
        }
        if (tag.contains("Mode")) {
            mode = TankMode.byId(tag.getInt("Mode"));
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        if (!fluid.isEmpty()) {
            CompoundTag fluidTag = new CompoundTag();
            fluid.writeToNBT(fluidTag);
            tag.put("Fluid", fluidTag);
        }
        tag.putInt("Capacity", capacity);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Mode", getMode().id);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        ContainerHelper.saveAllItems(tag, items);
        if (!fluid.isEmpty()) {
            CompoundTag fluidTag = new CompoundTag();
            fluid.writeToNBT(fluidTag);
            tag.put("Fluid", fluidTag);
        }
        tag.putInt("Capacity", capacity);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Mode", getMode().id);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        fluid = loadFluidFromTag(tag);
        if (tag.contains("Capacity")) {
            capacity = tag.getInt("Capacity");
        }
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
        }
        if (tag.contains("Mode")) {
            mode = TankMode.byId(tag.getInt("Mode"));
        }
    }

    static FluidStack loadFluidFromTag(CompoundTag tag) {
        if (tag != null && tag.contains("Fluid")) {
            return FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid"));
        }
        // Empty update packets omit the Fluid key, so absence must replace any
        // previous client-side value rather than leave a stale snapshot behind.
        return FluidStack.EMPTY;
    }
}
