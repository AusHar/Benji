

.PHONY: run test it clean build check codegen

run:
	./gradlew bootRun

test:
	./gradlew test

it:
	./gradlew test -Dspring.profiles.active=test

clean:
	./gradlew clean

build:
	./gradlew clean build

check:
	./gradlew spotlessApply test dependencyCheckAnalyze

codegen:
	./gradlew openApiGenerate