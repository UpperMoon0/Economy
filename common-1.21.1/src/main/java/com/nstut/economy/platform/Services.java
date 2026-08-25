package com.nstut.economy.platform;

import com.nstut.economy.platform.services.IFluidHelper;
import com.nstut.economy.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Service provider for platform-specific implementations.
 */
public class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    public static final IFluidHelper FLUID = load(IFluidHelper.class);

    public static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
    }
}
