package com.hartwig.hmftools.linx.analysis;

import static com.hartwig.hmftools.linx.LinxConfig.LNX_LOGGER;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.UNDER_CLUSTERING;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.annotateClusterDeletions;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.annotateReplicationBeforeRepair;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.reportUnderclustering;
import static com.hartwig.hmftools.linx.analysis.ClusteringPrep.annotateNearestSvData;
import static com.hartwig.hmftools.linx.analysis.ClusteringPrep.associateBreakendCnEvents;
import static com.hartwig.hmftools.linx.analysis.ClusteringPrep.populateChromosomeBreakendMap;
import static com.hartwig.hmftools.linx.analysis.ClusteringPrep.setSimpleVariantLengths;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.DOUBLE_MINUTES;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.annotateClusterChains;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.annotateTemplatedInsertions;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.runAnnotation;
import static com.hartwig.hmftools.linx.analysis.SvClassification.isSimpleSingleSV;
import static com.hartwig.hmftools.linx.analysis.SimpleClustering.checkClusterDuplicates;
import static com.hartwig.hmftools.linx.chaining.ChainJcnLimits.DELETED_TOTAL;
import static com.hartwig.hmftools.linx.chaining.ChainJcnLimits.RANGE_TOTAL;
import static com.hartwig.hmftools.linx.chaining.LinkFinder.createAssemblyLinkedPairs;
import static com.hartwig.hmftools.linx.types.ResolvedType.DOUBLE_MINUTE;
import static com.hartwig.hmftools.linx.types.ResolvedType.LINE;
import static com.hartwig.hmftools.linx.types.ResolvedType.NONE;
import static com.hartwig.hmftools.linx.types.ResolvedType.SIMPLE_GRP;
import static com.hartwig.hmftools.linx.types.LinxConstants.SHORT_DB_LENGTH;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.isStart;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.linx.annotators.LineElementAnnotator;
import com.hartwig.hmftools.linx.chaining.ChainFinder;
import com.hartwig.hmftools.linx.chaining.LinkFinder;
import com.hartwig.hmftools.linx.cn.CnDataLoader;
import com.hartwig.hmftools.linx.cn.LohEvent;
import com.hartwig.hmftools.linx.types.DoubleMinuteData;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvVarData;
import com.hartwig.hmftools.linx.LinxConfig;

public class ClusterAnalyser {

    private final LinxConfig mConfig;
    private final ClusteringState mState;

    private final SvFilters mFilters;
    private final SimpleClustering mSimpleClustering;
    private final ComplexClustering mComplexClustering;

    private CnDataLoader mCnDataLoader;
    private final DoubleMinuteFinder mDmFinder;
    private final BfbFinder mBfbFinder;
    private LineElementAnnotator mLineElementAnnotator;

    private String mSampleId;
    private final List<SvCluster> mClusters;
    private final List<SvVarData> mAllVariants;
    private final ChainFinder mChainFinder;

    private boolean mRunValidationChecks;

    PerformanceCounter mPcClustering;
    PerformanceCounter mPcChaining;

    public static int SMALL_CLUSTER_SIZE = 3;

    public ClusterAnalyser(final LinxConfig config)
    {
        mConfig = config;
        mState = new ClusteringState();
        mClusters = Lists.newArrayList();

        mFilters = new SvFilters(mState);
        mSimpleClustering = new SimpleClustering(mState, mConfig);
        mComplexClustering = new ComplexClustering(mState, mClusters, mSimpleClustering);

        mCnDataLoader = null;
        mLineElementAnnotator = null;
        mSampleId = "";
        mAllVariants = Lists.newArrayList();
        mChainFinder = new ChainFinder();
        mDmFinder = new DoubleMinuteFinder(mState.getChrBreakendMap());
        mBfbFinder = new BfbFinder();

        if(mConfig.hasMultipleSamples())
        {
            mChainFinder.getDiagnostics().setOutputDir(mConfig.OutputDataPath, mConfig.Output.LogChainingMaxSize);

            mDmFinder.setOutputDir(mConfig.OutputDataPath);
        }

        mChainFinder.setUseAllelePloidies(true); // can probably remove and assume always in place
        mChainFinder.setLogVerbose(mConfig.LogVerbose);

        mRunValidationChecks = false; // emabled in unit tests and after changes to merging-rule flow

        mPcClustering = new PerformanceCounter("Clustering");
        mPcChaining = new PerformanceCounter("Chaining");
    }

    public final ClusteringState getState() { return mState; }

