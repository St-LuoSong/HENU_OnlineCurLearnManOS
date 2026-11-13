package com.onlinecurlearnmanos.config;

import com.onlinecurlearnmanos.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserService userService;
    private final String username;
    private final String password;

    public BootstrapAdminInitializer(
            UserService userService,
            @Value("${app.bootstrap-admin.username:admin}") String username,
            @Value("${app.bootstrap-admin.password:}") String password
    ) {
        this.userService = userService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(password)) {
            log.warn("Bootstrap admin is enabled but APP_BOOTSTRAP_ADMIN_PASSWORD is empty");
            return;
        }
        userService.createUserIfAbsent(username, password, "系统管理员", null, "ADMIN");
        log.info("Bootstrap admin account is ready: {}", username);
    }
}
