package com.hartwig.hmftools.knowledgebaseimporter.transvar

import com.hartwig.hmftools.knowledgebaseimporter.output.ActionableEvent
import com.hartwig.hmftools.knowledgebaseimporter.output.GenomicRangeEvent
import com.hartwig.hmftools.knowledgebaseimporter.output.SomaticVariantEvent
import htsjdk.samtools.reference.IndexedFastaSequenceFile
import org.apache.logging.log4j.LogManager
import java.util.regex.Pattern

private val logger = LogManager.getLogger("GDNAVariantExtractor")

private val variantPositionsPattern = Pattern.compile("([0-9]+)(?:_([0-9]+))?.*")

private const val candidateSnvString = "candidate_snv_variants="
private const val candidateMnvString = "candidate_mnv_variants="
private const val candidatesString = "candidates="
private const val leftAlignedGDnaString = "left_align_gDNA="

private const val infoDelimiter = ";"
private const val variantDelimiter = ","
private const val chrIdentifier = "chr"
private const val chrDelimiter = ":"
private const val gDnaDelimiter = "g."

fun extractVariants(gene: String, transcript: String, transvarOutput: TransvarOutput,
                    reference: IndexedFastaSequenceFile): List<ActionableEvent> {
    val chromosome = extractChromosome(transvarOutput.coordinates)
    return if (chromosome.isEmpty()) {
        logger.warn("could not extract chromosome from line: $transvarOutput. Skipping")
        emptyList()
    } else {
        listOfNotNull(parseOptimalCandidate(gene, transcript, chromosome, transvarOutput, reference)) +
                parseInfo(gene, transcript, chromosome, transvarOutput.info, reference)
    }
}

private fun parseInfo(gene: String, transcript: String, chromosome: String, info: String,
                      reference: IndexedFastaSequenceFile): List<ActionableEvent> {
    val snvsGDna = infoVariantsAfter(info, candidateSnvString)
    val mnvsGDna = infoVariantsAfter(info, candidateMnvString)
    val indelsGDna = infoVariantsAfter(info, candidatesString).map { it.split("/")[2] }
    val mergedVariants = snvsGDna + mnvsGDna + indelsGDna
    return extractVariants(gene, transcript, chromosome, mergedVariants, reference)
}

private fun infoVariantsAfter(info: String, startDelimiter: String): List<String> {
    return info.substringAfter(startDelimiter, "").substringBefore(infoDelimiter).split(variantDelimiter).filterNot { it.isEmpty() }
}

private fun parseOptimalCandidate(gene: String, transcript: String, chromosome: String, transvarOutput: TransvarOutput,
                                  reference: IndexedFastaSequenceFile): ActionableEvent? {
    val variantGDna = if (transvarOutput.info.contains(leftAlignedGDnaString)) {
        transvarOutput.info.substringAfter(leftAlignedGDnaString).substringAfter(gDnaDelimiter, "").substringBefore(";")
    } else {
        transvarOutput.coordinates.substringAfter(gDnaDelimiter, "").substringBefore("/")
    }
    return extractVariant(gene, transcript, chromosome, variantGDna, reference)
}

private fun extractVariants(gene: String, transcript: String, chromosome: String, gDnaVariants: List<String>,
                            reference: IndexedFastaSequenceFile): List<ActionableEvent> {
    return gDnaVariants.map { it.substringAfter(gDnaDelimiter) }.mapNotNull {
        extractVariant(gene, transcript, chromosome, it, reference)
    }
}

fun extractVariant(gene: String, transcript: String, gDnaVariant: String, reference: IndexedFastaSequenceFile): ActionableEvent? {
    val chromosome = extractChromosome(gDnaVariant)
    return extractVariant(gene, transcript, chromosome, gDnaVariant.substringAfter(gDnaDelimiter), reference)
}

private fun extractVariant(gene: String, transcript: String, chromosome: String, variantGDna: String,
                           reference: IndexedFastaSequenceFile): ActionableEvent? {
    return try {
        when {
            variantGDna.contains(">")                                  -> extractSnv(gene, chromosome, variantGDna)
            variantGDna.contains("del") && variantGDna.contains("ins") -> extractMnv(gene, chromosome, variantGDna, reference)
            variantGDna.contains("ins")                                -> extractInsert(gene, chromosome, variantGDna, reference)
            variantGDna.contains("dup")                                -> extractDup(gene, chromosome, variantGDna, reference)
            variantGDna.contains("del")                                -> extractDelete(gene, chromosome, variantGDna, reference)
            variantGDna.matches("[0-9]+_[0-9]+".toRegex())             -> extractRange(gene, transcript, chromosome, variantGDna)
            else                                                       -> {
                logger.warn("variant $chromosome: $variantGDna could not be mapped to any known type")
                null
            }
        }
    } catch (t: Throwable) {
        logger.warn("Could not create variant from $chromosome: $variantGDna; error: $t")
        null
    }
}

