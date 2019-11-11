package com.hartwig.hmftools.linx.cn;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.common.purple.segment.SegmentSupport.CENTROMERE;
import static com.hartwig.hmftools.common.purple.segment.SegmentSupport.TELOMERE;
import static com.hartwig.hmftools.linx.LinxConfig.DATA_OUTPUT_DIR;
import static com.hartwig.hmftools.linx.LinxConfig.DB_PASS;
import static com.hartwig.hmftools.linx.LinxConfig.DB_URL;
import static com.hartwig.hmftools.linx.LinxConfig.DB_USER;
import static com.hartwig.hmftools.linx.LinxConfig.LOG_DEBUG;
import static com.hartwig.hmftools.linx.LinxConfig.SAMPLE;
import static com.hartwig.hmftools.linx.LinxConfig.databaseAccess;
import static com.hartwig.hmftools.linx.LinxConfig.formOutputPath;
import static com.hartwig.hmftools.linx.LinxConfig.sampleListFromConfigStr;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.CHROMOSOME_ARM_P;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.CHROMOSOME_ARM_Q;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.copyNumbersEqual;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.getChromosomalArmLength;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_PAIR;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;

import java.io.BufferedWriter;
import java.io.IOException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.segment.SegmentSupport;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantData;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

public class CopyNumberAnalyser
{

    private boolean mWriteLohEvents;
    private boolean mWritePloidyCalcs;
    private boolean mWriteChrArmData;

    private final String mOutputPath;
    private final DatabaseAccess mDbAccess;

    private final CnDataLoader mCnDataLoader;

    private final List<String> mSampleIds;
    private BufferedWriter mLohEventWriter;
    private BufferedWriter mPloidyCalcWriter;
    private BufferedWriter mChrArmWriter;

    private PerformanceCounter mPerfCounter;

    private static final String WRITE_LOH_TO_FILE = "write_loh_data";
    private static final String WRITE_PLOIDY_TO_FILE = "write_ploidy_data";
    private static final String WRITE_CHR_ARM_DATA = "write_chr_arm_data";

    private static final Logger LOGGER = LogManager.getLogger(CopyNumberAnalyser.class);

