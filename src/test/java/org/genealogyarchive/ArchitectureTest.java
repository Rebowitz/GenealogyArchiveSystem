package org.genealogyarchive;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.io.File;
import java.net.URL;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Module boundaries of the modular monolith (PHASE-0 "clean application/domain/service/data/
 * file-service boundaries"; BUILD-SPEC s.57, s.60, s.67).
 */
@AnalyzeClasses(packages = "org.genealogyarchive", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "org.genealogyarchive";

    /** Historical domain code stays free of frameworks, persistence, web and OS concerns. */
    @ArchTest
    static final ArchRule domainIsIndependent = noClasses()
            .that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "java.sql..", "javax.sql..", "org.flywaydb..",
                    BASE + ".data..", BASE + ".migration..", BASE + ".web..", BASE + ".ops..",
                    BASE + ".files..", BASE + ".platform..", BASE + ".boot..", BASE + ".cli..", BASE + ".worker..")
            .allowEmptyShould(true);

    /** SQL and JDBC live only in the data layer and the migration runner. */
    @ArchTest
    static final ArchRule sqlOnlyInDataLayer = noClasses()
            .that().resideOutsideOfPackages(BASE + ".data..", BASE + ".migration..")
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "org.springframework.jdbc..");

    /** Browser/UI code goes through services; it never mutates or queries the database directly. */
    @ArchTest
    static final ArchRule webUsesServicesOnly = noClasses()
            .that().resideInAnyPackage(BASE + ".web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    BASE + ".data..", BASE + ".migration..", "java.sql..", "javax.sql..", "org.flywaydb..");

    /** Lower layers never depend on process assembly, web or CLI. */
    @ArchTest
    static final ArchRule layersPointInward = noClasses()
            .that().resideInAnyPackage(BASE + ".service..", BASE + ".data..", BASE + ".migration..",
                    BASE + ".files..", BASE + ".platform..", BASE + ".config..", BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    BASE + ".web..", BASE + ".ops..", BASE + ".boot..", BASE + ".cli..", BASE + ".worker..");

    /** Path, file-service, platform and configuration code is plain Java: testable without Spring. */
    @ArchTest
    static final ArchRule portableCoreHasNoFramework = noClasses()
            .that().resideInAnyPackage(BASE + ".files..", BASE + ".platform..", BASE + ".config..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");

    /** Only the platform adapter may inspect host properties such as os.name. */
    @ArchTest
    static final ArchRule onlyPlatformInspectsHost = noClasses()
            .that().resideOutsideOfPackage(BASE + ".platform..")
            .should().callMethod(System.class, "getProperty", String.class)
            .orShould().callMethod(System.class, "getProperty", String.class, String.class);

    /** Separators are never concatenated by hand; java.nio.file.Path does host path handling. */
    @ArchTest
    static final ArchRule noHandBuiltSeparators = noClasses()
            .should().accessField(File.class, "separator")
            .orShould().accessField(File.class, "separatorChar")
            .orShould().accessField(File.class, "pathSeparator")
            .orShould().accessField(File.class, "pathSeparatorChar");

    /** No normal-runtime Internet/cloud dependency: application code opens no outbound HTTP connections. */
    @ArchTest
    static final ArchRule noOutboundNetworkClients = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "java.net.http..", "org.springframework.web.client..", "org.springframework.web.reactive.function.client..")
            .orShould().callMethod(URL.class, "openConnection")
            .orShould().callMethod(URL.class, "openStream");

    /** Prohibited v1 infrastructure (BUILD-SPEC s.60, NF-001) is not referenced by application code. */
    @ArchTest
    static final ArchRule noProhibitedInfrastructure = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.hibernate..", "redis..", "io.lettuce..", "org.apache.kafka..",
                    "com.rabbitmq..", "org.elasticsearch..", "org.opensearch..", "software.amazon..",
                    "com.amazonaws..", "com.azure..", "com.google.cloud..");
}
