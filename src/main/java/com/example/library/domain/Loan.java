package com.example.library.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "loans")
public class Loan {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false)
    private Reader reader;

    @Column(nullable = false)
    private LocalDateTime borrowedAt;

    @Column(nullable = false)
    private LocalDateTime dueAt;

    private LocalDateTime returnedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoanStatus status = LoanStatus.BORROWED;

    /** 是否已逾期（在借：以当前时间判断；已还：以归还时间判断） */
    public boolean isOverdue() {
        LocalDateTime end = returnedAt != null ? returnedAt : LocalDateTime.now();
        return end.isAfter(dueAt);
    }

    /** 逾期天数，未逾期返回 0 */
    public long getOverdueDays() {
        LocalDateTime end = returnedAt != null ? returnedAt : LocalDateTime.now();
        if (!end.isAfter(dueAt)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(dueAt.toLocalDate(), end.toLocalDate());
        return Math.max(1, days);
    }

    /** 在借且未逾期时距应还日期的剩余天数；已归还或已逾期返回 0 */
    @Transient
    public long getDaysLeft() {
        if (status == LoanStatus.RETURNED || isOverdue()) {
            return 0;
        }
        return ChronoUnit.DAYS.between(LocalDateTime.now().toLocalDate(), dueAt.toLocalDate());
    }

    @Transient
    public String getBorrowedAtText() {
        return format(borrowedAt);
    }

    @Transient
    public String getDueAtText() {
        return format(dueAt);
    }

    @Transient
    public String getReturnedAtText() {
        return returnedAt == null ? "—" : format(returnedAt);
    }

    private static String format(LocalDateTime value) {
        return value == null ? "—" : value.format(DATE_TIME);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public Reader getReader() {
        return reader;
    }

    public void setReader(Reader reader) {
        this.reader = reader;
    }

    public LocalDateTime getBorrowedAt() {
        return borrowedAt;
    }

    public void setBorrowedAt(LocalDateTime borrowedAt) {
        this.borrowedAt = borrowedAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(LocalDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }
}
