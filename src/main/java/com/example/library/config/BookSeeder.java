package com.example.library.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.AppSetting;
import com.example.library.domain.Book;
import com.example.library.repository.AppSettingRepository;
import com.example.library.repository.BookRepository;

/**
 * 首次启动时导入示例书目。
 * <p>只执行一次（通过 app_settings 表记录），示例书按 ISBN 去重：
 * 已有数据不会被覆盖，缺失的示例书会补齐；之后用户删除示例书也不会再次出现。</p>
 */
@Component
public class BookSeeder {

    /** app_settings 中的标记键，值记录本次新增的图书数量 */
    static final String SEEDED_KEY = "demo-books-seeded";

    private static final Logger log = LoggerFactory.getLogger(BookSeeder.class);

    private final BookRepository books;
    private final AppSettingRepository settings;

    public BookSeeder(BookRepository books, AppSettingRepository settings) {
        this.books = books;
        this.settings = settings;
    }

    /**
     * 首次启动时补齐缺失的示例书目，之后不再重复导入。
     *
     * @return 本次写入的图书数量；已经导入过或没有缺失时返回 0
     */
    @Transactional
    public int seedIfNeeded() {
        if (settings.existsById(SEEDED_KEY)) {
            return 0;
        }
        Set<String> existingIsbns = new HashSet<>();
        for (Book book : books.findAll()) {
            if (book.getIsbn() != null) {
                existingIsbns.add(book.getIsbn());
            }
        }
        List<Book> missing = DemoBooks.all().stream()
                .filter(book -> !existingIsbns.contains(book.getIsbn()))
                .toList();
        if (!missing.isEmpty()) {
            books.saveAll(missing);
        }
        settings.save(new AppSetting(SEEDED_KEY, String.valueOf(missing.size())));
        log.info("示例图书导入完成：新增 {} 本（已有 {} 本，按 ISBN 去重）", missing.size(), existingIsbns.size());
        return missing.size();
    }
}
