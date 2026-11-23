package com.example.library.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.library.domain.Book;

public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * 按关键词与分类检索，两个条件都可为空。
     *
     * @param keyword  已拼好通配符的关键词（小写），为 null 表示不限
     * @param category 分类名称，为 null 表示不限
     */
    @Query("""
            select b from Book b
            where (:category is null or b.category = :category)
              and (:kw is null
                   or lower(b.title) like :kw
                   or lower(b.author) like :kw
                   or lower(coalesce(b.isbn, '')) like :kw)
            order by b.id desc
            """)
    Page<Book> search(@Param("kw") String keyword,
                      @Param("category") String category,
                      Pageable pageable);

    List<Book> findByAvailableCopiesGreaterThanOrderByTitleAsc(int availableCopies);

    @Query("select coalesce(sum(b.totalCopies), 0) from Book b")
    long sumTotalCopies();

    @Query("""
            select b.category, count(b) from Book b
            where b.category is not null and b.category <> ''
            group by b.category
            order by b.category
            """)
    List<Object[]> countByCategory();
}
