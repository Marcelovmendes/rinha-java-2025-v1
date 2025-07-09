FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw


RUN ./mvnw dependency:go-offline -B


COPY src/ ./src/
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -g 1000 appuser && adduser -u 1000 -G appuser -s /bin/sh -D appuser

WORKDIR /app

COPY --from=builder /app/target/rinha-backend-v1-*.jar app.jar
RUN chown appuser:appuser app.jar

USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]