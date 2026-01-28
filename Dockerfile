FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew bootJar -x test
CMD ["java", "-jar", "build/libs/chillgram-0.0.1-SNAPSHOT.jar"]