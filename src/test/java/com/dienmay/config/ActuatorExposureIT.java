package com.dienmay.config;

import com.dienmay.support.MySqlIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ActuatorExposureIT extends MySqlIntegrationTestBase {
    @Autowired MockMvc mvc;
    @Test void health_anonymous_returnsOnlyOverallStatus()throws Exception{
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}",true));
    }
    @WithMockUser(roles="ADMIN")
    @ParameterizedTest @ValueSource(strings={"/actuator","/actuator/env","/actuator/beans","/actuator/heapdump","/actuator/health/db"})
    void actuator_adminCannotReadOtherEndpoints_returnsForbidden(String path)throws Exception{
        mvc.perform(get(path)).andExpect(status().isForbidden());
    }
}
