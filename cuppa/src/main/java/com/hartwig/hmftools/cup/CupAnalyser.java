package com.hartwig.hmftools.cup;

import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.cup.CuppaConfig.LOG_DEBUG;
import static com.hartwig.hmftools.cup.CuppaConfig.REF_SAMPLE_DATA_FILE;
import static com.hartwig.hmftools.cup.CuppaConfig.SAMPLE_DATA_FILE;
import static com.hartwig.hmftools.cup.CuppaConfig.SPECIFIC_SAMPLE_DATA;
import static com.hartwig.hmftools.cup.CuppaConfig.CUP_LOGGER;
import static com.hartwig.hmftools.cup.common.CupCalcs.calcClassifierScoreResult;
import static com.hartwig.hmftools.cup.common.CupCalcs.calcCombinedFeatureResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.cup.common.SampleData;
import com.hartwig.hmftools.cup.common.SampleDataCache;
import com.hartwig.hmftools.cup.common.SampleResult;
import com.hartwig.hmftools.cup.common.SampleSimilarity;
import com.hartwig.hmftools.cup.feature.FeatureAnnotation;
import com.hartwig.hmftools.cup.rna.RnaExpression;
import com.hartwig.hmftools.cup.sample.SampleTraits;
import com.hartwig.hmftools.cup.sigs.SomaticAnnotation;
import com.hartwig.hmftools.cup.svs.SvAnnotation;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

public class CupAnalyser
{
    private final CuppaConfig mConfig;

    private final SampleDataCache mSampleDataCache;

    private final FeatureAnnotation mFeatures;
    private final SomaticAnnotation mSomatics;
    private final SampleTraits mSampleTraits;
    private final SvAnnotation mSvAnnotation;
    private final RnaExpression mRnaExpression;

    private BufferedWriter mSampleDataWriter;
    private BufferedWriter mSampleSimilarityWriter;

    public CupAnalyser(final CommandLine cmd)
    {
        mConfig = new CuppaConfig(cmd);

        mSampleDataCache = new SampleDataCache();

        loadSampleData(cmd);

        mSomatics = new SomaticAnnotation(mConfig, mSampleDataCache);
        mFeatures = new FeatureAnnotation(mConfig, mSampleDataCache);
        mSampleTraits = new SampleTraits(mConfig, mSampleDataCache);
        mSvAnnotation = new SvAnnotation(mConfig, mSampleDataCache);
        mRnaExpression = new RnaExpression(mConfig, mSampleDataCache, cmd);

        mSampleDataWriter = null;
        mSampleSimilarityWriter = null;

    }

    private void loadSampleData(final CommandLine cmd)
    {
        mSampleDataCache.loadSampleData(cmd.getOptionValue(SPECIFIC_SAMPLE_DATA), cmd.getOptionValue(SAMPLE_DATA_FILE));
        mSampleDataCache.loadReferenceSampleData(cmd.getOptionValue(REF_SAMPLE_DATA_FILE), true);

        // mark any samples included in the ref data set so they can be excluded from self-comparison
        mSampleDataCache.SampleDataList.stream()
                .filter(x -> mSampleDataCache.RefSampleCancerTypeMap.containsKey(x.Id)).forEach(x -> x.setRefSample());
    }

    public void run()
    {
        if(!mConfig.isValid())
        {
            CUP_LOGGER.error("invalid config");
            return;
        }

        if(mSampleDataCache.SampleIds.isEmpty())
        {
            CUP_LOGGER.error("no samples specified");
            return;
        }

        if(!checkAnnotators())
            return;

        if(mSampleDataCache.isMultiSample())
        {
            CUP_LOGGER.info("loaded {} samples and {} cancer types",
                    mSampleDataCache.SampleIds.size(), mSampleDataCache.RefCancerSampleData.size());
        }

        initialiseOutputFiles();

        if(mSampleDataCache.SpecificSample != null)
        {
            final SampleData specificSample = mSampleDataCache.SpecificSample;

            CUP_LOGGER.info("sample({}) running CUP analysis", specificSample.Id);
            processSample(specificSample);
        }
        else
        {
            int sampleCount = 0;
            for(SampleData sample : mSampleDataCache.SampleDataList)
            {
                CUP_LOGGER.debug("sample({}) running CUP analysis", sample.Id);

                processSample(sample);

                if(!checkAnnotators())
                    break;

                ++sampleCount;

                if((sampleCount % 100) == 0)
                {
                    CUP_LOGGER.info("processed {} samples", sampleCount);
                }
            }
        }

        closeBufferedWriter(mSampleDataWriter);
        closeBufferedWriter(mSampleSimilarityWriter);

        CUP_LOGGER.info("CUP analysis complete");
    }

    private boolean checkAnnotators()
    {
        if(!mSvAnnotation.isValid() || !mFeatures.isValid() || !mSomatics.isValid() || !mSampleTraits.isValid() || !mRnaExpression.isValid())
        {
            CUP_LOGGER.error("invalid init: traits({}) sigs({}) SVs({}) features({}) rna({{}})",
                    mSampleTraits.isValid(), mSomatics.isValid(), mSvAnnotation.isValid(), mFeatures.isValid(), mRnaExpression.isValid());
            return false;
        }

        return true;
    }

