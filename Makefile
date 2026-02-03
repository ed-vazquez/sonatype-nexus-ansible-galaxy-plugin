MVN_IMAGE := maven:3.9-eclipse-temurin-17
MVN_SETTINGS := /build/.mvn/maven-settings.xml
MVN_DOCKER := docker run --rm -v "$(CURDIR)":/build -w /build $(MVN_IMAGE)

.PHONY: compile test verify package clean integration-test

## compile: Compile all source files
compile:
	$(MVN_DOCKER) mvn clean compile -s $(MVN_SETTINGS)

## test: Run unit tests
test:
	$(MVN_DOCKER) mvn clean test -s $(MVN_SETTINGS)

## verify: Full build with tests and packaging
verify:
	$(MVN_DOCKER) mvn clean verify -s $(MVN_SETTINGS)

## package: Build the plugin JAR (skip tests)
package:
	$(MVN_DOCKER) mvn clean package -s $(MVN_SETTINGS) -DskipTests

## clean: Remove build artifacts
clean:
	$(MVN_DOCKER) mvn clean -s $(MVN_SETTINGS)

## integration-test: Build plugin and run integration tests against Nexus in Docker
integration-test:
	./src/test/integration/run-it.sh
