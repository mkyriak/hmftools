package com.hartwig.hmftools.common.drivercatalog;

import static com.hartwig.hmftools.common.drivercatalog.DriverCatalogFactory.variantTypeCount;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.dnds.DndsDriverGeneLikelihood;
import com.hartwig.hmftools.common.dnds.DndsDriverGeneLikelihoodSupplier;
import com.hartwig.hmftools.common.dnds.DndsDriverImpactLikelihood;
import com.hartwig.hmftools.common.numeric.Doubles;
import com.hartwig.hmftools.common.variant.CodingEffect;
import com.hartwig.hmftools.common.variant.EnrichedSomaticVariant;
import com.hartwig.hmftools.common.variant.Hotspot;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.VariantType;

import org.jetbrains.annotations.NotNull;

public final class TsgDrivers {

    private TsgDrivers() {
    }

    @NotNull
    public static List<DriverCatalog> drivers(@NotNull final List<EnrichedSomaticVariant> variants) {
        return drivers(DndsDriverGeneLikelihoodSupplier.tsgLikelihood(), variants);
    }

    @NotNull
    private static List<DriverCatalog> drivers(@NotNull final Map<String, DndsDriverGeneLikelihood> likelihoodsByGene,
            @NotNull final List<EnrichedSomaticVariant> variants) {
        final List<DriverCatalog> driverCatalog = Lists.newArrayList();

        final Map<VariantType, Long> variantTypeCounts = variantTypeCount(variants);
        final Map<VariantType, Long> variantTypeCountsBiallelic = variantTypeCount(SomaticVariant::biallelic, variants);
        final Map<VariantType, Long> variantTypeCountsNonBiallelic = variantTypeCount(x -> !x.biallelic(), variants);

        final Map<String, List<EnrichedSomaticVariant>> codingVariants = codingVariantsByGene(likelihoodsByGene.keySet(), variants);

        for (String gene : codingVariants.keySet()) {
            final DndsDriverGeneLikelihood likelihood = likelihoodsByGene.get(gene);

            final List<EnrichedSomaticVariant> geneVariants = codingVariants.get(gene);
            driverCatalog.add(geneDriver(likelihood,
                    geneVariants,
                    variantTypeCounts,
                    variantTypeCountsBiallelic,
                    variantTypeCountsNonBiallelic));
        }

        return driverCatalog;
    }

