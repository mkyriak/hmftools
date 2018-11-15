package com.hartwig.hmftools.common.hotspot;

import static com.hartwig.hmftools.common.sam.SAMRecords.basesDeletedAfterPosition;
import static com.hartwig.hmftools.common.sam.SAMRecords.basesInsertedAfterPosition;
import static com.hartwig.hmftools.common.sam.SAMRecords.containsDelete;
import static com.hartwig.hmftools.common.sam.SAMRecords.containsInsert;
import static com.hartwig.hmftools.common.sam.SAMRecords.getBaseQuality;

import com.google.common.annotations.VisibleForTesting;
import com.hartwig.hmftools.common.sam.SAMRecords;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;

public class VariantHotspotEvidenceFactory {

    static final int MIN_BASE_QUALITY = 13;
    private static final int MIN_MAPPING_QUALITY = 1;

    private final IndexedFastaSequenceFile sequenceFile;
    private final SamReader samReader;
    private final int requiredBuffer;

    public VariantHotspotEvidenceFactory(@NotNull final IndexedFastaSequenceFile sequenceFile, @NotNull final SamReader samReader) {
        this.sequenceFile = sequenceFile;
        this.samReader = samReader;
        requiredBuffer = 1;
    }

    @NotNull
    public VariantHotspotEvidence indel(@NotNull final VariantHotspot hotspot) {
        final ModifiableVariantHotspotEvidence result = create(hotspot);

        int length = hotspot.ref().length();
        int minAllignment = (int) Math.max(0, hotspot.position() - requiredBuffer);
        int maxAllignment = (int) Math.min(sequenceFile.getSequence(hotspot.chromosome()).length(), hotspot.position() + length);

        final SAMRecordIterator samIterator = samReader.queryOverlapping(hotspot.chromosome(), minAllignment, maxAllignment);

        while (samIterator.hasNext()) {
            final SAMRecord record = samIterator.next();

            if (!samRecordOverlapsVariant(minAllignment, maxAllignment, record) || !samRecordMeetsQualityRequirements(record)
                    || !startPositionValid(hotspot, record)) {
                continue;
            }

            //            findEvidenceOfMNV(result, bufferStartPosition, refSequence, hotspot, record);
        }

        return result;

    }

    @NotNull
    public VariantHotspotEvidence mnv(@NotNull final VariantHotspot hotspot) {
        final ModifiableVariantHotspotEvidence result = create(hotspot);

        int mnvLength = Math.max(hotspot.ref().length(), hotspot.alt().length());
        int hotspotStartPosition = (int) hotspot.position();
        int hotspotEndPosition = (int) hotspot.position() + mnvLength - 1;

        int bufferStartPosition = Math.max(0, hotspotStartPosition - requiredBuffer);
        int bufferEndPosition = Math.min(sequenceFile.getSequence(hotspot.chromosome()).length(), hotspotEndPosition + requiredBuffer);

        final SAMRecordIterator samIterator = samReader.queryOverlapping(hotspot.chromosome(), bufferStartPosition, bufferEndPosition);
        final String refSequence = requiredBuffer == 0
                ? Strings.EMPTY
                : sequenceFile.getSubsequenceAt(hotspot.chromosome(), bufferStartPosition, bufferEndPosition).getBaseString();

        while (samIterator.hasNext()) {
            final SAMRecord record = samIterator.next();

            if (!samRecordOverlapsVariant(bufferStartPosition, bufferEndPosition, record) || !samRecordMeetsQualityRequirements(record)
                    || !startPositionValid(hotspot, record)) {
                continue;
            }

            findEvidenceOfMNV(result, bufferStartPosition, refSequence, hotspot, record);
        }

        return result;
    }

