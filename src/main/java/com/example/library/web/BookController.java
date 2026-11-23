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

import jakarta.validation.Valid;

@Controller
@RequestMapping("/books")
public class BookController {

    private static final int PAGE_SIZE = 10;

    private final BookService books;

    public BookController(BookService books) {
        this.books = books;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("page", books.search(q, page, PAGE_SIZE));
        model.addAttribute("q", q == null ? "" : q);
        return "books/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("book", new Book());
        return "books/form";
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
