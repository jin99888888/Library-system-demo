package com.example.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import com.example.library.domain.Reader;
import com.example.library.domain.ReaderStatus;

@SpringBootTest
@Transactional
class LoanServiceTest {

    @Autowired
    private BookService books;

    @Autowired
    private ReaderService readers;

    @Autowired
    private LoanService loans;

    private Book newBook(String title, int copies) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("作者");
        book.setTotalCopies(copies);
        return books.create(book);
    }

    private Reader newReader(String cardNo) {
        Reader reader = new Reader();
        reader.setCardNo(cardNo);
        reader.setName("借阅者-" + cardNo);
        return readers.create(reader);
    }

    @Test
    @DisplayName("借书成功：库存减一、生成在借记录、应还时间为 30 天后")
    void borrowSucceeds() {
        Book book = newBook("Clean Code", 2);
        Reader reader = newReader("R-1001");

        LocalDateTime before = LocalDateTime.now();
        Loan loan = loans.borrow(book.getId(), reader.getId());

        assertThat(loan.getId()).isNotNull();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.BORROWED);
        assertThat(loan.getBorrowedAt()).isNotNull();
        assertThat(loan.getDueAt()).isCloseTo(before.plusDays(30), within(2, ChronoUnit.MINUTES));
        assertThat(loan.isOverdue()).isFalse();
        assertThat(loan.getOverdueDays()).isZero();

        Book reloaded = books.get(book.getId());
        assertThat(reloaded.getAvailableCopies()).isEqualTo(1);
        assertThat(reloaded.getBorrowedCopies()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有可借册数时拒绝借书")
    void borrowFailsWhenNoCopiesAvailable() {
        Book book = newBook("独占书", 1);
        Reader first = newReader("R-1002");
        Reader second = newReader("R-1003");
        loans.borrow(book.getId(), first.getId());

        assertThatThrownBy(() -> loans.borrow(book.getId(), second.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已无可借册数");
    }

    @Test
    @DisplayName("停用读者不能借书")
    void borrowFailsWhenReaderDisabled() {
        Book book = newBook("停用测试", 2);
        Reader reader = newReader("R-1004");
        reader.setStatus(ReaderStatus.DISABLED);
        readers.update(reader.getId(), reader);

        assertThatThrownBy(() -> loans.borrow(book.getId(), reader.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    @DisplayName("超过读者借阅上限时拒绝借书")
    void borrowFailsWhenReaderReachesLimit() {
        Reader reader = newReader("R-1005");
        reader.setMaxBorrow(1);
        readers.update(reader.getId(), reader);
        Book firstBook = newBook("上限测试一", 1);
        Book secondBook = newBook("上限测试二", 1);

        loans.borrow(firstBook.getId(), reader.getId());

        assertThatThrownBy(() -> loans.borrow(secondBook.getId(), reader.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("在借数已达上限");
    }

    @Test
    @DisplayName("同一读者不能重复借阅同一本未归还的书")
    void borrowFailsForDuplicateActiveLoan() {
        Book book = newBook("重复借阅", 2);
        Reader reader = newReader("R-1006");
        loans.borrow(book.getId(), reader.getId());

        assertThatThrownBy(() -> loans.borrow(book.getId(), reader.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已借阅");
    }

    @Test
    @DisplayName("不存在的图书或读者借书时报错")
    void borrowFailsForUnknownBookOrReader() {
        Book book = newBook("缺失对象", 1);
        Reader reader = newReader("R-1007");

        assertThatThrownBy(() -> loans.borrow(-1L, reader.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图书不存在");
        assertThatThrownBy(() -> loans.borrow(book.getId(), -1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("读者不存在");
    }

    @Test
    @DisplayName("还书成功：状态置为已归还并恢复库存")
    void returnLoanRestoresCopy() {
        Book book = newBook("还书测试", 1);
        Reader reader = newReader("R-1008");
        Loan loan = loans.borrow(book.getId(), reader.getId());

        Loan returned = loans.returnLoan(loan.getId());

        assertThat(returned.getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(returned.getReturnedAt()).isNotNull();
        assertThat(books.get(book.getId()).getAvailableCopies()).isEqualTo(1);

        // 归还后可以再次借阅
        Loan again = loans.borrow(book.getId(), reader.getId());
        assertThat(again.getId()).isNotNull();
    }

    @Test
    @DisplayName("重复还书被拒绝")
    void returningTwiceIsRejected() {
        Book book = newBook("重复还书", 1);
        Reader reader = newReader("R-1009");
        Loan loan = loans.borrow(book.getId(), reader.getId());
        loans.returnLoan(loan.getId());

        assertThatThrownBy(() -> loans.returnLoan(loan.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已归还");
    }

    @Test
    @DisplayName("借还记录按状态筛选：ALL / BORROWED / RETURNED / OVERDUE")
    void pageFiltersByStatus() {
        Book book = newBook("筛选测试", 2);
        Reader reader = newReader("R-1010");
        Loan active = loans.borrow(book.getId(), reader.getId());
        Book other = newBook("筛选测试二", 1);
        Reader otherReader = newReader("R-1011");
        Loan returned = loans.borrow(other.getId(), otherReader.getId());
        loans.returnLoan(returned.getId());

        assertThat(loans.page("ALL", 0, 50).getTotalElements()).isEqualTo(2);
        assertThat(loans.page("BORROWED", 0, 50).getTotalElements()).isEqualTo(1);
        assertThat(loans.page("RETURNED", 0, 50).getTotalElements()).isEqualTo(1);
        // 未逾期的在借记录不会出现在 OVERDUE 中
        assertThat(loans.page("OVERDUE", 0, 50).getTotalElements()).isZero();
        assertThat(loans.page("BORROWED", 0, 50).getContent().get(0).getId()).isEqualTo(active.getId());
    }

    @Test
    @DisplayName("逾期天数按应还日期计算，至少 1 天")
    void overdueDaysComputed() {
        Loan loan = new Loan();
        loan.setBorrowedAt(LocalDateTime.now().minusDays(40));
        loan.setDueAt(LocalDateTime.now().minusDays(10));
        loan.setStatus(LoanStatus.BORROWED);

        assertThat(loan.isOverdue()).isTrue();
        assertThat(loan.getOverdueDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("图书维度：在借记录与归还历史分开返回")
    void bookScopedLoans() {
        Book book = newBook("图书维度", 3);
        Reader first = newReader("R-1020");
        Reader second = newReader("R-1021");
        Loan active = loans.borrow(book.getId(), first.getId());
        Loan returned = loans.borrow(book.getId(), second.getId());
        loans.returnLoan(returned.getId());

        assertThat(loans.activeByBook(book.getId()))
                .extracting(Loan::getId)
                .containsExactly(active.getId());
        Page<Loan> history = loans.historyByBook(book.getId(), 0, 10);
        assertThat(history.getTotalElements()).isEqualTo(1);
        assertThat(history.getContent().get(0).getId()).isEqualTo(returned.getId());
    }

    @Test
    @DisplayName("读者维度：在借记录与归还历史分开返回")
    void readerScopedLoans() {
        Book firstBook = newBook("读者维度一", 1);
        Book secondBook = newBook("读者维度二", 1);
        Reader reader = newReader("R-1022");
        Loan active = loans.borrow(firstBook.getId(), reader.getId());
        Loan returned = loans.borrow(secondBook.getId(), reader.getId());
        loans.returnLoan(returned.getId());

        assertThat(loans.activeByReader(reader.getId()))
                .extracting(Loan::getId)
                .containsExactly(active.getId());
        Page<Loan> history = loans.historyByReader(reader.getId(), 0, 10);
        assertThat(history.getTotalElements()).isEqualTo(1);
        assertThat(history.getContent().get(0).getId()).isEqualTo(returned.getId());
    }

    @Test
    @DisplayName("借阅历史按归还时间倒序分页，超出范围返回空页")
    void historyIsPaged() {
        Book book = newBook("分页历史", 3);
        for (int i = 0; i < 3; i++) {
            Reader reader = newReader("R-103" + i);
            Loan loan = loans.borrow(book.getId(), reader.getId());
            loans.returnLoan(loan.getId());
        }

        Page<Loan> firstPage = loans.historyByBook(book.getId(), 0, 2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);

        Page<Loan> secondPage = loans.historyByBook(book.getId(), 1, 2);
        assertThat(secondPage.getContent()).hasSize(1);

        Page<Loan> emptyPage = loans.historyByBook(book.getId(), 5, 2);
        assertThat(emptyPage.getContent()).isEmpty();
    }
}
