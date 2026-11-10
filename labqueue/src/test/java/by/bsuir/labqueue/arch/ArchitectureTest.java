package by.bsuir.labqueue.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Правила зависимостей модулей (представление SDD 003, REQ-MAINT-001). */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("by.bsuir.labqueue");

    @Test
    void queueCoreDependsOnlyOnJdk() {
        classes().that().resideInAPackage("..labqueue.queue..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..labqueue.queue..")
                .check(CLASSES);
    }

    @Test
    void onlyWebLayerUsesControllers() {
        noClasses().that().resideOutsideOfPackage("..labqueue.web..")
                .should().dependOnClassesThat().resideInAPackage("..labqueue.web..")
                .check(CLASSES);
    }
}
