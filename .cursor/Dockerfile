# Cloud Agent base image for the EDT-MCP repository.
#
# Reproduces the CI toolchain (.github/workflows/build.yml): JDK 17 + Maven for
# the Tycho/Eclipse plugin build (mcp/) and the standalone Java proxy (proxy/).
# Stable system dependencies live here; repository-dependent work (dependency
# resolution, cache warming) runs in the `install` phase of environment.json.

FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

# git/curl are required in fully custom images; JDK 17 is the version CI builds
# with (Tycho 4.0.5 refuses newer JDKs for the target platform). python3 runs
# the repo's skill/coverage validation scripts.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        git \
        curl \
        unzip \
        zip \
        python3 \
        openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

# Maven 3.9+ is mandatory for Tycho 4.0.5 (Ubuntu's apt maven is 3.8.x).
ARG MAVEN_VERSION=3.9.9
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        -o /tmp/maven.tar.gz \
    && tar -xzf /tmp/maven.tar.gz -C /opt \
    && ln -sfn "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven \
    && rm /tmp/maven.tar.gz

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV MAVEN_HOME=/opt/maven
ENV MAVEN_OPTS="-Xmx2g -XX:+UseParallelGC"
ENV PATH="/opt/maven/bin:${PATH}"

# Match the checkout owner used by Cloud Agents (present in ubuntu:24.04).
USER ubuntu
