package com.example.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.AppSetting;
import com.example.library.domain.Book;
import com.example.library.repository.AppSettingRepository;
import com.example.library.repository.BookRepository;

@SpringBootTest
@Transactional
class BookSeederTest {

    @Autowired
    private BookSeeder bookSeeder;

    @Autowired
    private BookRepository books;

    @Autowired
    private AppSettingRepository settings;

    @Test
    @DisplayName("首次启动导入 40 本示例图书并写入一次性标记，重复调用不再导入")
    void seedsFortyBooksOnceAndMarksSetting() {
        books.deleteAll();
        settings.deleteAll();

        int seeded = bookSeeder.seedIfNeeded();

        assertThat(seeded).isEqualTo(40);
        assertThat(books.count()).isEqualTo(40);
        assertThat(settings.findById(BookSeeder.SEEDED_KEY)).isPresent();

        assertThat(bookSeeder.seedIfNeeded()).isZero();
        assertThat(books.count()).isEqualTo(40);
    }

    @Test
    @DisplayName("已有图书时保留原数据，只补齐缺失的示例书（按 ISBN 去重）")
    void keepsExistingBooksAndFillsMissing() {
        books.deleteAll();
        settings.deleteAll();

        Book existing = new Book();
        existing.setIsbn("978-7-111-21126-9");
        existing.setTitle("我的设计模式");
        existing.setAuthor("某人");
        existing.setTotalCopies(1);
        existing.setAvailableCopies(1);
        books.save(existing);

        int seeded = bookSeeder.seedIfNeeded();

        assertThat(seeded).isEqualTo(39);
        assertThat(books.count()).isEqualTo(40);
        assertThat(books.findAll().stream()
                .filter(book -> "978-7-111-21126-9".equals(book.getIsbn()))
                .map(Book::getTitle))
                .containsExactly("我的设计模式");
    }

    @Test
    @DisplayName("用户清空图书后重启不会再次导入示例书")
    void doesNotReaddAfterUserClearsBooks() {
        books.deleteAll();
        settings.deleteAll();
        settings.save(new AppSetting(BookSeeder.SEEDED_KEY, "40"));

        assertThat(bookSeeder.seedIfNeeded()).isZero();
        assertThat(books.count()).isZero();
    }
}
