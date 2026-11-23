package com.example.library.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.library.service.BookService;
import com.example.library.service.BusinessException;
import com.example.library.service.LoanService;
import com.example.library.service.ReaderService;

@Controller
@RequestMapping("/loans")
public class LoanController {

    private static final int PAGE_SIZE = 10;

    private final LoanService loans;
    private final ReaderService readers;
    private final BookService books;

    public LoanController(LoanService loans, ReaderService readers, BookService books) {
        this.loans = loans;
        this.readers = readers;
        this.books = books;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "ALL") String status,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) Integer jump,
                       Model model) {
        String filter = status == null ? "ALL" : status.toUpperCase(java.util.Locale.ROOT);
        int requested = jump != null ? jump - 1 : page;
        model.addAttribute("page", Paging.fetch(p -> loans.page(filter, p, PAGE_SIZE), requested));
        model.addAttribute("status", filter);
        return "loans/list";
    }

    @GetMapping("/new")
    public String borrowForm(@RequestParam(required = false) Long readerId,
                             @RequestParam(required = false) Long bookId,
                             Model model) {
        model.addAttribute("readers", readers.findActive());
        model.addAttribute("books", books.findAvailable());
        model.addAttribute("overdueReaderIds", loans.readersWithOverdue());
        model.addAttribute("selectedReaderId", readerId);
        model.addAttribute("selectedBookId", bookId);
        return "loans/form";
    }

    @PostMapping("/borrow")
    public String borrow(@RequestParam(required = false) Long readerId,
                         @RequestParam(required = false) Long bookId,
                         RedirectAttributes redirectAttributes) {
        if (readerId == null || bookId == null) {
            redirectAttributes.addFlashAttribute("error", "请选择读者和图书");
            return "redirect:/loans/new";
        }
        try {
            loans.borrow(bookId, readerId);
            redirectAttributes.addFlashAttribute("msg", "借书成功");
            return "redirect:/loans";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/loans/new";
        }
    }

    @PostMapping("/{id}/return")
    public String returnLoan(@PathVariable Long id,
                             @RequestParam(required = false) String returnTo,
                             RedirectAttributes redirectAttributes) {
        try {
            loans.returnLoan(id);
            redirectAttributes.addFlashAttribute("msg", "还书成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + safeReturnPath(returnTo);
    }

    /** 只允许回到本站的图书/读者详情页，避免开放重定向。 */
    private String safeReturnPath(String returnTo) {
        if (returnTo != null && (returnTo.startsWith("/books/") || returnTo.startsWith("/readers/"))) {
            return returnTo;
        }
        return "/loans";
    }
}
