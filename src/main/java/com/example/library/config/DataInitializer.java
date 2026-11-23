package com.example.library.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.library.domain.UserAccount;
import com.example.library.repository.UserAccountRepository;

/** 首次启动时创建默认管理员账号，并按开关导入示例图书与示例借阅数据 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BookSeeder bookSeeder;
    private final DemoLoanSeeder demoLoanSeeder;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean seedBooks;
    private final boolean seedDemoLoans;

    public DataInitializer(UserAccountRepository users,
                           PasswordEncoder passwordEncoder,
                           BookSeeder bookSeeder,
                           DemoLoanSeeder demoLoanSeeder,
                           @Value("${library.admin.username:admin}") String adminUsername,
                           @Value("${library.admin.password:admin123}") String adminPassword,
                           @Value("${library.seed.books:true}") boolean seedBooks,
                           @Value("${library.seed.demo-loans:true}") boolean seedDemoLoans) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.bookSeeder = bookSeeder;
        this.demoLoanSeeder = demoLoanSeeder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.seedBooks = seedBooks;
        this.seedDemoLoans = seedDemoLoans;
    }

    @Override
    public void run(ApplicationArguments args) {
        createAdminIfNeeded();
        if (seedBooks) {
            bookSeeder.seedIfNeeded();
        }
        if (seedDemoLoans) {
            demoLoanSeeder.seedIfNeeded();
        }
    }

    private void createAdminIfNeeded() {
        if (users.count() > 0) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        users.save(admin);
        log.info("已创建默认管理员账号：{}（初始密码见配置项 library.admin.password）", adminUsername);
    }
}
