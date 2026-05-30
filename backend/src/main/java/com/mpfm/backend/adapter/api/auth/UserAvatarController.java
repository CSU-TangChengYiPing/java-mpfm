package com.mpfm.backend.adapter.api.auth;

import com.mpfm.backend.application.user.AvatarStorageService;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserAvatarController {

    private final AvatarStorageService avatarStorageService;

    public UserAvatarController(AvatarStorageService avatarStorageService) {
        this.avatarStorageService = avatarStorageService;
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> avatar(@PathVariable UUID userId,
                                           @RequestParam String exp,
                                           @RequestParam String sig) {
        avatarStorageService.verifyAvatarAccess(userId.toString(), exp, sig);
        Resource resource = avatarStorageService.load(userId.toString());
        String contentType = avatarStorageService.probeContentType(resource);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
