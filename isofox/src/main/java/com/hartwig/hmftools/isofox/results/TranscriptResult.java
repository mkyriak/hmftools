package com.hartwig.hmftools.isofox.results;

import static com.hartwig.hmftools.isofox.common.GeneCollection.TRANS_COUNT;
import static com.hartwig.hmftools.isofox.common.GeneCollection.UNIQUE_TRANS_COUNT;
import static com.hartwig.hmftools.isofox.common.RegionReadData.findExonRegion;
import static com.hartwig.hmftools.isofox.exp_rates.ExpectedRatesGenerator.FL_FREQUENCY;
import static com.hartwig.hmftools.isofox.exp_rates.ExpectedRatesGenerator.FL_LENGTH;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.DELIMITER;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_GENE_ID;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_GENE_NAME;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_TRANS_ID;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_TRANS_NAME;

import java.util.List;
import java.util.StringJoiner;

import com.hartwig.hmftools.common.ensemblcache.EnsemblGeneData;
import com.hartwig.hmftools.common.ensemblcache.ExonData;
import com.hartwig.hmftools.common.ensemblcache.TranscriptData;
import com.hartwig.hmftools.isofox.common.FragmentMatchType;
import com.hartwig.hmftools.isofox.common.GeneCollection;
import com.hartwig.hmftools.isofox.common.GeneReadData;
import com.hartwig.hmftools.isofox.common.RegionReadData;

public class TranscriptResult
{
    public final TranscriptData Trans;

    public final int ExonicBases;
    public final int ExonicBasesCovered;
    public final int SpliceJunctionsSupported;
    public final int UniqueSpliceJunctionsSupported;
    public final int UniqueSpliceJunctionFragments;
    public final int UniqueNonSJFragments;
    public final double EffectiveLength;

    private double mFitAllocation;
    private double mRawFitAllocation;
    private double mRawTpm;
    private double mAdjustedTpm;

    public TranscriptResult(
            final GeneCollection geneCollection, final GeneReadData geneReadData, final TranscriptData transData,
            final List<int[]> expRateFragmentLengths)
    {
        Trans = transData;

        int spliceJunctionsSupported = 0;
        int exonicBases = 0;
        int exonicBaseCoverage = 0;

        int uniqueSpliceJunctionsSupported = 0;

        final List<ExonData> exons = transData.exons();

        for(int i = 0; i < exons.size(); ++i)
        {
            ExonData exon = exons.get(i);

            final RegionReadData exonReadData = findExonRegion(geneReadData.getExonRegions(), exon.ExonStart, exon.ExonEnd);
            if(exonReadData == null)
                continue;

            int exonCoverage = exonReadData.baseCoverage(1);
            exonicBaseCoverage += exonCoverage;

            exonicBases += exon.ExonEnd - exon.ExonStart + 1;

            if(i > 0)
            {
                int[] sjReads = exonReadData.getTranscriptJunctionMatchCount(transData.TransId, SE_START);

                final ExonData prevExon = exons.get(i - 1);
                boolean sjUnique = isSpliceJunctionUnique(transData.TransName, geneReadData.getTranscripts(), prevExon.ExonEnd, exon.ExonStart);

                if(sjReads[TRANS_COUNT] > 0)
                {
                    ++spliceJunctionsSupported;

                    if(sjUnique)
                        ++uniqueSpliceJunctionsSupported;
                }
            }
        }

        ExonicBases = exonicBases;
        ExonicBasesCovered = exonicBaseCoverage;
        SpliceJunctionsSupported = spliceJunctionsSupported;
        UniqueSpliceJunctionsSupported = uniqueSpliceJunctionsSupported;

        int[][] supportingFragments = geneCollection.getTranscriptReadCount(transData.TransId);
        UniqueSpliceJunctionFragments = supportingFragments[FragmentMatchType.typeAsInt(FragmentMatchType.SPLICED)][UNIQUE_TRANS_COUNT];
        UniqueNonSJFragments = supportingFragments[FragmentMatchType.typeAsInt(FragmentMatchType.LONG)][UNIQUE_TRANS_COUNT];

        EffectiveLength = calcEffectiveLength(exonicBases, expRateFragmentLengths);

        mFitAllocation = 0;
        mRawFitAllocation = 0;
        mRawTpm = 0;
        mAdjustedTpm = 0;
    }

