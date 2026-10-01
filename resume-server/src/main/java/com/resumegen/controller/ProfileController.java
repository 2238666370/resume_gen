package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.dto.ProfileRequest;
import com.resumegen.dto.ProfileVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<ProfileVO> get() {
        return ApiResponse.ok(profileService.get(UserContext.userId()));
    }

    @PutMapping
    public ApiResponse<ProfileVO> update(@Valid @RequestBody ProfileRequest req) {
        return ApiResponse.ok(profileService.update(UserContext.userId(), req));
    }
}