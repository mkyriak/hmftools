package com.hartwig.hmftools.vicc.datamodel;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class BRCA {

    @NotNull
    public abstract BRCApart1 brcApart1();

    @NotNull
    public abstract BRCApart2 brcApart2();

}
