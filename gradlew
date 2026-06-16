#!/bin/sh
# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  YACR Gradle Wrapper Script                                                  ║
# ║  Developer : MNM YOUNUS                                                      ║
# ╚══════════════════════════════════════════════════════════════════════════════╝
#
# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS
# to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Resolve links: "$0" may be a link
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")"/$link"
  fi
done
SAVED="$(pwd)"
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

GRADLE_OPTS="${GRADLE_OPTS:-} ${DEFAULT_JVM_OPTS}"
JAVA_OPTS="${JAVA_OPTS:-}"

exec "${JAVA_HOME}/bin/java" ${JAVA_OPTS} \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
