/*
 * (c) 2016-2019 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.swirlds.regression;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swirlds.regression.jsonConfigs.RegressionConfig;
import com.swirlds.regression.jsonConfigs.TestConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.swirlds.common.PlatformLogMessages.PTD_FINISH;
import static com.swirlds.common.PlatformLogMessages.PTD_SUCCESS;

public class RegressionUtilities {

	public static final String WRITE_FILE_DIRECTORY = "tmp/";
	public static final String PUBLIC_IP_ADDRESS_FILE = WRITE_FILE_DIRECTORY + "publicAddresses.txt";
	public static final String PRIVATE_IP_ADDRESS_FILE = WRITE_FILE_DIRECTORY + "privateAddresses.txt";

	public static final String SDK_DIR = "../sdk/";
	public static final String PTD_CONFIG_DIR = "../platform-apps/tests/PlatformTestingDemo/src/main/resources/";
	public static final String SETTINGS_FILE = "settings.txt";
	public static final String DEFAULT_SETTINGS_DIR = "../sdk/";
	public static final String CONFIG_FILE = "config.txt";
	public static final Charset STANDARD_CHARSET = Charset.forName("UTF-8");
	public static final String RESULTS_FOLDER = "results";
	public static final String TAR_NAME = "remoteExperiment.tar.gz";
	public static final ArrayList<String> DIRECTORIES_TO_INCLUDE = new ArrayList<>(Arrays.asList("data"));
	public static final String JVM_OPTIONS_DEFAULT = "-Xmx100g -Xms8g -XX:+UnlockExperimentalVMOptions -XX:+UseZGC " +
			"-XX:ConcGCThreads=14 -XX:ZMarkStackSpaceLimit=16g -XX:+UseLargePages -XX:MaxDirectMemorySize=32g";
	public static final int SHA1_DIVISOR = 25;
	public static final long JAVA_PROC_CHECK_INTERVAL = 5 * 60 * 1000; // min * sec * millis
	public static final int MB = 1024 * 1024;
	public static final String CHECK_JAVA_PROC_COMMAND = "pgrep -fl java";
	public static final String KILL_JAVA_PROC_COMMAND = "sudo pkill -f java";
	public static final String KILL_REGRESSION_PROC_COMMAND = "sudo pkill -f regression";
	public static final String CHECK_FOR_PTD_TEST_MESSAGE = "egrep \"TEST SUCCESS|TEST FAIL|TRANSACTIONS " +
			"FINISHED|TEST" +
			" " +
			"ERROR\" remoteExperiment/swirlds.log";
	public static final String RESET_NODE = "sudo rm -rf remoteExperiment";
	public static final String EMPTY_HASH = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
	public static final long CLOUD_WAIT_MILLIS = 30000;
	public static final long POSTGRES_WAIT_MILLIS = 30000;
	public static final int MILLIS = 1000;

	public static final ArrayList<String> PTD_LOG_FINISHED_MESSAGES = new ArrayList<>(
			Arrays.asList(PTD_SUCCESS, PTD_FINISH));
	public static final String DROP_DATABASE_BEFORE_NEXT_TEST = "sudo -i -u postgres psql -c \"drop extension crypto;" +
			" " +
			"drop database fcfs; create database fcfs with owner = swirlds;\"";
	public static final String DROP_DATABASE_EXTENSION_BEFORE_NEXT_TEST = "sudo -i -u postgres psql -c \"drop " +
			"extension" +
			" crypto;\"";
	public static final String DROP_DATABASE_FCFS_TABE_BEFORE_NEXT_TEST = "sudo -i -u postgres psql -c \" drop " +
			"database" +
			" fcfs;\"";
	public static final int AMAZON_INSTANCE_WAIT_TIME_SECONDS = 3;
	static final String DROP_DATABASE_FCFS_EXPECTED_RESPONCE = "DROP DATABASE";
	static final String DROP_DATABASE_FCFS_KNOWN_RESPONCE = "ERROR:  database \"fcfs\" is being accessed by other " +
			"users";
	public static final String CREATE_DATABASE_FCFS_TABE_BEFORE_NEXT_TEST = "sudo -i -u postgres psql -c \"create " +
			"database fcfs with owner = swirlds;\"";
	static final String CREATE_DATABASE_FCFS_EXPECTED_RESPONCE = "CREATE DATABASE";

	public static String OLD_EVENT_PARENT = "will not use old otherParent";

	public static final String GIT_NOT_FOUND = "Git repo was not found in base directory.\n";

	public static final int EXCEPTIONS_SIZE = 1000;
	public static final int SSH_TEST_CMD_AFTER_SEC = 60;
	public static final String MVN_ERROR_FLAG = "[ERROR]";

	private static final Logger log = LogManager.getLogger(Experiment.class);
	private static final Marker MARKER = MarkerManager.getMarker("REGRESSION_TESTS");
	private static final Marker ERROR = MarkerManager.getMarker("EXCEPTION");


	static final String TEST_CONFIG = "configs/testRestartCfg.json";
	static final String REGRESSION_CONFIG = "configs/AwsRegressionCfg_Freeze.json";
	static final String REMOTE_EXPERIMENT_LOCATION = "remoteExperiment/";
	static final String REMOTE_STATE_LOCATION = REMOTE_EXPERIMENT_LOCATION + "data/saved/";
	static final String DB_BACKUP_FILENAME = "PostgresBackup.tar.gz";

	static final String REG_SLACK_CHANNEL = "regression";
	static final String REG_GIT_BRANCH = "develop";
	static final boolean CHECK_BRANCH_CHANNEL = true;
	static final String REG_GIT_USER_EMAIL = "swirlds-test@swirlds.org";
	static final boolean CHECK_USER_EMAIL_CHANNEL = true;

	static final boolean USE_STAKES_IN_CONFIG = true;
	// total stakes are the same as the number of the number of tinybars in existence
	// (50 billion)*(times 100 million)
	static final long TOTAL_STAKES = 50L * 1_000_000_000L * 100L * 1_000_000L;

	static final String[] NIGHTLY_REGRESSION_SERVER_LIST = {
			"i-03c90b3fdeed8edd7",
			"i-050d3f864b99b796f",
			"i-0611e2febc6a73d5a",
			"i-0cc227bff247a8a09" };
	static final String NIGHTLY_REGRESSION_KICKOFF_SERVER = "172.31.9.236";

	protected static TestConfig importExperimentConfig() {
		return importExperimentConfig(TEST_CONFIG);
	}

	protected static TestConfig importExperimentConfig(String testConfigFileLocation) {
		return importExperimentConfig(Paths.get(testConfigFileLocation));
	}

	protected static TestConfig importExperimentConfig(URI testConfigFileLocation) {
		return importExperimentConfig(Paths.get(testConfigFileLocation));
	}

	protected static TestConfig importExperimentConfig(Path testConfigFileLocation) {
		try {
			log.info(MARKER, "Importing experiment file: {}", testConfigFileLocation);
			byte[] jsonData = Files.readAllBytes(testConfigFileLocation);
			ObjectMapper objectMapper = new ObjectMapper().configure(JsonParser.Feature.ALLOW_COMMENTS, true);

			log.info(MARKER, "Parsing Test JSON......");
			TestConfig testConfig = objectMapper.readValue(jsonData, TestConfig.class);
			log.info(MARKER, "Parsed Test JSON......");
			return testConfig;

		} catch (JsonParseException e) {
			log.error(ERROR, "could not parse the json");
			e.printStackTrace();
		} catch (JsonMappingException e) {
			log.error(ERROR, "Couldn't map the JSON");
			e.printStackTrace();
		} catch (IOException e) {
			log.error(ERROR, "There was an issue with the json file.");
			e.printStackTrace();
		}
		return null;
	}

	protected static RegressionConfig importRegressionConfig() {
		return importRegressionConfig(REGRESSION_CONFIG);
	}

	protected static RegressionConfig importRegressionConfig(String regressionConfigFileLocation) {
		try {
			log.info(MARKER, "Importing regression file: {}", regressionConfigFileLocation);
			byte[] jsonData = Files.readAllBytes(Paths.get(regressionConfigFileLocation));
			ObjectMapper objectMapper = new ObjectMapper().configure(JsonParser.Feature.ALLOW_COMMENTS, true);

			log.info(MARKER, "Parsing JSON......");
			RegressionConfig regressionConfig = objectMapper.readValue(jsonData, RegressionConfig.class);

			return regressionConfig;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	protected static RegressionConfig importRegressionConfig(URI regressionConfigFileLocation) {
		try {
			log.info(MARKER, "Importing regression file: {}", regressionConfigFileLocation.toString());
			byte[] jsonData = Files.readAllBytes(Paths.get(regressionConfigFileLocation));
			ObjectMapper objectMapper = new ObjectMapper().configure(JsonParser.Feature.ALLOW_COMMENTS, true);

			log.info(MARKER, "Parsing JSON......");
			RegressionConfig regressionConfig = objectMapper.readValue(jsonData, RegressionConfig.class);

			return regressionConfig;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	protected static Collection<File> getSDKFilesToUpload(File keyFile, File log4jFile,
			ArrayList<File> configSpecifiedFiles) {
		Collection<File> returnIterator = new ArrayList<>();
		returnIterator.add(new File(SDK_DIR + "data/apps/"));
		returnIterator.add(new File(SDK_DIR + "data/backup/"));
		returnIterator.add(new File(SDK_DIR + "data/keys/"));
		returnIterator.add(new File(SDK_DIR + "data/lib/"));
		returnIterator.add(new File(SDK_DIR + "data/repos/"));
		returnIterator.add(new File(SDK_DIR + "kernels/"));
		returnIterator.add(new File(SDK_DIR + "swirlds.jar"));
		returnIterator.add(new File(PRIVATE_IP_ADDRESS_FILE));
		returnIterator.add(new File(PUBLIC_IP_ADDRESS_FILE));
		returnIterator.add(keyFile);
		returnIterator.add(new File(RegressionUtilities.WRITE_FILE_DIRECTORY + RegressionUtilities.CONFIG_FILE));
		returnIterator.add(new File(RegressionUtilities.WRITE_FILE_DIRECTORY + RegressionUtilities.SETTINGS_FILE));
		returnIterator.add(log4jFile);
		if (configSpecifiedFiles != null) {
			returnIterator.addAll(configSpecifiedFiles);
		}

		return returnIterator;
	}

	protected static ArrayList<String> getRsyncListToUpload(File keyFile, File log4jFile,
			ArrayList<File> configSpecifiedFiles) {
		ArrayList<String> returnIterator = new ArrayList<>();

		returnIterator.add("data/");
		returnIterator.add("data/apps/");
		returnIterator.add("data/apps/**");
		returnIterator.add("data/backup/");
		returnIterator.add("data/backup/**");
		returnIterator.add("data/keys/");
		returnIterator.add("data/keys/**");
		returnIterator.add("data/lib/");
		returnIterator.add("data/lib/**");
		returnIterator.add("data/repos/");
		returnIterator.add("data/repos/**");
		returnIterator.add("kernels/");
		returnIterator.add("kernels/**");
		returnIterator.add("swirlds.jar");
		returnIterator.add("privateAddresses.txt");
		returnIterator.add("publicAddresses.txt");
		returnIterator.add(keyFile.getName());
		returnIterator.add(RegressionUtilities.CONFIG_FILE);
		returnIterator.add(RegressionUtilities.SETTINGS_FILE);
		returnIterator.add(log4jFile.getName());

		if (configSpecifiedFiles != null) {
			for (File file : configSpecifiedFiles) {
				returnIterator.add(file.getName());
			}
		}
		return returnIterator;
	}

	protected static ArrayList<String> getSDKFilesToDownload(ArrayList<String> configSpecifiedFiles) {
		ArrayList<String> returnIterator = new ArrayList<>();

		returnIterator.add("*.csv");
		returnIterator.add("*.log");
		returnIterator.add("*.xml");
		returnIterator.add("*.txt");
		returnIterator.add("*.json");
		//returnIterator.add("badger_*");
		//returnIterator.add("stream_*");
		//returnIterator.add("postgres_reports");

		if (configSpecifiedFiles != null) {
			returnIterator.addAll(configSpecifiedFiles);
		}

		return returnIterator;
	}

	protected static String getExperimentTimFormatedString(ZonedDateTime timeToFormat) {
		return timeToFormat.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
	}

	protected static String getResultsFolder(ZonedDateTime timeToFormat, String testName) {
		return getExperimentTimFormatedString(timeToFormat) + "-" + testName;
	}

	public static String getRemoteSavedStatePath(String mainClass, long nodeId, String swirldName, long round) {
		return String.format("%s%s/%d/%s/%d/",
				REMOTE_STATE_LOCATION, mainClass, nodeId, swirldName, round);
	}

	public static List<String> getFilesInDir(String dirPath, boolean returnPaths) {
		File dir = new File(dirPath);
		if (!dir.isDirectory()) {
			return null;
		}
		return Arrays.stream(dir.listFiles()).filter(File::isFile)
				.map(returnPaths ? File::getAbsolutePath : File::getName).collect(Collectors.toList());
	}
}