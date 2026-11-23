package com.example.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.library.domain.Book;

class DemoBooksTest {

    @Test
    @DisplayName("示例书目共 40 本，书名与 ISBN 均不重复")
    void catalogHasFortyUniqueBooks() {
        List<Book> books = DemoBooks.all();

        assertThat(books).hasSize(40);
        assertThat(books).extracting(Book::getTitle).doesNotHaveDuplicates();
        assertThat(books).extracting(Book::getIsbn).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("示例书目字段完整、册数合法且全部可借")
    void catalogEntriesAreValid() {
        List<Book> books = DemoBooks.all();

        assertThat(books).allSatisfy(book -> {
            assertThat(book.getTitle()).isNotBlank();
            assertThat(book.getAuthor()).isNotBlank();
            assertThat(book.getIsbn()).isNotBlank();
            assertThat(book.getIsbn().length()).isLessThanOrEqualTo(32);
            assertThat(book.getCategory()).isNotBlank();
            assertThat(book.getPublisher()).isNotBlank();
            assertThat(book.getLocation()).isNotBlank();
            assertThat(book.getTotalCopies()).isGreaterThanOrEqualTo(1);
            assertThat(book.getAvailableCopies()).isEqualTo(book.getTotalCopies());
        });
    }
}
