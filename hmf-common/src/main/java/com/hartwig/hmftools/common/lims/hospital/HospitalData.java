package com.hartwig.hmftools.common.lims.hospital;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class HospitalData {

    // A PI is generally only defined in context of a study (CPCT / DRUP etc). Otherwise NOT_AVAILABLE
    @NotNull
    public abstract String hospitalPI();

    // A requester is generally only defined in context of a specific submission (CORE etc). Otherwise NOT_AVAILABLE
    @NotNull
    public abstract String requesterName();

    // A requester is generally only defined in context of a specific submission (CORE etc). Otherwise NOT_AVAILABLE
    @NotNull
    public abstract String requesterEmail();

    @NotNull
    public abstract String hospitalName();

    @NotNull
    public abstract String hospitalAddress();

}
