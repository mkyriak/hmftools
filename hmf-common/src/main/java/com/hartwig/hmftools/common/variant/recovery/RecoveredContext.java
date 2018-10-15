package com.hartwig.hmftools.common.variant.recovery;

import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumber;
import com.hartwig.hmftools.common.variant.structural.StructuralVariant;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import htsjdk.variant.variantcontext.VariantContext;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public interface RecoveredContext {

    @NotNull
    PurpleCopyNumber copyNumber();

    @NotNull
    PurpleCopyNumber prevCopyNumber();

    @NotNull
    VariantContext context();

    @Nullable
    VariantContext mate();

    @Nullable
    PurpleCopyNumber mateCopyNumber();

    @NotNull
    StructuralVariant variant();
}
