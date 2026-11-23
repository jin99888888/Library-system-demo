package com.example.library.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.AppSetting;
import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import com.example.library.domain.Reader;
import com.example.library.repository.AppSettingRepository;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.ReaderRepository;

/**
 * 首次启动时导入示例读者与借阅记录，方便直接查看借还、逾期等效果。
 * <p>只执行一次（app_settings 记录标记）。读者按借书证号去重，已存在的读者不重复创建；
 * 借阅记录只挂在本次新建的示例读者名下，避免给用户自己的读者生成假数据。</p>
 */
@Component
public class DemoLoanSeeder {

    /** app_settings 中的标记键，值记录本次写入的借阅记录数量 */
    static final String SEEDED_KEY = "demo-loans-seeded";

    /** 固定随机种子，保证每次演示数据形态一致 */
    private static final long SEED = 20261123L;

    private static final int RETURNED_LOANS = 6;
    private static final int ACTIVE_LOANS = 4;
    private static final int OVERDUE_LOANS = 3;

    private static final Logger log = LoggerFactory.getLogger(DemoLoanSeeder.class);

    private final ReaderRepository readers;
    private final BookRepository books;
    private final LoanRepository loans;
    private final AppSettingRepository settings;

    public DemoLoanSeeder(ReaderRepository readers, BookRepository books,
                          LoanRepository loans, AppSettingRepository settings) {
        this.readers = readers;
        this.books = books;
        this.loans = loans;
        this.settings = settings;
    }

    /**
     * 首次启动时写入示例读者与借阅记录。
     *
     * @return 本次写入的借阅记录数量；已导入过或没有可用图书时返回 0
     */
    @Transactional
    public int seedIfNeeded() {
        if (settings.existsById(SEEDED_KEY)) {
            return 0;
        }
        List<Reader> createdReaders = createMissingReaders();
        List<Book> availableBooks = new ArrayList<>(books.findAll().stream()
                .filter(book -> book.getAvailableCopies() > 0)
                .toList());
        if (createdReaders.isEmpty() || availableBooks.isEmpty()) {
            settings.save(new AppSetting(SEEDED_KEY, "0"));
            log.info("示例借阅记录跳过：可用图书 {} 本，新建读者 {} 位", availableBooks.size(), createdReaders.size());
            return 0;
        }

        Random random = new Random(SEED);
        Collections.shuffle(availableBooks, random);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        int written = 0;
        written += writeReturned(createdReaders, availableBooks, random, now);
        written += writeActive(createdReaders, availableBooks, random, now);
        written += writeOverdue(createdReaders, availableBooks, random, now);

        settings.save(new AppSetting(SEEDED_KEY, String.valueOf(written)));
        log.info("示例借阅记录导入完成：读者 {} 位，借阅记录 {} 条（含逾期 {} 条）",
                createdReaders.size(), written, OVERDUE_LOANS);
        return written;
    }

    private List<Reader> createMissingReaders() {
        Set<String> existing = new HashSet<>();
        for (Reader reader : readers.findAll()) {
            existing.add(reader.getCardNo());
        }
        List<Reader> created = new ArrayList<>();
        for (DemoReaders.Spec spec : DemoReaders.all()) {
            if (!existing.contains(spec.cardNo())) {
                created.add(DemoReaders.toEntity(spec));
            }
        }
        if (!created.isEmpty()) {
            readers.saveAll(created);
        }
        return created;
    }

    /** 已按时归还的记录：借出于 30~70 天前，归还时间在应还时间之前。 */
    private int writeReturned(List<Reader> readers, List<Book> pool, Random random, LocalDateTime now) {
        List<Loan> batch = new ArrayList<>();
        for (int i = 0; i < RETURNED_LOANS && !pool.isEmpty(); i++) {
            Loan loan = baseLoan(readers, pool, batch.size());
            LocalDateTime borrowedAt = now.minusDays(30 + random.nextInt(40));
            loan.setBorrowedAt(borrowedAt);
            loan.setDueAt(borrowedAt.plusDays(30));
            loan.setReturnedAt(borrowedAt.plusDays(3 + random.nextInt(20)));
            loan.setStatus(LoanStatus.RETURNED);
            batch.add(loan);
        }
        return persist(batch);
    }

    /** 正常在借：借出于 1~15 天前，应还日期还没到。 */
    private int writeActive(List<Reader> readers, List<Book> pool, Random random, LocalDateTime now) {
        List<Loan> batch = new ArrayList<>();
        for (int i = 0; i < ACTIVE_LOANS && !pool.isEmpty(); i++) {
            Loan loan = baseLoan(readers, pool, batch.size());
            LocalDateTime borrowedAt = now.minusDays(1 + random.nextInt(15));
            loan.setBorrowedAt(borrowedAt);
            loan.setDueAt(borrowedAt.plusDays(30));
            loan.setStatus(LoanStatus.BORROWED);
            hold(loan.getBook());
            books.save(loan.getBook());
            batch.add(loan);
        }
        return persist(batch);
    }

    /** 逾期未还：借出于 35~55 天前，应还日期已过。 */
    private int writeOverdue(List<Reader> readers, List<Book> pool, Random random, LocalDateTime now) {
        List<Loan> batch = new ArrayList<>();
        for (int i = 0; i < OVERDUE_LOANS && !pool.isEmpty(); i++) {
            Loan loan = baseLoan(readers, pool, batch.size());
            LocalDateTime borrowedAt = now.minusDays(35 + random.nextInt(20));
            loan.setBorrowedAt(borrowedAt);
            loan.setDueAt(borrowedAt.plusDays(30));
            loan.setStatus(LoanStatus.BORROWED);
            hold(loan.getBook());
            books.save(loan.getBook());
            batch.add(loan);
        }
        return persist(batch);
    }

    /** 从可用图书池里取一本、轮流分配给新读者；借出中的记录由调用方扣减可借数。 */
    private Loan baseLoan(List<Reader> readers, List<Book> pool, int index) {
        Book book = pool.remove(pool.size() - 1);
        Loan loan = new Loan();
        loan.setBook(book);
        loan.setReader(readers.get(index % readers.size()));
        return loan;
    }

    /** 标记为在借：可借数减 1 并保存图书。 */
    private static void hold(Book book) {
        book.setAvailableCopies(book.getAvailableCopies() - 1);
    }

    private int persist(List<Loan> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        loans.saveAll(batch);
        return batch.size();
    }
}
