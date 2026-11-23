package com.example.library.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.library.domain.Reader;
import com.example.library.domain.ReaderStatus;

public interface ReaderRepository extends JpaRepository<Reader, Long> {

    Page<Reader> findAllByOrderByIdDesc(Pageable pageable);

    @Query("""
            select r from Reader r
            where lower(r.cardNo) like :kw
               or lower(r.name) like :kw
               or lower(coalesce(r.phone, '')) like :kw
               or lower(coalesce(r.email, '')) like :kw
            order by r.id desc
            """)
    Page<Reader> search(@Param("kw") String keyword, Pageable pageable);

    Optional<Reader> findByCardNo(String cardNo);

    boolean existsByCardNo(String cardNo);

    boolean existsByCardNoAndIdNot(String cardNo, Long id);

    List<Reader> findByStatusOrderByNameAsc(ReaderStatus status);
}
