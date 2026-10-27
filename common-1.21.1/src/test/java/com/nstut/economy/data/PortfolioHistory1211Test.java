package com.nstut.economy.data;

import net.minecraft.nbt.CompoundTag;

class PortfolioHistory1211Test extends PortfolioHistoryContract {
    @Override protected EconomyAccountData roundTrip(EconomyAccountData data) {
        return load(data.save(new CompoundTag(), null));
    }
    @Override protected EconomyAccountData load(CompoundTag encoded) {
        return EconomyAccountData.load(encoded, null);
    }
}
