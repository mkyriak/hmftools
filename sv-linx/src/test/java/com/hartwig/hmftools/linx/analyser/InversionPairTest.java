package com.hartwig.hmftools.linx.analyser;

import static com.hartwig.hmftools.linx.types.ResolvedType.DEL_TI;
import static com.hartwig.hmftools.linx.types.ResolvedType.DUP_TI;
import static com.hartwig.hmftools.linx.types.ResolvedType.FB_INV_PAIR;
import static com.hartwig.hmftools.linx.types.ResolvedType.RECIP_INV;
import static com.hartwig.hmftools.linx.types.ResolvedType.RECIP_INV_DEL_DUP;
import static com.hartwig.hmftools.linx.types.ResolvedType.RECIP_INV_DUPS;
import static com.hartwig.hmftools.linx.types.SvaConstants.SHORT_TI_LENGTH;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static Utils.SvTestRoutines.createBnd;
import static Utils.SvTestRoutines.createDel;
import static Utils.SvTestRoutines.createInv;

import java.util.List;

import com.hartwig.hmftools.common.purple.segment.SegmentSupport;
import com.hartwig.hmftools.linx.cn.LohEvent;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.junit.Test;

import Utils.LinxTester;

public class InversionPairTest
{
    @Test
    public void testReciprocalInversions()
    {
        // basic reciprocal inversion with 2 DSBs - outer breakends overlap
        LinxTester tester = new LinxTester();

        SvVarData var1 = createInv(tester.nextVarId(), "1", 1000, 10000, 1);
        SvVarData var2 = createInv(tester.nextVarId(), "1", 1500, 10500, -1);

        tester.addAndCluster(var1, var2);

        assertEquals(1, tester.Analyser.getClusters().size());
        SvCluster cluster = tester.getClusters().get(0);

        assertTrue(cluster.isResolved());
        assertTrue(cluster.getResolvedType() == RECIP_INV);
        assertTrue(cluster.getChains().isEmpty());

        // test again but with an overlapping DB at one end
        var1 = createInv(tester.nextVarId(), "1", 100, 10010, 1);
        var2 = createInv(tester.nextVarId(), "1", 90, 10000, -1);

        tester.addAndCluster(var1, var2);

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.getClusters().get(0);

        assertTrue(cluster.isResolved());
        assertTrue(cluster.getResolvedType() == RECIP_INV);
        assertTrue(cluster.getChains().isEmpty());

        // require splitting a longer chain
        var1 = createBnd(tester.nextVarId(), "1", 1000, 1, "2", 100, -1);
        var2 = createBnd(tester.nextVarId(), "1", 10000, 1, "2", 200, 1);

        SvVarData var3 = createBnd(tester.nextVarId(), "1", 1500, -1, "3", 100, -1);
        SvVarData var4 = createBnd(tester.nextVarId(), "1", 10500, -1, "3", 200, 1);

        tester.clearClustersAndSVs();
        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);
        tester.AllVariants.add(var3);
        tester.AllVariants.add(var4);
        tester.preClusteringInit();
        tester.Analyser.clusterAndAnalyse();

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.getClusters().get(0);

