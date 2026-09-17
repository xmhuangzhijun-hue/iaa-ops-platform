package com.iaaops;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 模块边界（docs/design/module-boundaries.md）：
 * 模块之间只经各自的应用服务通信，不得跨模块直接引用持久层或 Web 层；共享内核不依赖任何业务模块。
 */
@AnalyzeClasses(packages = "com.iaaops", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final String[] MODULES = {
            "com.iaaops.iam..", "com.iaaops.reporting..", "com.iaaops.mapping..",
            "com.iaaops.governance..", "com.iaaops.ingestion..", "com.iaaops.system..",
    };

    @ArchTest
    static final ArchRule 共享内核不依赖业务模块 = noClasses()
            .that().resideInAPackage("com.iaaops.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(MODULES);

    @ArchTest
    static final ArchRule iam_的持久层只属于_iam = persistenceIsPrivate("iam");

    @ArchTest
    static final ArchRule reporting_的持久层只属于_reporting = persistenceIsPrivate("reporting");

    @ArchTest
    static final ArchRule mapping_的持久层只属于_mapping = persistenceIsPrivate("mapping");

    @ArchTest
    static final ArchRule governance_的持久层只属于_governance = persistenceIsPrivate("governance");

    @ArchTest
    static final ArchRule iam_的_Web_层只属于_iam = webIsPrivate("iam");

    @ArchTest
    static final ArchRule reporting_的_Web_层只属于_reporting = webIsPrivate("reporting");

    @ArchTest
    static final ArchRule mapping_的_Web_层只属于_mapping = webIsPrivate("mapping");

    @ArchTest
    static final ArchRule governance_的_Web_层只属于_governance = webIsPrivate("governance");

    @ArchTest
    static final ArchRule ingestion_的持久层只属于_ingestion = persistenceIsPrivate("ingestion");

    @ArchTest
    static final ArchRule ingestion_的_Web_层只属于_ingestion = webIsPrivate("ingestion");

    @ArchTest
    static final ArchRule 报表模块不被其他模块依赖 = noClasses()
            .that().resideOutsideOfPackage("com.iaaops.reporting..")
            .should().dependOnClassesThat().resideInAPackage("com.iaaops.reporting..");

    @ArchTest
    static final ArchRule 持久层不出现在控制器里 = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");

    /** 模块的 persistence 包是模块内部实现，跨模块要数据只能走应用服务。 */
    private static ArchRule persistenceIsPrivate(String module) {
        return noClasses()
                .that().resideOutsideOfPackage("com.iaaops." + module + "..")
                .should().dependOnClassesThat().resideInAPackage("com.iaaops." + module + ".persistence..");
    }

    private static ArchRule webIsPrivate(String module) {
        return noClasses()
                .that().resideOutsideOfPackage("com.iaaops." + module + "..")
                .should().dependOnClassesThat().resideInAPackage("com.iaaops." + module + ".web..");
    }
}
