package com.hartwig.hmftools.svannotation.analysis;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.structural.annotation.EnsemblGeneData;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.Transcript;
import com.hartwig.hmftools.common.variant.structural.annotation.TranscriptExonData;
import com.hartwig.hmftools.svannotation.SvGeneTranscriptCollection;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public class SvAnnotatorTestUtils
{
    public static void initLogger()
    {
        Configurator.setRootLevel(Level.DEBUG);
    }

    public static GeneAnnotation createGeneAnnotation(int svId, boolean isStart, final String geneName, String stableId, int strand,
            final String chromosome, long position, int orientation)
    {
        List<String> synonyms = Lists.newArrayList();
        List<Integer> entrezIds = Lists.newArrayList();
        String karyotypeBand = "";

        GeneAnnotation gene = new GeneAnnotation(svId, isStart, geneName, stableId, strand, synonyms, entrezIds, karyotypeBand);
        gene.setPositionalData(chromosome, position, (byte)orientation);

        return gene;
    }

    // Ensembl data types
    public static EnsemblGeneData createEnsemblGeneData(String geneId, String geneName, String chromosome, int strand, long geneStart, long geneEnd)
    {
        return new EnsemblGeneData(geneId, geneName, chromosome, (byte)strand, geneStart, geneEnd, "", "", "");
    }

    public static void addTransExonData(SvGeneTranscriptCollection geneTransCache, final String geneId, List<TranscriptExonData> transExonList)
    {
        geneTransCache.getGeneExonDataMap().put(geneId, transExonList);
    }

    public static void addGeneData(SvGeneTranscriptCollection geneTransCache, final String chromosome, List<EnsemblGeneData> geneDataList)
    {
        geneTransCache.getChrGeneDataMap().put(chromosome, geneDataList);
    }



}
