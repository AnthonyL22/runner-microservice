## Purpose

This project is to create a executable JAR file of the SaucelabsRunner class.  Typically, this executable is run by a 
Jenkins job in which the Saucelabs Support plugin has been enabled.  

Once Saucelabs is enabled in Jenkins and the desired Browser Type, Browser Version, and Platform (operating systems) 
have been selected, the environment variable **SAUCE_ONDEMAND_BROWSERS** is automatically set for the 
duration of the job. 

When this JAR is executed, the **SAUCE_ONDEMAND_BROWSERS** environment variable is dynamically read and a PERL script 
is generated named **parallel-exec.pl** in the root of your Jenkins job which executed this runner.

The resulting PERL script is then able to concurrently run Maven instances of automated tests 
against any browser/version/platform version(s) selected in the Saucelabs Jenkins plugin. When the PERL script is 
executed the test results are output to the target/failsafe-reports-X (X = index of the number of 
browser/version/platform version(s) combinations you ran against.

This tool assumes you are using the [ipit-automation-service](http://toautoweb2.na.qualcomm.com:8080/job/ipit-automation-service/)

## Prerequisites

1. Maven 3.x
2. Java 1.7+
3. ITIT Automation Service
4. TestNG

## Maven Dependency

```
<dependency>
    <groupId>qcom.itlegal.ipit</groupId>
    <artifactId>ipit-automation-runner</artifactId>
    <version>1.0.1</version>
</dependency>
```

## Simplified Order of Operations

1. Compile Saucelabs Runner via [Jenkins Job](http://toautoweb2.na.qualcomm.com:8080/job/ipit-automation-runner/)
2. Execute Saucelabs Runner JAR with SAUCE_ONDEMAND_BROWSERS environment variable set
3. Generate PERL script named 'parallel-exec.pl'
4. Run PERL script
5. PERL script runs asynchronous Maven jobs
6. Run tests via Maven
7. Gather test results with TestNG Jenkins plugin


## Mandatory Variable Attributes

The following 2 fields must be defined by your Jenkins job and passed to PERL script in this order:

1. Maven Profile  (example: regression)
2. Test Environment (example: pad-dev)

The following configuration must be included in your Profile in the POM.xml
    **<reportsDirectory>${project.build.directory}/${test.results.dir}</reportsDirectory>**
    
```
<profile>
    <id>regression</id>
    <activation>
        <activeByDefault>false</activeByDefault>
    </activation>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>${maven-failsafe-plugin.version}</version>
                <executions>
                    <execution>
                        <id>regression-system-tests</id>
                        <goals>
                            <goal>integration-test</goal>
                        </goals>
                        <configuration>
                            <groups>regressionTest</groups>
                            <excludedGroups>inProgressTest,manualTest,acceptanceTest</excludedGroups>
                            <parallel>methods</parallel>
                            <parallelOptimized>true</parallelOptimized>
                            <threadCount>${default.thread.runners}</threadCount>
                            <includes>
                                <include>**/*Test.java</include>
                                <include>**/Test*.java</include>
                            </includes>
                            <reportsDirectory>${project.build.directory}/${test.results.dir}</reportsDirectory>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```    

## Execute PERL Runner
The following sample is how to execute this runner JAR to produce the resulting .pl PERL file from Jenkins.  Execution
from Jenkins is suggested becaause the Saucelabs plugin automatically sets the SAUCE_ONDEMAND_BROWSERS environment 
variable which is mandatory.

### Jenkins Execution
Preconditions of this output are:

1. Jenkins & Saucelabs Plugin setting SAUCE_ONDEMAND_BROWSERS environment variable
2. Arg[0] MANDATORY Test Profile variable args sent on command line during execution
3. Arg[1] MANDATORY Test Environment variable args sent on command line during execution
4. Arg[2] OPTIONAL Browser Resolution variable args sent on command line during execution
5. Arg[3] OPTIONAL File Path to resulting PERL file variable args sent on command line during execution
6. Arg[4] OPTIONAL File Name to resulting PERL file variable args sent on command line during execution

```
java -cp "C:\Program Files (x86)\Jenkins\workspace\ipit-automation-runner\target\ipit-automation-runner-1.0.0-SNAPSHOT.jar" qcom.itlegal.ipit.runner.SaucelabsRunner %Test_Profile% %Test_Environment%
```

## Acceptable Browser Resolutions
[Saucelabs Configuration](https://docs.saucelabs.com/reference/test-configuration/)

"800x600", "1024x768", "1152x720", "1152x864", "1152x900", "1280x720", "1280x768", "1280x800", "1280x960",
"1280x1024", "1366x768", "1376x1032", "1400x1050", "1440x900", "1600x900", "1600x1200", "1680x1050",
"1920x1200", "1920x1440", "2048x1152", "2048x1536", "2360x1770"

## PERL Output Sample

```

#! perl -slw
use strict;
use Thread qw(yield async);

system("mvn clean install");

my $t0 = async{
`mvn install -Pacceptance -Dtest.env=pad-dev -Dbrowser="chrome" -Dplatform="Windows 10" -Dbrowser.version=45 -Dtest.results.dir=failsafe-reports-0`
};

my $t1 = async{
`mvn install -Pacceptance -Dtest.env=pad-dev -Dbrowser="firefox" -Dplatform="Windows 7" -Dbrowser.version=44 -Dtest.results.dir=failsafe-reports-1`
};

my $output0 = $t0->join;
my $output1 = $t1->join;

print for $output0;
print for $output1;

```

## Test Results

All test results are stored in multiple 'target/failsafe-reports-X' directories.  In Jenkins, your Publish TestNG 
Results configuration settings must be modified with a wildcard since you will have multiple result XML files.  This
wildcard will be sure to combine all test results into a single TestNG test report.

For example: **/testng-results.xml
