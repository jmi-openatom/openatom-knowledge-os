@ECHO OFF
SET BASE_DIR=%~dp0
java -classpath "%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" org.apache.maven.wrapper.MavenWrapperMain -f "%BASE_DIR%\backend\pom.xml" %*
