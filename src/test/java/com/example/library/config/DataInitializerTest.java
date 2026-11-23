package com.example.library.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.library.domain.UserAccount;
import com.example.library.repository.UserAccountRepository;

class DataInitializerTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final BookSeeder bookSeeder = mock(BookSeeder.class);

    private DataInitializer initializer(boolean seedBooks) {
        return new DataInitializer(users, passwordEncoder, bookSeeder,
                "admin", "admin123", seedBooks);
    }

    private void run(DataInitializer initializer) {
        initializer.run(new DefaultApplicationArguments(new String[0]));
    }

    @Test
    @DisplayName("空库首次启动时创建管理员并导入示例图书")
    void createsAdminAndSeedsBooks() {
        when(users.count()).thenReturn(0L);
        when(passwordEncoder.encode("admin123")).thenReturn("encoded");
        when(bookSeeder.seedIfNeeded()).thenReturn(40);

        run(initializer(true));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(captor.capture());
        UserAccount admin = captor.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getPassword()).isEqualTo("encoded");
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        verify(bookSeeder).seedIfNeeded();
    }

    @Test
    @DisplayName("已有管理员时不再创建账号，但仍会检查示例图书")
    void skipsAdminWhenPresentButStillSeedsBooks() {
        when(users.count()).thenReturn(1L);

        run(initializer(true));

        verify(users, never()).save(any(UserAccount.class));
        verify(bookSeeder).seedIfNeeded();
    }

    @Test
    @DisplayName("关闭示例数据开关后不导入图书")
    void skipsSeedingWhenDisabled() {
        when(users.count()).thenReturn(1L);

        run(initializer(false));

        verifyNoInteractions(bookSeeder);
    }
}
