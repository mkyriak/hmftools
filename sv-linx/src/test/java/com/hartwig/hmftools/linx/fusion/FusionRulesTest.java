package com.hartwig.hmftools.linx.fusion;

import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.createGeneAnnotation;
import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.getCodingBases;
import static com.hartwig.hmftools.common.fusion.KnownFusionType.KNOWN_PAIR;
import static com.hartwig.hmftools.common.fusion.Transcript.POST_CODING_PHASE;
import static com.hartwig.hmftools.linx.fusion.FusionFinder.checkFusionLogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.fusion.GeneAnnotation;
import com.hartwig.hmftools.common.fusion.KnownFusionData;
import com.hartwig.hmftools.common.fusion.Transcript;

import org.junit.Test;

public class FusionRulesTest
{
    @Test
    public void testFusionTypes()
    {
        String geneName = "GENE1";
        String geneId = "ENSG0001";
        String chromosome = "1";

        // SV breakend positions won't impact fusion determination since transcripts are created manually
        GeneAnnotation gene1 = createGeneAnnotation(0, true, geneName, geneId, 1, chromosome, 150, 1);

        // one on the negative strand
        String geneName2 = "GENE2";
        String geneId2 = "ENSG0003";
        String chromosome2 = "1";

        GeneAnnotation gene2 = createGeneAnnotation(0, false, geneName2, geneId2, -1, chromosome2, 150, 1);

        String transName1 = "ENST0001";
        int transId1 = 1;

        /* invalid fusions:
            - up or down post-coding
            - down non-coding
            - up promoter
            - down single exon
            - up not disruptive
            - up 5'UTR to down coding
            - up coding to down not-coding
            - up coding exonic to not down coding exonic
            - up non-coding to down coding
            - unmatched phasing - exact or not
         */


        // non-coding combos
        Integer codingStart = null;
        Integer codingEnd = null;

        Transcript trans1 = new Transcript(gene1, transId1, transName1, 2, -1, 3, -1,
                0, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        String transName2 = "ENST0002";
        int transId2 = 2;

        codingStart = 100;
        codingEnd = 200;
        Transcript trans2 = new Transcript(gene2, transId2, transName2, 2, -1, 3, -1,
                10, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        FusionParameters params = new FusionParameters();
        params.AllowExonSkipping = false;
        params.RequirePhaseMatch = true;

        // up non-coding
        assertTrue(trans1.nonCoding());
        assertTrue(trans2.isCoding());
        assertNull(checkFusionLogic(trans1, trans2, params));

        codingStart = null;
        codingEnd = null;
        trans2 = new Transcript(gene2, transId2, transName2, 2, -1, 3, -1,
                0, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        codingStart = 100;
        codingEnd = 200;
        trans1 = new Transcript(gene1, transId1, transName1, 2, -1, 3, -1,
                10, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        // down non-coding
        assertTrue(trans1.isCoding());
        assertTrue(trans2.nonCoding());
        assertNull(checkFusionLogic(trans1, trans2, params));

        // up / down post coding - upstream can be post-coding if can skip exons to form a phase-matched fusion
        trans2 = new Transcript(gene2, transId2, transName2, 2, 0, 3, 0,
                10, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        trans1 = new Transcript(gene1, transId1, transName1, 2, -1, 3, -1,
                getCodingBases(codingStart, codingEnd), getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertTrue(trans1.postCoding());
        assertTrue(trans2.isCoding());
        assertNull(checkFusionLogic(trans1, trans2, params));

        // up promotor
        trans1 = new Transcript(gene1, transId1, transName1, 0, -1, 1, -1,
                0, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertTrue(trans1.isPromoter());
        assertNull(checkFusionLogic(trans1, trans2, params));

        // down single exon
        trans1 = new Transcript(gene1, transId1, transName1, 2, -1, 3, -1,
                0, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        trans2 = new Transcript(gene2, transId2, transName2, 2, -1, 3, -1,
                10, getCodingBases(codingStart, codingEnd),1, true, 50, 250, codingStart, codingEnd);

        assertTrue(trans1.preCoding());
        assertNull(checkFusionLogic(trans1, trans2, params));

        // up not disruptive
        trans1.setIsDisruptive(false);

        trans2 = new Transcript(gene2, transId2, transName2, 2, -1, 3, -1,
                10, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertNull(checkFusionLogic(trans1, trans2, params));

        // up 5'UTR to down coding
        trans1.setIsDisruptive(true);

        // up coding exonic to not down coding exonic
        trans1 = new Transcript(gene1, transId1, transName1, 2, -1, 2, -1,
                0, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        trans2 = new Transcript(gene2, transId2, transName2, 2, -1, 3, -1,
                0, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertTrue(trans1.isExonic());
        assertTrue(trans2.isIntronic());
        assertNull(checkFusionLogic(trans1, trans2, params));

        // unmatched phasing intronic
        trans1 = new Transcript(gene1, transId1, transName1, 2, -1, 3, -1,
                10, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        trans2 = new Transcript(gene2, transId2, transName2, 2, 0, 3, 0,
                10, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertTrue(trans1.isCoding());
        assertTrue(trans2.isCoding());
        assertTrue(trans1.isIntronic());
        assertTrue(trans2.isIntronic());
        assertNull(checkFusionLogic(trans1, trans2, params));

        // pre-coding to pre-coding same gene
        trans1 = new Transcript(gene1, transId1, transName1, 1, -1, 2, -1,
                0, getCodingBases(codingStart, codingEnd),10, true, 50, 450, codingStart, codingEnd);

        trans2 = new Transcript(gene1, transId1, transName1, 4, -1, 5, -1,
                0, getCodingBases(codingStart, codingEnd),10, true, 50, 450, codingStart, codingEnd);

        assertTrue(trans1.preCoding());
        assertTrue(trans2.preCoding());
        assertNull(checkFusionLogic(trans1, trans2, params));

        // exon to exon phasing - to be phased, the upstream phase needs to be 1 less than the downstream phase

        // upstream coding bases - 10, phase = 0, will need the downstream phase to be 1

        // unmatched phasing exon to exon
        trans1 = new Transcript(gene1, transId1, transName1, 2, 2, 2, 1,
                10, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        trans2 = new Transcript(gene2, transId2, transName2, 2, 1, 2, 0,
                10, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertTrue(trans1.isExonic());
        assertTrue(trans2.isExonic());
        assertNull(checkFusionLogic(trans1, trans2, params));

        // also invalid exonic fusion
        trans1 = new Transcript(gene1, transId1, transName1, 2, 2, 2, 1,
                11, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertNull(checkFusionLogic(trans1, trans2, params));

        // valid exonic fusion
        trans1 = new Transcript(gene1, transId1, transName1, 2, 2, 2, 1,
                12, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertNotNull(checkFusionLogic(trans1, trans2, params));

        // exon to exon but both pre-coding
        trans1 = new Transcript(gene1, transId1, transName1, 2, -1, 2, -1,
                0, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        trans2 = new Transcript(gene2, transId2, transName2, 2, -1, 2, -1,
                0, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertTrue(trans1.isExonic());
        assertTrue(trans2.isExonic());
        assertTrue(trans1.preCoding());
        assertTrue(trans2.preCoding());
        assertNotNull(checkFusionLogic(trans1, trans2, params));

        // valid intronic fusion
        trans1 = new Transcript(gene1, transId1, transName1, 2, 1, 3, 2,
                10, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        trans2 = new Transcript(gene2, transId2, transName2, 2, 0, 3, 1,
                11, getCodingBases(codingStart, codingEnd),4, true, 50, 250, codingStart, codingEnd);

        assertNotNull(checkFusionLogic(trans1, trans2, params));

        FusionFinder fusionFinder = new FusionFinder(null, null);
        fusionFinder.getKnownFusionCache().addData(new KnownFusionData(KNOWN_PAIR, gene1.GeneName, gene2.GeneName, "", ""));
        gene1.addTranscript(trans1);
        gene2.addTranscript(trans2);
        final List<GeneFusion> fusions = fusionFinder.findFusions(Lists.newArrayList(gene1), Lists.newArrayList(gene2), params);
        assertEquals(1, fusions.size());
        assertTrue(fusions.get(0).phaseMatched());
    }

    @Test
    public void testAlternatePhasings()
    {
        String geneName = "GENE1";
        String geneId = "ENSG0001";
        String chromosome = "1";

        // SV breakend positions won't impact fusion determination since transcripts are created manually
        GeneAnnotation gene1 = createGeneAnnotation(0, true, geneName, geneId, 1, chromosome, 150, 1);

        // one on the negative strand
        String geneName2 = "GENE2";
        String geneId2 = "ENSG0003";
        String chromosome2 = "1";

        GeneAnnotation gene2 = createGeneAnnotation(0, false, geneName2, geneId2, -1, chromosome2, 150, 1);

        String transName1 = "ENST0001";
        int transId1 = 1;

        // non-coding combos
        Integer codingStart = 100;
        Integer codingEnd = 200;

        Transcript transUp = new Transcript(gene1, transId1, transName1, 2, 1, 3, 1,
                10, getCodingBases(codingStart, codingEnd),10, true, 50, 250, codingStart, codingEnd);

        String transName2 = "ENST0002";
        int transId2 = 2;

        Transcript transDown = new Transcript(gene2, transId2, transName2, 2, 0, 3, 0,
                10, getCodingBases(codingStart, codingEnd),10, true, 50, 250, codingStart, codingEnd);

        FusionParameters params = new FusionParameters();
        params.AllowExonSkipping = true;
        params.RequirePhaseMatch = false;

        // up non-coding
        assertTrue(transUp.isCoding());
        assertTrue(transDown.isCoding());
        GeneFusion fusion = checkFusionLogic(transUp, transDown, params);

        assertNotNull(fusion);
        assertFalse(fusion.phaseMatched());

        Map<Integer,Integer> altPhasingsUp = transUp.getAlternativePhasing();
        altPhasingsUp.put(0, 1);

        fusion = checkFusionLogic(transUp, transDown, params);

        assertNotNull(fusion);
        assertTrue(fusion.phaseMatched());
        assertEquals(fusion.getExonsSkipped(true), 1);
        assertEquals(fusion.getExonsSkipped(false), 0);

        altPhasingsUp.clear();
        Map<Integer,Integer> altPhasingsDown = transDown.getAlternativePhasing();
        altPhasingsDown.put(1, 1);

        fusion = checkFusionLogic(transUp, transDown, params);

        assertNotNull(fusion);
        assertTrue(fusion.phaseMatched());
        assertEquals(0, fusion.getExonsSkipped(true));
        assertEquals(1, fusion.getExonsSkipped(false));

        // check 5' gene fusing from the 3'UTR region
        transUp = new Transcript(gene1, transId1, transName1, 6, -1, 7, -1,
                100, 100,10, true, 50, 250, codingStart, codingEnd);

        assertTrue(transUp.postCoding());
        assertEquals(transUp.ExonDownstreamPhase, POST_CODING_PHASE);
        assertEquals(transUp.ExonUpstreamPhase, POST_CODING_PHASE);

        altPhasingsUp = transUp.getAlternativePhasing();

        altPhasingsUp.clear();
        altPhasingsUp.put(0, 3);

        fusion = checkFusionLogic(transUp, transDown, params);

        assertNotNull(fusion);
        assertTrue( fusion.phaseMatched());
        assertEquals(fusion.getExonsSkipped(true), 3);
        assertEquals(fusion.getExonsSkipped(false), 0);

        // check a fusion requiring skipping on both transcripts
        transUp = new Transcript(gene1, transId1, transName1, 2, 1, 3, 1,
                10, getCodingBases(codingStart, codingEnd),10, true, 50, 250, codingStart, codingEnd);

        transDown = new Transcript(gene2, transId2, transName2, 2, 0, 3, 0,
                10, getCodingBases(codingStart, codingEnd),10, true, 50, 250, codingStart, codingEnd);

        params.AllowExonSkipping = true;

        fusion = checkFusionLogic(transUp, transDown, params);

        assertNotNull(fusion);
        assertFalse(fusion.phaseMatched());

        altPhasingsUp = transUp.getAlternativePhasing();
        altPhasingsDown = transDown.getAlternativePhasing();
        altPhasingsUp.put(2, 2);
        altPhasingsDown.put(2, 3);

        fusion = checkFusionLogic(transUp, transDown, params);

        assertNotNull(fusion);
        assertTrue(fusion.phaseMatched());
        assertEquals(2, fusion.getExonsSkipped(true));
        assertEquals(3, fusion.getExonsSkipped(false));
    }
}
