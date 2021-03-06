package com.hartwig.hmftools.protect.homozygousdisruption;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import com.google.common.io.Resources;

import org.junit.Test;

public class HomozygousDisruptionAnalyzerTest {

    private static final String LINX_DRIVERS_CATALOG_TSV = Resources.getResource("test_run/linx/sample.drivers.catalog.tsv").getPath();

    @Test
    public void canExtractHomozygousDisruptions() throws IOException {
        List<ReportableHomozygousDisruption> homozygousDisruptions =
                HomozygousDisruptionAnalyzer.extractFromLinxDriversTsv(LINX_DRIVERS_CATALOG_TSV);

        assertEquals(1, homozygousDisruptions.size());

        ReportableHomozygousDisruption homozygousDisruption1 = homozygousDisruptions.get(0);
        assertEquals("9", homozygousDisruption1.chromosome());
        assertEquals("p23-p24.1", homozygousDisruption1.chromosomeBand());
        assertEquals("PTPRD", homozygousDisruption1.gene());
    }
}