package com.example.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.AppSetting;
import com.example.library.domain.Book;
import com.example.library.domain.LoanStatus;
import com.example.library.repository.AppSettingRepository;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.ReaderRepository;

@SpringBootTest
@Transactional
class DemoLoanSeederTest {

    @Autowired
    private DemoLoanSeeder demoLoanSeeder;

    @Autowired
    private BookSeeder bookSeeder;

    @Autowired
    private BookRepository books;

    @Autowired
    private ReaderRepository readers;

    @Autowired
    private LoanRepository loans;

    @Autowired
    private AppSettingRepository settings;

    @Test
    @DisplayName("首次启动写入 6 位读者与 13 条借阅记录（含逾期），并扣减在借图书的可借数")
    void seedsReadersAndLoansOnce() {
        clearAll();
        bookSeeder.seedIfNeeded();

        int seeded = demoLoanSeeder.seedIfNeeded();

        assertThat(seeded).isEqualTo(13);
        assertThat(readers.count()).isEqualTo(6);
        assertThat(loans.count()).isEqualTo(13);
        assertThat(settings.findById(DemoLoanSeeder.SEEDED_KEY)).isPresent();

        LocalDateTime now = LocalDateTime.now();
        assertThat(loans.countByStatusAndDueAtBefore(LoanStatus.BORROWED, now)).isEqualTo(3);
        assertThat(loans.countByStatus(LoanStatus.RETURNED)).isEqualTo(6);
        assertThat(loans.countByStatus(LoanStatus.BORROWED)).isEqualTo(7);

        for (Book book : books.findAll()) {
            long borrowed = loans.countByBookIdAndStatus(book.getId(), LoanStatus.BORROWED);
            assertThat(book.getAvailableCopies())
                    .as("《" + book.getTitle() + "》可借数应等于总册数减去在借数")
                    .isEqualTo(book.getTotalCopies() - (int) borrowed);
        }

        assertThat(demoLoanSeeder.seedIfNeeded()).isZero();
        assertThat(loans.count()).isEqualTo(13);
        assertThat(readers.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("已有标记时不再写入示例借阅数据")
    void doesNotSeedTwice() {
        clearAll();
        settings.save(new AppSetting(DemoLoanSeeder.SEEDED_KEY, "13"));

        assertThat(demoLoanSeeder.seedIfNeeded()).isZero();
        assertThat(readers.count()).isZero();
        assertThat(loans.count()).isZero();
    }

    @Test
    @DisplayName("没有可用图书时不生成借阅记录，但仍写入标记")
    void skipsWhenNoBooksAvailable() {
        clearAll();

        assertThat(demoLoanSeeder.seedIfNeeded()).isZero();
        assertThat(loans.count()).isZero();
        assertThat(readers.count()).isEqualTo(6);
        assertThat(settings.findById(DemoLoanSeeder.SEEDED_KEY)).isPresent();
    }

    private void clearAll() {
        loans.deleteAll();
        readers.deleteAll();
        books.deleteAll();
        settings.deleteAll();
    }
}
