package com.hartwig.hmftools.common.variant.structural.annotation;

public class ExonData
{
    public final int TransId;
    public final long ExonStart;
    public final long ExonEnd;
    public final int ExonRank;
    public final int ExonPhase;
    public final int ExonPhaseEnd;

    public ExonData(int transId, long exonStart, long exonEnd, int exonRank, int exonPhase, int exonPhaseEnd)
    {
        TransId = transId;
        ExonStart = exonStart;
        ExonEnd = exonEnd;
        ExonRank = exonRank;
        ExonPhase = exonPhase;
        ExonPhaseEnd = exonPhaseEnd;
    }

    public boolean overlaps(final ExonData other)
    {
        // assumes not the same exon
        return !(ExonStart > other.ExonEnd || ExonEnd < other.ExonStart);
    }

}