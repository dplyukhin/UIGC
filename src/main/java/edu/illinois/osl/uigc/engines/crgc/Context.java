package edu.illinois.osl.uigc.engines.crgc;

import com.typesafe.config.Config;

/**
 * A data structure for quickly fetching CRGC-specific configuration options.
 */
public class Context {
    final public short DeltaGraphSize;
    final public int EntryFieldSize;

    public Context(Config config) {
        DeltaGraphSize = (short) config.getInt("uigc.crgc.delta-graph-size");
        EntryFieldSize = config.getInt("uigc.crgc.entry-field-size");
    }
}
