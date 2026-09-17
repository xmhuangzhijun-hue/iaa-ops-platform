package com.iaaops.system;

import com.iaaops.shared.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SystemController {

    private final AppProperties properties;

    SystemController(AppProperties properties) {
        this.properties = properties;
    }

    /** 与契约中的 getHealth 一致：仅表示进程存活，不体现数据库可用性。 */
    @GetMapping("/health")
    public Health health() {
        return new Health("ok", properties.version(), properties.environment());
    }

    public record Health(String status, String version, String environment) {
    }
}
