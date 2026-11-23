package com.example.library.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @EntityGraph(attributePaths = {"book", "reader"})
    Page<Loan> findAllByOrderByBorrowedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"book", "reader"})
    Page<Loan> findByStatusOrderByBorrowedAtDesc(LoanStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"book", "reader"})
    @Query("""
            select l from Loan l
            where l.status = :status and l.dueAt < :now
            order by l.dueAt asc
            """)
    Page<Loan> findOverdue(@Param("status") LoanStatus status,
                           @Param("now") LocalDateTime now,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<Loan> findTop5ByStatusAndDueAtBeforeOrderByDueAtAsc(LoanStatus status, LocalDateTime now);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<Loan> findByBookIdAndStatusOrderByDueAtAsc(Long bookId, LoanStatus status);

    @EntityGraph(attributePaths = {"book", "reader"})
    Page<Loan> findByBookIdAndStatusOrderByReturnedAtDesc(Long bookId,
                                                         LoanStatus status,
                                                         Pageable pageable);

    @EntityGraph(attributePaths = {"book", "reader"})
    List<Loan> findByReaderIdAndStatusOrderByDueAtAsc(Long readerId, LoanStatus status);

    List<Loan> findByReaderId(Long readerId);

    @EntityGraph(attributePaths = {"book", "reader"})
    Page<Loan> findByReaderIdAndStatusOrderByReturnedAtDesc(Long readerId,
                                                           LoanStatus status,
                                                           Pageable pageable);

    long countByStatus(LoanStatus status);

    long countByStatusAndDueAtBefore(LoanStatus status, LocalDateTime now);

    long countByReaderIdAndStatus(Long readerId, LoanStatus status);

    long countByReaderIdAndStatusAndDueAtBefore(Long readerId, LoanStatus status, LocalDateTime now);

    /** 有逾期未还记录的读者 id，用于读者列表的状态展示。 */
    @Query("""
            select distinct l.reader.id from Loan l
            where l.status = :status and l.dueAt < :now
            """)
    List<Long> findReaderIdsWithOverdue(@Param("status") LoanStatus status,
                                        @Param("now") LocalDateTime now);

    long countByReaderId(Long readerId);

    long countByBookId(Long bookId);

    long countByBookIdAndStatus(Long bookId, LoanStatus status);

    boolean existsByReaderIdAndBookIdAndStatus(Long readerId, Long bookId, LoanStatus status);
}
