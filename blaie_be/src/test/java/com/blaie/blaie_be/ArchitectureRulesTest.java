package com.blaie.blaie_be;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {
    private static final String BASE = "com.blaie.blaie_be.";
    private static final String AUTH = BASE + "auth..";
    private static final String AUTHZ = BASE + "authz..";
    private static final String CAPTURE = BASE + "capture..";
    private static final String AUDIT = BASE + "audit..";
    private static final String RETENTION = BASE + "retention..";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(BASE);

    @Test
    void domainShouldStayIndependentFromOuterLayersAndFrameworks() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..api..",
                        "..application..",
                        "..infrastructure..",
                        "jakarta.persistence..",
                        "org.springframework.."
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void apiShouldNotDependOnPersistenceDetails() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..infrastructure.persistence..",
                        "jakarta.persistence.."
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationShouldNotDependOnInfrastructureOrFrameworkAdapters() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..api..",
                        "..infrastructure..",
                        "jakarta.persistence..",
                        "org.springframework.data..",
                        "org.springframework.web..",
                        "org.springframework.security.."
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationPortsShouldNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAPackage("..application..port..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..api..",
                        "..infrastructure.."
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void infrastructureShouldNotDependOnApi() {
        noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void coreShouldNotDependOnBusinessModules() {
        noClasses()
                .that().resideInAPackage(BASE + "core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        AUTH,
                        AUTHZ,
                        CAPTURE,
                        AUDIT,
                        RETENTION
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void authShouldNotDependOnOtherBusinessModules() {
        noClasses()
                .that().resideInAPackage(AUTH)
                .should().dependOnClassesThat().resideInAnyPackage(
                        AUTHZ,
                        CAPTURE,
                        AUDIT,
                        RETENTION
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void captureShouldOnlyUseCoreAndAuthzOutsideItsModule() {
        noClasses()
                .that().resideInAPackage(CAPTURE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        AUTH,
                        AUDIT,
                        RETENTION
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void auditShouldOnlyUseCoreAndAuthzOutsideItsModule() {
        noClasses()
                .that().resideInAPackage(AUDIT)
                .should().dependOnClassesThat().resideInAnyPackage(
                        AUTH,
                        CAPTURE,
                        RETENTION
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void moduleInfrastructureShouldNotDependOnAnotherModuleInfrastructure() {
        noClasses()
                .that().resideInAPackage(BASE + "auth.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "capture.infrastructure..",
                        BASE + "audit.infrastructure..",
                        BASE + "retention.infrastructure.."
                )
                .check(PRODUCTION_CLASSES);

        noClasses()
                .that().resideInAPackage(BASE + "capture.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "auth.infrastructure..",
                        BASE + "audit.infrastructure..",
                        BASE + "retention.infrastructure.."
                )
                .check(PRODUCTION_CLASSES);

        noClasses()
                .that().resideInAPackage(BASE + "audit.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "auth.infrastructure..",
                        BASE + "capture.infrastructure..",
                        BASE + "retention.infrastructure.."
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void topLevelModulesShouldBeFreeOfCycles() {
        slices()
                .matching(BASE + "(*)..")
                .should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void captureAdminTypesShouldStayInsideCaptureAdminNamespaces() {
        classes()
                .that().resideInAPackage(CAPTURE)
                .and().haveSimpleNameStartingWith("Admin")
                .should().resideInAnyPackage(
                        CAPTURE + "api.admin..",
                        CAPTURE + "application.admin..",
                        CAPTURE + "infrastructure.persistence.admin.."
                )
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void globalSecurityConfigurationShouldStayInConfigurationNamespace() {
        classes()
                .that().haveSimpleName("ApplicationSecurityConfiguration")
                .should().resideInAPackage(BASE + "configuration.security")
                .check(PRODUCTION_CLASSES);
    }
}