    static ModifiableVariantHotspotEvidence findEvidenceOfInsert(@NotNull final ModifiableVariantHotspotEvidence builder, @NotNull final VariantHotspot hotspot, @NotNull final SAMRecord record) {
        assert (hotspot.isSimpleInsert());

        int hotspotStartPosition = (int) hotspot.position();
        int recordStartPosition = record.getReadPositionAtReferencePosition(hotspotStartPosition);
        if (recordStartPosition == 0) {
            return builder;
        }

        int recordStartQuality = getBaseQuality(record, recordStartPosition);
        if (containsInsert(record, hotspotStartPosition, hotspot.alt())) {
            int quality = SAMRecords.getAvgBaseQuality(record, recordStartPosition, hotspot.alt().length());
            if (quality < MIN_BASE_QUALITY) {
                return builder;
            }
            return builder.setReadDepth(builder.readDepth() + 1)
                    .setAltQuality(builder.altQuality() + quality)
                    .setAltSupport(builder.altSupport() + 1);
        }

        if (recordStartQuality < MIN_BASE_QUALITY) {
            return builder;
        }

        int deletedBases = basesDeletedAfterPosition(record, hotspotStartPosition);
        if (deletedBases == 0 && record.getReadString().charAt(recordStartQuality - 1) == hotspot.ref().charAt(0)) {
            builder.setRefSupport(builder.refSupport() + 1);
        }

        return builder.setReadDepth(builder.readDepth() + 1);
    }

    static ModifiableVariantHotspotEvidence findEvidenceOfDelete(@NotNull final ModifiableVariantHotspotEvidence builder, @NotNull final VariantHotspot hotspot, @NotNull final SAMRecord record) {
        assert (hotspot.isSimpleDelete());

        int hotspotStartPosition = (int) hotspot.position();
        int recordStartPosition = record.getReadPositionAtReferencePosition(hotspotStartPosition);
        if (recordStartPosition == 0) {
            return builder;
        }

        int recordStartQuality = getBaseQuality(record, recordStartPosition);

        if (containsDelete(record, hotspotStartPosition, hotspot.ref())) {
            int quality =
                    record.getReadLength() > recordStartPosition ? getBaseQuality(record, recordStartPosition + 1) : recordStartQuality;
            if (quality < MIN_BASE_QUALITY) {
                return builder;
            }
            return builder.setReadDepth(builder.readDepth() + 1)
                    .setAltQuality(builder.altQuality() + quality)
                    .setAltSupport(builder.altSupport() + 1);
        }

        if (recordStartQuality < MIN_BASE_QUALITY) {
            return builder;
        }

        int insertedBases = basesInsertedAfterPosition(record, hotspotStartPosition);
        if (insertedBases == 0 && record.getReadString().charAt(recordStartQuality - 1) == hotspot.ref().charAt(0)) {
            builder.setRefSupport(builder.refSupport() + 1);
        }

        return builder.setReadDepth(builder.readDepth() + 1);
    }

    @VisibleForTesting
    @NotNull
    static ModifiableVariantHotspotEvidence findEvidenceOfMNV(@NotNull final ModifiableVariantHotspotEvidence builder, int start,
            @NotNull final String refSequence, @NotNull final VariantHotspot hotspot, @NotNull final SAMRecord record) {

        int hotspotStartPosition = (int) hotspot.position();
        int hotspotLength = Math.max(hotspot.ref().length(), hotspot.alt().length());

        int recordStartPosition = record.getReadPositionAtReferencePosition(hotspotStartPosition);
        int recordStartQuality = SAMRecords.getBaseQuality(record, recordStartPosition);

        if (isVariantPartOfLargerMNV(start, refSequence, hotspot, record)) {
            return recordStartQuality < MIN_BASE_QUALITY ? builder : builder.setReadDepth(builder.readDepth() + 1);
        }

        for (int i = 0; i < hotspotLength; i++) {
            int readPosition = record.getReadPositionAtReferencePosition(hotspotStartPosition + i);
            boolean isDeleted = readPosition == 0;
            boolean isInserted = record.getReferencePositionAtReadPosition(recordStartPosition + i) == 0;

            if (isInserted || isDeleted) {
                if (recordStartQuality < MIN_BASE_QUALITY) {
                    return builder;
                }
                return builder.setReadDepth(builder.readDepth() + 1);
            }
        }

        final String samBases = record.getReadString().substring(recordStartPosition - 1, recordStartPosition - 1 + hotspotLength);
        if (samBases.equals(hotspot.alt())) {
            int altQuality = SAMRecords.getAvgBaseQuality(record, recordStartPosition, hotspotLength);
            if (altQuality < MIN_BASE_QUALITY) {
                return builder;
            }

            builder.setAltQuality(builder.altQuality() + altQuality).setAltSupport(builder.altSupport() + 1);
        } else if (samBases.equals(hotspot.ref())) {
            builder.setRefSupport(builder.refSupport() + 1);
        }

        return builder.setReadDepth(builder.readDepth() + 1);
    }

