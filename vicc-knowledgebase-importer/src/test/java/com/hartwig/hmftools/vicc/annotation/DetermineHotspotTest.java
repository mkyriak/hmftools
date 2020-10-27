package com.hartwig.hmftools.vicc.annotation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.util.Strings;
import org.junit.Test;

public class DetermineHotspotTest {

    @Test
    public void canConvertFeatureNameToProteinAnnotation() {
        assertEquals("E709K", DetermineHotspot.extractProteinAnnotation("E709K"));
        assertEquals("E709K", DetermineHotspot.extractProteinAnnotation("EGFR E709K "));
        assertEquals("E709K", DetermineHotspot.extractProteinAnnotation("EGFR:E709K"));
        assertEquals("E709K", DetermineHotspot.extractProteinAnnotation("EGFR:p.E709K"));
        assertEquals("E709K", DetermineHotspot.extractProteinAnnotation("EGFR p.E709K"));
        assertEquals("E709K", DetermineHotspot.extractProteinAnnotation("E709K (c.2100A>c)"));

        assertEquals("G778_P780dup", DetermineHotspot.extractProteinAnnotation("G778_P780DUP"));
        assertEquals("V560del", DetermineHotspot.extractProteinAnnotation("KIT:p.V560DEL"));
        assertEquals("V560fs", DetermineHotspot.extractProteinAnnotation("KIT:p.V560FS"));
        assertEquals("V560fs", DetermineHotspot.extractProteinAnnotation("KIT:p.V560FS*"));
        assertEquals("V560insAYVM", DetermineHotspot.extractProteinAnnotation("KIT:p.V560INSAYVM"));
        assertEquals("V560insINS", DetermineHotspot.extractProteinAnnotation("KIT:p.V560INSINS"));
        assertEquals("V560delinsDEL", DetermineHotspot.extractProteinAnnotation("KIT:p.V560DELINSDEL"));
    }

    @Test
    public void canAssessWhetherProteinAnnotationIsHotspot() {
        assertTrue(DetermineHotspot.isHotspot("K5N"));
        assertTrue(DetermineHotspot.isHotspot("L2230V"));
        assertTrue(DetermineHotspot.isHotspot("V5del"));
        assertTrue(DetermineHotspot.isHotspot("L755_T759del"));
        assertTrue(DetermineHotspot.isHotspot("L755_T756delinsPP"));
        assertTrue(DetermineHotspot.isHotspot("D770delinsGY"));
        assertTrue(DetermineHotspot.isHotspot("G10dup"));
        assertTrue(DetermineHotspot.isHotspot("G10fs"));
        assertTrue(DetermineHotspot.isHotspot("G10fs*"));
        assertTrue(DetermineHotspot.isHotspot("*10L"));

        // Just plain wrong protein annotations
        assertFalse(DetermineHotspot.isHotspot(Strings.EMPTY));
        assertFalse(DetermineHotspot.isHotspot("truncating"));
        assertFalse(DetermineHotspot.isHotspot("20LtoV"));
        assertFalse(DetermineHotspot.isHotspot("L20"));
        assertFalse(DetermineHotspot.isHotspot("LP"));
        assertFalse(DetermineHotspot.isHotspot("L"));
        assertFalse(DetermineHotspot.isHotspot("L2"));
        assertFalse(DetermineHotspot.isHotspot("L20Pdel5"));
        assertFalse(DetermineHotspot.isHotspot("fs"));

        // Splice variants are ignored by hotspot extractor:
        assertFalse(DetermineHotspot.isHotspot("963_D1010splice"));

        // Frameshifts which also change the codon in which the frame is shifted are ignored.
        assertFalse(DetermineHotspot.isHotspot("G20Pfs"));

        // Frameshifts which are preceded by stop codon are ignored.
        assertFalse(DetermineHotspot.isHotspot("G20*fs"));

        // Not a correctly formatted insert (position not clear)
        assertFalse(DetermineHotspot.isHotspot("T599insTT"));

        // Not a correctly formatted insert
        assertFalse(DetermineHotspot.isHotspot("V600D_K601insFGLAT"));

        // Inframe event is too long
        assertFalse(DetermineHotspot.isHotspot("L4_T40del"));
        assertFalse(DetermineHotspot.isHotspot("L698_S1037dup"));

        // Wild-type mutations are ignored
        assertFalse(DetermineHotspot.isHotspot("V600X"));

        // Mutations with logical OR are ignored
        assertFalse(DetermineHotspot.isHotspot("V600E/K"));
    }

}