fun extractChromosome(coordinates: String): String {
    return coordinates.substringAfter(chrIdentifier, "").substringBefore(chrDelimiter)
}

private fun extractPositions(variant: String): Pair<Long, Long?> {
    val matcher = variantPositionsPattern.matcher(variant.substringAfter(gDnaDelimiter))
    matcher.find()
    return Pair(matcher.group(1).toLong(), matcher.group(2)?.toLongOrNull())
}

// MIVO: extract SNV from gDna variant of the form: 133738357T>C
private fun extractSnv(gene: String, chromosome: String, variantGDna: String): SomaticVariantEvent {
    val (start, _) = extractPositions(variantGDna)
    val ref = variantGDna.substringBefore(">").last().toString()
    val alt = variantGDna.substringAfter(">").first().toString()
    return SomaticVariantEvent(gene, chromosome, start.toString(), ref, alt)
}

// MIVO: extract MNV from gDna variant of the form: 105239404_105239405delinsGC or 133748289_133748290delTCinsGT
private fun extractMnv(gene: String, chromosome: String, variantGDna: String, reference: IndexedFastaSequenceFile): SomaticVariantEvent {
    val (start, end) = extractPositions(variantGDna)
    return if (variantGDna.contains("delins")) {
        val endPosition = end ?: start
        val alt = variantGDna.substringAfter("delins")
        val ref = reference.getSubsequenceAt(chromosome, start, endPosition).baseString
        SomaticVariantEvent(gene, chromosome, start.toString(), ref, alt)
    } else {
        val ref = variantGDna.substringAfter("del").substringBefore("ins")
        val alt = variantGDna.substringAfter("ins")
        return SomaticVariantEvent(gene, chromosome, start.toString(), ref, alt)
    }
}

// MIVO: extract Insert from gDna variant of the form: 41201160dupA
private fun extractDup(gene: String, chromosome: String, variantGDna: String, reference: IndexedFastaSequenceFile): SomaticVariantEvent {
    val (start, _) = extractPositions(variantGDna)
    val position = start - 1
    val ref = reference.getSubsequenceAt(chromosome, position, position).baseString
    val insertedBases = variantGDna.substringAfter("dup")
    val alt = ref + insertedBases
    return SomaticVariantEvent(gene, chromosome, position.toString(), ref, alt)
}

// MIVO: extract Insert from gDna variant of the form: 32930598_32930599insC
private fun extractInsert(gene: String, chromosome: String, variantGDna: String,
                          reference: IndexedFastaSequenceFile): SomaticVariantEvent? {
    val (start, _) = extractPositions(variantGDna)
    val ref = reference.getSubsequenceAt(chromosome, start, start).baseString
    val insertedBases = variantGDna.substringAfter("ins")
    val alt = ref + insertedBases
    return if (alt.contains("N")) {
        null
    } else {
        SomaticVariantEvent(gene, chromosome, start.toString(), ref, alt)
    }
}

// MIVO: extract Delete from gDna variant of the form: 55152094_55152105delCATCATGCATGA or 55152095_55152106del12
private fun extractDelete(gene: String, chromosome: String, variantGDna: String,
                          reference: IndexedFastaSequenceFile): SomaticVariantEvent? {
    val (start, _) = extractPositions(variantGDna)
    val position = start - 1
    val deletedBases = variantGDna.substringAfter("del")
    val deletedBasesCount = deletedBases.toIntOrNull()
    val (ref, alt) = if (deletedBasesCount == null) {
        val alt = reference.getSubsequenceAt(chromosome, position, position).baseString
        val ref = alt + deletedBases
        Pair(ref, alt)
    } else {
        if (deletedBasesCount > 20) {
            logger.warn("Skipping deletion of more than 20 bases for variant $chromosome: $variantGDna")
            return null
        }
        val ref = reference.getSubsequenceAt(chromosome, position, position + deletedBasesCount).baseString
        val alt = ref.first().toString()
        Pair(ref, alt)
    }
    return SomaticVariantEvent(gene, chromosome, position.toString(), ref, alt)
}

private fun extractRange(gene: String, transcript: String, chromosome: String, variantGDna: String): GenomicRangeEvent {
    val (start, end) = extractPositions(variantGDna)
    return GenomicRangeEvent(gene, transcript, chromosome, start.toString(), end.toString(), transcript)
}
