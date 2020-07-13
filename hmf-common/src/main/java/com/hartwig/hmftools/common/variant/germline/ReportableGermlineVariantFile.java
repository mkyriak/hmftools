package com.hartwig.hmftools.common.variant.germline;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.fusion.ImmutableReportableGeneFusion;
import com.hartwig.hmftools.common.fusion.Transcript;
import com.hartwig.hmftools.common.variant.CodingEffect;

import org.jetbrains.annotations.NotNull;

public final class ReportableGermlineVariantFile
{
    private static final String DELIMITER = "\t";

    private static final String FILE_EXTENSION = ".reportable_germline_variant.tsv";

    @NotNull
    public static String generateFilename(@NotNull final String basePath, @NotNull final String sample)
    {
        return basePath + File.separator + sample + FILE_EXTENSION;
    }

    @NotNull
    public static List<ReportableGermlineVariant> read(final String filePath) throws IOException
    {
        return fromLines(Files.readAllLines(new File(filePath).toPath()));
    }

    public static void write(@NotNull final String filename, @NotNull List<ReportableGermlineVariant> dataList) throws IOException
    {
        Files.write(new File(filename).toPath(), toLines(dataList));
    }

    @NotNull
    private static List<String> toLines(@NotNull final List<ReportableGermlineVariant> dataList)
    {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        dataList.stream().map(ReportableGermlineVariantFile::toString).forEach(lines::add);
        return lines;
    }

    @NotNull
    private static List<ReportableGermlineVariant> fromLines(@NotNull List<String> lines)
    {
        return lines.stream()
                .skip(1)
                .map(ReportableGermlineVariantFile::fromString).collect(toList());
    }

    @NotNull
    private static String header() {
        return new StringJoiner(DELIMITER)
                .add("passFilter")
                .add("gene")
                .add("ref")
                .add("alt")
                .add("codingEffect")
                .add("chromosome")
                .add("position")
                .add("hgvsProtein")
                .add("hgvsCoding")
                .add("totalReadCount")
                .add("alleleReadCount")
                .add("adjustedVaf")
                .add("adjustedCopyNumber")
                .add("biallelic")
                .toString();
    }

    @NotNull
    public static String toString(@NotNull final ReportableGermlineVariant data)
    {
        return new StringJoiner(DELIMITER)
                .add(String.valueOf(data.passFilter()))
                .add(String.valueOf(data.gene()))
                .add(String.valueOf(data.ref()))
                .add(String.valueOf(data.alt()))
                .add(String.valueOf(data.codingEffect()))
                .add(String.valueOf(data.chromosome()))
                .add(String.valueOf(data.position()))
                .add(String.valueOf(data.hgvsProtein()))
                .add(String.valueOf(data.hgvsCoding()))
                .add(String.valueOf(data.totalReadCount()))
                .add(String.valueOf(data.alleleReadCount()))
                .add(String.valueOf(data.adjustedVaf()))
                .add(String.valueOf(data.adjustedCopyNumber()))
                .add(String.valueOf(data.biallelic()))
                .toString();
    }

    @NotNull
    private static ReportableGermlineVariant fromString(@NotNull final String data)
    {
        String[] values = data.split(DELIMITER);

        int index = 0;

        return ImmutableReportableGermlineVariant.builder()
                .gene(values[index++])
                .ref(values[index++])
                .alt(values[index++])
                .codingEffect(CodingEffect.valueOf(values[index++]))
                .chromosome(values[index++])
                .position(Long.parseLong(values[index++]))
                .hgvsProtein(values[index++])
                .hgvsCoding(values[index++])
                .totalReadCount(Integer.parseInt(values[index++]))
                .alleleReadCount(Integer.parseInt(values[index++]))
                .adjustedVaf(Double.parseDouble(values[index++]))
                .adjustedCopyNumber(Double.parseDouble(values[index++]))
                .biallelic(Boolean.parseBoolean(values[index++]))
                .build();
    }
}
