# For trying the demo out on your own machine with Docker alone: the first stage builds the application in production
# mode, so neither Java nor Maven is needed, and the second runs the jar as in Vaadin's Docker guide.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
# Charts and Dashboard are commercial Vaadin components, whose production build needs a Vaadin license; built without
# one, the application shows Vaadin's commercial banner.
RUN mvn -B -ntp -Pproduction -DskipTests -Dvaadin.commercialWithBanner=true package

FROM eclipse-temurin:21-jre
COPY --from=build /src/target/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
