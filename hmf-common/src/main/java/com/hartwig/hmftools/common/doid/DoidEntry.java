package com.hartwig.hmftools.common.doid;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class DoidEntry {

    @NotNull
    public abstract String doid();

    @NotNull
    public abstract String url();

    @Nullable
    public abstract String doidTerm();

    @Nullable
    public abstract String type();

    @Nullable
    public abstract DoidMetadata doidMetadata();

    @Nullable
    public abstract DoidNodes doidNodes();

}