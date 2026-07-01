#!/bin/sh

# Gradle wrapper script
DEFAULT_GRADLE_VERSION="8.5"
GRADLE_VERSION=${GRADLE_VERSION:-$DEFAULT_GRADLE_VERSION}

if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading Gradle Wrapper..."
    mkdir -p gradle/wrapper
    curl -L -o gradle/wrapper/gradle-wrapper.jar \
        "https://github.com/gradle/gradle/raw/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
fi

exec java -classpath gradle/wrapper/gradle-wrapper.jar \
    org.gradle.wrapper.GradleWrapperMain "$@"