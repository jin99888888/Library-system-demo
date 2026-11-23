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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.library.domain.Book;
import com.example.library.service.BookService;
import com.example.library.service.BusinessException;
import com.example.library.service.CoverStorage;
import com.example.library.service.LoanService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/books")
public class BookController {

    private static final int PAGE_SIZE = 10;

    private final BookService books;
    private final LoanService loans;
    private final CoverStorage covers;

    public BookController(BookService books, LoanService loans, CoverStorage covers) {
        this.books = books;
        this.loans = loans;
        this.covers = covers;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) Integer jump,
                       Model model) {
        String selected = category == null || category.isBlank() ? null : category.trim();
        int requested = jump != null ? jump - 1 : page;
        model.addAttribute("page", Paging.fetch(
                p -> books.search(q, selected, p, PAGE_SIZE), requested));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("category", selected);
        model.addAttribute("categories", books.categories());
        model.addAttribute("totalTitles", books.totalTitles());
        return "books/list";
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
                         @RequestParam(value = "cover", required = false) MultipartFile cover,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            return "books/form";
        }
        try {
            book.setCoverPath(covers.save(cover));
        } catch (BusinessException e) {
            model.addAttribute("coverError", e.getMessage());
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
                         @RequestParam(value = "cover", required = false) MultipartFile cover,
                         @RequestParam(value = "removeCover", defaultValue = "false") boolean removeCover,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        Book current = books.get(id);
        if (binding.hasErrors()) {
            book.setId(id);
            book.setCoverPath(current.getCoverPath());
            return "books/form";
        }
        try {
            String uploaded = covers.save(cover);
            if (uploaded != null) {
                covers.delete(current.getCoverPath());
                book.setCoverPath(uploaded);
            } else if (removeCover) {
                covers.delete(current.getCoverPath());
                book.setCoverPath(null);
            } else {
                book.setCoverPath(current.getCoverPath());
            }
        } catch (BusinessException e) {
            book.setId(id);
            book.setCoverPath(current.getCoverPath());
            model.addAttribute("coverError", e.getMessage());
            return "books/form";
        }
        books.update(id, book);
        redirectAttributes.addFlashAttribute("msg", "已保存《" + book.getTitle() + "》");
        return "redirect:/books";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String cover = books.get(id).getCoverPath();
        books.delete(id);
        covers.delete(cover);
        redirectAttributes.addFlashAttribute("msg", "已删除图书");
        return "redirect:/books";
    }
}
