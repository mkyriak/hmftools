package com.hartwig.hmftools.patientdb;

import java.io.IOException;
import java.sql.SQLException;

import com.hartwig.hmftools.common.metrics.WGSMetrics;
import com.hartwig.hmftools.common.metrics.WGSMetricsFile;
import com.hartwig.hmftools.common.pharmacogenetics.PGXCalls;
import com.hartwig.hmftools.common.pharmacogenetics.PGXCallsFile;
import com.hartwig.hmftools.common.pharmacogenetics.PGXGenotype;
import com.hartwig.hmftools.common.pharmacogenetics.PGXGenotypeFile;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class LoadPgxData {
    private static final Logger LOGGER = LogManager.getLogger(LoadPgxData.class);

    private static final String DB_USER = "db_user";
    private static final String DB_PASS = "db_pass";
    private static final String DB_URL = "db_url";

    private static final String SAMPLE = "sample";
    private static final String PGX_CALLS_TXT = "pgx_calls_txt";
    private static final String PGX_GENOTYPE_TXT = "pgx_genotype_txt";



    public static void main(@NotNull final String[] args) throws ParseException, SQLException, IOException {
        Options options = createOptions();
        CommandLine cmd = new DefaultParser().parse(options, args);

        String userName = cmd.getOptionValue(DB_USER);
        String password = cmd.getOptionValue(DB_PASS);
        String databaseUrl = cmd.getOptionValue(DB_URL);

        String sample = cmd.getOptionValue(SAMPLE);

        String pgxCallsFileName = cmd.getOptionValue(PGX_CALLS_TXT);
        String pgxGenotypeFileName = cmd.getOptionValue(PGX_GENOTYPE_TXT);

        if (Utils.anyNull(userName, password, databaseUrl, sample, pgxCallsFileName, pgxGenotypeFileName )) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("patient-db - load metrics data", options);
        } else {
            final String jdbcUrl = "jdbc:" + databaseUrl;
            final DatabaseAccess dbWriter = new DatabaseAccess(userName, password, jdbcUrl);

            LOGGER.info("Reading pgx calls file {}", pgxCallsFileName);
            PGXCalls pgxCalls = PGXCallsFile.read(pgxCallsFileName);

            LOGGER.info("Reading pgx genotype file {}", pgxGenotypeFileName);
            PGXGenotype pgxGenotype = PGXGenotypeFile.read(pgxCallsFileName);

            LOGGER.info("Writing pgx into database");
            dbWriter.writePGX(sample, pgxGenotype, pgxCalls);

            LOGGER.info("Extracting and writing pgx for {}", sample);

        }

    }

    @NotNull
    private static Options createOptions() {
        Options options = new Options();

        options.addOption(SAMPLE, true, "Sample for which we are going to load the metrics");

        options.addOption(PGX_CALLS_TXT, true, "Path towards the pgx calls txt file");
        options.addOption(PGX_GENOTYPE_TXT, true, "Path towards the pgx genotype txt file");

        options.addOption(DB_USER, true, "Database user name.");
        options.addOption(DB_PASS, true, "Database password.");
        options.addOption(DB_URL, true, "Database url.");

        return options;
    }


}
