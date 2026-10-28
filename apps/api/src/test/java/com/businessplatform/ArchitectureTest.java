package com.businessplatform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** The shared core never depends on a module; modules reach the core through its interfaces. */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.businessplatform");

    @Test
    void the_core_does_not_depend_on_modules() {
        noClasses().that().resideInAnyPackage("..common..", "..identity..", "..org..", "..module..", "..workflow..",
                "..calendar..", "..activity..", "..notification..", "..file..", "..document..", "..oidc..")
                .should().dependOnClassesThat().resideInAnyPackage("..project..", "..overview..", "..report..", "..hr..",
                        "..retail..", "..business..")
                .check(CLASSES);
    }

    @Test
    void modules_do_not_depend_on_each_other() {
        noClasses().that().resideInAPackage("..hr..")
                .should().dependOnClassesThat().resideInAnyPackage("..project..", "..overview..", "..report..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..project..")
                .should().dependOnClassesThat().resideInAnyPackage("..hr..", "..retail..", "..business..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..hr..")
                .should().dependOnClassesThat().resideInAPackage("..retail..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..retail..")
                .should().dependOnClassesThat().resideInAnyPackage("..project..", "..hr..", "..overview..", "..report..",
                        "..business..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..hr..")
                .should().dependOnClassesThat().resideInAPackage("..business..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..business..")
                .should().dependOnClassesThat().resideInAnyPackage("..project..", "..hr..", "..retail..", "..overview..",
                        "..report..")
                .check(CLASSES);
    }

    @Test
    void controllers_do_not_use_repositories() {
        noClasses().that().haveSimpleNameEndingWith("Controller").and().doNotHaveSimpleName("MeController")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .check(CLASSES);
    }
}
