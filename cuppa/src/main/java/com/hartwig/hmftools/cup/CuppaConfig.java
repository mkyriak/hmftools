package com.hartwig.hmftools.cup;

import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.OUTPUT_DIR;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.parseOutputDir;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.addDatabaseCmdLineArgs;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.createDatabaseAccess;

import com.hartwig.hmftools.cup.rna.RnaExpression;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CuppaConfig
{
    // reference data
    public final String RefSampleDataFile;
    public final String RefSnvCountsFile;
    public final String RefSvPercFile;
    public final String RefSigContributionFile;
    public final String RefFeaturePrevFile;
    public final String RefTraitPercFile;
    public final String RefTraitRateFile;
    public final String RefSnvPosFreqFile;
    public final String RefFeatureAvgFile;
    public final String RefRnaCancerExpFile;
    public final String RefRnaGeneCancerPercFile;

    // sample data, if not sourced from the database
    public final String SampleDataDir;

    public final String SampleDataFile;
    public final String SampleFeatureFile;
    public final String SampleTraitsFile;
    public final String SampleSnvCountsFile;
    public final String SampleSnvPosFreqFile;
    public final String SampleSigContribFile;
    public final String SampleSomaticVcf;
    public final String SampleSvFile;
    public final String SampleRnaExpFile;

    // database access
    public final DatabaseAccess DbAccess;

    public final boolean WriteSimilarities;
    public final String OutputDir;
    public final String OutputFileId;

    // config strings
    public static final String SAMPLE_DATA_DIR = "sample_data_dir";

    public static final String SPECIFIC_SAMPLE_DATA = "sample_data";
    public static final String SAMPLE_DATA_FILE = "sample_data_file";
    private static final String SAMPLE_FEAT_FILE = "sample_feature_file";
    private static final String SAMPLE_TRAITS_FILE = "sample_traits_file";
    private static final String SAMPLE_SNV_COUNTS_FILE = "sample_snv_counts_file";
    private static final String SAMPLE_SNV_POS_FREQ_FILE = "sample_snv_pos_freq_file";
    private static final String SAMPLE_SIG_CONTRIB_FILE = "sample_sig_contrib_file";
    private static final String SAMPLE_SOMATIC_VCF = "sample_somatic_vcf";
    private static final String SAMPLE_SV_FILE = "sample_sv_file";
    private static final String SAMPLE_RNA_EXP_FILE = "sample_rna_exp_file";

    public static final String REF_SAMPLE_DATA_FILE = "ref_sample_data_file";
    public static final String REF_SNV_COUNTS_FILE = "ref_snv_counts_file";
    private static final String REF_SIG_CONTRIB_FILE = "ref_sig_contrib_file";
    private static final String REF_FEAT_PREV_FILE = "ref_feature_prev_file";
    private static final String REF_TRAIT_PERC_FILE = "ref_trait_perc_file";
    private static final String REF_TRAIT_RATE_FILE = "ref_trait_rate_file";
    private static final String REF_SV_PERC_FILE = "ref_sv_perc_file";
    private static final String REF_SNV_POS_FREQ_FILE = "ref_snv_pos_freq_file";
    private static final String REF_FEAT_AVG_FILE = "ref_feature_avg_file";
    private static final String REF_RNA_CANCER_EXP_FILE = "ref_rna_cancer_exp_file";
    private static final String REF_RNA_GENE_CANCER_PERC_FILE = "ref_rna_gene_cancer_file";

    public static final String WRITE_SIMS = "write_similarities";
    public static final String OUTPUT_FILE_ID = "output_file_id";
    public static final String LOG_DEBUG = "log_debug";

    public static final Logger CUP_LOGGER = LogManager.getLogger(CuppaConfig.class);

    public static final String CANCER_SUBTYPE_OTHER = "Other";
    public static final String DATA_DELIM = ",";
    public static final String SUBSET_DELIM = ";";

    public CuppaConfig(final CommandLine cmd)
    {
        SampleDataDir = cmd.getOptionValue(SAMPLE_DATA_DIR, "");

        SampleDataFile = cmd.getOptionValue(SAMPLE_DATA_FILE, "");
        SampleTraitsFile = cmd.getOptionValue(SAMPLE_TRAITS_FILE, "");
        SampleFeatureFile = cmd.getOptionValue(SAMPLE_FEAT_FILE, "");
        SampleSnvCountsFile = cmd.getOptionValue(SAMPLE_SNV_COUNTS_FILE, "");
        SampleSnvPosFreqFile = cmd.getOptionValue(SAMPLE_SNV_POS_FREQ_FILE, "");
        SampleSigContribFile = cmd.getOptionValue(SAMPLE_SIG_CONTRIB_FILE, "");
        SampleSvFile = cmd.getOptionValue(SAMPLE_SV_FILE, "");
        SampleRnaExpFile = cmd.getOptionValue(SAMPLE_RNA_EXP_FILE, "");
        SampleSomaticVcf = cmd.getOptionValue(SAMPLE_SOMATIC_VCF, "");

        RefSampleDataFile = cmd.getOptionValue(REF_SAMPLE_DATA_FILE, "");
        RefSnvCountsFile = cmd.getOptionValue(REF_SNV_COUNTS_FILE, "");
        RefSigContributionFile = cmd.getOptionValue(REF_SIG_CONTRIB_FILE, "");
        RefFeaturePrevFile = cmd.getOptionValue(REF_FEAT_PREV_FILE, "");
        RefTraitPercFile = cmd.getOptionValue(REF_TRAIT_PERC_FILE, "");
        RefSvPercFile = cmd.getOptionValue(REF_SV_PERC_FILE, "");
        RefTraitRateFile = cmd.getOptionValue(REF_TRAIT_RATE_FILE, "");
        RefSnvPosFreqFile = cmd.getOptionValue(REF_SNV_POS_FREQ_FILE, "");
        RefFeatureAvgFile = cmd.getOptionValue(REF_FEAT_AVG_FILE, "");
        RefRnaCancerExpFile = cmd.getOptionValue(REF_RNA_CANCER_EXP_FILE, "");
        RefRnaGeneCancerPercFile = cmd.getOptionValue(REF_RNA_GENE_CANCER_PERC_FILE, "");

        OutputDir = parseOutputDir(cmd);
        OutputFileId = cmd.getOptionValue(OUTPUT_FILE_ID, "");
        WriteSimilarities = Boolean.parseBoolean(cmd.getOptionValue(WRITE_SIMS, "true"));

        DbAccess = createDatabaseAccess(cmd);
    }

    public boolean isValid()
    {
        return !OutputDir.isEmpty();
    }

    public String formOutputFilename(final String fileId)
    {
        String outputFile = OutputDir + "CUP";

        if(!OutputFileId.isEmpty())
            outputFile += "." + OutputFileId;

        return outputFile + "." + fileId + ".csv";
    }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(SPECIFIC_SAMPLE_DATA, true, "Specific sample in form 'SampleId;CancerType;CancerSubtype' (last 2 optional)");
        options.addOption(SAMPLE_DATA_DIR, true, "Directory containing standard sample files from pipeline");

        options.addOption(SAMPLE_DATA_FILE, true, "Sample data file");

        options.addOption(SAMPLE_SNV_COUNTS_FILE, true, "Sample SNV counts");
        options.addOption(SAMPLE_SNV_POS_FREQ_FILE, true, "Sample SNV position frequence counts");
        options.addOption(SAMPLE_SIG_CONTRIB_FILE, true, "Sample signature contributions");

        options.addOption(SAMPLE_FEAT_FILE, true, "Cohort features file (drivers, fusions and viruses)");
        options.addOption(SAMPLE_TRAITS_FILE, true, "Cohort sample traits file");
        options.addOption(SAMPLE_SV_FILE, true, "Cohort SV data");
        options.addOption(SAMPLE_RNA_EXP_FILE, true, "Sample RNA gene expression TPMs");
        options.addOption(SAMPLE_SOMATIC_VCF, true, "Sample somatic VCF");

        options.addOption(REF_SAMPLE_DATA_FILE, true, "Reference sample data");
        options.addOption(REF_SNV_COUNTS_FILE, true, "Reference SNV sample counts");
        options.addOption(REF_SIG_CONTRIB_FILE, true, "SNV signatures");
        options.addOption(REF_FEAT_PREV_FILE, true, "Reference driver prevalence");
        options.addOption(REF_SV_PERC_FILE, true, "Reference SV percentiles file");
        options.addOption(REF_TRAIT_PERC_FILE, true, "Reference traits percentiles file");
        options.addOption(REF_TRAIT_RATE_FILE, true, "Reference traits rates file");
        options.addOption(REF_SNV_POS_FREQ_FILE, true, "Reference SNV position frequency file");
        options.addOption(REF_FEAT_AVG_FILE, true, "Reference features per sample file");
        options.addOption(REF_RNA_CANCER_EXP_FILE, true, "Reference RNA gene expression file");
        options.addOption(REF_RNA_GENE_CANCER_PERC_FILE, true, "Reference RNA gene cancer percentiles file");

        options.addOption(WRITE_SIMS, true, "Cohort-only - write top-20 CSS similarities to file");

        addDatabaseCmdLineArgs(options);
        RnaExpression.addCmdLineArgs(options);

        options.addOption(OUTPUT_DIR, true, "Path to output files");
        options.addOption(OUTPUT_FILE_ID, true, "Output file ID");
        options.addOption(LOG_DEBUG, false, "Sets log level to Debug, off by default");
    }

}