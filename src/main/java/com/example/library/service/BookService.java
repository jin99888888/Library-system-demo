package com.example.library.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.Book;
import com.example.library.domain.LoanStatus;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;

@Service
public class BookService {

    private final BookRepository books;
    private final LoanRepository loans;

    public BookService(BookRepository books, LoanRepository loans) {
        this.books = books;
        this.loans = loans;
    }

    @Transactional(readOnly = true)
    public Page<Book> search(String keyword, int page, int size) {
        return search(keyword, page, size, null, null);
    }

    /**
     * 分页检索图书。
     *
     * @param sortKey 排序字段：title（书名）、category（分类）；其它值表示默认排序（最新添加在前）
     * @param direction asc 或 desc，默认 asc
     */
    @Transactional(readOnly = true)
    public Page<Book> search(String keyword, int page, int size, String sortKey, String direction) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size, sortOf(sortKey, direction));
        if (keyword == null || keyword.isBlank()) {
            return books.findAll(pageable);
        }
        return books.search("%" + keyword.trim().toLowerCase(Locale.ROOT) + "%", pageable);
    }

    /** 把页面传来的排序参数翻译成 Sort，非法值回退到默认排序。 */
    public static Sort sortOf(String sortKey, String direction) {
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String key = sortKey == null ? "" : sortKey;
        return switch (key) {
            case "title" -> Sort.by(dir, "title").and(Sort.by(Sort.Direction.ASC, "id"));
            case "category" -> Sort.by(dir, "category").and(Sort.by(Sort.Direction.ASC, "title"));
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
    }

    @Transactional(readOnly = true)
    public Book get(Long id) {
        return books.findById(id)
                .orElseThrow(() -> new BusinessException("图书不存在（id=" + id + "）"));
    }

    @Transactional(readOnly = true)
    public List<Book> findAvailable() {
        return books.findByAvailableCopiesGreaterThanOrderByTitleAsc(0);
    }

    @Transactional
    public Book create(Book form) {
        form.setId(null);
        form.setAvailableCopies(form.getTotalCopies());
        return books.save(form);
    }

    @Transactional
    public Book update(Long id, Book form) {
        Book book = get(id);
        int borrowed = book.getBorrowedCopies();
        if (form.getTotalCopies() < borrowed) {
            throw new BusinessException("总册数不能小于当前已借出册数（" + borrowed + " 册）");
        }
        book.setIsbn(form.getIsbn());
        book.setTitle(form.getTitle());
        book.setAuthor(form.getAuthor());
        book.setCategory(form.getCategory());
        book.setPublisher(form.getPublisher());
        book.setLocation(form.getLocation());
        book.setTotalCopies(form.getTotalCopies());
        book.setAvailableCopies(form.getTotalCopies() - borrowed);
        return books.save(book);
    }

    @Transactional
    public void delete(Long id) {
        Book book = get(id);
        if (loans.countByBookIdAndStatus(id, LoanStatus.BORROWED) > 0) {
            throw new BusinessException("《" + book.getTitle() + "》还有未归还的借阅记录，不能删除");
        }
        if (loans.countByBookId(id) > 0) {
            throw new BusinessException("《" + book.getTitle() + "》存在历史借阅记录，不能删除（可改小册数或保留归档）");
        }
        books.delete(book);
    }

    @Transactional(readOnly = true)
    public long totalCopies() {
        return books.sumTotalCopies();
    }
}
