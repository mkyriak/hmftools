DROP TABLE IF EXISTS structuralVariantCluster;
DROP TABLE IF EXISTS copyNumberCluster;

ALTER TABLE copyNumber
    CHANGE structuralVariantSupport segmentStartSupport varchar(255) NOT NULL,
    ADD COLUMN segmentEndSupport varchar(255) NOT NULL AFTER segmentStartSupport,
    DROP COLUMN ratioSupport,
    ADD COLUMN copyNumberMethod varchar(255) NOT NULL AFTER copyNumber;

ALTER TABLE copyNumberRegion
    CHANGE structuralVariantSupport segmentStartSupport varchar(255) NOT NULL,
    ADD COLUMN actualTumorBaf DOUBLE PRECISION not null AFTER modelTumorRatio,
    ADD COLUMN gcContent DOUBLE PRECISION not null AFTER observedTumorRatioCount,
    DROP COLUMN highConfidenceBaf,
    DROP COLUMN highConfidenceCopyNumber,
    CHANGE status germlineStatus varchar(255) NOT NULL,
    ADD COLUMN svCluster BOOLEAN NOT NULL AFTER germlineStatus;

DROP TABLE IF EXISTS copyNumberGermline;
CREATE TABLE copyNumberGermline
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    start int not null,
    end int not null,
    segmentStartSupport varchar(255) NOT NULL,
    segmentEndSupport varchar(255) NOT NULL,
    bafCount int not null,
    observedBaf DOUBLE PRECISION not null,
    actualBaf DOUBLE PRECISION not null,
    copyNumber DOUBLE PRECISION not null,
    copyNumberMethod varchar(255) NOT NULL,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

ALTER TABLE geneCopyNumber
    CHANGE regions somaticRegions int not null,
    ADD COLUMN germlineHomRegions int not null AFTER somaticRegions,
    ADD COLUMN germlineHetRegions int not null AFTER germlineHomRegions;


ALTER TABLE structuralVariant
    ADD COLUMN ploidy DOUBLE PRECISION AFTER endAF,
    ADD COLUMN adjustedStartAF DOUBLE PRECISION AFTER ploidy,
    ADD COLUMN adjustedEndAF DOUBLE PRECISION AFTER adjustedStartAF,
    ADD COLUMN adjustedStartCopyNumber DOUBLE PRECISION AFTER adjustedEndAF,
    ADD COLUMN adjustedEndCopyNumber DOUBLE PRECISION AFTER adjustedStartCopyNumber,
    ADD COLUMN adjustedStartCopyNumberChange DOUBLE PRECISION AFTER adjustedEndCopyNumber,
    ADD COLUMN adjustedEndCopyNumberChange DOUBLE PRECISION AFTER adjustedStartCopyNumberChange;


ALTER TABLE purity
    ADD COLUMN version VARCHAR(255) NOT NULL AFTER modified;