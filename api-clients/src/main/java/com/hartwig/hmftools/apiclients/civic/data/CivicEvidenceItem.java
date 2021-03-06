package com.hartwig.hmftools.apiclients.civic.data;

import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Gson.TypeAdapters
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class CivicEvidenceItem {
    public abstract int id();

    public abstract String name();

    public abstract String description();

    public abstract CivicDisease disease();

    public abstract List<CivicDrug> drugs();

    @SerializedName("evidence_level")
    public abstract Character level();

    @SerializedName("evidence_type")
    public abstract String evidenceType();

    @Nullable
    @SerializedName("clinical_significance")
    public abstract String significance();

    @Nullable
    @SerializedName("evidence_direction")
    public abstract String direction();

    @Nullable
    @SerializedName("variant_origin")
    public abstract String variantOrigin();

    @Nullable
    @SerializedName("drug_interaction_type")
    public abstract String drugInteractionType();

    public abstract String status();

    public abstract CivicEvidenceSource source();

    @Override
    public String toString() {
        return name() + "[" + level() + "]: " + direction() + " " + significance() + " to " + drugs();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof CivicEvidenceItem) {
            final CivicEvidenceItem other = (CivicEvidenceItem) obj;
            return other.id() == id();
        }
        return false;
    }

    @NotNull
    @Value.Derived
    public List<String> drugNames() {
        final String drugInteractionType = drugInteractionType();
        if (drugInteractionType != null && drugInteractionType.toLowerCase().equals("combination")) {
            return Lists.newArrayList(drugs().stream().map(CivicDrug::name).sorted().collect(joining(", ")) + " (Combination)");
        } else {
            return drugs().stream().map(CivicDrug::name).sorted().collect(Collectors.toList());
        }
    }
}
