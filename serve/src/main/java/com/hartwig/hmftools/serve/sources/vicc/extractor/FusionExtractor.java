package com.hartwig.hmftools.serve.sources.vicc.extractor;

import static com.hartwig.hmftools.serve.fusion.FusionAnnotationConfig.EXONIC_FUSIONS_MAP;
import static com.hartwig.hmftools.serve.fusion.FusionAnnotationConfig.ODDLY_NAMED_GENES_MAP;

import java.util.Map;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.serve.classification.MutationType;
import com.hartwig.hmftools.serve.fusion.ImmutableKnownFusionPair;
import com.hartwig.hmftools.serve.fusion.KnownFusionPair;
import com.hartwig.hmftools.serve.sources.vicc.ViccUtil;
import com.hartwig.hmftools.serve.sources.vicc.check.GeneChecker;
import com.hartwig.hmftools.vicc.datamodel.Feature;
import com.hartwig.hmftools.vicc.datamodel.ViccEntry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class FusionExtractor {

    private static final Logger LOGGER = LogManager.getLogger(FusionExtractor.class);

    @NotNull
    private final GeneChecker geneChecker;

    public FusionExtractor(@NotNull final GeneChecker geneChecker) {
        this.geneChecker = geneChecker;
    }

    @NotNull
    public Map<Feature, KnownFusionPair> extract(@NotNull ViccEntry viccEntry) {
        Map<Feature, KnownFusionPair> fusionsPerFeature = Maps.newHashMap();

        ImmutableKnownFusionPair.Builder fusionBuilder =
                ImmutableKnownFusionPair.builder().addSources(ViccUtil.toKnowledgebase(viccEntry.source()));
        for (Feature feature : viccEntry.features()) {
            String fusion = feature.name();

            if (feature.type() == MutationType.FUSION_PAIR) {
                KnownFusionPair annotatedFusion;
                if (EXONIC_FUSIONS_MAP.containsKey(fusion)) {
                    annotatedFusion = fusionBuilder.from(EXONIC_FUSIONS_MAP.get(fusion)).build();
                } else if (ODDLY_NAMED_GENES_MAP.containsKey(fusion)) {
                    annotatedFusion = fusionBuilder.from(ODDLY_NAMED_GENES_MAP.get(fusion)).build();
                } else {
                    String[] fusionArray = fusion.split("-");
                    annotatedFusion = fusionBuilder.geneUp(fusionArray[0]).geneDown(fusionArray[1].split(" ")[0]).build();
                }

                if (geneChecker.isValidGene(annotatedFusion.geneUp()) && geneChecker.isValidGene(annotatedFusion.geneDown())) {
                    fusionsPerFeature.put(feature, annotatedFusion);
                }
            } else if (feature.type() == MutationType.FUSION_PAIR_AND_EXON) {
                if (EXONIC_FUSIONS_MAP.containsKey(fusion)) {
                    KnownFusionPair fusionPair = fusionBuilder.from(EXONIC_FUSIONS_MAP.get(fusion)).build();
                    if (fusionPair.geneUp().equals(feature.geneSymbol()) && fusionPair.geneDown().equals(feature.geneSymbol())) {
                        fusionsPerFeature.put(feature, fusionPair);
                    } else {
                        LOGGER.warn("Configured fusion for '{}' on '{}' does not match in terms of genes!", fusion, feature.geneSymbol());
                    }
                } else {
                    LOGGER.warn("Exonic fusion not configured for '{}' on '{}'", fusion, feature.geneSymbol());
                }

            }
        }
        return fusionsPerFeature;
    }
}