    private boolean startPositionValid(@NotNull final VariantHotspot hotspot, @NotNull final SAMRecord record) {
        return record.getReadPositionAtReferencePosition((int) hotspot.position()) != 0;
    }

    private boolean samRecordOverlapsVariant(int start, int end, @NotNull final SAMRecord record) {
        return record.getAlignmentStart() <= start && record.getAlignmentEnd() >= end;
    }

    private boolean samRecordMeetsQualityRequirements(@NotNull final SAMRecord record) {
        return record.getMappingQuality() >= MIN_MAPPING_QUALITY && !record.getDuplicateReadFlag();
    }

    @VisibleForTesting
    static boolean isVariantPartOfLargerMNV(int start, @NotNull final String refSequence, @NotNull final VariantHotspot hotspot,
            @NotNull final SAMRecord record) {

        int mvnLength = Math.max(hotspot.ref().length(), hotspot.alt().length());
        int hotspotStartPosition = (int) hotspot.position();
        int hotspotEndPosition = (int) hotspot.position() + mvnLength - 1;

        int requiredBuffer = hotspotStartPosition - start;
        if (requiredBuffer == 0) {
            return false;
        }

        return isStartPartOfLargerMNV(start, record, refSequence.substring(0, requiredBuffer)) || isEndPartOfLargerMNV(
                hotspotEndPosition + 1, record, refSequence.substring(requiredBuffer + mvnLength));

    }

    private static boolean isStartPartOfLargerMNV(int samOffset, @NotNull final SAMRecord samRecord, @NotNull final String refSequence) {
        int variantStart = samOffset + refSequence.length();

        int startReadPosition = samRecord.getReadPositionAtReferencePosition(variantStart);
        assert (startReadPosition != 0);

        for (int i = 1; i <= refSequence.length(); i++) {
            int bufferReadPosition = samRecord.getReadPositionAtReferencePosition(variantStart - i);

            if (bufferReadPosition == 0) {
                return false; // DEL
            }

            if (samRecord.getReferencePositionAtReadPosition(startReadPosition - i) == 0) {
                return false; // INS
            }

            int refSequenceIndex = refSequence.length() - i;
            if (samRecord.getReadString().charAt(bufferReadPosition - 1) != refSequence.charAt(refSequenceIndex)) {
                return true;
            }

        }

        return false;
    }

    private static boolean isEndPartOfLargerMNV(int samOffset, @NotNull final SAMRecord samRecord, @NotNull final String refSequence) {
        int variantEnd = samOffset - 1;

        int endReadPosition = samRecord.getReadPositionAtReferencePosition(variantEnd);
        assert (endReadPosition != 0);

        for (int i = 1; i <= refSequence.length(); i++) {
            int bufferReadPosition = samRecord.getReadPositionAtReferencePosition(variantEnd + i);

            if (bufferReadPosition == 0) {
                return false; // DEL
            }

            if (samRecord.getReferencePositionAtReadPosition(endReadPosition + i) == 0) {
                return false; // INS
            }

            if (samRecord.getReadString().charAt(bufferReadPosition - 1) != refSequence.charAt(i - 1)) {
                return true;
            }

        }

        return false;
    }

    @NotNull
    static ModifiableVariantHotspotEvidence create(@NotNull final VariantHotspot hotspot) {
        return ModifiableVariantHotspotEvidence.create()
                .from(hotspot)
                .setRef(hotspot.ref())
                .setAlt(hotspot.alt())
                .setAltQuality(0)
                .setAltSupport(0)
                .setRefSupport(0)
                .setReadDepth(0);
    }

}
