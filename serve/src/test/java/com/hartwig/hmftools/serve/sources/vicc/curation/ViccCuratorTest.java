package com.hartwig.hmftools.serve.sources.vicc.curation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.hartwig.hmftools.serve.sources.vicc.ViccTestFactory;
import com.hartwig.hmftools.vicc.datamodel.Feature;
import com.hartwig.hmftools.vicc.datamodel.ViccEntry;
import com.hartwig.hmftools.vicc.datamodel.ViccSource;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ViccCuratorTest {

    @Test
    public void canCurateFeatures() {
        CurationKey firstOncoKbKey = firstOncoKbMappingKey();
        String firstMappedFeature = CurationFactory.FEATURE_MAPPINGS.get(firstOncoKbKey).featureName();

        ViccEntry entry = ViccTestFactory.testEntryWithSourceAndTranscript(ViccSource.ONCOKB, firstOncoKbKey.transcript());

        Feature feature = ViccTestFactory.testFeatureWithGeneAndName(firstOncoKbKey.gene(), firstOncoKbKey.featureName());

        assertEquals(firstMappedFeature, new ViccCurator().curate(entry, feature).name());
    }

    @Test
    public void canBlacklistFeatures() {
        CurationKey firstOncoKbKey = firstOncoKbBlacklistKey();
        ViccEntry entry = ViccTestFactory.testEntryWithSourceAndTranscript(ViccSource.ONCOKB, firstOncoKbKey.transcript());

        Feature feature = ViccTestFactory.testFeatureWithGeneAndName(firstOncoKbKey.gene(), firstOncoKbKey.featureName());
        assertNull(new ViccCurator().curate(entry, feature));
    }

    @Test
    public void canKeepTrackOfFeatures() {
        ViccCurator curator = new ViccCurator();

        ViccEntry entry = ViccTestFactory.testEntryWithSourceAndTranscript(ViccSource.ONCOKB, "any");
        Feature feature = ViccTestFactory.testFeatureWithGeneAndName("any", "any");

        assertNotNull(curator.curate(entry, feature));

        CurationKey blacklistKey = firstOncoKbBlacklistKey();
        ViccEntry blacklistEntry = ViccTestFactory.testEntryWithSourceAndTranscript(ViccSource.ONCOKB, blacklistKey.transcript());

        Feature blacklistFeature = ViccTestFactory.testFeatureWithGeneAndName(blacklistKey.gene(), blacklistKey.featureName());

        assertNull(curator.curate(blacklistEntry, blacklistFeature));

        curator.reportUnusedCurationEntries();
    }

    @NotNull
    private static CurationKey firstOncoKbMappingKey() {
        for (CurationKey key : CurationFactory.FEATURE_MAPPINGS.keySet()) {
            if (key.source() == ViccSource.ONCOKB) {
                return key;
            }
        }
        throw new IllegalStateException("No OncoKB mapping keys found!");
    }

    @NotNull
    private static CurationKey firstOncoKbBlacklistKey() {
        for (CurationKey key : CurationFactory.FEATURE_BLACKLIST) {
            if (key.source() == ViccSource.ONCOKB) {
                return key;
            }
        }
        throw new IllegalStateException("No OncoKB blacklist keys found!");
    }
}