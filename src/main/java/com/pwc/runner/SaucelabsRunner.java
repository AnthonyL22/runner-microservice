package com.pwc.runner;

import com.pwc.core.framework.util.JsonUtils;
import com.pwc.core.framework.util.PropertiesUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;

/**
 * Utility to generate the PERL script necessary to run Maven tasks
 * concurrently on the same server using Sauce labs
 * <p/>
 * args[0] - Maven PROFILE name
 * args[1] - ENVIRONMENT config name
 * args[2] - OPTIONAL: Browser Resolution
 * args[3] - OPTIONAL: override path to where the PERL file is written to
 * args[4] - OPTIONAL: override file name of PERL file
 */
public class SaucelabsRunner {

    private static final String SAUCE_ONDEMAND_BROWSERS_ENV_VARIABLE = "SAUCE_ONDEMAND_BROWSERS";
    private static final String DEFAULT_OUTPUT_FILE_NAME = "parallel-exec.pl";
    private static final String DEFAULT_BROWSER_RESOLUTION = "1280x1024";
    private static final List<String> ACCEPTABLE_BROWSER_RESOLUTIONS = new ArrayList<>(Arrays.asList(
            "800x600", "1024x768", "1152x720", "1152x864", "1152x900", "1280x720", "1280x768", "1280x800", "1280x960",
            "1280x1024", "1366x768", "1376x1032", "1400x1050", "1440x900", "1600x900", "1600x1200", "1680x1050",
            "1920x1200", "1920x1440", "2048x1152", "2048x1536", "2360x1770"));

    private static final String BROWSER_KEYWORD = "browser";
    private static final String OS_KEYWORD = "os";
    private static final String PLATFORM_KEYWORD = "platform";
    private static final String BROWSER_VERSION_KEYWORD = "browser-version";
    private static final String DEVICE_KEYWORD = "device";
    private static final String ORIENTATION_KEYWORD = "device-orientation";

    private static String browserResolution;
    private static String outputFilePath = null;

    public static void main(String[] args) {

//        manualMobileBasedEnvSetup();
//        manualBrowserBasedEnvSetup();

        System.out.println("SAUCE_ONDEMAND_BROWSERS = " + System.getenv(SAUCE_ONDEMAND_BROWSERS_ENV_VARIABLE));

        File fileToOutput = createOutputFile(args);
        if (!System.getenv(SAUCE_ONDEMAND_BROWSERS_ENV_VARIABLE).equals("")) {
            generatePerlScript(fileToOutput, args);
        }

    }

    /**
     * Create basis output file to write PERL script to
     *
     * @param args variable runtime arguements
     * @return empty base file
     */
    private static File createOutputFile(String[] args) {
        File fileToOutput;

        configureBrowserResolution(args);
        configureRunnableFileOutputPath(args);
        configureRunnableFileNameAndFullPath(args);

        fileToOutput = new File(outputFilePath);
        if (fileToOutput.exists()) {
            fileToOutput.delete();
        }
        System.out.println("\n");
        System.out.println("Writing Parallel Executor to Path=" + fileToOutput.getPath());
        return fileToOutput;
    }

    /**
     * Configure the runnable OPTIONAL PERL file path
     *
     * @param args current runtime arguments
     * @return partial path to runnable PERL file
     */
    private static void configureRunnableFileOutputPath(String[] args) {
        if (args.length > 3) {
            if (!args[3].endsWith("\\")) {
                args[3] = args[3] + "/";
            }
            outputFilePath = args[3];
        }
    }

    /**
     * Configure the runnable OPTIONAL PERL file name (.pl) and it's full path
     *
     * @param args current runtime arguments
     * @return completed path to runnable PERL file
     */
    private static void configureRunnableFileNameAndFullPath(String[] args) {
        if (args.length > 4) {
            if (!args[4].endsWith(".pl")) {
                args[4] = args[4] + ".pl";
            }
            outputFilePath = outputFilePath + args[4];
        } else {
            if (outputFilePath == null) {
                outputFilePath = DEFAULT_OUTPUT_FILE_NAME;
            } else {
                outputFilePath = outputFilePath + DEFAULT_OUTPUT_FILE_NAME;
            }
        }
    }

    /**
     * Configure the browser resolution for the test run.  Defaults to '1280x1024' not specified as one of the
     * supported resolutions in the Saucelabs API
     * <p/>
     * https://docs.saucelabs.com/reference/test-configuration/
     * <p/>
     * IMPORTANT: Do not define a browser resolution if one is not provided.  Allow Saucelabs to provide default
     *
     * @param args current runtime arguments
     */
    private static void configureBrowserResolution(String[] args) {
        if (args.length > 2) {
            browserResolution = args[2];
            if (StringUtils.equalsIgnoreCase(browserResolution, "none")) {
                browserResolution = null;
            } else {
                if (!ACCEPTABLE_BROWSER_RESOLUTIONS.contains(browserResolution)) {
                    browserResolution = DEFAULT_BROWSER_RESOLUTION;
                }
            }
        }
    }