    public void setLineAnnotator(final LineElementAnnotator lineAnnotator)
    {
        mLineElementAnnotator = lineAnnotator;
    }

    public void setCnDataLoader(CnDataLoader cnDataLoader)
    {
        mCnDataLoader = cnDataLoader;
        mState.setSampleCnEventData(mCnDataLoader.getLohData(), mCnDataLoader.getHomLossData());

        mDmFinder.setCopyNumberAnalyser(cnDataLoader);
        mBfbFinder.setCopyNumberAnalyser(cnDataLoader);
        mComplexClustering.setCopyNumberAnalyser(cnDataLoader);
    }

    public void setGeneCollection(final EnsemblDataCache geneDataCache)
    {
        mDmFinder.setGeneTransCache(geneDataCache);
    }

    // access for unit testing
    public final ChainFinder getChainFinder() { return mChainFinder; }

    public void setRunValidationChecks(boolean toggle) { mRunValidationChecks = toggle; }

    public void setSampleData(final String sampleId, List<SvVarData> allVariants)
    {
        mSampleId = sampleId;
        mAllVariants.clear();
        mAllVariants.addAll(allVariants);
        mClusters.clear();
        mSimpleClustering.initialise(sampleId);
        mChainFinder.setSampleId(sampleId);
    }

    public final List<SvCluster> getClusters() { return mClusters; }

    public void preClusteringPreparation()
    {
        mState.reset();

        populateChromosomeBreakendMap(mAllVariants, mState);
        mFilters.applyFilters();

        annotateNearestSvData(mState.getChrBreakendMap());

        LinkFinder.findDeletionBridges(mState.getChrBreakendMap());

        setSimpleVariantLengths(mState);
    }

    public boolean clusterAndAnalyse()
    {
        mClusters.clear();
        mDmFinder.clear();

        mPcClustering.start();
        mFilters.clusterExcludedVariants(mClusters);
        mSimpleClustering.clusterByProximity(mClusters);
        mClusters.stream().filter(x -> x.getSvCount() > 1).forEach(x -> x.updateClusterDetails());
        mPcClustering.pause();

        // mark line clusters since these are excluded from most subsequent logic
        mClusters.forEach(x -> mLineElementAnnotator.markLineCluster(x));

        associateBreakendCnEvents(mSampleId, mState);

        if(mRunValidationChecks)
        {
            if(!mSimpleClustering.validateClustering(mClusters))
            {
                LNX_LOGGER.info("exiting with cluster-validation errors");
                return false;
            }
        }

        mPcChaining.start();
        findLimitedChains();
        mPcChaining.pause();

        mPcClustering.resume();
        mSimpleClustering.mergeClusters(mClusters);
        mPcClustering.pause();

        // log basic clustering details
        mClusters.stream().filter(x -> x.getSvCount() > 1).forEach(SvCluster::logDetails);

        // INVs and other SV-pairs which make foldbacks are now used in the inconsistent clustering logic
        FoldbackFinder.markFoldbacks(mState.getChrBreakendMap());

        // subclonal clusters won't be merged any further
        mClusters.forEach(x -> x.markSubclonal());

        mPcClustering.resume();
        mComplexClustering.applyRules(mSampleId);
        mSimpleClustering.mergeLongDelDupClusters(mClusters);
        mPcClustering.stop();

        mPcChaining.resume();
        dissolveSimpleGroups();
        findLinksAndChains();
        mPcChaining.stop();

        if(mRunValidationChecks)
        {
            if(!mSimpleClustering.validateClustering(mClusters) || !checkClusterDuplicates(mClusters))
            {
                LNX_LOGGER.warn("exiting with cluster-validation errors");
                return false;
            }
        }

        // final clean-up and analysis

        // re-check foldbacks amongst newly formed chains and then DM status
        FoldbackFinder.markFoldbacks(mState.getChrBreakendMap(), true);

        for(SvCluster cluster : mClusters)
        {
            if(!cluster.getDoubleMinuteSVs().isEmpty())
                mDmFinder.analyseCluster(cluster, true);

            if(cluster.getResolvedType() != DOUBLE_MINUTE && !cluster.isResolved())
                mBfbFinder.analyseCluster(cluster);

            if(!cluster.isResolved() && cluster.getResolvedType() != NONE)
            {
                // any cluster with a long DEL or DUP not merged can now be marked as resolved
                if(cluster.getSvCount() == 1 && cluster.getResolvedType().isSimple())
                    cluster.setResolved(true, cluster.getResolvedType());
            }

            cluster.cacheLinkedPairs();
            cluster.buildArmClusters();
        }

        return true;
    }

