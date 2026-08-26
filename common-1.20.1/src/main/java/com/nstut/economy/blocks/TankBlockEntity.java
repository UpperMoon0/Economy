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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.nstut.economy.trading.EconomyFluidStack;
import com.nstut.economy.config.EconomyConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


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
    private EconomyFluidStack fluid = EconomyFluidStack.EMPTY;
    private UUID owner;
    private TankMode mode = TankMode.BOTH;
    private NonNullList<ItemStack> items;

    public TankBlockEntity(BlockPos pos, BlockState state) {
        this(BlockRegistries.TANK_BE != null ? BlockRegistries.TANK_BE.get() : null, pos, state);
    }

    public TankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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

    public EconomyFluidStack getFluid() {
        return fluid;
    }

    public int getFluidAmount() {
        return fluid.getAmount();
    }

    public void setFluid(EconomyFluidStack stack) {
        this.fluid = stack.copy();
        if (this.fluid.getAmount() > capacity) {
            this.fluid.setAmount(capacity);
        }
        syncStateToClients();
    }

    public int fill(EconomyFluidStack resource) {
        return fillInternal(resource, false);
    }

    /**
     * Simulates filling without mutating the tank. Mirrors the per-tank match
     * rules of {@link #fill(EconomyFluidStack)}.
     */
    public int simulateFill(EconomyFluidStack resource) {
        return fillInternal(resource, true);
    }

    private int fillInternal(EconomyFluidStack resource, boolean simulate) {
        if (resource.isEmpty()) return 0;
        if (!fluid.isEmpty() && !fluid.isFluidEqual(resource)) return 0;
        int amount = Math.min(resource.getAmount(), capacity - fluid.getAmount());
        if (amount <= 0 || simulate) return Math.max(0, amount);
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

    public EconomyFluidStack drain(int maxDrain) {
        if (fluid.isEmpty() || maxDrain <= 0) return EconomyFluidStack.EMPTY;
        int drained = Math.min(fluid.getAmount(), maxDrain);
        EconomyFluidStack result = fluid.copy();
        result.setAmount(drained);
        fluid.shrink(drained);
        if (fluid.getAmount() <= 0) fluid = EconomyFluidStack.EMPTY;
        syncStateToClients();
        return result;
    }

    public EconomyFluidStack drain(EconomyFluidStack resource) {
        if (resource.isEmpty() || !resource.isFluidEqual(fluid)) return EconomyFluidStack.EMPTY;
        return drain(resource.getAmount());
    }

    public void handleBucketTransfer() {
        ItemStack bucketStack = items.get(0);
        if (level == null || level.isClientSide || bucketStack.isEmpty()) return;

        var emptyResult = com.nstut.economy.platform.Services.FLUID.tryEmptyContainerIntoTank(
                bucketStack.copy(), capacity, fluid.copy());
        if (emptyResult.isPresent()) {
            commitContainerTransfer(emptyResult.get().resultContainer(), emptyResult.get().resultTankFluid());
            return;
        }

        if (!fluid.isEmpty()) {
            var fillResult = com.nstut.economy.platform.Services.FLUID.tryFillContainerFromTank(
                    bucketStack.copy(), capacity, fluid.copy());
            if (fillResult.isPresent()) {
                commitContainerTransfer(fillResult.get().resultContainer(), fillResult.get().resultTankFluid());
            }
        }
    }

    private void commitContainerTransfer(ItemStack resultContainer, EconomyFluidStack resultingFluid) {
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
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide && owner != null) {
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
            fluid.writeTo(fluidTag);
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
            fluid.writeTo(fluidTag);
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

    static EconomyFluidStack loadFluidFromTag(CompoundTag tag) {
        if (tag != null && tag.contains("Fluid")) {
            return EconomyFluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid"));
        }
        // Empty update packets omit the Fluid key, so absence must replace any
        // previous client-side value rather than leave a stale snapshot behind.
        return EconomyFluidStack.EMPTY;
    }
}
