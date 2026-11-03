@echo off
rem Runs PlannedAgent from the built jar. Build first with: mvnw.cmd package
java -jar "%~dp0target\plannedagent.jar" %*
