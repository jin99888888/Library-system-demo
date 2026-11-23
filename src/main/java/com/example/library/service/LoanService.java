package com.example.library.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import com.example.library.domain.Reader;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.ReaderRepository;

@Service
public class LoanService {

    private final LoanRepository loans;
    private final BookRepository books;
    private final ReaderRepository readers;
    private final int loanPeriodDays;

    public LoanService(LoanRepository loans,
                       BookRepository books,
                       ReaderRepository readers,
                       @Value("${library.loan.period-days:30}") int loanPeriodDays) {
        this.loans = loans;
        this.books = books;
        this.readers = readers;
        this.loanPeriodDays = loanPeriodDays;
    }

    @Transactional
    public Loan borrow(Long bookId, Long readerId) {
        Book book = books.findById(bookId)
                .orElseThrow(() -> new BusinessException("图书不存在（id=" + bookId + "）"));
        Reader reader = readers.findById(readerId)
                .orElseThrow(() -> new BusinessException("读者不存在（id=" + readerId + "）"));

        if (!reader.isActive()) {
            throw new BusinessException("读者「" + reader.getName() + "」已停用，不能借书");
        }
        if (hasOverdue(readerId)) {
            throw new BusinessException("读者「" + reader.getName() + "」有逾期未还的图书，请先归还后再借书");
        }
        if (book.getAvailableCopies() <= 0) {
            throw new BusinessException("《" + book.getTitle() + "》已无可借册数");
        }
        long activeLoans = loans.countByReaderIdAndStatus(readerId, LoanStatus.BORROWED);
        if (activeLoans >= reader.getMaxBorrow()) {
            throw new BusinessException("读者「" + reader.getName() + "」在借数已达上限（"
                    + reader.getMaxBorrow() + " 册），请先还书");
        }
        if (loans.existsByReaderIdAndBookIdAndStatus(readerId, bookId, LoanStatus.BORROWED)) {
            throw new BusinessException("读者「" + reader.getName() + "」已借阅《"
                    + book.getTitle() + "》且尚未归还");
        }

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        books.save(book);

        LocalDateTime now = LocalDateTime.now();
        Loan loan = new Loan();
        loan.setBook(book);
        loan.setReader(reader);
        loan.setBorrowedAt(now);
        loan.setDueAt(now.plusDays(loanPeriodDays));
        loan.setStatus(LoanStatus.BORROWED);
        return loans.save(loan);
    }

    /** 读者是否有逾期未还的借阅。 */
    @Transactional(readOnly = true)
    public boolean hasOverdue(Long readerId) {
        return loans.countByReaderIdAndStatusAndDueAtBefore(readerId, LoanStatus.BORROWED, LocalDateTime.now()) > 0;
    }

    /** 有逾期未还记录的读者 id 集合，供读者列表展示「逾期」状态。 */
    @Transactional(readOnly = true)
    public List<Long> readersWithOverdue() {
        return loans.findReaderIdsWithOverdue(LoanStatus.BORROWED, LocalDateTime.now());
    }

    /** 某位读者当前在借的册数。 */
    @Transactional(readOnly = true)
    public long activeCount(Long readerId) {
        return loans.countByReaderIdAndStatus(readerId, LoanStatus.BORROWED);
    }

    @Transactional
    public Loan returnLoan(Long loanId) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> new BusinessException("借阅记录不存在（id=" + loanId + "）"));
        if (loan.getStatus() == LoanStatus.RETURNED) {
            throw new BusinessException("该借阅记录已归还，无需重复操作");
        }
        loan.setReturnedAt(LocalDateTime.now());
        loan.setStatus(LoanStatus.RETURNED);

        Book book = loan.getBook();
        if (book.getAvailableCopies() < book.getTotalCopies()) {
            book.setAvailableCopies(book.getAvailableCopies() + 1);
            books.save(book);
        }
        return loans.save(loan);
    }

    @Transactional(readOnly = true)
    public Page<Loan> page(String filter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        String normalized = filter == null ? "ALL" : filter.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BORROWED" -> loans.findByStatusOrderByBorrowedAtDesc(LoanStatus.BORROWED, pageable);
            case "RETURNED" -> loans.findByStatusOrderByBorrowedAtDesc(LoanStatus.RETURNED, pageable);
            case "OVERDUE" -> loans.findOverdue(LoanStatus.BORROWED, LocalDateTime.now(), pageable);
            default -> loans.findAllByOrderByBorrowedAtDesc(pageable);
        };
    }

    /** 某本书当前未归还的借阅记录，按应还时间升序（最紧急的在前）。 */
    @Transactional(readOnly = true)
    public List<Loan> activeByBook(Long bookId) {
        return loans.findByBookIdAndStatusOrderByDueAtAsc(bookId, LoanStatus.BORROWED);
    }

    /** 某本书已归还的借阅历史，按归还时间倒序（最近的在前）。 */
    @Transactional(readOnly = true)
    public Page<Loan> historyByBook(Long bookId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        return loans.findByBookIdAndStatusOrderByReturnedAtDesc(bookId, LoanStatus.RETURNED, pageable);
    }

    /** 某位读者当前未归还的借阅记录，按应还时间升序。 */
    @Transactional(readOnly = true)
    public List<Loan> activeByReader(Long readerId) {
        return loans.findByReaderIdAndStatusOrderByDueAtAsc(readerId, LoanStatus.BORROWED);
    }

    /** 某位读者已归还的借阅历史，按归还时间倒序。 */
    @Transactional(readOnly = true)
    public Page<Loan> historyByReader(Long readerId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        return loans.findByReaderIdAndStatusOrderByReturnedAtDesc(readerId, LoanStatus.RETURNED, pageable);
    }
}
