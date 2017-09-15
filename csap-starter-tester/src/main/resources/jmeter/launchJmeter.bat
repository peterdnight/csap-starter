echo %JAVA_HOME% 
echo setting the current dir to target folder, this is the default location for generating reports and errors

echo ==
echo == DO NOT FORGET to update path below for your workspaces, and copy the jmeter csv file to same folder as jmx
echo ==
SET
echo == 
cd C:\Users\pnightin\git\javasamples\BootEnterprise\target
echo %CD%
set JVM_ARGS=-Xms512m -Xmx512m
echo == JVM_ARGS may be increased decreased based on number of results collected: %JVM_ARGS%
REM C:\java\apache-jmeter-2.7\bin\jmeter.bat
REM C:\java\apache-jmeter-2.13\bin\jmeter.bat
 C:\java\apache-jmeter-3.2\bin\jmeter.bat