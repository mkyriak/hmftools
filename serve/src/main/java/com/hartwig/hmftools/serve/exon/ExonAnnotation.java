package com.hartwig.hmftools.serve.exon;

import com.hartwig.hmftools.common.genome.region.GenomeRegion;
import com.hartwig.hmftools.serve.actionability.range.MutationTypeFilter;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class ExonAnnotation implements GenomeRegion {

    @NotNull
    public abstract String gene();

    @NotNull
    public abstract MutationTypeFilter mutationType();

    public abstract int exonIndex();

    @Nullable
    public abstract String exonEnsemblId();
}