package com.example.library.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.library.domain.Book;
import com.example.library.service.BookService;
import com.example.library.service.LoanService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/books")
public class BookController {

    private static final int PAGE_SIZE = 10;

    private final BookService books;
    private final LoanService loans;

    public BookController(BookService books, LoanService loans) {
        this.books = books;
        this.loans = loans;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir,
                       @RequestParam(required = false) Integer jump,
                       Model model) {
        String sortKey = normalizeSort(sort);
        String direction = normalizeDirection(dir);
        int requested = jump != null ? jump - 1 : page;
        model.addAttribute("page", Paging.fetch(
                p -> books.search(q, p, PAGE_SIZE, sortKey, direction), requested));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("sort", sortKey);
        model.addAttribute("dir", direction);
        return "books/list";
    }

    /** 只接受 title / category，其他值走默认排序（最新添加在前）。 */
    private static String normalizeSort(String sort) {
        return "title".equals(sort) || "category".equals(sort) ? sort : "id";
    }

    private static String normalizeDirection(String dir) {
        return "desc".equalsIgnoreCase(dir) ? "desc" : "asc";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("book", new Book());
        return "books/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        model.addAttribute("book", books.get(id));
        model.addAttribute("activeLoans", loans.activeByBook(id));
        model.addAttribute("history", loans.historyByBook(id, page, PAGE_SIZE));
        return "books/detail";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("book") Book book,
                         BindingResult binding,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            return "books/form";
        }
        books.create(book);
        redirectAttributes.addFlashAttribute("msg", "已添加《" + book.getTitle() + "》");
        return "redirect:/books";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("book", books.get(id));
        return "books/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("book") Book book,
                         BindingResult binding,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            book.setId(id);
            return "books/form";
        }
        books.update(id, book);
        redirectAttributes.addFlashAttribute("msg", "已保存《" + book.getTitle() + "》");
        return "redirect:/books";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        books.delete(id);
        redirectAttributes.addFlashAttribute("msg", "已删除图书");
        return "redirect:/books";
    }
}
