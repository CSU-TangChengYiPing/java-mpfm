package com.mpfm.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * v4 上传运行时集成测试：验证任务 SUCCESS 与物理落盘强绑定。
 * 若任务已 SUCCESS 但目标目录不存在文件，测试必须失败，防止“假成功”回归。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferUploadRuntimeIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadRuntimeTaskSuccessMustHaveFileInMountRoot() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "u_rt_" + suffix;
        String password = "Passw0rd!";
        String mountName = "local-" + suffix;
        String fileName = "upload-" + suffix + ".txt";
        String content = "runtime-upload-content-" + suffix;
        Path mountRoot = Path.of("./target/test-local/" + username + "/" + mountName).normalize();

        register(username, password);
        String accessToken = loginAndGetAccessToken(username, password);
        MountRef mount = createMount(accessToken, mountName, mountRoot);

        MvcResult uploadResult = mockMvc.perform(post("/api/v4/transfers/uploads/runtime/tasks")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("X-Upload-Virtual-Path", "/personal/" + mount.mountId() + "/")
                        .header("X-Upload-Filename", fileName)
                        .header("X-Upload-Size", content.getBytes(StandardCharsets.UTF_8).length)
                        .content(content.getBytes(StandardCharsets.UTF_8))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String uploadJson = uploadResult.getResponse().getContentAsString();
        String uploadId = JsonPath.read(uploadJson, "$.uploadId");
        waitTaskToSuccess(accessToken, uploadId, Duration.ofSeconds(8));

        Path targetFile = Path.of(mount.root()).resolve(fileName).normalize();
        assertThat(Files.exists(targetFile)).isTrue();
        assertThat(Files.readString(targetFile, StandardCharsets.UTF_8)).isEqualTo(content);
    }

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"%s",
                                  "displayName":"Runtime User"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk());
    }

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.token.accessToken");
    }

    private MountRef createMount(String accessToken, String mountName, Path mountRoot) throws Exception {
        MvcResult mountResult = mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false,
                                  "root":"%s"
                                }
                                """.formatted(mountName, mountRoot.toString().replace("\\", "\\\\"))))
                .andExpect(status().isOk())
                .andReturn();
        String body = mountResult.getResponse().getContentAsString();
        return new MountRef(
                JsonPath.read(body, "$.mountId"),
                JsonPath.read(body, "$.root"));
    }

    private record MountRef(String mountId, String root) { }

    private void waitTaskToSuccess(String accessToken, String taskId, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String lastStatus = "";
        while (System.currentTimeMillis() < deadline) {
            MvcResult taskResult = mockMvc.perform(get("/api/v4/transfers/uploads/{taskId}", taskId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = taskResult.getResponse().getContentAsString();
            lastStatus = JsonPath.read(body, "$.status");
            if ("success".equalsIgnoreCase(lastStatus)) {
                return;
            }
            Thread.sleep(120L);
        }
        assertThat(lastStatus).isEqualToIgnoringCase("success");
    }
}
