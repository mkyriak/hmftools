package com.hartwig.hmftools.common.serve.classification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;

import org.junit.Test;

public class ExclusiveEventClassifierTest {

    private static final String MATCHING_GENE = "match";
    private static final String NON_MATCHING_GENE = "no-match";

    @Test
    public void excludingEventClassifierWorks() {
        EventClassifier geneMatcher = (gene, event) -> gene.equals(MATCHING_GENE);
        EventClassifier noMatcher = (gene, event) -> false;

        EventClassifier noExclusions = new ExclusiveEventClassifier(Lists.newArrayList(), geneMatcher);
        assertTrue(noExclusions.matches(MATCHING_GENE, "any"));
        assertFalse(noExclusions.matches(NON_MATCHING_GENE, "any"));

        EventClassifier noExclusionsNoInclusions = new ExclusiveEventClassifier(Lists.newArrayList(), noMatcher);
        assertFalse(noExclusionsNoInclusions.matches(MATCHING_GENE, "any"));
        assertFalse(noExclusionsNoInclusions.matches(NON_MATCHING_GENE, "any"));

        EventClassifier excludingAndIncluding = new ExclusiveEventClassifier(Lists.newArrayList(geneMatcher), geneMatcher);
        assertFalse(excludingAndIncluding.matches(MATCHING_GENE, "any"));
        assertFalse(excludingAndIncluding.matches(NON_MATCHING_GENE, "any"));

        EventClassifier excludingAndNoInclusions = new ExclusiveEventClassifier(Lists.newArrayList(geneMatcher), noMatcher);
        assertFalse(excludingAndNoInclusions.matches(MATCHING_GENE, "any"));
        assertFalse(excludingAndNoInclusions.matches(NON_MATCHING_GENE, "any"));
    }
}