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
import com.example.library.domain.Gender;
import com.example.library.domain.Reader;
import com.example.library.domain.ReaderStatus;

@SpringBootTest
@Transactional
class ReaderServiceTest {

    @Autowired
    private ReaderService readers;

    @Autowired
    private BookService books;

    @Autowired
    private LoanService loans;

    private Reader newReader(String cardNo, String name) {
        Reader reader = new Reader();
        reader.setCardNo(cardNo);
        reader.setName(name);
        return readers.create(reader);
    }

    private Book newBook(String title) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("作者");
        book.setTotalCopies(1);
        return books.create(book);
    }

    @Test
    @DisplayName("新增读者默认状态正常、可借上限为 5")
    void createReaderDefaults() {
        Reader reader = newReader("R-3001", "默认读者");

        assertThat(reader.getId()).isNotNull();
        assertThat(reader.getStatus()).isEqualTo(ReaderStatus.ACTIVE);
        assertThat(reader.isActive()).isTrue();
        assertThat(reader.getMaxBorrow()).isEqualTo(5);
    }

    @Test
    @DisplayName("借书证号重复时拒绝登记")
    void duplicateCardNoIsRejected() {
        newReader("R-3002", "第一位");

        assertThatThrownBy(() -> newReader("R-3002", "第二位"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("修改读者时不能占用他人证号")
    void updateToExistingCardNoIsRejected() {
        Reader first = newReader("R-3003", "甲");
        Reader second = newReader("R-3004", "乙");

        second.setCardNo(first.getCardNo());

        assertThatThrownBy(() -> readers.update(second.getId(), second))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他读者使用");
    }

    @Test
    @DisplayName("停用读者后不在可借读者列表中")
    void disabledReaderIsNotAvailable() {
        Reader reader = newReader("R-3005", "将被停用");
        reader.setStatus(ReaderStatus.DISABLED);
        readers.update(reader.getId(), reader);

        assertThat(readers.findActive())
                .extracting(Reader::getCardNo)
                .doesNotContain("R-3005");
        assertThat(readers.findActive())
                .allMatch(Reader::isActive);
    }

    @Test
    @DisplayName("有未归还借阅时不能删除读者")
    void deleteReaderWithActiveLoanIsRejected() {
        Reader reader = newReader("R-3006", "有借阅");
        Book book = newBook("读者删除测试");
        loans.borrow(book.getId(), reader.getId());

        assertThatThrownBy(() -> readers.delete(reader.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还有未归还的借阅记录");
    }

    @Test
    @DisplayName("有历史借阅记录时不能删除读者，可改为停用")
    void deleteReaderWithHistoryIsRejected() {
        Reader reader = newReader("R-3007", "有历史");
        Book book = newBook("读者历史测试");
        var loan = loans.borrow(book.getId(), reader.getId());
        loans.returnLoan(loan.getId());

        assertThatThrownBy(() -> readers.delete(reader.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("历史借阅记录");
    }

    @Test
    @DisplayName("没有借阅记录的读者可以删除")
    void deleteReaderWithoutLoansSucceeds() {
        Reader reader = newReader("R-3008", "可删除");

        readers.delete(reader.getId());

        assertThat(readers.search("R-3008", 0, 10).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("读者搜索支持证号、姓名、电话、邮箱")
    void searchMatchesCardNoNameAndPhone() {
        Reader reader = newReader("R-3009", "钱七");
        reader.setPhone("13900001111");
        reader.setEmail("qianqi@example.com");
        readers.update(reader.getId(), reader);

        Page<Reader> byCardNo = readers.search("3009", 0, 10);
        Page<Reader> byName = readers.search("钱七", 0, 10);
        Page<Reader> byPhone = readers.search("13900001111", 0, 10);
        Page<Reader> byEmail = readers.search("qianqi@example.com", 0, 10);

        assertThat(byCardNo.getTotalElements()).isEqualTo(1);
        assertThat(byName.getTotalElements()).isEqualTo(1);
        assertThat(byPhone.getTotalElements()).isEqualTo(1);
        assertThat(byEmail.getTotalElements()).isEqualTo(1);
        assertThat(byName.getContent().get(0).getId()).isEqualTo(reader.getId());
    }

    @Test
    @DisplayName("邮箱与性别可以保存并随修改更新")
    void emailAndGenderAreSaved() {
        Reader reader = newReader("R-3010", "邮箱读者");
        reader.setEmail("before@example.com");
        reader.setGender(Gender.FEMALE);
        readers.update(reader.getId(), reader);

        Reader saved = readers.get(reader.getId());
        assertThat(saved.getEmail()).isEqualTo("before@example.com");
        assertThat(saved.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(saved.getGenderText()).isEqualTo("女");

        saved.setEmail("after@example.com");
        saved.setGender(Gender.MALE);
        readers.update(saved.getId(), saved);

        Reader updated = readers.get(reader.getId());
        assertThat(updated.getEmail()).isEqualTo("after@example.com");
        assertThat(updated.getGenderText()).isEqualTo("男");
    }
}
