package com.iaaops.iam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iaaops.support.PostgresTestBase;
import org.junit.jupiter.api.Test;

class CorsDefaultApiTest extends PostgresTestBase {
    @Test
    void defaultBrowserOriginsRemainExplicitAndDoNotAllowThePreviewPort() throws Exception {
        for (String origin : new String[] {"http://127.0.0.1:5173", "http://localhost:5173"}) {
            mvc.perform(options("/api/v1/agent/runs").header("Origin", origin)
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
        mvc.perform(options("/api/v1/agent/runs").header("Origin", "http://127.0.0.1:5180")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
