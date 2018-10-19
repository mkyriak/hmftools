package com.hartwig.hmftools.purple;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.amber.AmberBAF;
import com.hartwig.hmftools.common.chromosome.Chromosome;
import com.hartwig.hmftools.common.chromosome.ChromosomeLength;
import com.hartwig.hmftools.common.cobalt.CobaltRatio;
import com.hartwig.hmftools.common.gc.GCProfile;
import com.hartwig.hmftools.common.gc.GCProfileFactory;
import com.hartwig.hmftools.common.pcf.PCFPosition;
import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.purple.region.ObservedRegion;
import com.hartwig.hmftools.common.purple.region.ObservedRegionFactory;
import com.hartwig.hmftools.common.purple.segment.Cluster;
import com.hartwig.hmftools.common.purple.segment.ClusterFactory;
import com.hartwig.hmftools.common.purple.segment.PurpleSegment;
import com.hartwig.hmftools.common.purple.segment.PurpleSegmentFactory;
import com.hartwig.hmftools.common.variant.structural.StructuralVariant;
import com.hartwig.hmftools.purple.config.CommonConfig;
import com.hartwig.hmftools.purple.config.ConfigSupplier;
import com.hartwig.hmftools.purple.ratio.ChromosomeLengthSupplier;
import com.hartwig.hmftools.purple.segment.PCFPositionsSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

class Segmentation {

    private static final Logger LOGGER = LogManager.getLogger(Segmentation.class);

    private final CommonConfig config;
    private final Gender gender;
    private final Multimap<Chromosome, AmberBAF> bafs;
    private final Map<Chromosome, ChromosomeLength> lengths;
    private final Multimap<String, PCFPosition> pcfPositions;
    private final Multimap<Chromosome, GCProfile> gcProfiles;
    private final ListMultimap<Chromosome, CobaltRatio> ratios;

    public Segmentation(@NotNull final ConfigSupplier configSupplier, @NotNull final Gender gender,
            @NotNull final ListMultimap<Chromosome, CobaltRatio> ratios, @NotNull final Multimap<Chromosome, AmberBAF> bafs) throws IOException {
        this.config = configSupplier.commonConfig();
        this.gender = gender;
        this.ratios = ratios;
        this.bafs = bafs;
        this.lengths = new ChromosomeLengthSupplier(config, ratios).get();
        this.pcfPositions = PCFPositionsSupplier.createPositions(configSupplier);

        LOGGER.info("Reading GC Profiles from {}", config.gcProfile());
        this.gcProfiles = GCProfileFactory.loadGCContent(config.windowSize(), config.gcProfile());

    }

    @NotNull
    public List<ObservedRegion> createSegments(@NotNull final List<StructuralVariant> structuralVariants) {
        final Multimap<String, Cluster> clusterMap =
                new ClusterFactory(config.windowSize()).cluster(structuralVariants, pcfPositions, ratios);
        final List<PurpleSegment> segments = PurpleSegmentFactory.segment(clusterMap, lengths);

        final ObservedRegionFactory observedRegionFactory = new ObservedRegionFactory(config.windowSize(), gender);
        return observedRegionFactory.combine(segments, bafs, ratios, gcProfiles);
    }
}
