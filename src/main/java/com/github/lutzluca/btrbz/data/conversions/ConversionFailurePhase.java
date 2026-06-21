package com.github.lutzluca.btrbz.data.conversions;

public enum ConversionFailurePhase {
    LoadLocalCache,
    LoadBundledSeed,
    HypixelBazaar,
    HypixelItems,
    NeuCommit,
    NeuZip,
    Parse,
    Validate,
    Persist
}