    private void processSample(final SampleData sample)
    {
        final List<SampleResult> allResults = Lists.newArrayList();
        final List<SampleSimilarity> similarities = Lists.newArrayList();

        final List<SampleResult> traitsResults = mSampleTraits.processSample(sample);
        allResults.addAll(traitsResults);

        mSomatics.processSample(sample, allResults, similarities);

        final List<SampleResult> svResults = mSvAnnotation.processSample(sample);
        allResults.addAll(svResults);

        final List<SampleResult> driverResults = mFeatures.processSample(sample);
        allResults.addAll(driverResults);

        mRnaExpression.processSample(sample, allResults, similarities);

        SampleResult combinedFeatureResult = calcCombinedFeatureResult(sample, allResults);

        if(combinedFeatureResult != null)
            allResults.add(combinedFeatureResult);

        SampleResult classifierScoreResult = calcClassifierScoreResult(sample, allResults);

        if(classifierScoreResult != null)
            allResults.add(classifierScoreResult);

        writeSampleData(sample, allResults);
        writeSampleSimilarities(sample, similarities);
    }

    private void initialiseOutputFiles()
    {
        try
        {
            final String sampleDataFilename = mSampleDataCache.isSingleSample() ?
                    mConfig.OutputDir + mSampleDataCache.SpecificSample.Id + ".cup.data.csv"
                    : mConfig.formOutputFilename("SAMPLE_DATA");

            mSampleDataWriter = createBufferedWriter(sampleDataFilename, false);

            mSampleDataWriter.write("SampleId,Category,ResultType,DataType,Value,RefCancerType,RefValue");

            mSampleDataWriter.newLine();

            if(mConfig.WriteSimilarities)
            {
                final String sampleSimilarityFilename = mSampleDataCache.isSingleSample() ?
                        mConfig.OutputDir + mSampleDataCache.SpecificSample.Id + ".cup.similarities.csv"
                        : mConfig.formOutputFilename("SAMPLE_SIMILARITIES");

                mSampleSimilarityWriter = createBufferedWriter(sampleSimilarityFilename, false);

                mSampleSimilarityWriter.write("SampleId,MatchType,Score,MatchSampleId");
                mSampleSimilarityWriter.write(",MatchCancerType,MatchPrimaryLocation,MatchCancerSubtype");
                mSampleSimilarityWriter.newLine();
            }
        }
        catch(IOException e)
        {
            CUP_LOGGER.error("failed to write SNV sample CSS output: {}", e.toString());
        }
    }

    private void writeSampleData(final SampleData sampleData, final List<SampleResult> results)
    {
        if(results.isEmpty() || mSampleDataWriter == null)
            return;

        try
        {
            for(SampleResult result : results)
            {
                final String sampleStr = String.format("%s,%s,%s,%s,%s",
                        sampleData.Id, result.Category, result.ResultType, result.DataType, result.Value.toString());

                for(Map.Entry<String,Double> cancerValues : result.CancerTypeValues.entrySet())
                {
                    mSampleDataWriter.write(String.format("%s,%s,%.3g",
                            sampleStr, cancerValues.getKey(), cancerValues.getValue()));
                    mSampleDataWriter.newLine();
                }
            }
        }
        catch(IOException e)
        {
            CUP_LOGGER.error("failed to write sample data: {}", e.toString());
        }
    }

    private void writeSampleSimilarities(final SampleData sampleData, final List<SampleSimilarity> similarities)
    {
        if(similarities.isEmpty() || mSampleSimilarityWriter == null)
            return;

        try
        {
            for(SampleSimilarity similarity : similarities)
            {
                final SampleData refSample = mSampleDataCache.findRefSampleData(similarity.MatchedSampleId);

                if(refSample == null)
                {
                    CUP_LOGGER.error("refSample({}) not found", similarity.MatchedSampleId);
                    continue;
                }

                mSampleSimilarityWriter.write(String.format("%s,%s,%.3f,%s,%s,%s,%s",
                        sampleData.Id, similarity.MatchType, similarity.Score,
                        refSample.Id, refSample.CancerType, refSample.OriginalCancerType, refSample.CancerSubtype));

                mSampleSimilarityWriter.newLine();
            }
        }
        catch(IOException e)
        {
            CUP_LOGGER.error("failed to write sample similarity: {}", e.toString());
        }
    }

    public static void main(@NotNull final String[] args) throws ParseException
    {
        Options options = new Options();
        CuppaConfig.addCmdLineArgs(options);

        final CommandLine cmd = createCommandLine(args, options);

        if (cmd.hasOption(LOG_DEBUG))
        {
            Configurator.setRootLevel(Level.DEBUG);
        }

        CupAnalyser cupAnalyser = new CupAnalyser(cmd);
        cupAnalyser.run();
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException
    {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

}