        assertTrue(cluster.isResolved());
        assertTrue(cluster.getResolvedType() == RECIP_INV);
        assertEquals(2, cluster.getChains().size());
        assertFalse(cluster.getChains().stream().anyMatch(x -> x.getLinkedPairs().stream().anyMatch(y -> y.length() > SHORT_TI_LENGTH)));
    }

    @Test
    public void testFacingInversions()
    {
        LinxTester tester = new LinxTester();

        SvVarData var1 = createInv(tester.nextVarId(), "1", 1000, 2000, -1);
        SvVarData var2 = createInv(tester.nextVarId(), "1", 5000, 6000, 1);

        tester.addAndCluster(var1, var2);

        assertEquals(1, tester.Analyser.getClusters().size());
        SvCluster cluster = tester.getClusters().get(0);

        assertTrue(!cluster.isResolved());
        assertEquals(FB_INV_PAIR, cluster.getResolvedType());
        assertEquals(1, cluster.getChains().size());

        // test again but with a short TI in one of the inversions
        var1 = createBnd(tester.nextVarId(), "1", 1000, -1, "2", 1500, 1);
        var2 = createBnd(tester.nextVarId(), "1", 2000, -1, "2", 1000, -1);
        SvVarData var3 = createInv(tester.nextVarId(), "1", 5000, 6000, 1);

        tester.clearClustersAndSVs();
        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);
        tester.AllVariants.add(var3);
        tester.preClusteringInit();
        tester.Analyser.clusterAndAnalyse();

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.getClusters().get(0);

        assertTrue(!cluster.isResolved());
        assertEquals(FB_INV_PAIR, cluster.getResolvedType());
        assertEquals(1, cluster.getChains().size());
    }

    @Test
    public void testInnerOverlapInversions()
    {
        // one of inner breakends overlaps with the other INV
        // if the resultant long TI is either very long, LOH-bounded or has an INV-overlap < 100K, then it's a DUP_TI
        // otherwise it's 2 unconnected SVs and is a RECIP_INV_DUPs
        LinxTester tester = new LinxTester();

        // first with a uniform ploidy and long TI
        SvVarData var1 = createInv(tester.nextVarId(), "1", 110000, 350000, 1);
        SvVarData var2 = createInv(tester.nextVarId(), "1", 1000, 200000, -1);

        tester.addAndCluster(var1, var2);

        assertEquals(1, tester.Analyser.getClusters().size());
        SvCluster cluster = tester.getClusters().get(0);

        assertTrue(!cluster.isResolved());
        assertEquals(DUP_TI, cluster.getResolvedType());

        // test again but with the TI in LOH bounds
        var1 = createInv(tester.nextVarId(), "1", 50000, 350000, 1);
        var2 = createInv(tester.nextVarId(), "1", 1000, 200000, -1);

        tester.CnDataLoader.getLohData().add(new LohEvent( var2.chromosome(true), 1, var2.position(true),
                SegmentSupport.TELOMERE.toString(), var2.typeStr(), 1, LohEvent.CN_DATA_NO_SV, var2.id()));

        tester.addAndCluster(var1, var2);

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.getClusters().get(0);

        assertTrue(!cluster.isResolved());
        assertEquals(DUP_TI, cluster.getResolvedType());

        // neither case, is resolved as a RECIP_INV_DUPS
        tester.CnDataLoader.getLohData().clear();

        tester.addAndCluster(var1, var2);

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.getClusters().get(0);

        assertTrue(!cluster.isResolved());
        assertEquals(RECIP_INV_DUPS, cluster.getResolvedType());
    }

    @Test
    public void testEnclosingInversions()
    {
        // one INV enclosed another INV
        // if the resultant long TI is either very long, LOH-bounded or has an INV-overlap < 100K, then it's a DEL_TI
        // otherwise it's 2 unconnected SVs and is a RECIP_INV_DEL_DUP
        LinxTester tester = new LinxTester();

        // first with a uniform ploidy and long TI
        SvVarData var1 = createInv(tester.nextVarId(), "1", 1000, 200000, 1);
        SvVarData var2 = createInv(tester.nextVarId(), "1", 5000, 50000, -1);

        tester.addAndCluster(var1, var2);

        assertEquals(1, tester.Analyser.getClusters().size());
        SvCluster cluster = tester.getClusters().get(0);

        assertTrue(!cluster.isResolved());
        assertEquals(DEL_TI, cluster.getResolvedType());

        // test again but with the TI in LOH bounds
        var1 = createInv(tester.nextVarId(), "1", 1000, 200000, 1);
        var2 = createInv(tester.nextVarId(), "1", 5000, 150000, -1);

        tester.CnDataLoader.getLohData().add(new LohEvent( var1.chromosome(false), var1.position(false), 1000000,
                var1.typeStr(), SegmentSupport.TELOMERE.toString(), 1, var1.id(), LohEvent.CN_DATA_NO_SV));

        tester.addAndCluster(var1, var2);

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.getClusters().get(0);

        assertTrue(!cluster.isResolved());
        assertEquals(DEL_TI, cluster.getResolvedType());

        // neither case, is resolved as a RECIP_INV_DUPS
        tester.CnDataLoader.getLohData().clear();

        tester.addAndCluster(var1, var2);

        assertEquals(1, tester.Analyser.getClusters().size());
        cluster = tester.getClusters().get(0);

        assertTrue(!cluster.isResolved());
        assertEquals(RECIP_INV_DEL_DUP, cluster.getResolvedType());
    }

}