package com.hartwig.hmftools.common.chord;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import com.google.common.io.Resources;

import org.junit.Test;

public class ChordFileReaderTest {
    private static final String BASE_DIRECTORY = Resources.getResource("chord_pilot").getPath();
    private static final double EPSILON = 1.0E-10;

    private static final String SAMPLE = "sample";
    private static final double NONE = 0.850;

    private static final double BRCA1 = 0.230;
    private static final double BRCA2 = 0.400;
    private static final double HRD = 0.530;
    private static final boolean PREDICTEDRESPONSE = true;

    @Test
    public void loadChordFile() throws IOException {
        String file = ChordFileReader.generateFilename(BASE_DIRECTORY.substring(0, BASE_DIRECTORY.length() - "chord_pilot".length() - 1), SAMPLE);

        ChordAnalysis chordAnalysis = ChordFileReader.read(file);

        assertEquals(BRCA1, chordAnalysis.BRCA1Value(), EPSILON);
        assertEquals(NONE, chordAnalysis.noneValue(), EPSILON);
        assertEquals(BRCA2, chordAnalysis.BRCA2Value(), EPSILON);
        assertEquals(HRD, chordAnalysis.hrdValue(), EPSILON);
        assertEquals(PREDICTEDRESPONSE, chordAnalysis.predictedResponseValue());

    }
}