package io.github.abdellahjebar.paraphe;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The module boundaries are checked here, not left to convention.
 *
 * <p>Layout of a module: {@code domain} (plain Java), {@code port} (interfaces), {@code application}
 * (use cases), {@code infrastructure} (adapters: JDBC, web, storage).
 */
class ArchitectureTest {

    private static final String BASE = "io.github.abdellahjebar.paraphe";
    private static final String[] MODULES = {"audit", "reviews", "projects", "documents", "submission", "identity"};

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void domainDoesNotDependOnFrameworksOrDatabaseApis() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "java.sql..", "javax.sql..")
                .because("business rules must be testable without infrastructure")
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnOtherLayersOfItsModule() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application..", "..infrastructure..", "..port..")
                .check(classes);
    }

    @Test
    void useCasesDoNotDependOnAdapters() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("use cases talk to ports, never to JDBC or web adapters")
                .check(classes);
    }

    @Test
    void modulesAreFreeOfCycles() {
        slices().matching(BASE + ".(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }

    @Test
    void aModuleNeverReachesIntoAnotherModulesAdapters() {
        for (String module : MODULES) {
            noClasses().that().resideOutsideOfPackage(BASE + "." + module + "..")
                    .should().dependOnClassesThat().resideInAPackage(BASE + "." + module + ".infrastructure..")
                    .because("only the " + module + " module may use its own adapters")
                    .check(classes);
        }
    }

    @Test
    void theSharedKernelDependsOnNoModule() {
        noClasses().that().resideInAPackage(BASE + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".audit..", BASE + ".reviews..", BASE + ".projects..", BASE + ".documents..",
                        BASE + ".submission..", BASE + ".identity..", BASE + ".api..")
                .because("the shared kernel exists to break cycles, so it must stay at the bottom")
                .check(classes);
    }
}