    public void setFitAllocation(double alloc) { mFitAllocation = alloc; }
    public void setPreGcFitAllocation(double alloc) { mRawFitAllocation = alloc; }

    public void setTPM(double raw, double adjusted)
    {
        mRawTpm = raw;
        mAdjustedTpm = adjusted;
    }

    public double getFitAllocation() { return mFitAllocation; }

    public static double calcEffectiveLength(int transLength, final List<int[]> fragmentLengthData)
    {
        if(fragmentLengthData.isEmpty())
            return transLength;

        long flFrequencyTotal = 0;
        long flBasesTotal = 0;

        for(final int[] flData : fragmentLengthData)
        {
            int fragLength = flData[FL_LENGTH];
            int fragFrequency = flData[FL_FREQUENCY];

            if(fragLength >= transLength)
                continue;

            long possibleBases = transLength - fragLength;
            flFrequencyTotal += fragFrequency;
            flBasesTotal += possibleBases * fragFrequency;
        }

        if(flFrequencyTotal == 0)
            return 0;

        return flBasesTotal / (double)flFrequencyTotal;
    }

    public double fragmentsPerKb()
    {
        return EffectiveLength > 0 ? mFitAllocation / (EffectiveLength / 1000.0) : 0;
    }

    private static boolean isSpliceJunctionUnique(final String transId, final List<TranscriptData> transDataList, long exonEnd, long exonStart)
    {
        for(TranscriptData transData : transDataList)
        {
            if (transData.TransName.equals(transId))
                continue;

            for (int i = 1; i < transData.exons().size(); ++i)
            {
                if(transData.exons().get(i-1).ExonEnd == exonEnd && transData.exons().get(i).ExonStart == exonStart)
                    return false;
            }
        }

        return true;
    }

    public static final String FLD_FITTED_FRAGMENTS = "FittedFragments";
    public static final String FLD_EFFECTIVE_LENGTH = "EffectiveLength";
    public static final String FLD_TPM = "AdjTPM";

    public static String csvHeader()
    {
        return new StringJoiner(DELIMITER)
                .add(FLD_GENE_ID)
                .add(FLD_GENE_NAME)
                .add(FLD_TRANS_ID)
                .add(FLD_TRANS_NAME)
                .add("ExonCount")
                .add("TranscriptLength")
                .add(FLD_EFFECTIVE_LENGTH)
                .add(FLD_FITTED_FRAGMENTS)
                .add("RawFittedFragments")
                .add(FLD_TPM)
                .add("RawTPM")
                .add("TranscriptBasesCovered")
                .add("SJSupported")
                .add("UniqueSJSupported")
                .add("UniqueSJFragments")
                .add("UniqueNonSJFragments")
                .toString();
    }

    public String toCsv(final EnsemblGeneData geneData)
    {
        return new StringJoiner(DELIMITER)
                .add(geneData.GeneId)
                .add(geneData.GeneName)
                .add(String.valueOf(Trans.TransId))
                .add(Trans.TransName)
                .add(String.valueOf(Trans.exons().size()))
                .add(String.valueOf(ExonicBases))
                .add(String.format("%.0f", EffectiveLength))
                .add(String.format("%.1f", mFitAllocation))
                .add(String.format("%.1f", mRawFitAllocation))
                .add(String.format("%6.3e", mAdjustedTpm))
                .add(String.format("%6.3e", mRawTpm))
                .add(String.valueOf(ExonicBasesCovered))
                .add(String.valueOf(SpliceJunctionsSupported))
                .add(String.valueOf(UniqueSpliceJunctionsSupported))
                .add(String.valueOf(UniqueSpliceJunctionFragments))
                .add(String.valueOf(UniqueNonSJFragments))
                .toString();
    }
}
