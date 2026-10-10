package com.example.bootserver.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** 领域模块的可执行架构边界。 */
class ModuleArchitectureTest {

    private static final String BASE = "com.example.bootserver.";
    private static final List<String> MODULES = List.of(
            "identity", "catalog", "cart", "inventory", "order", "payment");

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.example.bootserver");
    }

    @Test
    void modulesExposeOnlyApiAndKeepImplementationInternal() {
        classes().that().resideInAnyPackage(MODULES.stream()
                        .map(module -> BASE + module + "..").toArray(String[]::new))
                .should().resideInAnyPackage(MODULES.stream()
                        .flatMap(module -> List.of(BASE + module + ".api..", BASE + module + ".internal..").stream())
                        .toArray(String[]::new))
                .because("领域模块中的类型只能位于公开 api 或 internal 实现区")
                .check(productionClasses);

        noClasses().that().resideInAPackage("com.example.bootserver..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.example.bootserver..internal..",
                        "org.springframework..", "com.baomidou..", "jakarta..")
                .because("模块公开 API 必须是稳定且与实现框架无关的契约")
                .check(productionClasses);
    }

    @Test
    void crossModuleDependenciesTargetOnlyPublicApi() {
        for (String source : MODULES) {
            for (String target : MODULES) {
                if (!source.equals(target)) {
                    noClasses().that().resideInAPackage(BASE + source + "..")
                            .should().dependOnClassesThat().resideInAPackage(BASE + target + ".internal..")
                            .because(source + " 模块只能通过 " + target + ".api 协作")
                            .check(productionClasses);
                }
            }
        }
    }

    @Test
    void internalLayersFollowDependencyDirection() {
        noClasses().that().resideInAPackage("com.example.bootserver..internal.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.example.bootserver..internal.interfaces..",
                        "com.example.bootserver..internal.infrastructure..")
                .because("应用层只能编排领域模型和端口")
                .check(productionClasses);

        noClasses().that().resideInAPackage("com.example.bootserver..internal.interfaces..")
                .should().dependOnClassesThat().resideInAPackage(
                        "com.example.bootserver..internal.infrastructure..")
                .because("接口层不能绕过应用层直接访问基础设施")
                .check(productionClasses);

        noClasses().that().resideInAPackage("com.example.bootserver..internal.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "com.baomidou..", "org.apache.ibatis..",
                        "jakarta..", "com.fasterxml.jackson..", "lombok..",
                        "com.example.bootserver.common.error..")
                .because("领域层必须保持纯 Java")
                .check(productionClasses);
    }

    @Test
    void commandAndQueryModelsStaySeparated() {
        noClasses().that().resideInAPackage("com.example.bootserver..internal.application.command..")
                .should().dependOnClassesThat().resideInAPackage(
                        "com.example.bootserver..internal.application.query..")
                .because("命令侧不能依赖查询侧实现")
                .check(productionClasses);
        noClasses().that().resideInAPackage("com.example.bootserver..internal.application.query..")
                .should().dependOnClassesThat().resideInAPackage(
                        "com.example.bootserver..internal.application.command..")
                .because("查询侧不能依赖命令侧实现")
                .check(productionClasses);
    }

    @Test
    void topLevelPackagesAreFreeOfCycles() {
        slices().matching("com.example.bootserver.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses);
    }
}
