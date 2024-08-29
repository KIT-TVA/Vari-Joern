package edu.kit.varijoern.composers.kbuild;

import superc.core.PresenceConditionManager;

/**
 * A {@link PresenceConditionManager} that can be can free the {@link com.microsoft.z3.Context} it uses.
 */
public class CloseablePresenceConditionManager extends PresenceConditionManager implements AutoCloseable {
    /**
     * Creates a new {@link CloseablePresenceConditionManager}.
     */
    public CloseablePresenceConditionManager() {
        super();
    }

    @Override
    public void close() {
        this.ctx.close();
    }
}
