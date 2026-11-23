package com.example.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.AppSetting;
import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import com.example.library.domain.Reader;
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
    @DisplayName("首次启动写入 8 位读者与 20 条借阅记录，且数据符合借阅规则")
    void seedsReadersAndLoansOnce() {
        clearAll();
        bookSeeder.seedIfNeeded();

        int seeded = demoLoanSeeder.seedIfNeeded();

        assertThat(seeded).isEqualTo(20);
        assertThat(readers.count()).isEqualTo(8);
        assertThat(loans.count()).isEqualTo(20);
        assertThat(settings.findById(DemoLoanSeeder.SEEDED_KEY)).isPresent();

        LocalDateTime now = LocalDateTime.now();
        assertThat(loans.countByStatus(LoanStatus.RETURNED)).isEqualTo(12);
        assertThat(loans.countByStatus(LoanStatus.BORROWED)).isEqualTo(8);
        assertThat(loans.countByStatusAndDueAtBefore(LoanStatus.BORROWED, now)).isEqualTo(3);

        for (Book book : books.findAll()) {
            long borrowed = loans.countByBookIdAndStatus(book.getId(), LoanStatus.BORROWED);
            assertThat(book.getAvailableCopies())
                    .as("《" + book.getTitle() + "》可借数应等于总册数减去在借数")
                    .isEqualTo(book.getTotalCopies() - (int) borrowed);
        }

        for (Reader reader : readers.findAll()) {
            long active = loans.countByReaderIdAndStatus(reader.getId(), LoanStatus.BORROWED);
            assertThat(active)
                    .as("读者「" + reader.getName() + "」在借数不应超过上限")
                    .isLessThanOrEqualTo(reader.getMaxBorrow());
            for (Loan loan : loans.findByReaderIdAndStatusOrderByDueAtAsc(reader.getId(), LoanStatus.BORROWED)) {
                assertThat(loans.existsByReaderIdAndBookIdAndStatus(
                        reader.getId(), loan.getBook().getId(), LoanStatus.BORROWED)).isTrue();
                assertThat(loan.getDueAt()).isAfter(loan.getBorrowedAt());
            }
        }

        assertThat(demoLoanSeeder.seedIfNeeded()).isZero();
        assertThat(loans.count()).isEqualTo(20);
        assertThat(readers.count()).isEqualTo(8);
    }

    @Test
    @DisplayName("升级时清理旧示例读者及其借阅记录，并归还占用的库存")
    void removesPreviousDemoDataAndReleasesStock() {
        clearAll();
        bookSeeder.seedIfNeeded();

        Reader oldReader = new Reader();
        oldReader.setCardNo("R-2026-001");
        oldReader.setName("陈小明");
        readers.save(oldReader);

        Book book = new Book();
        book.setTitle("旧示例书");
        book.setAuthor("作者");
        book.setTotalCopies(2);
        book.setAvailableCopies(1);
        books.save(book);

        Loan oldLoan = new Loan();
        oldLoan.setBook(book);
        oldLoan.setReader(oldReader);
        oldLoan.setBorrowedAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        oldLoan.setDueAt(LocalDateTime.of(2000, 1, 31, 0, 0));
        oldLoan.setStatus(LoanStatus.BORROWED);
        loans.save(oldLoan);

        int seeded = demoLoanSeeder.seedIfNeeded();

        assertThat(seeded).isEqualTo(20);
        assertThat(readers.findByCardNo("R-2026-001")).isEmpty();
        assertThat(loans.findAll()).noneMatch(loan -> loan.getBorrowedAt().getYear() == 2000);
        assertThat(books.findById(book.getId()).orElseThrow().getAvailableCopies())
                .isEqualTo(2 - (int) loans.countByBookIdAndStatus(book.getId(), LoanStatus.BORROWED));
        assertThat(readers.count()).isEqualTo(8);
        assertThat(settings.findById(DemoLoanSeeder.SEEDED_KEY)).isPresent();
    }

    @Test
    @DisplayName("已有标记时不再写入示例借阅数据")
    void doesNotSeedTwice() {
        clearAll();
        settings.save(new AppSetting(DemoLoanSeeder.SEEDED_KEY, "20"));

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
        assertThat(readers.count()).isEqualTo(8);
        assertThat(settings.findById(DemoLoanSeeder.SEEDED_KEY)).isPresent();
    }

    @Test
    @DisplayName("示例读者带有邮箱与性别")
    void demoReadersHaveEmailAndGender() {
        List<DemoReaders.Spec> specs = DemoReaders.all();
        assertThat(specs).hasSize(8);
        assertThat(specs).allSatisfy(spec -> {
            assertThat(spec.email()).contains("@");
            assertThat(spec.gender()).isIn("男", "女");
        });
        assertThat(specs.stream().map(DemoReaders.Spec::cardNo).distinct().count()).isEqualTo(8);
    }

    private void clearAll() {
        loans.deleteAll();
        readers.deleteAll();
        books.deleteAll();
        settings.deleteAll();
    }
}