    public CopyNumberAnalyser(final String outputPath, DatabaseAccess dbAccess)
    {
        mDbAccess = dbAccess;
        mOutputPath = outputPath;
        mSampleIds = Lists.newArrayList();

        mCnDataLoader = new CnDataLoader("", dbAccess);

        mPerfCounter = new PerformanceCounter("CnAnalysis");

        mWriteChrArmData = false;
        mWriteLohEvents = false;
        mWritePloidyCalcs = false;

        mLohEventWriter = null;
        mPloidyCalcWriter = null;
        mChrArmWriter = null;
    }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(DATA_OUTPUT_DIR, true, "Output directory");
        options.addOption(DB_USER, true, "Database user name");
        options.addOption(DB_PASS, true, "Database password");
        options.addOption(DB_URL, true, "Database URL");
        options.addOption(SAMPLE, true, "Sample(s) or CSV file with sample IDs");
        options.addOption(WRITE_LOH_TO_FILE, false, "Write LOH events CSV");
        options.addOption(WRITE_PLOIDY_TO_FILE, false, "Write adjusted ploidy to CSV");
        options.addOption(WRITE_CHR_ARM_DATA, false, "Write adjusted ploidy to CSV");
    }

    public boolean loadConfig(final CommandLine cmd)
    {
        if(cmd.hasOption(SAMPLE))
        {
            mSampleIds.addAll(sampleListFromConfigStr(cmd.getOptionValue(SAMPLE)));
        }

        mWritePloidyCalcs = cmd.hasOption(WRITE_PLOIDY_TO_FILE);
        mWriteLohEvents = cmd.hasOption(WRITE_LOH_TO_FILE);
        mWriteChrArmData = cmd.hasOption(WRITE_CHR_ARM_DATA);
        return true;
    }

    public void runAnalysis()
    {
        final List<String> samplesList = mSampleIds.isEmpty() ? mDbAccess.getSampleIds() : mSampleIds;

        int sampleCount = 0;
        for (final String sampleId : samplesList)
        {
            mPerfCounter.start();

            List<StructuralVariantData> svRecords = mDbAccess.readStructuralVariantData(sampleId);

            if (svRecords.isEmpty())
            {
                continue;
            }

            LOGGER.info("analysing sample({}), totalProcessed({})", sampleId, sampleCount);

            mCnDataLoader.loadSampleData(sampleId, svRecords);

            if(mWriteLohEvents)
                writeLohData(sampleId);

            if(mWritePloidyCalcs)
                writePloidyCalcData(sampleId);

            if(mWriteChrArmData)
                writeChrArmData(sampleId);

            ++sampleCount;

            mPerfCounter.stop();
        }

        mPerfCounter.logStats();
    }

    private void writeChrArmData(final String sampleId)
    {
        try
        {
            if (mChrArmWriter == null)
            {
                String outputFileName = mOutputPath + "CN_CHR_ARM_DATA.csv";

                mChrArmWriter = createBufferedWriter(outputFileName, false);

                mChrArmWriter.write("SampleId,IsMale,Chromosome,Ploidy,WholeChrLoh,LohP,LohQ,CentroCnP,CentroCnQ,TeloCnP,TeloCnQ");
                mChrArmWriter.write(",AvgCnP,AvgCnQ,MedianCnP,MedianCnQ,MaxCnP,MaxCnQ,MinCnP,MinCnQ,SegCountP,SegCountQ");
                mChrArmWriter.newLine();
            }

            // record: sampleId, sample ploidy, whole-arm LOH,
            // record for each arm: LOH, centromere and telomere copy number, avg and median copy number

            final Map<String,List<SvCNData>> chrCnDataMap = mCnDataLoader.getChrCnDataMap();

            double samplePloidy = mCnDataLoader.getPurityContext().bestFit().ploidy();
            boolean isMale = mCnDataLoader.getPurityContext().gender().toString().startsWith("MALE");

            final List<LohEvent> lohEvents = mCnDataLoader.getLohData();

            for(Map.Entry<String,List<SvCNData>> entry : chrCnDataMap.entrySet())
            {
                final String chromosome = entry.getKey();

                if(!isMale && chromosome.equals("Y"))
                    continue;

                final List<SvCNData> cnDataList = entry.getValue();

                final CnArmStats[] armStats = calcArmStats(chromosome, cnDataList, lohEvents);

                boolean hasChrLoh = lohEvents.stream().anyMatch(x -> x.Chromosome.equals(chromosome) && x.chromosomeLoss());

                mChrArmWriter.write(String.format("%s,%s,%s,%.2f,%s,%s,%s",
                        sampleId, isMale, chromosome, samplePloidy,
                        hasChrLoh, armStats[P_ARM_INDEX].HasLOH, armStats[Q_ARM_INDEX].HasLOH));

                mChrArmWriter.write(String.format(",%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d",
                        armStats[P_ARM_INDEX].CentromereCopyNumber, armStats[Q_ARM_INDEX].CentromereCopyNumber,
                        armStats[P_ARM_INDEX].TelomereCopyNumber, armStats[Q_ARM_INDEX].TelomereCopyNumber,
                        armStats[P_ARM_INDEX].AverageCopyNumber, armStats[Q_ARM_INDEX].AverageCopyNumber,
                        armStats[P_ARM_INDEX].MedianCopyNumber, armStats[Q_ARM_INDEX].MedianCopyNumber,
                        armStats[P_ARM_INDEX].MaxCopyNumber, armStats[Q_ARM_INDEX].MaxCopyNumber,
                        armStats[P_ARM_INDEX].MinCopyNumber, armStats[Q_ARM_INDEX].MinCopyNumber,
                        armStats[P_ARM_INDEX].SegmentCount, armStats[Q_ARM_INDEX].SegmentCount));

                mChrArmWriter.newLine();

            }
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to copy number chr-arm outputFile: {}", e.toString());
        }
    }

    private static int P_ARM_INDEX = 0;
    private static int Q_ARM_INDEX = 1;

    private static int SEG_LENGTH_INDEX = 0;
    private static int SEG_CN_INDEX = 1;

    private CnArmStats[] calcArmStats(final String chromosome, final List<SvCNData> cnDataList, final List<LohEvent> lohEvents)
    {
        CnArmStats[] armStats = new CnArmStats[Q_ARM_INDEX+1];
        armStats[P_ARM_INDEX] = new CnArmStats(chromosome, CHROMOSOME_ARM_P);
        armStats[Q_ARM_INDEX] = new CnArmStats(chromosome, CHROMOSOME_ARM_Q);

        int armIndex = P_ARM_INDEX;

        double baseDistanceCopyNumber = 0;
        final List<double[]> cnSegments = Lists.newArrayList(); // order by copy number ascending

        for(int i = 0; i < cnDataList.size(); ++i)
        {
            final SvCNData cnData = cnDataList.get(i);

            double copyNumber = cnData.CopyNumber;

            if(cnData.matchesSegment(TELOMERE, true))
            {
                armStats[armIndex].TelomereCopyNumber = copyNumber;
            }
            else if(cnData.matchesSegment(CENTROMERE, true))
            {
                armIndex = Q_ARM_INDEX;

                cnSegments.clear();
                baseDistanceCopyNumber = 0;

                armStats[armIndex].CentromereCopyNumber = copyNumber;
            }

            ++armStats[armIndex].SegmentCount;

            long segLength = cnData.length();

            armStats[armIndex].MaxCopyNumber = max(armStats[armIndex].MaxCopyNumber, copyNumber);

            if(armStats[armIndex].MinCopyNumber == -1 || copyNumber < armStats[armIndex].MinCopyNumber)
                armStats[armIndex].MinCopyNumber = copyNumber;

            baseDistanceCopyNumber += segLength * copyNumber;

            int index = 0;
            while(index < cnSegments.size())
            {
                if(cnSegments.get(index)[SEG_CN_INDEX] > copyNumber)
                    break;

                ++index;
            }

            cnSegments.add(index, new double[] {(double)segLength, copyNumber});

            if(cnData.matchesSegment(CENTROMERE, false))
            {
                armStats[armIndex].CentromereCopyNumber = copyNumber;
                calcArmCopyNumber(armStats[armIndex], baseDistanceCopyNumber, cnSegments);
            }
            else if(cnData.matchesSegment(TELOMERE, false))
            {
                armStats[armIndex].TelomereCopyNumber = copyNumber;
                calcArmCopyNumber(armStats[armIndex], baseDistanceCopyNumber, cnSegments);
            }
        }

        armStats[P_ARM_INDEX].HasLOH = lohEvents.stream().anyMatch(x -> x.Chromosome.equals(chromosome) && x.armLoss(CHROMOSOME_ARM_P));
        armStats[Q_ARM_INDEX].HasLOH = lohEvents.stream().anyMatch(x -> x.Chromosome.equals(chromosome) && x.armLoss(CHROMOSOME_ARM_Q));

        return armStats;
    }

    private void calcArmCopyNumber(CnArmStats armStats, double baseDistanceCopyNumber, final List<double[]> cnSegments)
    {
        long armLength = getChromosomalArmLength(armStats.Chromosome, armStats.Arm);
        double halfArmLength = armLength * 0.5;

        armStats.AverageCopyNumber = baseDistanceCopyNumber / armLength;

        double cumulativeLength = 0;

        for(int i = 0; i < cnSegments.size(); ++i)
        {
            final double[] segment = cnSegments.get(i);
            double segLength = segment[SEG_LENGTH_INDEX];

            if(cumulativeLength + segLength >= halfArmLength)
            {
                if (i > 0)
                {
                    final double[] prevSegment = cnSegments.get(i-1);
                    double prevCopyNumber = cumulativeLength / halfArmLength * prevSegment[SEG_CN_INDEX];
                    double nextCopyNumber = (halfArmLength - cumulativeLength) / halfArmLength * segment[SEG_CN_INDEX];
                    armStats.MedianCopyNumber = (prevCopyNumber + nextCopyNumber) * 0.5;
                }
                else
                {
                    armStats.MedianCopyNumber = segment[SEG_CN_INDEX];
                }
            }
            else
            {
                cumulativeLength += segLength;
            }
        }
    }

    private boolean isCopyNumberNeutral(final LohEvent lohEvent)
    {
        if(lohEvent.chromosomeLoss())
            return false;

        final List<SvCNData> cnDataList = mCnDataLoader.getChrCnDataMap().get(lohEvent.Chromosome);

        if(cnDataList == null || cnDataList.isEmpty())
            return false;

        if(!lohEvent.SegStart.equals(TELOMERE))
        {
            final SvCNData startCnData = lohEvent.getCnData(true);

            if(startCnData.getIndex() == 0)
                return false;

            final SvCNData prevCnData = cnDataList.get(startCnData.getIndex() - 1);
            if (!copyNumbersEqual(startCnData.CopyNumber, prevCnData.CopyNumber))
                return false;
        }

        if(!lohEvent.SegEnd.equals(TELOMERE))
        {
            final SvCNData endCnData = lohEvent.getCnData(false);

            if(endCnData.getIndex() >= cnDataList.size() - 1)
                return false;

            final SvCNData nextCnData = cnDataList.get(endCnData.getIndex() + 1);
            if (!copyNumbersEqual(endCnData.CopyNumber, nextCnData.CopyNumber))
                return false;
        }

        return true;
    }

    private void writeLohData(final String sampleId)
    {
        try
        {
            if (mLohEventWriter == null)
            {
                String outputFileName = mOutputPath + "CN_LOH_EVENTS.csv";

                mLohEventWriter = createBufferedWriter(outputFileName, false);

                mLohEventWriter.write("SampleId,Ploidy,Chromosome,PosStart,PosEnd,SegStart,SegEnd");
                mLohEventWriter.write(",SegCount,Length,StartSV,EndSV,CnStart,CnEnd,CnNeutral");
                mLohEventWriter.newLine();
            }

            final List<LohEvent> lohEvents = mCnDataLoader.getLohData();
            double samplePloidy = mCnDataLoader.getPurityContext().bestFit().ploidy();

            for(final LohEvent lohData : lohEvents)
            {
                // report whether this LOH has copy number change on both ends or not
                boolean cnNeutral = isCopyNumberNeutral(lohData);

                mLohEventWriter.write(String.format("%s,%.2f,%s,%d,%d,%s,%s",
                        sampleId, samplePloidy, lohData.Chromosome, lohData.PosStart, lohData.PosEnd, lohData.SegStart, lohData.SegEnd));

                mLohEventWriter.write(String.format(",%d,%d,%s,%s",
                        lohData.SegCount, lohData.length(), lohData.StartSV, lohData.EndSV));

                mLohEventWriter.write(String.format(",%.2f,%.2f,%s",
                        lohData.getCnData(true).CopyNumber, lohData.getCnData(false).CopyNumber, cnNeutral));

                mLohEventWriter.newLine();
            }
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to copy number LOH outputFile: {}", e.toString());
        }
    }

    private void writePloidyCalcData(final String sampleId)
    {
        try
        {
            if (mPloidyCalcWriter == null)
            {
                String outputFileName = mOutputPath + "CN_PLOIDY_CALC_DATA.csv";

                mPloidyCalcWriter = createBufferedWriter(outputFileName, false);

                mPloidyCalcWriter.write("SampleId,SvId,Type,Ploidy,VafStart,VafEnd,TumorRCStart,TumorRCEnd");
                mPloidyCalcWriter.write(",ChrStart,PosStart,OrientStart,MaxCNStart,CNChgStart,PrevDWCountStart,NextDWCountStart");
                mPloidyCalcWriter.write(",ChrEnd,PosEnd,OrientEnd,MaxCNEnd,CNChgEnd,PrevDWCountEnd,NextDWCountEnd");
                mPloidyCalcWriter.write(",EstPloidy,EstUncertainty,MinPloidy,MaxPloidy");

                mPloidyCalcWriter.newLine();
            }

            /*
            mPloidyCalcWriter.write(String.format("%s,%d,%s,%.4f,%.4f,%.4f,%d,%d",
                    sampleId, svData.id(), svData.type(), svData.ploidy(), adjVafStart, adjVafEnd,
                    tumorReadCountStart, tumorReadCountEnd));

            mPloidyCalcWriter.write(String.format(",%s,%d,%d,%.4f,%.4f,%d,%d",
                    svData.startChromosome(), svData.startPosition(), svData.startOrientation(),
                    maxCNStart, cnChgStart, startDepthData[0], startDepthData[1]));

            mPloidyCalcWriter.write(String.format(",%s,%d,%d,%.4f,%.4f,%d,%d",
                    svData.endChromosome(), svData.endPosition(), svData.endOrientation(), maxCNEnd, cnChgEnd,
                    endDepthData != null ? endDepthData[0] : 0, endDepthData != null ? endDepthData[1] : 0));

            mPloidyCalcWriter.write(String.format(",%.2f,%.2f,%.2f,%.2f",
                    ploidyEstimate, ploidyUncertainty,
                    ploidyEstimate - ploidyUncertainty,
                    ploidyEstimate + ploidyUncertainty));
            mPloidyCalcWriter.newLine();
            */
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to ploidy recalc outputFile: {}", e.toString());
        }
    }

    public void close()
    {
        closeBufferedWriter(mLohEventWriter);
        closeBufferedWriter(mPloidyCalcWriter);
        closeBufferedWriter(mChrArmWriter);
    }

    public static void main(@NotNull final String[] args) throws ParseException, SQLException
    {
        final Options options = new Options();
        CopyNumberAnalyser.addCmdLineArgs(options);

        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(LOG_DEBUG))
        {
            Configurator.setRootLevel(Level.DEBUG);
        }

        String outputDir = formOutputPath(cmd.getOptionValue(DATA_OUTPUT_DIR));

        final DatabaseAccess dbAccess = cmd.hasOption(DB_URL) ? databaseAccess(cmd) : null;

        CopyNumberAnalyser cnAnalyser = new CopyNumberAnalyser(outputDir, dbAccess);
        cnAnalyser.loadConfig(cmd);

        cnAnalyser.runAnalysis();
        cnAnalyser.close();

        LOGGER.info("CN analysis complete");
    }


}