    public void findLimitedChains()
    {
        // chain small clusters and only assembled links in larger ones
        for(SvCluster cluster : mClusters)
        {
            if(isSimpleSingleSV(cluster))
            {
                mDmFinder.analyseCluster(cluster);
                setClusterResolvedState(cluster, false);
                continue;
            }

            // more complicated clusters for now
            boolean isSimple = cluster.getSvCount() <= SMALL_CLUSTER_SIZE && cluster.isConsistent() && !cluster.hasVariedJcn();

            cluster.setAssemblyLinkedPairs(createAssemblyLinkedPairs(cluster));
            cluster.setJcnReplication(mConfig.ChainingSvLimit);

            if(isSimple)
                mDmFinder.analyseCluster(cluster);

            // then look for fully-linked clusters, ie chains involving all SVs
            findChains(cluster, !isSimple);

            if(isSimple)
            {
                setClusterResolvedState(cluster, false);

                if(cluster.isFullyChained(true))
                {
                    LNX_LOGGER.debug("cluster({}) simple and consistent with {} SVs", cluster.id(), cluster.getSvCount());
                }
            }
        }
    }

    private void findLinksAndChains()
    {
        for (SvCluster cluster : mClusters)
        {
            if (cluster.getResolvedType() == LINE) // only simple assembly links for LINE clusters
                continue;

            if (cluster.getResolvedType() != NONE) // any cluster previously resolved and not modified does not need to be chained again
                continue;

            // these are either already chained or no need to chain
            if (isSimpleSingleSV(cluster) || cluster.isFullyChained(false) || cluster.getSvCount() < 2)
            {
                setClusterResolvedState(cluster, true);
                continue;
            }

            cluster.dissolveLinksAndChains();

            // look for and mark clusters has DM candidates, which can subsequently affect chaining
            mDmFinder.analyseCluster(cluster, true);

            cluster.setJcnReplication(mConfig.ChainingSvLimit);

            // no need to re-find assembled TIs

            // then look for fully-linked clusters, ie chains involving all SVs
            findChains(cluster, false);

            setClusterResolvedState(cluster, true);
            cluster.logDetails();
        }
    }

    private void dissolveSimpleGroups()
    {
        // break apart any clusters of simple SVs which aren't likely or required to be chained
        // based on the presence of assmebled links and linking LOH events
        List<SvCluster> simpleGroups = mClusters.stream().filter(x -> x.getResolvedType() == SIMPLE_GRP).collect(Collectors.toList());

        // if all links are assembled or joined in an LOH or have a short DB then keep the group
        for(SvCluster cluster : simpleGroups)
        {
            final List<LohEvent> lohEvents = cluster.getLohEvents().stream()
                    .filter(x -> x.doubleSvEvent() && x.isValid())
                    .filter(x -> x.StartSV != x.EndSV)
                    .collect(Collectors.toList());

            int assemblyLinks = 0;
            int lohLinks = 0;
            int dbLinks = 0;

            final List<SvVarData> discardSVs = Lists.newArrayList();

            for(SvVarData var : cluster.getSVs())
            {
                // assembled
                if(!var.getAssembledLinkedPairs(true).isEmpty() || !var.getAssembledLinkedPairs(false).isEmpty())
                {
                    ++assemblyLinks;
                    continue;
                }

                // in an LOH
                if(!lohEvents.isEmpty() && lohEvents.stream().anyMatch(x -> x.StartSV == var.id() || x.EndSV == var.id()))
                {
                    ++lohLinks;
                    continue;
                }

                // in a short DB with another SV in this cluster
                boolean inShortDB = false;

                for(int se = SE_START; se <= SE_END; ++se)
                {
                    boolean isStart = isStart(se);

                    if (var.getDBLink(isStart) != null && var.getDBLink(isStart).length() <= SHORT_DB_LENGTH
                            && cluster.getSVs().contains(var.getDBLink(isStart).getOtherSV(var)))
                    {
                        inShortDB = true;
                        break;
                    }
                }

                if(inShortDB)
                {
                    ++dbLinks;
                    continue;
                }

                discardSVs.add(var);
            }

            if(discardSVs.isEmpty())
            {
                LNX_LOGGER.debug("cluster({}: {}) simple group kept: assembled({}) inLOH({}) shortDB({})",
                        cluster.id(), cluster.getDesc(), assemblyLinks, lohLinks, dbLinks);
                continue;
            }

            if(discardSVs.size() >= cluster.getSvCount() - 1) // cannot just leave a single SV in a cluster
            {
                LNX_LOGGER.debug("cluster({}: {}) de-merging {} simple SVs", cluster.id(), cluster.getDesc(), discardSVs.size());
                mClusters.remove(cluster);
            }
            else
            {
                LNX_LOGGER.debug("cluster({}: {}) de-merging {} simple SVs from total({})",
                        cluster.id(), cluster.getDesc(), discardSVs.size(), cluster.getSvCount());

                for(SvVarData var : discardSVs)
                {
                    cluster.removeVariant(var);
                }

                cluster.logDetails();
            }

            for(SvVarData var : discardSVs)
            {
                SvCluster newCluster = new SvCluster(mState.getNextClusterId());
                var.clearClusteringData();
                newCluster.addVariant(var);

                mDmFinder.analyseCluster(newCluster);

                setClusterResolvedState(newCluster, true);
                mClusters.add(newCluster);
            }
        }
    }

