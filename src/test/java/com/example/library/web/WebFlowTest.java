package com.example.library.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.file.Files;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.ReaderRepository;
import com.example.library.service.CoverStorage;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BookRepository books;

    @Autowired
    private ReaderRepository readers;

    @Autowired
    private LoanRepository loans;

    @Autowired
    private CoverStorage covers;

    @org.springframework.beans.factory.annotation.Value("${library.version:0.1.0}")
    private String appVersion;

    @Test
    @DisplayName("未登录访问首页会跳转到登录页")
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("登录页可匿名访问")
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("图书管理系统")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("v" + appVersion)));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("新增图书缺少必填项时回到表单并提示校验错误")
    void createBookValidatesRequiredFields() throws Exception {
        long before = books.count();

        mvc.perform(post("/books").with(csrf())
                        .param("title", " ")
                        .param("author", "某人")
                        .param("totalCopies", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("books/form"))
                .andExpect(model().attributeHasFieldErrors("book", "title"));

        assertThat(books.count()).isEqualTo(before);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("端到端闭环：新增图书 → 登记读者 → 借出 → 还书")
    void fullBorrowAndReturnFlow() throws Exception {
        mvc.perform(post("/books").with(csrf())
                        .param("title", "深入浅出 Spring Boot")
                        .param("author", "张三")
                        .param("isbn", "978-7-000-00001-1")
                        .param("category", "计算机")
                        .param("totalCopies", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/books"))
                .andExpect(flash().attributeExists("msg"));

        mvc.perform(post("/readers").with(csrf())
                        .param("cardNo", "R-2001")
                        .param("name", "李四")
                        .param("phone", "13800000000")
                        .param("status", "ACTIVE")
                        .param("maxBorrow", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/readers"));

        Book book = books.findAll(org.springframework.data.domain.PageRequest.of(0, 1,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "id")))
                .getContent().get(0);
        Long readerId = readers.findAllByOrderByIdDesc(org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getId();

        assertThat(book.getTitle()).isEqualTo("深入浅出 Spring Boot");

        mvc.perform(get("/loans/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("loans/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("深入浅出 Spring Boot")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("李四")));

        mvc.perform(post("/loans/borrow").with(csrf())
                        .param("readerId", String.valueOf(readerId))
                        .param("bookId", String.valueOf(book.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/loans"))
                .andExpect(flash().attribute("msg", "借书成功"));

        Loan loan = loans.findByStatusOrderByBorrowedAtDesc(LoanStatus.BORROWED,
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent().get(0);

        mvc.perform(get("/loans"))
                .andExpect(status().isOk())
                .andExpect(view().name("loans/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("李四")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("在借")));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("在借册数")));

        mvc.perform(post("/loans/" + loan.getId() + "/return").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/loans"))
                .andExpect(flash().attribute("msg", "还书成功"));

        Book reloaded = books.findById(book.getId()).orElseThrow();
        assertThat(reloaded.getAvailableCopies()).isEqualTo(2);
        assertThat(loans.findById(loan.getId()).orElseThrow().getStatus()).isEqualTo(LoanStatus.RETURNED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("借书失败时带着错误信息回到借书页")
    void borrowFailureShowsBusinessError() throws Exception {
        Book emptyBook = new Book();
        emptyBook.setTitle("绝版书");
        emptyBook.setAuthor("某人");
        emptyBook.setTotalCopies(1);
        emptyBook.setAvailableCopies(0);
        books.save(emptyBook);

        com.example.library.domain.Reader reader = new com.example.library.domain.Reader();
        reader.setCardNo("R-2002");
        reader.setName("王五");
        readers.save(reader);

        mvc.perform(post("/loans/borrow").with(csrf())
                        .param("readerId", String.valueOf(reader.getId()))
                        .param("bookId", String.valueOf(emptyBook.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/loans/new"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("图书与读者列表支持关键字搜索")
    void listPagesSupportSearch() throws Exception {
        Book book = new Book();
        book.setTitle("Refactoring");
        book.setAuthor("Martin Fowler");
        book.setTotalCopies(1);
        books.save(book);

        com.example.library.domain.Reader reader = new com.example.library.domain.Reader();
        reader.setCardNo("R-2003");
        reader.setName("赵六");
        readers.save(reader);

        mvc.perform(get("/books").param("q", "refactor"))
                .andExpect(status().isOk())
                .andExpect(view().name("books/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Refactoring")));

        mvc.perform(get("/books").param("q", "不存在的关键字"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("没有找到匹配的图书")));

        mvc.perform(get("/readers").param("q", "赵六"))
                .andExpect(status().isOk())
                .andExpect(view().name("readers/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("赵六")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("图书超过一页时列表显示分页信息")
    void booksListIsPaged() throws Exception {
        for (int i = 1; i <= 12; i++) {
            Book paged = new Book();
            paged.setTitle("分页图书" + i);
            paged.setAuthor("作者" + i);
            paged.setTotalCopies(1);
            paged.setAvailableCopies(1);
            books.save(paged);
        }
        long total = books.count();
        long pages = (total + 9) / 10;

        mvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(view().name("books/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "第 1 / " + pages + " 页")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("下一页")));

        mvc.perform(get("/books").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "第 2 / " + pages + " 页")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("上一页")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("图书列表可按分类标签筛选，并可叠加关键词搜索")
    void booksListFiltersByCategory() throws Exception {
        books.deleteAll();
        saveBook("Alpha", "文学");
        saveBook("Bravo", "科幻");
        saveBook("Charlie", "科幻");

        assertThat(titlesOf(get("/books").param("category", "科幻")))
                .containsExactly("Charlie", "Bravo");

        mvc.perform(get("/books").param("category", "科幻"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("全部")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("chip active")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(">Alpha<"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"category\"")));

        assertThat(titlesOf(get("/books").param("category", "科幻").param("q", "Bravo")))
                .containsExactly("Bravo");
        assertThat(titlesOf(get("/books").param("q", "Alpha")))
                .containsExactly("Alpha");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("分类标签展示每个分类的图书数量")
    void booksListShowsCategoryCounts() throws Exception {
        books.deleteAll();
        saveBook("Alpha", "文学");
        saveBook("Bravo", "科幻");
        saveBook("Charlie", "科幻");

        mvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("科幻")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("文学")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("category=")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("不存在的分类显示空状态而不是报错")
    void unknownCategoryShowsEmptyState() throws Exception {
        books.deleteAll();
        saveBook("Alpha", "文学");

        mvc.perform(get("/books").param("category", "不存在的分类"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("该分类下暂时没有图书")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("页码跳转支持指定页，越界页码回到最后一页")
    void pagerJumpGoesToRequestedPageAndClamps() throws Exception {
        books.deleteAll();
        for (int i = 1; i <= 12; i++) {
            saveBook("跳页图书" + i, "A类");
        }

        mvc.perform(get("/books").param("jump", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("第 2 / 2 页")));

        mvc.perform(get("/books").param("jump", "99"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("第 2 / 2 页")));

        mvc.perform(get("/books").param("jump", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("第 1 / 2 页")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("概览按分类统计书目种类，并可从统计卡跳转")
    void dashboardShowsCategoryCountAndJumpLinks() throws Exception {
        books.deleteAll();
        saveBook("甲", "文学");
        saveBook("乙", "科幻");
        saveBook("丙", "科幻");

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("categoryCount", 2L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/books\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/readers\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("status=BORROWED")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("status=OVERDUE")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("读者有逾期未还图书时，列表与详情显示逾期，且借书表单禁用该读者")
    void overdueReaderIsMarkedAndBlocked() throws Exception {
        Book book = new Book();
        book.setTitle("逾期示例");
        book.setAuthor("作者丙");
        book.setTotalCopies(1);
        book.setAvailableCopies(0);
        book = books.save(book);

        com.example.library.domain.Reader reader = new com.example.library.domain.Reader();
        reader.setCardNo("R-4101");
        reader.setName("逾期读者");
        reader = readers.save(reader);

        Loan overdue = new Loan();
        overdue.setBook(book);
        overdue.setReader(reader);
        overdue.setBorrowedAt(LocalDateTime.now().minusDays(40));
        overdue.setDueAt(LocalDateTime.now().minusDays(5));
        loans.save(overdue);

        mvc.perform(get("/readers"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("R-4101")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">逾期<")));

        mvc.perform(get("/readers/" + reader.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("该读者有逾期未还的图书，归还前不能借书。")));

        Book free = new Book();
        free.setTitle("可借新书");
        free.setAuthor("作者丁");
        free.setTotalCopies(1);
        free.setAvailableCopies(1);
        free = books.save(free);

        mvc.perform(get("/loans/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("loans/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("有逾期，暂不可借")));

        mvc.perform(post("/loans/borrow").with(csrf())
                        .param("readerId", String.valueOf(reader.getId()))
                        .param("bookId", String.valueOf(free.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/loans/new"))
                .andExpect(flash().attribute("error",
                        org.hamcrest.Matchers.containsString("逾期")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("图书详情页展示简介；没有封面时显示占位封面")
    void bookDetailShowsDescriptionAndCoverPlaceholder() throws Exception {
        Book book = new Book();
        book.setTitle("朝花夕拾");
        book.setAuthor("鲁迅");
        book.setTotalCopies(1);
        book.setAvailableCopies(1);
        book.setDescription("回忆童年与青年时代的散文集。");
        book = books.save(book);

        mvc.perform(get("/books/" + book.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">简介<")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("回忆童年与青年时代的散文集。")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cover placeholder")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("新增图书时可以上传封面并保存简介")
    void createBookWithCoverUpload() throws Exception {
        MockMultipartFile cover = new MockMultipartFile("cover", "cover.png", "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 9, 9});

        mvc.perform(multipart("/books").file(cover).with(csrf())
                        .param("title", "封面上传图书")
                        .param("author", "上传作者")
                        .param("totalCopies", "1")
                        .param("description", "上传封面时填写的简介"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/books"));

        Book created = books.findAll(org.springframework.data.domain.PageRequest.of(0, 1,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "id")))
                .getContent().get(0);

        assertThat(created.getTitle()).isEqualTo("封面上传图书");
        assertThat(created.getDescription()).isEqualTo("上传封面时填写的简介");
        assertThat(created.getCoverPath()).startsWith(CoverStorage.URL_PREFIX);

        java.nio.file.Path saved = covers.getRoot()
                .resolve(created.getCoverPath().substring(CoverStorage.URL_PREFIX.length()));
        assertThat(Files.exists(saved)).isTrue();

        mvc.perform(get("/books/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(created.getCoverPath())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("上传封面时填写的简介")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("cover placeholder"))));

        covers.delete(created.getCoverPath());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("上传非图片文件时回到表单并提示格式问题")
    void uploadInvalidCoverShowsError() throws Exception {
        MockMultipartFile text = new MockMultipartFile("cover", "note.txt", "text/plain", "not an image".getBytes());

        mvc.perform(multipart("/books").file(text).with(csrf())
                        .param("title", "错误封面的图书")
                        .param("author", "某个作者")
                        .param("totalCopies", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("books/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("封面只支持")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("编辑图书时可以移除已有封面")
    void updateBookCanRemoveCover() throws Exception {
        Book book = new Book();
        book.setTitle("有封面的书");
        book.setAuthor("作者");
        book.setTotalCopies(1);
        book.setAvailableCopies(1);
        book.setCoverPath(CoverStorage.URL_PREFIX + "keep.png");
        book = books.save(book);

        java.nio.file.Path saved = covers.getRoot().resolve("keep.png");
        Files.write(saved, new byte[] {1, 2, 3});

        mvc.perform(multipart("/books/" + book.getId()).with(csrf())
                        .param("title", "有封面的书")
                        .param("author", "作者")
                        .param("totalCopies", "1")
                        .param("coverPath", CoverStorage.URL_PREFIX + "keep.png")
                        .param("removeCover", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/books"));

        assertThat(books.findById(book.getId()).orElseThrow().getCoverPath()).isNull();
        assertThat(Files.exists(saved)).isFalse();
    }

    private void saveBook(String title, String category) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("作者");
        book.setCategory(category);
        book.setTotalCopies(1);
        book.setAvailableCopies(1);
        books.save(book);
    }

    private java.util.List<String> titlesOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        var page = (org.springframework.data.domain.Page<Book>) result.getModelAndView().getModel().get("page");
        return page.getContent().stream().map(Book::getTitle).toList();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("图书详情页展示在借读者、逾期历史，并支持还书后回到详情页")
    void bookDetailShowsActiveAndHistory() throws Exception {
        LocalDateTime base = LocalDateTime.now().withNano(0);

        Book book = new Book();
        book.setTitle("详情页图书");
        book.setAuthor("作者甲");
        book.setTotalCopies(2);
        book.setAvailableCopies(1);
        book = books.save(book);

        com.example.library.domain.Reader activeReader = new com.example.library.domain.Reader();
        activeReader.setCardNo("R-3001");
        activeReader.setName("在借读者");
        activeReader = readers.save(activeReader);

        com.example.library.domain.Reader historyReader = new com.example.library.domain.Reader();
        historyReader.setCardNo("R-3002");
        historyReader.setName("历史读者");
        historyReader = readers.save(historyReader);

        Loan active = new Loan();
        active.setBook(book);
        active.setReader(activeReader);
        active.setBorrowedAt(base.minusDays(3));
        active.setDueAt(base.plusDays(27));
        active = loans.save(active);

        Loan returned = new Loan();
        returned.setBook(book);
        returned.setReader(historyReader);
        returned.setBorrowedAt(base.minusDays(40));
        returned.setDueAt(base.minusDays(10));
        returned.setReturnedAt(base.minusDays(8));
        returned.setStatus(LoanStatus.RETURNED);
        loans.save(returned);

        mvc.perform(get("/books/" + book.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("books/detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("在借中（1）")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("在借读者")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("历史读者")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("逾期归还 2 天")));

        mvc.perform(post("/loans/" + active.getId() + "/return").with(csrf())
                        .param("returnTo", "/books/" + book.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/books/" + book.getId()))
                .andExpect(flash().attribute("msg", "还书成功"));

        assertThat(books.findById(book.getId()).orElseThrow().getAvailableCopies()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("读者详情页展示在借图书与剩余可借额度")
    void readerDetailShowsBorrowingAndQuota() throws Exception {
        Book book = new Book();
        book.setTitle("读者详情借阅书");
        book.setAuthor("作者乙");
        book.setTotalCopies(1);
        book.setAvailableCopies(0);
        book = books.save(book);

        com.example.library.domain.Reader reader = new com.example.library.domain.Reader();
        reader.setCardNo("R-3003");
        reader.setName("详情读者");
        reader.setMaxBorrow(5);
        reader = readers.save(reader);

        Loan loan = new Loan();
        loan.setBook(book);
        loan.setReader(reader);
        loan.setBorrowedAt(LocalDateTime.now().minusDays(1));
        loan.setDueAt(LocalDateTime.now().plusDays(29));
        loans.save(loan);

        mvc.perform(get("/readers/" + reader.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("readers/detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("详情读者")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 / 4")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("读者详情借阅书")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("访问不存在的图书或读者详情时显示业务错误页")
    void detailOfMissingEntityShowsBusinessError() throws Exception {
        mvc.perform(get("/books/999999"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/business"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("图书不存在")));

        mvc.perform(get("/readers/999999"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/business"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("读者不存在")));
    }
}
