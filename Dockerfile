# Giai đoạn build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Giai đoạn chạy
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
            "-Dspring.profiles.active=prod", \
            "-Duser.timezone=Asia/Ho_Chi_Minh", \
            "-jar", "app.jar"]