    private void setClusterResolvedState(SvCluster cluster, boolean isFinal)
    {
        SvClassification.setClusterResolvedState(cluster, isFinal,
                mState.getDelCutoffLength(), mState.getDupCutoffLength(), mConfig.ProximityDistance);
    }

    private void findChains(SvCluster cluster, boolean assembledLinksOnly)
    {
        int svCount = cluster.getSvCount();

        if(mConfig.ChainingSvLimit > 0 && svCount > mConfig.ChainingSvLimit)
        {
            LNX_LOGGER.debug("sample({}) skipping large cluster({}) with SV counts: unique({}) replicated({})",
                    mSampleId, cluster.id(), cluster.getSvCount(), svCount);
            return;
        }

        cluster.getChains().clear();
        mChainFinder.initialise(cluster);
        mChainFinder.formChains(assembledLinksOnly);
        mChainFinder.addChains(cluster);

        if(!assembledLinksOnly)
            mChainFinder.getDiagnostics().diagnoseChains();

        final long[] rangeData = mChainFinder.calcRangeData();

        if(rangeData != null)
        {
            cluster.getMetrics().ValidAlleleJcnSegmentPerc = mChainFinder.getValidAllelePloidySegmentPerc();
            cluster.getMetrics().TraversedRange = rangeData[RANGE_TOTAL];
            cluster.getMetrics().TotalDeleted = rangeData[DELETED_TOTAL];
        }

        mChainFinder.clear(); // release any refs to clusters and SVs
    }

    public void annotateClusters()
    {
        // final clean-up and analysis
        mClusters.forEach(x -> annotateTemplatedInsertions(x, mState.getChrBreakendMap()));

        mClusters.forEach(this::reportClusterFeatures);

        if(runAnnotation(mConfig.RequiredAnnotations, UNDER_CLUSTERING))
        {
            reportUnderclustering(mSampleId, mClusters, mState.getChrBreakendMap());
        }
    }

    private void reportClusterFeatures(final SvCluster cluster)
    {
        annotateClusterChains(cluster);
        annotateClusterDeletions(cluster, mState.getChrBreakendMap());
        annotateReplicationBeforeRepair(cluster);

        ClusterMetrics metrics = cluster.getMetrics();

        if(metrics.TotalDeleted == 0)
            metrics.TotalDeleted = metrics.TotalDBLength;

        if(runAnnotation(mConfig.RequiredAnnotations, DOUBLE_MINUTES))
            mDmFinder.reportCluster(mSampleId, cluster);
    }

    public void writeComponentSvHeaders(BufferedWriter writer) throws IOException
    {
        // allow specialised sub-components to add per-SV data
        if(runAnnotation(mConfig.RequiredAnnotations, DOUBLE_MINUTES))
            writer.write(",DMSV");
    }

    public void writeComponentSvData(BufferedWriter writer, final SvVarData var) throws IOException
    {
        if(runAnnotation(mConfig.RequiredAnnotations, DOUBLE_MINUTES))
        {
            final DoubleMinuteData dmData = mDmFinder.getDoubleMinutes().get(var.getCluster().id());
            boolean isDmSv = dmData != null && dmData.SVs.contains(var);
            writer.write(String.format(",%s", isDmSv));
        }
    }

    public void close()
    {
        mDmFinder.close();
        mChainFinder.getDiagnostics().close();
        mSimpleClustering.close();
    }

    public void logStats()
    {
        mPcClustering.logStats();
        mPcChaining.logStats();
    }

}
