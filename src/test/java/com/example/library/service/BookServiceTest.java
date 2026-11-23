package com.example.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.Book;
import com.example.library.domain.Reader;

@SpringBootTest
@Transactional
class BookServiceTest {

    @Autowired
    private BookService books;

    @Autowired
    private ReaderService readers;

    @Autowired
    private LoanService loans;

    private Book newBook(String title, String author, int copies) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setIsbn("978-7-111-" + Math.abs(title.hashCode() % 10000));
        book.setTotalCopies(copies);
        return books.create(book);
    }

    private Reader newReader(String cardNo) {
        Reader reader = new Reader();
        reader.setCardNo(cardNo);
        reader.setName("测试读者-" + cardNo);
        return readers.create(reader);
    }

    @Test
    @DisplayName("新增图书时可用册数等于总册数")
    void createBookAvailableEqualsTotal() {
        Book book = newBook("重构", "Martin Fowler", 3);

        assertThat(book.getId()).isNotNull();
        assertThat(book.getTotalCopies()).isEqualTo(3);
        assertThat(book.getAvailableCopies()).isEqualTo(3);
        assertThat(book.getBorrowedCopies()).isZero();
    }

    @Test
    @DisplayName("修改图书总册数不能小于已借出册数")
    void updateTotalCopiesBelowBorrowedIsRejected() {
        Book book = newBook("设计模式", "GoF", 2);
        Reader reader = newReader("R-9001");
        loans.borrow(book.getId(), reader.getId());

        Book form = new Book();
        form.setTitle("设计模式");
        form.setAuthor("GoF");
        form.setTotalCopies(0);

        assertThatThrownBy(() -> books.update(book.getId(), form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("总册数不能小于当前已借出册数");
    }

    @Test
    @DisplayName("修改总册数后可用册数按已借出量重算")
    void updateRecalculatesAvailableCopies() {
        Book book = newBook("代码大全", "Steve McConnell", 3);
        Reader reader = newReader("R-9002");
        loans.borrow(book.getId(), reader.getId());

        Book form = new Book();
        form.setTitle("代码大全（第2版）");
        form.setAuthor("Steve McConnell");
        form.setTotalCopies(5);

        Book updated = books.update(book.getId(), form);

        assertThat(updated.getTitle()).isEqualTo("代码大全（第2版）");
        assertThat(updated.getTotalCopies()).isEqualTo(5);
        assertThat(updated.getAvailableCopies()).isEqualTo(4);
    }

    @Test
    @DisplayName("有未归还借阅记录时不能删除图书")
    void deleteBookWithActiveLoanIsRejected() {
        Book book = newBook("算法导论", "Cormen", 1);
        Reader reader = newReader("R-9003");
        loans.borrow(book.getId(), reader.getId());

        assertThatThrownBy(() -> books.delete(book.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还有未归还的借阅记录");
    }

    @Test
    @DisplayName("有已归还的借阅历史时仍不能删除图书，避免破坏历史记录")
    void deleteBookWithReturnedHistoryIsRejected() {
        Book book = newBook("编译原理", "Aho", 1);
        Reader reader = newReader("R-9004");
        var loan = loans.borrow(book.getId(), reader.getId());
        loans.returnLoan(loan.getId());

        assertThatThrownBy(() -> books.delete(book.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("历史借阅记录");
    }

    @Test
    @DisplayName("从未被借阅的图书可以删除")
    void deleteBookWithoutLoansSucceeds() {
        Book book = newBook("待删除图书", "某人", 1);

        books.delete(book.getId());

        assertThat(books.search("待删除图书", 0, 10).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("搜索可匹配书名、作者并分页")
    void searchMatchesTitleAndAuthorWithPaging() {
        newBook("Effective Java", "Joshua Bloch", 2);
        newBook("深入理解Java虚拟机", "周志明", 3);
        newBook("Spring 实战", "Craig Walls", 1);

        Page<Book> byTitle = books.search("java", 0, 10);
        assertThat(byTitle.getTotalElements()).isEqualTo(2);

        Page<Book> byAuthor = books.search("walls", 0, 10);
        assertThat(byAuthor.getTotalElements()).isEqualTo(1);
        assertThat(byAuthor.getContent().get(0).getTitle()).isEqualTo("Spring 实战");

        Page<Book> firstPage = books.search("", 0, 2);
        Page<Book> secondPage = books.search("", 1, 2);

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(firstPage.getContent()).doesNotContainAnyElementsOf(secondPage.getContent());
    }

    @Test
    @DisplayName("按分类筛选，并统计分类数量")
    void searchFiltersByCategoryAndCountsCategories() {
        newCategorizedBook("科幻一", "科幻");
        newCategorizedBook("科幻二", "科幻");
        newCategorizedBook("文学一", "文学");

        Page<Book> sciFi = books.search(null, "科幻", 0, 10);
        assertThat(sciFi.getTotalElements()).isEqualTo(2);
        assertThat(sciFi.getContent()).allMatch(book -> "科幻".equals(book.getCategory()));

        assertThat(books.search("", "科幻", 0, 10).getTotalElements()).isEqualTo(2);
        assertThat(books.search("科幻一", "文学", 0, 10).getTotalElements()).isZero();

        assertThat(books.totalTitles()).isEqualTo(3);
        assertThat(books.categories()).extracting(BookService.CategoryCount::name)
                .containsExactly("文学", "科幻");
        assertThat(books.categories()).extracting(BookService.CategoryCount::count)
                .containsExactly(1L, 2L);
    }

    private Book newCategorizedBook(String title, String category) {
        Book book = newBook(title, "作者", 1);
        book.setCategory(category);
        return books.update(book.getId(), book);
    }

    @Test
    @DisplayName("统计藏书册数时未借出与借出都计入")
    void totalCopiesCountsAllCopies() {
        long before = books.totalCopies();

        Book book = newBook("数据库系统概念", "Silberschatz", 4);
        Reader reader = newReader("R-9005");
        loans.borrow(book.getId(), reader.getId());

        assertThat(books.totalCopies()).isEqualTo(before + 4);
    }
}
