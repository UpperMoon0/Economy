package example.economyaddon.v013;

import com.nstut.economy.api.EconomyEvents;

/** Consumer bytecode intentionally compiled against the Economy 0.0.13 API baseline. */
public final class Legacy013EventAddon {
    private Legacy013EventAddon() {}

    public static void main(String[] args) {
        EconomyEvents.clearListeners();
    }
}
