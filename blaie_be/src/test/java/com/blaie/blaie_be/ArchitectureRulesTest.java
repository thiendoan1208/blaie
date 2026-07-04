package com.blaie.blaie_be;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.blaie.blaie_be");

    @Test
    void domainShouldStayIndependentFromAdaptersAndApi() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..api..",
                        "..infrastructure..",
                        "jakarta.persistence..",
                        "org.springframework.data..",
                        "org.springframework.web.."
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
                .that().resideInAPackage("..application.port..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
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
    void coreRateLimitShouldNotDependOnAuthModule() {
        noClasses()
                .that().resideInAPackage("..core.ratelimit..")
                .should().dependOnClassesThat().resideInAPackage("..auth..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void coreShouldNotDependOnBusinessModules() {
        noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..auth..",
                        "..authz.."
                )
                .check(PRODUCTION_CLASSES);
    }
}
