@echo off
REM Backend test kosucusu: Surefire fork TEMP yolundaki '&' karakterinde
REM kirildigi icin java.io.tmpdir proje altina alinir.
set JAVA_HOME=D:\IropsPilot\runtime\jdk25
powershell -NoProfile -Command ^
  "New-Item -ItemType Directory -Force -Path D:/Relayra/backend/target/tmp-surefire | Out-Null; " ^
  "$env:MAVEN_OPTS='-Djava.io.tmpdir=D:/Relayra/backend/target/tmp-surefire'; " ^
  "D:/tools/apache-maven-3.9.9/bin/mvn.cmd -B -s D:/tools/m2settings.xml -f D:/Relayra/backend/pom.xml %*"
