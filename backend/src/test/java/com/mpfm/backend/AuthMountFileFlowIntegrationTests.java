package com.mpfm.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthMountFileFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerLoginMountAndFileCrudShouldWork() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "alice_" + suffix;
        String mountName = "alice-local-" + suffix;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!",
                                  "displayName":"Alice"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token.accessToken").exists());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();

        String loginJson = loginResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginJson, "$.token.accessToken");

        MvcResult mountResult = mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false,
                                  "root":"./target/test-local/%s/%s"
                                }
                                """.formatted(mountName, username, mountName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("enabled"))
                .andReturn();

        String mountId = JsonPath.read(mountResult.getResponse().getContentAsString(), "$.mountId");
        String filePath = "/personal/%s/docs/readme.txt".formatted(mountId);

        mockMvc.perform(put("/api/v1/files/content")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "virtualPath":"%s",
                                  "content":"hello"
                                }
                                """.formatted(filePath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("readme.txt"));

        mockMvc.perform(get("/api/v1/files/content")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("virtualPath", filePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("hello"));

        mockMvc.perform(delete("/api/v1/files")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("If-Match", "*")
                        .param("virtualPath", filePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(get("/api/v1/files/content")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("virtualPath", filePath))
                .andExpect(status().isNotFound());
    }
}

