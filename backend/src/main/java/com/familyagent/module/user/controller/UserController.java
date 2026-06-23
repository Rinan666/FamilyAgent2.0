package com.familyagent.module.user.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.user.dto.ChangePasswordRequest;
import com.familyagent.module.user.dto.LoginRequest;
import com.familyagent.module.user.dto.LoginResponse;
import com.familyagent.module.user.dto.RegisterRequest;
import com.familyagent.module.user.dto.UpdateProfileRequest;
import com.familyagent.module.user.dto.WeChatLoginRequest;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.service.UserService;
import com.familyagent.module.user.service.WeChatLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User controller.
 */
@Tag(name = "鐢ㄦ埛绠＄悊")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WeChatLoginService weChatLoginService;

    @Operation(summary = "鐢ㄦ埛娉ㄥ唽")
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(request.getUsername());
        loginRequest.setPassword(request.getPassword());

        return Result.success(userService.login(loginRequest));
    }

    @Operation(summary = "鐢ㄦ埛鐧诲綍")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success(response);
    }

    @Operation(summary = "WeChat mini app login")
    @PostMapping("/wechat/login")
    public Result<LoginResponse> wechatLogin(@Valid @RequestBody WeChatLoginRequest request) {
        return Result.success(weChatLoginService.login(request));
    }

    @Operation(summary = "鑾峰彇褰撳墠鐢ㄦ埛淇℃伅")
    @GetMapping("/me")
    public Result<User> getCurrentUser() {
        User user = userService.getCurrentUser();
        return Result.success(user);
    }

    @Operation(summary = "鏇存柊褰撳墠鐢ㄦ埛璧勬枡")
    @PostMapping("/me/profile")
    public Result<User> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        User user = userService.updateProfile(request);
        return Result.success(user);
    }

    @Operation(summary = "淇敼褰撳墠鐢ㄦ埛瀵嗙爜")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Result.success();
    }

    @Operation(summary = "鑾峰彇鐢ㄦ埛淇℃伅")
    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        CurrentUserGuard.requireSelf(id);
        User user = userService.getById(id);
        return Result.success(user);
    }
}
