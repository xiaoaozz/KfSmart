package com.smart.kf.service;

import com.smart.kf.exception.CustomException;
import com.smart.kf.model.User;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.LoginRecordRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.utils.PasswordUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * UserService 的测试类
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    // 模拟 UserRepository 实例
    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationTagRepository organizationTagRepository;

    @Mock
    @SuppressWarnings("unused")
    private FileUploadRepository fileUploadRepository;

    @Mock
    @SuppressWarnings("unused")
    private OrgTagCacheService orgTagCacheService;

    @Mock
    @SuppressWarnings("unused")
    private LoginRecordRepository loginRecordRepository;

    @InjectMocks
    private UserService userService;

    /**
     * 测试用户注册成功的情况
     */
    @Test
    void testRegisterUser_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(organizationTagRepository.existsByTagId("DEFAULT")).thenReturn(false);
        when(organizationTagRepository.existsByTagId("default")).thenReturn(false);
        when(organizationTagRepository.existsByTagId("PRIVATE_testuser")).thenReturn(false);
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) user.setId(1L);
            return user;
        });

        userService.registerUser("testuser", "password123");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());

        User savedUser = userCaptor.getAllValues().stream()
                .filter(u -> "testuser".equals(u.getUsername()))
                .findFirst().orElse(null);
        assertNotNull(savedUser);
        assertEquals("testuser", savedUser.getUsername());
    }

    /**
     * 测试用户注册时用户名已存在的情况
     */
    @Test
    void testRegisterUser_UsernameExists() {
        // 假设用户名 "testuser" 在数据库中已存在
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(new User()));

        // 断言在注册已存在的用户名时抛出 CustomException 异常
        CustomException exception = assertThrows(CustomException.class, () -> userService.registerUser("testuser", "password123"));
        assertEquals("Username already exists", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    /**
     * 测试用户认证成功的情况
     */
   @Test
void testAuthenticateUser_Success() {
    // 使用 PasswordUtil 生成加密密码
    String rawPassword = "password123";
    String encodedPassword = PasswordUtil.encode(rawPassword);

    // 创建一个带有加密密码的用户对象，并确保设置了用户名
    User user = new User();
    user.setUsername("testuser"); // 确保设置了用户名
    user.setPassword(encodedPassword);

    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

    try {
        // 调用 userService 的 authenticateUser 方法进行用户认证
        String username = userService.authenticateUser("testuser", rawPassword);

        // 打印返回的用户名（仅用于调试）
        System.out.println("Returned username: " + username);

        // 断言返回的用户名是否正确
        assertEquals("testuser", username);
    } catch (CustomException e) {
        // 捕获并打印异常信息
        System.out.println("Exception message: " + e.getMessage());
        throw e;
    }

    // 打印实际的加密密码（仅用于调试）
    System.out.println("Actual encrypted password: " + user.getPassword());
}





    /**
     * 测试用户认证失败的情况
     */
    @Test
    void testAuthenticateUser_InvalidCredentials() {
        // 假设用户名 "testuser" 在数据库中不存在
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // 断言在使用错误密码认证时抛出 CustomException 异常
        CustomException exception = assertThrows(CustomException.class, () -> userService.authenticateUser("testuser", "wrongpassword"));
        assertEquals("用户名或密码错误", exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }
}
