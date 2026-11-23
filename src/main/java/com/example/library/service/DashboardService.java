package com.example.library.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.ReaderRepository;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final BookRepository books;
    private final ReaderRepository readers;
    private final LoanRepository loans;

    public DashboardService(BookRepository books, ReaderRepository readers, LoanRepository loans) {
        this.books = books;
        this.readers = readers;
        this.loans = loans;
    }

    public long bookTitles() {
        return books.count();
    }

    public long totalCopies() {
        return books.sumTotalCopies();
    }

    public long readerCount() {
        return readers.count();
    }

    public long activeLoanCount() {
        return loans.countByStatus(LoanStatus.BORROWED);
    }

    public long overdueCount() {
        return loans.countByStatusAndDueAtBefore(LoanStatus.BORROWED, LocalDateTime.now());
    }

    public List<Loan> latestOverdue() {
        return loans.findTop5ByStatusAndDueAtBeforeOrderByDueAtAsc(LoanStatus.BORROWED, LocalDateTime.now());
    }
}
