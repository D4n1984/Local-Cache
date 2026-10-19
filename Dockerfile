FROM --platform=linux/amd64 eclipse-temurin:17-jdk-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/*
ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/tools.zip \
    && unzip -q /tmp/tools.zip -d /tmp/tools \
    && mv /tmp/tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm -rf /tmp/tools /tmp/tools.zip \
    && yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null \
    && ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager 'platforms;android-34' 'build-tools;35.0.0' 'platform-tools'
RUN useradd --uid 1000 --create-home builder \
    && mkdir -p /home/builder/.gradle && chown -R builder:builder /home/builder/.gradle
USER builder
WORKDIR /work/android
ENTRYPOINT ["bash", "gradlew", "--no-daemon"]
