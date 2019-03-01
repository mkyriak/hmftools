package com.hartwig.hmftools.svanalysis.analysis;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.purple.segment.SegmentSupport.MULTIPLE;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DEL;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INS;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INV;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.SGL;
import static com.hartwig.hmftools.svanalysis.analysis.ClusterAnnotations.annotateClusterArmSegments;
import static com.hartwig.hmftools.svanalysis.analysis.ClusterAnnotations.annotateTemplatedInsertions;
import static com.hartwig.hmftools.svanalysis.analysis.ClusterAnnotations.classifyChainedClusters;
import static com.hartwig.hmftools.svanalysis.analysis.ClusterAnnotations.findIncompleteFoldbackCandidates;
import static com.hartwig.hmftools.svanalysis.analysis.ClusterAnnotations.reportDoubleMinutes;
import static com.hartwig.hmftools.svanalysis.analysis.LinkFinder.MIN_TEMPLATED_INSERTION_LENGTH;
import static com.hartwig.hmftools.svanalysis.analysis.SvClusteringMethods.CLUSTER_REASON_COMMON_ARMS;
import static com.hartwig.hmftools.svanalysis.analysis.SvClusteringMethods.CLUSTER_REASON_FOLDBACKS;
import static com.hartwig.hmftools.svanalysis.analysis.SvClusteringMethods.CLUSTER_REASON_LOOSE_OVERLAP;
import static com.hartwig.hmftools.svanalysis.analysis.SvClusteringMethods.addClusterReason;
import static com.hartwig.hmftools.svanalysis.analysis.SvClusteringMethods.applyCopyNumberReplication;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.CHROMOSOME_ARM_P;
import static com.hartwig.hmftools.svanalysis.annotators.LineElementAnnotator.markLineCluster;
import static com.hartwig.hmftools.svanalysis.types.SvArmCluster.mergeArmClusters;
import static com.hartwig.hmftools.svanalysis.types.SvChain.CHAIN_ASSEMBLY_LINK_COUNT;
import static com.hartwig.hmftools.svanalysis.types.SvChain.CHAIN_LENGTH;
import static com.hartwig.hmftools.svanalysis.types.SvChain.CHAIN_LINK_COUNT;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_COMPLEX_CHAIN;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_LINE;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_NONE;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_SGL_PAIR_DEL;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_SGL_PAIR_DUP;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_SIMPLE_CHAIN;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_SGL_PAIR_INS;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_SIMPLE_SV;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.copyNumbersEqual;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.areSpecificClusters;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.isSpecificCluster;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.ASSEMBLY_MATCH_MATCHED;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.LINK_TYPE_TI;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.SVI_END;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.SVI_START;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.findVariantById;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.haveSameChrArms;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.isSpecificSV;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.isStart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.svanalysis.types.SvArmCluster;
import com.hartwig.hmftools.svanalysis.types.SvArmGroup;
import com.hartwig.hmftools.svanalysis.types.SvBreakend;
import com.hartwig.hmftools.svanalysis.types.SvChain;
import com.hartwig.hmftools.svanalysis.types.SvCluster;
import com.hartwig.hmftools.svanalysis.types.SvLOH;
import com.hartwig.hmftools.svanalysis.types.SvVarData;
import com.hartwig.hmftools.svanalysis.types.SvLinkedPair;
import com.hartwig.hmftools.svanalysis.types.SvaConfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClusterAnalyser {

    final SvaConfig mConfig;
    SvClusteringMethods mClusteringMethods;

    String mSampleId;
    private List<SvVarData> mAllVariants;
    List<SvCluster> mClusters;
    private ChainFinder mChainFinder;
    private LinkFinder mLinkFinder;

    private boolean mRunValidationChecks;

    PerformanceCounter mPcClustering;
    PerformanceCounter mPcChaining;
    PerformanceCounter mPcAnnotation;

    public static int SMALL_CLUSTER_SIZE = 3;
    public static int SHORT_TI_LENGTH = 1000;

    private static final Logger LOGGER = LogManager.getLogger(ClusterAnalyser.class);

    public ClusterAnalyser(final SvaConfig config, SvClusteringMethods clusteringMethods)
    {
        mConfig = config;
        mClusteringMethods = clusteringMethods;
        mClusters = Lists.newArrayList();
        mAllVariants = Lists.newArrayList();
        mSampleId = "";
        mLinkFinder = new LinkFinder();
        mChainFinder = new ChainFinder();
        mChainFinder.setLogVerbose(mConfig.LogVerbose);
        mLinkFinder.setLogVerbose(mConfig.LogVerbose);
        mRunValidationChecks = false;

        mPcClustering = new PerformanceCounter("Clustering");
        mPcChaining = new PerformanceCounter("Chaining");
        mPcAnnotation = new PerformanceCounter("Annotation");
    }

    // access for unit testing
    public final SvClusteringMethods getClusterer() { return mClusteringMethods; }
    public final ChainFinder getChainFinder() { return mChainFinder; }
    public final LinkFinder getLinkFinder() { return mLinkFinder; }

    public void setRunValidationChecks(boolean toggle) { mRunValidationChecks = toggle; }

    public void setSampleData(final String sampleId, List<SvVarData> allVariants)
    {
        mSampleId = sampleId;
        mAllVariants = allVariants;
        mClusters.clear();
    }

    public final List<SvCluster> getClusters() { return mClusters; }

    public void clusterAndAnalyse()
    {
        mClusters.clear();

        mPcClustering.start();
        mClusteringMethods.clusterByProximity(mAllVariants, mClusters);
        mPcClustering.pause();

        // mark line clusters since these are exluded from most subsequent logic
        for(SvCluster cluster : mClusters)
        {
            markLineCluster(cluster, mClusteringMethods.getProximityDistance());
        }

        if(mRunValidationChecks)
        {
            if(!mClusteringMethods.validateClustering(mClusters))
            {
                LOGGER.info("exiting with cluster-validation errors");
                return;
            }
        }

        mPcChaining.start();
        findLimitedChains();
        mPcChaining.pause();

        mPcClustering.resume();
        mClusteringMethods.mergeClusters(mSampleId, mClusters);
        mPcClustering.pause();

        // log basic clustering details
        for(SvCluster cluster : mClusters)
        {
            if(cluster.getCount() > 1)
                cluster.logDetails();
        }

        // INVs and other SV-pairs which make foldbacks are now used in the inconsistent clustering logic
        markFoldbacks();

        mPcClustering.resume();
        mergeClusters();
        mPcClustering.stop();

        mPcChaining.resume();
        findLinksAndChains();
        mPcChaining.stop();

        if(mRunValidationChecks)
        {
            if(!mClusteringMethods.validateClustering(mClusters) || !mClusteringMethods.checkClusterDuplicates(mClusters))
            {
                LOGGER.info("exiting with cluster-validation errors");
                return;
            }
        }

        mPcAnnotation.start();
        // analyseOverlappingTIs();

        annotateTemplatedInsertions(mClusters, mClusteringMethods.getChrBreakendMap());
        // checkSkippedLOHEvents();

        // final clean-up and analysis
        for(SvCluster cluster : mClusters)
        {
            if(!cluster.isResolved() && cluster.getResolvedType() != RESOLVED_TYPE_NONE)
            {
                // any cluster with a long DEL or DUP not merged can now be marked as resolved
                if(cluster.getCount() == 1 &&  cluster.getResolvedType() == RESOLVED_TYPE_SIMPLE_SV)
                    cluster.setResolved(true, RESOLVED_TYPE_SIMPLE_SV);

                // mark off any fully chained simple clusters
                if(cluster.getCount() == 1 &&  cluster.getResolvedType() == RESOLVED_TYPE_SIMPLE_CHAIN)
                    cluster.setResolved(true, RESOLVED_TYPE_SIMPLE_CHAIN);
            }

            mergeArmClusters(cluster.getArmClusters());

            // if(LOGGER.isDebugEnabled())
            //     logArmClusterData(cluster);

            reportClusterFeatures(cluster);
            annotateClusterArmSegments(cluster);
        }

        mPcAnnotation.stop();

        // validation-only: checkClusterDuplicates(mClusters);
    }

    public void findLimitedChains()
    {
        // chain small clusters and only assembled links in larger ones
        for(SvCluster cluster : mClusters)
        {
            if(cluster.getCount() == 1 && cluster.isSimpleSVs())
            {
                setClusterResolvedState(cluster);
                continue;
            }

            // more complicated clusters for now
            boolean isSimple =  cluster.getCount() <= SMALL_CLUSTER_SIZE && cluster.isConsistent() && !cluster.hasVariedCopyNumber();

            // inferred links are used to classify simple resolved types involving 2-3 SVs
            mLinkFinder.findLinkedPairs(cluster, isSimple);

            // then look for fully-linked clusters, ie chains involving all SVs
            findChains(cluster, !isSimple);

            cluster.cacheLinkedPairs();

            if(isSimple)
            {
                setClusterResolvedState(cluster);

                if(cluster.isFullyChained() && cluster.isConsistent())
                {
                    LOGGER.debug("sample({}) cluster({}) simple and consistent with {} SVs", mSampleId, cluster.id(), cluster.getCount());
                }
            }
        }
    }

    public void findLinksAndChains()
    {
        for (SvCluster cluster : mClusters)
        {
            // isSpecificCluster(cluster);

            if (cluster.isResolved() && cluster.getResolvedType() != RESOLVED_TYPE_LINE)
                continue;

            // these are either already chained or no need to chain
            if (cluster.isSimpleSingleSV() || cluster.isFullyChained() || cluster.getUniqueSvCount() < 2)
                continue;

            cluster.dissolveLinksAndChains();

            cluster.removeReplicatedSvs();
            applyCopyNumberReplication(cluster);

            // first establish links between SVs (eg TIs and DBs)
            mLinkFinder.findLinkedPairs(cluster, false);

            // then look for fully-linked clusters, ie chains involving all SVs
            findChains(cluster, false);

            cluster.cacheLinkedPairs();
            setClusterResolvedState(cluster);
            cluster.logDetails();
        }
    }

    public void mergeClusters()
    {
        // now look at merging unresolved & inconsistent clusters where they share the same chromosomal arms
        List<SvCluster> mergedClusters = Lists.newArrayList();

        mergedClusters = mergeInconsistentClusters(mergedClusters);
        boolean foundMerges = !mergedClusters.isEmpty();

        while(foundMerges)
        {
            List<SvCluster> newMergedClusters = mergeInconsistentClusters(mergedClusters);
            foundMerges = !newMergedClusters.isEmpty();

            for(SvCluster cluster : newMergedClusters)
            {
                if(!mergedClusters.contains(cluster))
                    mergedClusters.add(cluster);
            }
        }
    }

    private void findChains(SvCluster cluster, boolean assembledLinksOnly)
    {
        mChainFinder.initialise(cluster);

        // isSpecificCluster(cluster);

        if(mConfig.MaxClusterSize > 0 && (cluster.getUniqueSvCount() > mConfig.MaxClusterSize || cluster.getCount() > mConfig.MaxClusterSize * 5))
        {
            LOGGER.info("cluster({}) skipping large cluster: unique({}) replicated({})",
                    cluster.id(), cluster.getUniqueSvCount(), cluster.getCount());
            return;
        }

        mChainFinder.formClusterChains(assembledLinksOnly);
    }

    private void setClusterResolvedState(SvCluster cluster)
    {
        if(cluster.getResolvedType() != RESOLVED_TYPE_NONE)
            return;

        if(cluster.hasLinkingLineElements())
        {
            // skip further classification for now
            cluster.setResolved(true, RESOLVED_TYPE_LINE);
            return;
        }

        if (cluster.isSimpleSVs())
        {
            if(cluster.getTypeCount(DEL) + cluster.getTypeCount(DUP) == 2)
            {
                if(mClusteringMethods.markDelDupPairTypes(cluster))
                    return;
            }

            boolean hasLongSVs = false;
            for(SvVarData var : cluster.getSVs())
            {
                if(var.length() >= mClusteringMethods.getDelDupCutoffLength())
                {
                    hasLongSVs = true;
                    break;
                }
            }

            cluster.setResolved(!hasLongSVs, RESOLVED_TYPE_SIMPLE_SV);
            return;
        }

        // next simple reciprocal inversions and translocations
        if (cluster.getCount() == 2 && cluster.isConsistent())
        {
            if(cluster.getTypeCount(BND) == 2)
            {
                mClusteringMethods.markBndPairTypes(cluster);
            }
            else if(cluster.getTypeCount(INV) == 2)
            {
                mClusteringMethods.markInversionPairTypes(cluster);
            }
            else if(cluster.getTypeCount(SGL) == 2)
            {
                final SvVarData var1 = cluster.getSVs().get(0);
                final SvVarData var2 = cluster.getSVs().get(1);

                if(var1.orientation(true) != var2.orientation(true))
                {
                    long length = abs(var1.position(true) - var2.position(true));

                    if(length < MIN_TEMPLATED_INSERTION_LENGTH)
                    {
                        cluster.setResolved(true, RESOLVED_TYPE_SGL_PAIR_INS);
                    }
                    else
                    {
                        boolean v1First = var1.position(true) < var2.position(true);
                        boolean v1PosOrientation = var1.orientation(true) == 1;

                        if(v1First == v1PosOrientation)
                            cluster.setResolved(true, RESOLVED_TYPE_SGL_PAIR_DEL);
                        else
                            cluster.setResolved(true, RESOLVED_TYPE_SGL_PAIR_DUP);
                    }
                }
            }

            return;
        }

        // isSpecificCluster(cluster);

        // next clusters with which start and end on the same arm, have the same start and end orientation
        // and the same start and end copy number
        if(!cluster.getChains().isEmpty())
        {
            // set the type but don't consider long chains resolved
            if(cluster.getFoldbacks().isEmpty())
                cluster.setResolved(false, RESOLVED_TYPE_SIMPLE_CHAIN);
            else
                cluster.setResolved(false, RESOLVED_TYPE_COMPLEX_CHAIN);

            return;
        }
    }

    private List<SvCluster> mergeInconsistentClusters(List<SvCluster> existingMergedClusters)
    {
        // second round of cluster merging on more complex criteria and inconsistencies:
        // merge on foldbacks on the same arm
        // merge on links between common arms
        // merge if one cluster has footprints which overlap unresolved complex SVs

        // any merges result in a new cluster with the original clusters made into sub-clusters
        // subsequent merging keeps the 'super' clusters and adds more sub-clusters
        // the purpose of sub-clusters was to a) allow de-merging and b) preserve chains and links
        // but this could be revisited
        List<SvCluster> mergedClusters = Lists.newArrayList();

        long longDelDupCutoffLength = mClusteringMethods.getDelDupCutoffLength();

        int index1 = 0;
        while(index1 < mClusters.size())
        {
            SvCluster cluster1 = mClusters.get(index1);

            if(cluster1.isResolved())
            {
                ++index1;
                continue;
            }

            List<SvCluster> cluster1Overlaps = getTraversedClusters(cluster1);

            boolean cluster1Merged = false;
            SvCluster newCluster = null;

            int index2 = index1 + 1;
            while(index2 < mClusters.size())
            {
                SvCluster cluster2 = mClusters.get(index2);

                if(cluster2.isResolved())
                {
                    ++index2;
                    continue;
                }

                // try each merge reason in turn
                boolean canMergeClusters = false;

                canMergeClusters = canMergeClustersOnFoldbacks(cluster1, cluster2);

                if(!canMergeClusters)
                    canMergeClusters = canMergeClustersOnCommonArms(cluster1, cluster2, longDelDupCutoffLength);

                if(!canMergeClusters)
                {
                    List<SvCluster> cluster2Overlaps = getTraversedClusters(cluster2);

                    if(cluster1Overlaps.contains(cluster2) || cluster2Overlaps.contains(cluster1))
                    {
                        addClusterReason(cluster1, CLUSTER_REASON_LOOSE_OVERLAP, "");
                        addClusterReason(cluster2, CLUSTER_REASON_LOOSE_OVERLAP, "");
                        canMergeClusters = true;
                    }
                }

                if(!canMergeClusters)
                {
                    ++index2;
                    continue;
                }

                boolean cluster2Merged = false;

                if(cluster1.hasSubClusters())
                {
                    LOGGER.debug("cluster({} svs={}) merges in cluster({} svs={})",
                            cluster1.id(), cluster1.getCount(), cluster2.id(), cluster2.getCount());

                    cluster2Merged = true;

                    if(cluster2.hasSubClusters())
                    {
                        for(SvCluster subCluster : cluster2.getSubClusters())
                        {
                            cluster1.addSubCluster(subCluster);
                        }

                        existingMergedClusters.remove(cluster2);
                    }
                    else
                    {
                        cluster1.addSubCluster(cluster2);
                    }
                }
                else
                {
                    cluster1Merged = true;
                    cluster2Merged = true;

                    newCluster = new SvCluster(mClusteringMethods.getNextClusterId());

                    LOGGER.debug("new cluster({}) from merge of cluster({} svs={}) and cluster({} svs={})",
                            newCluster.id(), cluster1.id(), cluster1.getCount(), cluster2.id(), cluster2.getCount());

                    newCluster.addSubCluster(cluster1);

                    if(cluster2.hasSubClusters())
                    {
                        for(SvCluster subCluster : cluster2.getSubClusters())
                        {
                            newCluster.addSubCluster(subCluster);
                        }

                        existingMergedClusters.remove(cluster2);
                    }
                    else
                    {
                        newCluster.addSubCluster(cluster2);
                    }

                    mergedClusters.add(newCluster);
                }

                if(cluster2Merged)
                    mClusters.remove(index2);
                else
                    ++index2;

                if(cluster1Merged)
                    break;
            }

            if(cluster1Merged && newCluster != null)
            {
                // cluster has been replaced with a combined one
                mClusters.remove(index1);
                mClusters.add(index1, newCluster);
            }
            else
            {
                ++index1;
            }
        }

        return mergedClusters;
    }

    private static int MAX_FOLDBACK_NEXT_CLUSTER_DISTANCE = 5000000;

    private boolean canMergeClustersOnFoldbacks(final SvCluster cluster1, final SvCluster cluster2)
    {
        final List<SvVarData> cluster1Foldbacks = cluster1.getFoldbacks();
        final List<SvVarData> cluster2Foldbacks = cluster2.getFoldbacks();

        if(cluster1Foldbacks.isEmpty() && cluster2Foldbacks.isEmpty())
            return false;

        if(!cluster1Foldbacks.isEmpty() && !cluster2Foldbacks.isEmpty())
        {
            for (final SvVarData var1 : cluster1Foldbacks)
            {
                for (int be1 = SVI_START; be1 <= SVI_END; ++be1)
                {
                    boolean v1Start = isStart(be1);

                    if (be1 == SVI_END && var1.type() != BND)
                            continue;

                    if (var1.getFoldbackLink(v1Start).isEmpty())
                        continue;

                    for (final SvVarData var2 : cluster2Foldbacks)
                    {
                        for (int be2 = SVI_START; be2 <= SVI_END; ++be2)
                        {
                            boolean v2Start = isStart(be2);

                            if (be2 == SVI_END && var2.type() != BND)
                                    continue;

                            if (var2.getFoldbackLink(v2Start).isEmpty())
                                continue;

                            if (!var1.chromosome(v1Start).equals(var2.chromosome(v2Start)) || !var1.arm(v1Start).equals(var2.arm(v2Start)))
                                continue;

                            LOGGER.debug("cluster({}) SV({}) and cluster({}) SV({}) have foldbacks on same arm",
                                    cluster1.id(), var1.posId(), cluster2.id(), var2.posId());

                            addClusterReason(cluster1, CLUSTER_REASON_FOLDBACKS, var2.id());
                            addClusterReason(cluster2, CLUSTER_REASON_FOLDBACKS, var1.id());
                            return true;
                        }
                    }
                }
            }
        }

        final Map<String, List<SvBreakend>> chrBreakendMap = mClusteringMethods.getChrBreakendMap();

        // additionally check whether any of the foldbacks face an opposing SV in the other cluster
        // must be the next SV, less than 5M bases and
        for(int i = 0; i <= 1; ++i)
        {
            final List<SvVarData> foldbacks = (i == 0) ? cluster1Foldbacks : cluster2Foldbacks;
            final SvCluster foldbackCluster = (i == 0) ? cluster1 : cluster2;
            final SvCluster otherCluster = (i == 0) ? cluster2 : cluster1;

            for (final SvVarData var : foldbacks)
            {
                for (int be1 = SVI_START; be1 <= SVI_END; ++be1)
                {
                    boolean isStart = isStart(be1);

                    if (be1 == SVI_END && var.type() != BND)
                        continue;

                    if (var.getFoldbackLink(isStart).isEmpty())
                        continue;

                    final SvBreakend foldbackBreakend = var.getBreakend(isStart);

                    final List<SvBreakend> breakendList = chrBreakendMap.get(foldbackBreakend.chromosome());
                    boolean traverseUp = foldbackBreakend.orientation() == -1;
                    int nextIndex = traverseUp ? foldbackBreakend.getChrPosIndex() + 1 : foldbackBreakend.getChrPosIndex() - 1;

                    SvBreakend nextBreakend = getNextUnresolvedBreakend(breakendList, nextIndex, traverseUp);

                    if(nextBreakend == null || nextBreakend.getSV().getCluster() != otherCluster)
                        continue;

                    if(abs(nextBreakend.position() - foldbackBreakend.position()) > MAX_FOLDBACK_NEXT_CLUSTER_DISTANCE)
                        continue;

                    double fbPloidy = foldbackBreakend.getSV().getSvData().ploidy();
                    double nbPloidy = nextBreakend.getSV().getSvData().ploidy();

                    if(nbPloidy < fbPloidy && !copyNumbersEqual(nbPloidy, fbPloidy))
                        continue;

                    LOGGER.debug("cluster({}) foldback breakend({}) faces cluster({}) breakend({})",
                            foldbackCluster.id(), foldbackBreakend.toString(), otherCluster.id(), nextBreakend.toString());

                    return true;
                }
            }
        }

        return false;
    }

    private boolean canMergeClustersOnCommonArms(final SvCluster cluster1, final SvCluster cluster2, long armWidthCutoff)
    {
        // merge if the 2 clusters have BNDs linking the same 2 inconsistent or long arms
        areSpecificClusters(cluster1, cluster2);

        // re-check which BNDs may link arms
        cluster1.setArmLinks();
        cluster2.setArmLinks();

        // first find arm groups which are inconsistent in both clusters
        // BNDs only touching an arm in a short TI are ignored from the common arm check
        List<SvArmGroup> inconsistentArms1 = cluster1.getArmGroups().stream()
                .filter(x -> x.canLink(armWidthCutoff))
                .collect(Collectors.toList());

        List<SvArmGroup> inconsistentArms2 = cluster2.getArmGroups().stream()
                .filter(x -> x.canLink(armWidthCutoff))
                .collect(Collectors.toList());

        // now search for common BNDs where either end is in one of these inconsistent arms
        if(inconsistentArms1.isEmpty() || inconsistentArms2.isEmpty())
            return false;

        final List<SvVarData> crossArmList1 = cluster1.getUnlinkedRemoteSVs();
        final List<SvVarData> crossArmList2 = cluster2.getUnlinkedRemoteSVs();

        // now that the candidate arm groups have been established, just need to find a single BND
        // from each cluster that falls into the same par of arm groups

        for (final SvVarData var1 : crossArmList1)
        {
            for (final SvVarData var2 : crossArmList2)
            {
                if(!haveSameChrArms(var1, var2))
                    continue;

                for(final SvArmGroup armGroup1 : inconsistentArms1)
                {
                    if (!armGroup1.getSVs().contains(var1))
                        continue;

                    for (final SvArmGroup armGroup2 : inconsistentArms2)
                    {
                        if (!armGroup2.getSVs().contains(var2))
                            continue;

                        LOGGER.debug("cluster({}) and cluster({}) have common links with SV({}) and SV({})",
                                cluster1.id(), cluster2.id(), var1.posId(), var2.posId());

                        final String commonArms = var1.id() + "_" + var2.id();

                        addClusterReason(cluster1, CLUSTER_REASON_COMMON_ARMS, commonArms);
                        addClusterReason(cluster2, CLUSTER_REASON_COMMON_ARMS, commonArms);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private List<SvCluster> getTraversedClusters(final SvCluster cluster)
    {
        // find all clusters which are overlapped by consecutive same-orientation SVs in this cluster
        // and where the overlapped clusters contain opposing orientation BNDs or SGLs
        List<SvCluster> traversedClusters = Lists.newArrayList();

        if(cluster.isResolved() || cluster.isFullyChained())
            return traversedClusters;

        final Map<String, List<SvBreakend>> chrBreakendMap = cluster.getChrBreakendMap();

        for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
        {
            List<SvBreakend> breakendList = entry.getValue();

            List<SvBreakend> fullBreakendList = mClusteringMethods.getChrBreakendMap().get(entry.getKey());

            for (int i = 0; i < breakendList.size() - 1; ++i)
            {
                final SvBreakend lowerBreakend = breakendList.get(i);
                SvBreakend upperBreakend = breakendList.get(i + 1);

                boolean isFoldback = false;

                if (lowerBreakend.arm() != upperBreakend.arm())
                    continue;

                if (lowerBreakend.orientation() != upperBreakend.orientation())
                {
                    // allow for short DBs where the breakends remain in a foldback
                    if (i < breakendList.size() - 2)
                    {
                        final SvBreakend nextBreakend = breakendList.get(i + 2);

                        if (!lowerBreakend.getSV().getFoldbackLink(lowerBreakend.usesStart()).isEmpty()
                                && lowerBreakend.getSV().getFoldbackLink(lowerBreakend.usesStart())
                                .equals(nextBreakend.getSV().getFoldbackLink(nextBreakend.usesStart())))
                        {
                            isFoldback = true;
                            upperBreakend = nextBreakend;
                        }
                    }

                    if (!isFoldback)
                        continue;
                }

                final SvBreakend frontBE = lowerBreakend.orientation() == 1 ? lowerBreakend : upperBreakend;

                if (frontBE.getSV().getDBLink(frontBE.usesStart()) != null)
                {
                    // check for an overlapping short DB which would invalidate these consecutive breakends
                    if (frontBE.getSV().getDBLink(frontBE.usesStart()).length() < 0)
                        continue;
                }

                for (int j = lowerBreakend.getChrPosIndex() + 1; j <= upperBreakend.getChrPosIndex() - 1; ++j)
                {
                    final SvBreakend breakend = fullBreakendList.get(j);

                    if(breakend.getSV().type() != BND && breakend.getSV().type() != SGL)
                        continue;

                    if(breakend.orientation() == lowerBreakend.orientation())
                        continue;

                    final SvCluster otherCluster = breakend.getSV().getCluster();

                    if (otherCluster == cluster || otherCluster.isResolved())
                        continue;

                    if(!traversedClusters.contains(otherCluster))
                    {
                        //LOGGER.debug("cluster({}) breakends({} & {}) overlap cluster({}) breakend({})",
                        //        cluster.id(), lowerBreakend.toString(), upperBreakend.toString(), otherCluster.id(), breakend.toString());
                        traversedClusters.add(otherCluster);
                    }
                }
            }
        }

        return traversedClusters;
    }

    public void markFoldbacks()
    {
        for(final Map.Entry<String, List<SvBreakend>> entry : mClusteringMethods.getChrBreakendMap().entrySet())
        {
            List<SvBreakend> breakendList = entry.getValue();

            for(int i = 0; i < breakendList.size() - 1; ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                final SvBreakend nextBreakend = breakendList.get(i + 1);

                SvBreakend beFront = null; // the lower position for orientation +1 and vice versa
                SvBreakend beBack = null;

                if(breakend.orientation() == nextBreakend.orientation())
                {
                    beFront = breakend.orientation() == 1 ? breakend : nextBreakend;
                    beBack = breakend.orientation() == 1 ? nextBreakend : breakend;
                }
                else if(i < breakendList.size() - 2)
                {
                    SvBreakend postNextBreakend = breakendList.get(i + 2);

                    // check for a short overlapping deletion bridge on the either breakend
                    // that would otherwise mask this foldback
                    if(breakend.orientation() == postNextBreakend.orientation())
                    {
                        if(postNextBreakend.orientation() == 1
                        && postNextBreakend.position() - nextBreakend.position() < MIN_TEMPLATED_INSERTION_LENGTH)
                        {
                            beFront = breakend;
                            beBack = postNextBreakend;
                        }
                        else if(postNextBreakend.orientation() == -1
                        && nextBreakend.position() - breakend.position() < MIN_TEMPLATED_INSERTION_LENGTH)
                        {
                            beBack = breakend;
                            beFront = postNextBreakend;
                        }
                    }
                }

                if(beFront != null && beBack != null)
                {
                    // the foldback is invalid if it has a deletion bridge (including < 30b overhang) on the front-facing breakend
                    final SvLinkedPair dbLink = beFront.getSV().getDBLink(beFront.usesStart());
                    if(dbLink == null || dbLink.length() >= MIN_TEMPLATED_INSERTION_LENGTH)
                    {
                        checkFoldbackBreakends(beFront, beBack);
                    }
                }

                checkReplicatedBreakendFoldback(breakend);
            }
        }

        // now foldbacks are known, add other annotations about them
        for(final SvCluster cluster : mClusters)
        {
            final Map<String, List<SvBreakend>> chrBreakendMap = cluster.getChrBreakendMap();

            for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
            {
                int chromosomeFoldbackCount = 0;

                for(final SvBreakend breakend : entry.getValue())
                {
                    if (breakend.getSV().getFoldbackLink(breakend.usesStart()).isEmpty())
                        continue;

                    if (!breakend.getSV().getFoldbackLink(true).isEmpty()
                        && !breakend.getSV().getFoldbackLink(false).isEmpty() && !breakend.usesStart())
                    {
                        continue;
                    }

                    ++chromosomeFoldbackCount;
                }

                int foldbackIndex = 0;

                for(final SvBreakend breakend : entry.getValue())
                {
                    // isSpecificSV(breakend.getSV());

                    if(breakend.getSV().getFoldbackLink(breakend.usesStart()).isEmpty())
                        continue;

                    if(!breakend.getSV().getFoldbackLink(true).isEmpty()
                    && !breakend.getSV().getFoldbackLink(false).isEmpty() && !breakend.usesStart())
                    {
                        // only process each SV's foldback once where both breakends are part of it
                        continue;
                    }

                    final String existingInfo = breakend.getSV().getFoldbackLinkInfo(breakend.usesStart());

                    long armLength = SvUtilities.getChromosomalArmLength(breakend.chromosome(), breakend.arm());
                    double positionPercent;
                    int foldbackRank;

                    if(breakend.arm() == CHROMOSOME_ARM_P)
                    {
                        foldbackRank = foldbackIndex;
                        positionPercent = breakend.position() / (double)armLength;
                    }
                    else
                    {
                        foldbackRank = chromosomeFoldbackCount - foldbackIndex - 1;
                        long chromosomeLength = SvUtilities.CHROMOSOME_LENGTHS.get(breakend.chromosome());
                        long centromere = chromosomeLength - armLength;
                        positionPercent = 1 - (breakend.position() - centromere) / (double)armLength;
                    }

                    ++foldbackIndex;

                    String facesTorC = (breakend.orientation() == 1) == (breakend.arm() == CHROMOSOME_ARM_P) ? "T" : "C";

                    String foldbackInfo = String.format("%s;%s;%d;%.4f",
                            existingInfo, facesTorC, foldbackRank, positionPercent);

                    for(int be = SVI_START; be <= SVI_END; ++be)
                    {
                        boolean isStart = isStart(be);

                        if(breakend.getSV().getFoldbackLink(isStart).isEmpty())
                            continue;

                        breakend.getSV().setFoldbackLink(
                                isStart,
                                breakend.getSV().getFoldbackLink(isStart),
                                breakend.getSV().getFoldbackLen(isStart),
                                foldbackInfo);
                    }
                }
            }
        }
    }

    public static int MAX_FOLDBACK_CHAIN_LENGTH = 5000;

    private void checkFoldbackBreakends(SvBreakend beStart, SvBreakend beEnd)
    {
        // SVs are marked as being in a foldback if they are consecutive breakends,
        // have the same orientation, and are either an INV or part of a chain

        // beStart is the one with the lower position

        SvVarData varEnd = beEnd.getSV();
        SvVarData varStart = beStart.getSV();

        if(varEnd.type() == INS || varStart.type() == INS)
            return;

        isSpecificSV(varStart);

        // skip unclustered DELs & DUPs, reciprocal INV or reciprocal BNDs
        final SvCluster cluster1 = varEnd.getCluster();

        if(cluster1.isResolved() && cluster1.getTypeCount(INV) == 0)
            return;

        SvCluster cluster2 = null;

        if(varEnd.equals(varStart))
        {
            cluster2 = cluster1;
        }
        else
        {
            cluster2 = varStart.getCluster();

            // must be same cluster
            if(cluster1 != cluster2)
                return;
        }

        boolean v1Start = beEnd.usesStart();
        boolean v2Start = beStart.usesStart();

        String chainInfo = "0;0;0";

        if(varEnd.equals(varStart))
        {
            // constraint is that the ends of this INV don't link to BND taking the path off this chromosome
            final SvChain chain = cluster1.findChain(varEnd);

            if(chain != null)
            {
                int bndLinks = 0;
                for (final SvLinkedPair pair : chain.getLinkedPairs())
                {
                    if (pair.first().equals(varEnd, true) && pair.second().type() == BND)
                        ++bndLinks;
                    else if (pair.second().equals(varEnd, true) && pair.first().type() == BND)
                        ++bndLinks;

                    if (bndLinks == 2)
                        return;
                }
            }
        }
        else
        {
            // must be same cluster and part of the same chain
            if(cluster1 != cluster2)
                return;

            if(varEnd.getReplicatedCount() != varStart.getReplicatedCount())
                return;

            final SvChain chain1 = cluster1.findChain(varEnd);
            final SvChain chain2 = cluster2.findChain(varStart);

            if(chain1 == null || chain2 == null || chain1 != chain2)
                return;

            // check if a path can be walked between these 2 breakends along the chain
            // without going back through this foldback point
            int[] chainData = chain1.breakendsAreChained(varEnd, !v1Start, varStart, !v2Start);

            if(chainData[CHAIN_LINK_COUNT] == 0)
                return;

            int chainLength = chainData[CHAIN_LENGTH];

            if(chainLength > MAX_FOLDBACK_CHAIN_LENGTH)
                return;

            chainInfo = String.format("%d;%d;%d",
                    chainData[CHAIN_LINK_COUNT], chainData[CHAIN_ASSEMBLY_LINK_COUNT], chainLength);
        }

        // check copy numbers match
        double cn1 = 0;
        double cn2 = 0;
        if((beEnd.orientation() == 1 && beEnd.position() < beStart.position()) || (beEnd.orientation() == -1 && beEnd.position() > beStart.position()))
        {
            // be1 is facing away from be2, so need to take its copy number on the break side
            cn1 = varEnd.copyNumber(v1Start) - varEnd.copyNumberChange(v1Start);
            cn2 = varStart.copyNumber(v2Start);
        }
        else
        {
            cn2 = varStart.copyNumber(v2Start) - varStart.copyNumberChange(v2Start);
            cn1 = varEnd.copyNumber(v1Start);
        }

        if(!copyNumbersEqual(cn1, cn2))
            return;

        int length = (int)abs(beEnd.position() - beStart.position());

        // if either variant already has foldback info set, favour
        // a) simple inversions then
        // b) shortest length

        boolean skipFoldback = false;
        if(!varEnd.getFoldbackLink(v1Start).isEmpty() && !varEnd.equals(varStart) && varEnd.getFoldbackLen(v1Start) < length)
        {
            skipFoldback = true;
        }
        else if(!varStart.getFoldbackLink(v2Start).isEmpty() && !varEnd.equals(varStart) && varStart.getFoldbackLen(v2Start) < length)
        {
            skipFoldback = true;
        }

        if(skipFoldback)
            return;

        if(!varEnd.getFoldbackLink(v1Start).isEmpty())
            clearFoldbackInfo(varEnd.getFoldbackLink(v1Start), varEnd.id(), cluster1);

        if(!varStart.getFoldbackLink(v2Start).isEmpty())
            clearFoldbackInfo(varStart.getFoldbackLink(v2Start), varStart.id(), cluster2);

        varEnd.setFoldbackLink(v1Start, varStart.id(), length, chainInfo);
        varStart.setFoldbackLink(v2Start, varEnd.id(), length, chainInfo);

        if(varEnd.equals(varStart))
        {
            LOGGER.debug(String.format("cluster(%s) foldback inversion SV(%s) length(%d) copyNumber(%.3f)",
                    cluster1.id(), varEnd.posId(), length, cn1));
        }
        else
        {
            LOGGER.debug(String.format("cluster(%s) foldback be1(%s) be2(%s) length(%d) copyNumber(%.3f)",
                    cluster1.id(), beEnd.toString(), beStart.toString(), length, cn1));
        }
    }

    private void checkReplicatedBreakendFoldback(SvBreakend be)
    {
        // a special case where one ends of an SV connects to both ends of a single other variant
        // during a replication event and in doing so forms a foldback
        final SvVarData var = be.getSV();

        if(var.type() != BND || var.getReplicatedCount() != 2)
            return;

        if(!var.getAssemblyMatchType(true).equals(ASSEMBLY_MATCH_MATCHED)
        && !var.getAssemblyMatchType(false).equals(ASSEMBLY_MATCH_MATCHED))
        {
            return;
        }

        // check if the replicated SV has the same linked pairing
        // or if the variant forms both ends of the cluster's chain
        final SvCluster cluster = var.getCluster();
        if(cluster == null)
            return;

        for(final SvChain chain : cluster.getChains())
        {
            if(chain.getFirstSV().equals(var, true)
            && chain.getLastSV().equals(var, true)
            && chain.firstLinkOpenOnStart() == chain.lastLinkOpenOnStart())
            {
                final String chainInfo = String.format("%d;%d;%d",
                        chain.getLinkCount(), chain.getAssemblyLinkCount(), chain.getLength());

                boolean foldbackIsStart = chain.firstLinkOpenOnStart();

                var.setFoldbackLink(foldbackIsStart, var.id(), 0, chainInfo);

                LOGGER.debug("cluster({}) foldback translocation SV({}) with self on {}",
                        cluster.id(), var.posId(), foldbackIsStart ? "start" : "end");
            }
        }
    }

    private void clearFoldbackInfo(final String varId, final String matchVarId, SvCluster cluster)
    {
        SvVarData var = findVariantById(varId, cluster.getSVs());

        if(var == null)
            return;

        if(var.getFoldbackLink(true).equals(matchVarId))
            var.setFoldbackLink(true, "", -1, "");
        else
            var.setFoldbackLink(false, "", -1, "");
    }

    public void checkSkippedLOHEvents()
    {
        List<SvLOH> lohList = mClusteringMethods.getSampleLohData().get(mSampleId);
        List<SvLOH> unmatchedLohList = Lists.newArrayList();

        if(lohList != null)
            unmatchedLohList.addAll(lohList.stream().filter(x -> x.Skipped).collect(Collectors.toList()));

        int matchedLohCount = 0;

        // check if an LOH was a skipped for being a potential TI or DB
        int index = 0;
        while(index < unmatchedLohList.size())
        {
            final SvLOH lohEvent = unmatchedLohList.get(index);

            boolean matched = false;
            long lohLength = lohEvent.PosEnd - lohEvent.PosStart;

            final List<SvBreakend> breakendList = mClusteringMethods.getChrBreakendMap().get(lohEvent.Chromosome);

            for(int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                final SvVarData var = breakend.getSV();

                if(!lohEvent.StartSV.equals(var.id()))
                    continue;

                if(lohEvent.StartSV.equals(var.id()) && lohEvent.EndSV.equals(var.id()))
                {
                    LOGGER.debug("var({} {}) matches skipped LOH: chr({}) breaks({} -> {}, len={})",
                            var.id(), var.type(), lohEvent.Chromosome, lohEvent.PosStart, lohEvent.PosEnd, lohLength);

                    if(var.type() == INV || var.type() == DUP)
                        matched = true;

                    break;
                }

                for (int be1 = SVI_START; be1 <= SVI_END; ++be1)
                {
                    boolean v1Start = isStart(be1);

                    final SvLinkedPair dbPair = var.getDBLink(v1Start);

                    if (dbPair != null && dbPair.getOtherSV(var).id().equals(lohEvent.EndSV)
                    && dbPair.getBreakend(true).position() == lohEvent.PosStart
                    && dbPair.getBreakend(false).position() == lohEvent.PosEnd - 1)
                    {
                        LOGGER.debug("deletionBridge({}) matches skipped LOH: chr({}) breaks({} -> {}, len={})",
                                var.getDBLink(v1Start).toString(), lohEvent.Chromosome,
                                lohEvent.PosStart, lohEvent.PosEnd, lohLength);
                        matched = true;
                        break;
                    }

                    final SvLinkedPair tiPair = var.getLinkedPair(v1Start);

                    if (tiPair != null && tiPair.getOtherSV(var).id().equals(lohEvent.EndSV)
                    && tiPair.getBreakend(true).position() == lohEvent.PosStart
                    && tiPair.getBreakend(false).position() == lohEvent.PosEnd - 1)
                    {
                        LOGGER.debug("templatedInsertion({}) matches skipped LOH: chr({}) breaks({} -> {}, len={})",
                                var.getLinkedPair(v1Start).toString(), lohEvent.Chromosome,
                                lohEvent.PosStart, lohEvent.PosEnd, lohLength);
                        matched = true;
                        break;
                    }
                }

                if(!matched)
                {
                    // check for line and SGLs which may not have formed TIs
                    SvVarData varEnd = null;

                    if (i < breakendList.size() - 1 && breakendList.get(i + 1).getSV().id().equals(lohEvent.EndSV))
                    {
                        // should be the next SV
                        varEnd = breakendList.get(i + 1).getSV();
                    }

                    if (var.inLineElement() || (varEnd != null && varEnd.inLineElement()))
                    {
                        LOGGER.debug("line SVs({} and {}) match skipped LOH: chr({}) breaks({} -> {}, len={})",
                                var.id(), varEnd != null ? varEnd.id() : "null",
                                lohEvent.Chromosome, lohEvent.PosStart, lohEvent.PosEnd, lohLength);
                        matched = true;
                    }
                    else if (var.type() == SGL || (varEnd != null && varEnd.type() == SGL))
                    {
                        matched = true;
                    }
                }

                break;
            }

            if(matched || lohEvent.matchesSegment(MULTIPLE, true) || lohEvent.matchesSegment(MULTIPLE, false))
            {
                unmatchedLohList.remove(index);
                ++matchedLohCount;
            }
            else
            {
                ++index;
            }
        }

        if(!unmatchedLohList.isEmpty())
        {
            LOGGER.info("sample({}) has matched({}) unmatched({}) skipped LOH events",
                    mSampleId, matchedLohCount, unmatchedLohList.size());

            for(final SvLOH lohEvent : unmatchedLohList)
            {
                LOGGER.info("unmatched LOH: chr({}) breaks({} -> {}, len={}) SV start({} {}) end({} {}) {} SV",
                        lohEvent.Chromosome, lohEvent.PosStart, lohEvent.PosEnd, lohEvent.PosEnd - lohEvent.PosStart,
                        lohEvent.StartSV, lohEvent.SegStart, lohEvent.EndSV, lohEvent.SegEnd,
                        lohEvent.StartSV == lohEvent.EndSV ? "same" : "diff");
            }
        }

    }

    public void reportClusterFeatures(final SvCluster cluster)
    {
        reportDoubleMinutes(cluster, mClusteringMethods.getChrBreakendMap());

        checkLooseFoldbacks(cluster);

        classifyChainedClusters(cluster, mClusteringMethods.getProximityDistance());

        // findIncompleteFoldbackCandidates(mSampleId, cluster, mClusteringMethods.getChrBreakendMap(), mClusteringMethods.getChrCopyNumberMap());
    }

    private final SvBreakend getNextUnresolvedBreakend(final List<SvBreakend> breakendList, int startIndex, boolean traverseUp)
    {
        int index = startIndex;

        while(index >= 0 && index < breakendList.size())
        {
            final SvBreakend breakend = breakendList.get(index);
            final SvCluster cluster = breakend.getSV().getCluster();

            if (!cluster.isResolved())
                return breakend;

            if(traverseUp)
                ++index;
            else
                --index;
        }

        return null;
    }

    private void checkLooseFoldbacks(final SvCluster cluster)
    {
        if (cluster.isResolved() || cluster.isFullyChained())
            return;

        final Map<String, List<SvBreakend>> chrBreakendMap = cluster.getChrBreakendMap();

        for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
        {
            List<SvBreakend> breakendList = entry.getValue();

            for (int i = 0; i < breakendList.size() - 1; ++i)
            {
                final SvBreakend lowerBreakend = breakendList.get(i);
                SvBreakend upperBreakend = breakendList.get(i + 1);

                boolean isFoldback = false;

                if (lowerBreakend.arm() != upperBreakend.arm())
                    continue;

                if (lowerBreakend.orientation() != upperBreakend.orientation())
                {
                    // allow for short DBs where the breakends remain in a foldback
                    if (i < breakendList.size() - 2)
                    {
                        final SvBreakend nextBreakend = breakendList.get(i + 2);

                        if (!lowerBreakend.getSV().getFoldbackLink(lowerBreakend.usesStart()).isEmpty()
                                && lowerBreakend.getSV().getFoldbackLink(lowerBreakend.usesStart())
                                .equals(nextBreakend.getSV().getFoldbackLink(nextBreakend.usesStart())))
                        {
                            isFoldback = true;
                            upperBreakend = nextBreakend;
                        }
                    }

                    if (!isFoldback)
                        continue;
                }

                final SvBreakend frontBE = lowerBreakend.orientation() == 1 ? lowerBreakend : upperBreakend;
                final SvBreakend backBE = lowerBreakend.orientation() == 1 ? upperBreakend : lowerBreakend;

                if (frontBE.getSV().getDBLink(frontBE.usesStart()) != null)
                {
                    // check for an overlapping short DB which would invalidate these consecutive breakends
                    if (frontBE.getSV().getDBLink(frontBE.usesStart()).length() < 0)
                        continue;
                }

                final SvVarData frontSv = frontBE.getSV();
                final SvVarData backSv = backBE.getSV();

                frontSv.setConsecBEStart(backSv.origId(), frontBE.usesStart());
                backSv.setConsecBEStart(frontSv.origId(), backBE.usesStart());
            }
        }
    }

    public void logStats()
    {
        mPcClustering.logStats(false);
        mPcChaining.logStats(false);
        mPcAnnotation.logStats(false);
    }

}
