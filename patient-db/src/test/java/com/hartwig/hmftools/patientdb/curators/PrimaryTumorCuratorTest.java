package com.hartwig.hmftools.patientdb.curators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.hartwig.hmftools.common.doid.DiseaseOntology;
import com.hartwig.hmftools.common.doid.DoidNode;
import com.hartwig.hmftools.patientdb.data.CuratedPrimaryTumor;

import org.junit.Test;

public class PrimaryTumorCuratorTest {

    private static final String DOID_FILE_JSON = Resources.getResource("doid/example_doid.json").getPath();

    @Test
    public void canDetermineUnusedTerms() {
        PrimaryTumorCurator curator = TestCuratorFactory.primaryTumorCurator();
        assertEquals(5, curator.unusedSearchTerms().size());

        curator.search("desmoïd tumor");
        assertEquals(4, curator.unusedSearchTerms().size());
    }

    @Test
    public void canCurateDesmoidTumor() {
        // See DEV-275
        PrimaryTumorCurator curator = TestCuratorFactory.primaryTumorCurator();
        String desmoidTumor = "desmoïd tumor";
        CuratedPrimaryTumor primaryTumor = curator.search(desmoidTumor);

        assertEquals("Bone/Soft tissue", primaryTumor.location());
    }

    @Test
    public void canResolveDoidNodes() throws IOException {
        List<DoidNode> doidNodes = DiseaseOntology.readDoidOwlEntryFromDoidJson(DOID_FILE_JSON).nodes();
        assertEquals(Lists.newArrayList(doidNodes.get(0)), PrimaryTumorCurator.resolveDoidNodes(doidNodes, Lists.newArrayList("8718")));
    }

    @Test
    public void canCurateSearchTermWithChar34() {
        String searchTerm = "Non-small cell carcinoma NOS (mostly resembling lung carcinoma): working diagnosis \"lung carcinoma\"";
        PrimaryTumorCurator curator = TestCuratorFactory.primaryTumorCurator();
        CuratedPrimaryTumor primaryTumor = curator.search(searchTerm);

        String location = primaryTumor.location();
        assertNotNull(location);
        assertEquals("lung", location.toLowerCase());
    }
}