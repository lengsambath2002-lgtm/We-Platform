# Business Platform API — build with the Gradle wrapper, run on a JRE as a non-root user.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*.jar /app.jar

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /app /data/files && chown -R app:app /app /data
WORKDIR /app
COPY --from=build --chown=app:app /app.jar app.jar
USER app
ENV PORT=8280 \
    PLATFORM_FILES_DIR=/data/files \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
VOLUME /data/files
EXPOSE 8280
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
