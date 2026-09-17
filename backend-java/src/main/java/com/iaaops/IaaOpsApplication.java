package com.iaaops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 模块化单体：每个业务模块是 com.iaaops 下的一个包，模块之间只经应用服务与领域事件通信。
 * 模块清单与依赖方向见 docs/design/module-boundaries.md，边界由 ArchUnit 测试校验。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class IaaOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(IaaOpsApplication.class, args);
    }
}
