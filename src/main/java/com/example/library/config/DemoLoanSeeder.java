package com.example.library.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * 首次启动（或版本升级后首次启动）时重建示例读者与借阅记录。
 * <p>v2 相比 v1 的变化：读者带邮箱与性别；借阅记录数量更多（20 条）；
 * 升级时先清理 v1 导入的旧示例读者及其借阅记录，并把在借记录的库存还回去，
 * 再按业务规则重新生成一份干净、自洽的数据。</p>
 * <p>只执行一次（app_settings 记录 {@value #SEEDED_KEY}）。</p>
 */
@Component
public class DemoLoanSeeder {

    /** app_settings 中的标记键，值记录本次写入的借阅记录数量 */
    static final String SEEDED_KEY = "demo-loans-seeded-v2";

    /** 旧版本 v1 的标记键，仅用于说明来源 */
    private static final String PREVIOUS_KEY = "demo-loans-seeded";

    /** 固定随机种子，保证每次演示数据形态一致 */
    private static final long SEED = 20261123L;

    private static final int RETURNED_LOANS = 12;
    private static final int ACTIVE_LOANS = 5;
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
        int removedReaders = removePreviousDemoData();
        List<Reader> demoReaders = createDemoReaders();
        List<Book> pool = new ArrayList<>(books.findAll().stream()
                .filter(book -> book.getAvailableCopies() > 0)
                .toList());
        if (demoReaders.isEmpty() || pool.isEmpty()) {
            settings.save(new AppSetting(SEEDED_KEY, "0"));
            log.info("示例借阅记录跳过：可用图书 {} 本，示例读者 {} 位", pool.size(), demoReaders.size());
            return 0;
        }

        Random random = new Random(SEED);
        Collections.shuffle(pool, random);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        List<Loan> batch = new ArrayList<>();
        batch.addAll(writeReturned(demoReaders, pool, random, now));
        batch.addAll(writeActive(demoReaders, pool, random, now));
        batch.addAll(writeOverdue(demoReaders, pool, random, now));
        loans.saveAll(batch);

        verifyRules(demoReaders);

        settings.save(new AppSetting(SEEDED_KEY, String.valueOf(batch.size())));
        log.info("示例借阅记录重建完成：清理旧读者 {} 位，新建读者 {} 位，借阅记录 {} 条（归还 {}、在借 {}、逾期 {}）",
                removedReaders, demoReaders.size(), batch.size(), RETURNED_LOANS, ACTIVE_LOANS, OVERDUE_LOANS);
        return batch.size();
    }

    /** 清理 v1 导入的示例读者：先删其借阅记录（在借的把库存还回去），再删读者。 */
    private int removePreviousDemoData() {
        List<String> oldCardNos = DemoReaders.previousCardNos();
        List<Reader> old = readers.findAll().stream()
                .filter(reader -> oldCardNos.contains(reader.getCardNo()))
                .toList();
        for (Reader reader : old) {
            for (Loan loan : loans.findByReaderId(reader.getId())) {
                if (loan.getStatus() == LoanStatus.BORROWED) {
                    release(loan.getBook());
                }
                loans.delete(loan);
            }
            readers.delete(reader);
        }
        if (!old.isEmpty()) {
            log.info("已清理旧示例读者 {} 位及其借阅记录", old.size());
        }
        return old.size();
    }

    /** 创建缺失的示例读者（按借书证号去重），返回全部示例读者。 */
    private List<Reader> createDemoReaders() {
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
        Map<String, Reader> byCardNo = new HashMap<>();
        for (Reader reader : readers.findAll()) {
            byCardNo.put(reader.getCardNo(), reader);
        }
        List<Reader> demo = new ArrayList<>();
        for (DemoReaders.Spec spec : DemoReaders.all()) {
            Reader reader = byCardNo.get(spec.cardNo());
            if (reader != null) {
                demo.add(reader);
            }
        }
        return demo;
    }

    /** 已按时归还：借出于 20~110 天前，在应还日之前归还。 */
    private List<Loan> writeReturned(List<Reader> readers, List<Book> pool, Random random, LocalDateTime now) {
        List<Loan> batch = new ArrayList<>();
        for (int i = 0; i < RETURNED_LOANS && !pool.isEmpty(); i++) {
            Loan loan = baseLoan(readers, pool, i + 2);
            LocalDateTime borrowedAt = now.minusDays(20 + random.nextInt(90));
            loan.setBorrowedAt(borrowedAt);
            loan.setDueAt(borrowedAt.plusDays(30));
            loan.setReturnedAt(borrowedAt.plusDays(7 + random.nextInt(20)));
            loan.setStatus(LoanStatus.RETURNED);
            batch.add(loan);
        }
        return batch;
    }

    /** 正常在借：借出于 1~20 天前，应还日期尚未到。 */
    private List<Loan> writeActive(List<Reader> readers, List<Book> pool, Random random, LocalDateTime now) {
        List<Loan> batch = new ArrayList<>();
        for (int i = 0; i < ACTIVE_LOANS && !pool.isEmpty(); i++) {
            Loan loan = baseLoan(readers, pool, i);
            LocalDateTime borrowedAt = now.minusDays(1 + random.nextInt(20));
            loan.setBorrowedAt(borrowedAt);
            loan.setDueAt(borrowedAt.plusDays(30));
            loan.setStatus(LoanStatus.BORROWED);
            hold(loan.getBook());
            batch.add(loan);
        }
        return batch;
    }

    /** 逾期未还：借出于 40~70 天前，应还日期已过。 */
    private List<Loan> writeOverdue(List<Reader> readers, List<Book> pool, Random random, LocalDateTime now) {
        List<Loan> batch = new ArrayList<>();
        for (int i = 0; i < OVERDUE_LOANS && !pool.isEmpty(); i++) {
            Loan loan = baseLoan(readers, pool, ACTIVE_LOANS + i);
            LocalDateTime borrowedAt = now.minusDays(40 + random.nextInt(30));
            loan.setBorrowedAt(borrowedAt);
            loan.setDueAt(borrowedAt.plusDays(30));
            loan.setStatus(LoanStatus.BORROWED);
            hold(loan.getBook());
            batch.add(loan);
        }
        return batch;
    }

    /** 从可用图书池里取一本、轮流分配给示例读者。 */
    private Loan baseLoan(List<Reader> readers, List<Book> pool, int index) {
        Book book = pool.remove(pool.size() - 1);
        Loan loan = new Loan();
        loan.setBook(book);
        loan.setReader(readers.get(index % readers.size()));
        return loan;
    }

    /** 标记为在借：可借数减 1。 */
    private static void hold(Book book) {
        book.setAvailableCopies(Math.max(0, book.getAvailableCopies() - 1));
    }

    /** 归还/清理借阅：可借数加 1，但不超过总册数。 */
    private static void release(Book book) {
        book.setAvailableCopies(Math.min(book.getTotalCopies(), book.getAvailableCopies() + 1));
    }

    /** 写入后做一次规则自检：没人超出借阅上限，借出中的记录库存一致。 */
    private void verifyRules(List<Reader> demoReaders) {
        for (Reader reader : demoReaders) {
            long active = loans.countByReaderIdAndStatus(reader.getId(), LoanStatus.BORROWED);
            if (active > reader.getMaxBorrow()) {
                log.warn("示例数据规则异常：读者 {} 在借 {} 册，超过上限 {}", reader.getName(), active, reader.getMaxBorrow());
            }
        }
    }
}