    @NotNull
    static DriverCatalog geneDriver(@NotNull final DndsDriverGeneLikelihood likelihood,
            @NotNull final List<EnrichedSomaticVariant> geneVariants, @NotNull final Map<VariantType, Long> standardCounts,
            @NotNull final Map<VariantType, Long> biallelicCounts, @NotNull final Map<VariantType, Long> nonBiallelicCounts) {
        geneVariants.sort(new TsgImpactComparator());

        final Map<DriverImpact, Long> variantCounts = DriverCatalogFactory.driverImpactCount(geneVariants);
        long missenseVariants = variantCounts.getOrDefault(DriverImpact.MISSENSE, 0L);
        long nonsenseVariants = variantCounts.getOrDefault(DriverImpact.NONSENSE, 0L);
        long spliceVariants = variantCounts.getOrDefault(DriverImpact.SPLICE, 0L);
        long inframeVariants = variantCounts.getOrDefault(DriverImpact.INFRAME, 0L);
        long frameshiftVariants = variantCounts.getOrDefault(DriverImpact.FRAMESHIFT, 0L);

        final double maxDndsLikelihood = geneVariants.stream()
                .map(x -> impactLikelihood(likelihood, x))
                .mapToDouble(DndsDriverImpactLikelihood::dndsLikelihood)
                .max()
                .orElse(0);

        final ImmutableDriverCatalog.Builder builder = ImmutableDriverCatalog.builder()
                .gene(likelihood.gene())
                .driver(DriverType.MUTATION)
                .category(DriverCategory.TSG)
                .driverLikelihood(1)
                .dndsLikelihood(maxDndsLikelihood)
                .missense(missenseVariants)
                .nonsense(nonsenseVariants)
                .splice(spliceVariants)
                .inframe(inframeVariants)
                .frameshift(frameshiftVariants)
                .biallelic(geneVariants.stream().anyMatch(SomaticVariant::biallelic))
                .likelihoodMethod(LikelihoodMethod.DNDS);

        if (geneVariants.stream().anyMatch(SomaticVariant::isHotspot)) {
            return builder.likelihoodMethod(LikelihoodMethod.HOTSPOT).build();
        }

        if (geneVariants.stream().anyMatch(x -> x.biallelic() && !DriverImpact.isMissense(x))) {
            return builder.likelihoodMethod(LikelihoodMethod.BIALLELIC).build();
        }

        final DndsDriverImpactLikelihood firstImpactLikelihood = impactLikelihood(likelihood, geneVariants.get(0));
        final long firstVariantTypeCount =
                variantCount(likelihood.useBiallelic(), geneVariants.get(0), standardCounts, biallelicCounts, nonBiallelicCounts);

        if (geneVariants.size() == 1) {
            return builder.dndsLikelihood(firstImpactLikelihood.dndsLikelihood())
                    .driverLikelihood(singleHit(firstVariantTypeCount, firstImpactLikelihood))
                    .build();
        }

        // MultiHit
        final DndsDriverImpactLikelihood secondImpactLikelihood = impactLikelihood(likelihood, geneVariants.get(1));
        final long secondVariantTypeCount =
                variantCount(likelihood.useBiallelic(), geneVariants.get(1), standardCounts, biallelicCounts, nonBiallelicCounts);

        return builder.dndsLikelihood(Math.max(firstImpactLikelihood.dndsLikelihood(), secondImpactLikelihood.dndsLikelihood()))
                .driverLikelihood(DriverCatalogFactory.probabilityDriverVariant(firstVariantTypeCount,
                        secondVariantTypeCount,
                        firstImpactLikelihood,
                        secondImpactLikelihood))
                .build();
    }

    @NotNull
    private static DndsDriverImpactLikelihood impactLikelihood(@NotNull final DndsDriverGeneLikelihood dndsLikelihood,
            @NotNull final SomaticVariant variant) {
        if (variant.type() == VariantType.INDEL) {
            return dndsLikelihood.indel();
        } else if (variant.canonicalCodingEffect() == CodingEffect.MISSENSE) {
            return dndsLikelihood.missense();
        } else if (variant.canonicalCodingEffect() == CodingEffect.NONSENSE_OR_FRAMESHIFT) {
            return dndsLikelihood.nonsense();
        }
        return dndsLikelihood.splice();
    }

    private static double singleHit(long sampleCount, @NotNull final DndsDriverImpactLikelihood likelihood) {
        return Doubles.positive(likelihood.dndsLikelihood()) ? DriverCatalogFactory.probabilityDriverVariant(sampleCount, likelihood) : 0;
    }

    @NotNull
    private static <T extends SomaticVariant> Map<String, List<T>> codingVariantsByGene(@NotNull final Set<String> genes,
            @NotNull final List<T> variants) {
        Set<CodingEffect> suitableCodingEffects =
                EnumSet.of(CodingEffect.MISSENSE, CodingEffect.NONSENSE_OR_FRAMESHIFT, CodingEffect.SPLICE);

        return variants.stream()
                .filter(x -> genes.contains(x.gene()))
                .filter(x -> suitableCodingEffects.contains(x.canonicalCodingEffect()) || x.hotspot() == Hotspot.HOTSPOT)
                .collect(Collectors.groupingBy(SomaticVariant::gene));
    }

    private static long variantCount(boolean useBiallelic, @NotNull final SomaticVariant variant,
            @NotNull final Map<VariantType, Long> standard, @NotNull final Map<VariantType, Long> biallelic,
            @NotNull final Map<VariantType, Long> nonBiallelic) {
        final Map<VariantType, Long> map;
        if (!useBiallelic) {
            map = standard;
        } else if (variant.biallelic()) {
            map = biallelic;
        } else {
            map = nonBiallelic;
        }

        return variant.type() == VariantType.INDEL ? map.getOrDefault(VariantType.INDEL, 0L) : map.getOrDefault(VariantType.SNP, 0L);
    }

}
