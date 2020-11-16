package com.hartwig.hmftools.patientdb.data;

import java.util.List;

import com.hartwig.hmftools.common.doid.DoidNode;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class CuratedPrimaryTumor {

    @Nullable
    public abstract String searchTerm();

    @Nullable
    public abstract String primaryTumorLocation();

    @Nullable
    public abstract String primaryTumorSubLocation();

    @Nullable
    public abstract String primaryTumorType();

    @Nullable
    public abstract String primaryTumorSubType();

    @Nullable
    public abstract String primaryTumorExtraDetails();

    @Nullable
    public abstract List<DoidNode> doidNodes();

}