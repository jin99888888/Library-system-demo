package com.example.library.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.library.domain.Book;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("""
            select b from Book b
            where lower(b.title) like :kw
               or lower(b.author) like :kw
               or lower(coalesce(b.isbn, '')) like :kw
            """)
    Page<Book> search(@Param("kw") String keyword, Pageable pageable);

    List<Book> findByAvailableCopiesGreaterThanOrderByTitleAsc(int availableCopies);

    @Query("select coalesce(sum(b.totalCopies), 0) from Book b")
    long sumTotalCopies();
}