    /**
     * Output a custom PERL (.pl) file that will execute concurrent Maven processes concurrently based on the
     * Sauce labs Environment variable 'SAUCE_ONDEMAND_BROWSERS' set by Jenkins plugin
     *
     * @param output      file to create and finally execute
     * @param runtimeArgs Maven profile and test environment arguements
     */
    private static void generatePerlScript(File output, String[] runtimeArgs) {
        List<String> lines = new ArrayList<>();
        int index = 0;
        try {
            List<HashMap> list = JsonUtils.getJsonList(System.getenv(SAUCE_ONDEMAND_BROWSERS_ENV_VARIABLE));
            StringBuilder header = new StringBuilder("#! perl -slw");
            header.append("\n");
            header.append("use strict;");
            header.append("\n");
            header.append("use Thread qw(yield async);");
            header.append("\n");
            lines.add(header.toString());
            for (HashMap hashMap : list) {
                StringBuilder command = new StringBuilder();
                command.append("my $t" + index + " = async{");
                command.append("\n");
                command.append("`mvn install");
                command.append(" -P");
                command.append(runtimeArgs[0]);
                command.append(" -Dtest.env=");
                command.append(runtimeArgs[1]);
                if (browserResolution != null) {
                    command.append(" -Dbrowser.resolution=");
                    command.append(browserResolution);
                }
                command.append(" -Dbrowser=\"");
                command.append(hashMap.get(BROWSER_KEYWORD));
                command.append("\" -Dplatform=\"");
                command.append(hashMap.get(OS_KEYWORD));
                command.append("\" -Dbrowser.version=");
                command.append(hashMap.get(BROWSER_VERSION_KEYWORD));

                if (!StringUtils.isEmpty((CharSequence) hashMap.get(OS_KEYWORD))) {
                    command.append(" -Dos=\"");
                    command.append(hashMap.get(OS_KEYWORD));
                    command.append("\"");
                }

                if (!StringUtils.isEmpty((CharSequence) hashMap.get(DEVICE_KEYWORD))) {
                    command.append(" -DdeviceName=\"");
                    command.append(hashMap.get(DEVICE_KEYWORD));
                    command.append("\"");
                }

                if (!StringUtils.isEmpty((CharSequence) hashMap.get(ORIENTATION_KEYWORD))) {
                    command.append(" -DdeviceOrientation=\"");
                    command.append(hashMap.get(ORIENTATION_KEYWORD));
                    command.append("\"");
                }

                command.append(" -Dtest.results.dir=failsafe-reports-" + index);
                command.append("`");
                command.append("\n");
                command.append("};");
                command.append("\n");
                index++;
                lines.add(command.toString());

            }

            StringBuilder outputStatement = new StringBuilder();
            for (int i = 0; i < index; i++) {
                outputStatement.append("my $output" + i + " = $t" + i + "->join;");
                outputStatement.append("\n");
            }
            lines.add(outputStatement.toString());

            StringBuilder printStatement = new StringBuilder();
            for (int i = 0; i < index; i++) {
                printStatement.append("print $output" + i + ";");
                printStatement.append("\n");
            }
            lines.add(printStatement.toString());

            FileUtils.writeLines(output, lines);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This is a helper method that should only be used to test the application.  Typically, this environment
     * variable is typcially set by the Jenkins Sauce labs plugin
     */
    private static void manualMobileBasedEnvSetup() {
        String MOCK_MOBILE_TEST_ENV_VAR = "[{\"os\":\"android\",\"platform\":\"ANDROID\",\"browser\":\"android\",\"browser-version\":\"4.4\",\"long-name\":\"LG Nexus 4 Emulator\",\"long-version\":\"4.4.\",\"url\":\"sauce-ondemand:?os=android&browser=android&browser-version=4.4&username=pad-jenkins&access-key=71047900-7f51-4185-a3d7-5d2b413f2efa\",\"device\":\"LG Nexus 4 Emulator\",\"device-orientation\":\"portrait\"},{\"os\":\"android\",\"platform\":\"ANDROID\",\"browser\":\"android\",\"browser-version\":\"4.4\",\"long-name\":\"Samsung Galaxy Nexus Emulator\",\"long-version\":\"4.4.\",\"url\":\"sauce-ondemand:?os=android&browser=android&browser-version=4.4&username=pad-jenkins&access-key=71047900-7f51-4185-a3d7-5d2b413f2efa\",\"device\":\"Samsung Galaxy Nexus Emulator\",\"device-orientation\":\"landscape\"}]";
        Map<String, String> map = new HashMap<>();
        map.put(SAUCE_ONDEMAND_BROWSERS_ENV_VARIABLE, MOCK_MOBILE_TEST_ENV_VAR);
        PropertiesUtils.setEnv(map);
    }

    private static void manualBrowserBasedEnvSetup() {
        String MOCK_BROWSER_TEST_ENV_VAR = "[{\"os\":\"Windows 10\",\"platform\":\"XP\",\"browser\":\"chrome\",\"browser-version\":\"45\",\"long-name\":\"Chrome\",\"long-version\":\"10.0.2.\",\"url\":\"sauce-ondemand:?os=Windows 10&browser=chrome&browser-version=45&username=pad-jenkins&access-key=71047900-7f51-4185-a3d7-5d2b413f2efa\"},{\"os\":\"Windows 10\",\"platform\":\"XP\",\"browser\":\"chrome\",\"browser-version\":\"44\",\"long-name\":\"Chrome\",\"long-version\":\"12.0.\",\"url\":\"sauce-ondemand:?os=Windows 10&browser=chrome&browser-version=44&username=pad-jenkins&access-key=71047900-7f51-4185-a3d7-5d2b413f2efa\"}]";
        Map<String, String> map = new HashMap<>();
        map.put(SAUCE_ONDEMAND_BROWSERS_ENV_VARIABLE, MOCK_BROWSER_TEST_ENV_VAR);
        PropertiesUtils.setEnv(map);
    }

    public static String getBrowserResolution() {
        return browserResolution;
    }

    public static String getOutputFilePath() {
        return outputFilePath;
    }

}
