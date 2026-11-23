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

import com.example.library.domain.Reader;
import com.example.library.domain.ReaderStatus;
import com.example.library.service.LoanService;
import com.example.library.service.ReaderService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/readers")
public class ReaderController {

    private static final int PAGE_SIZE = 10;

    private final ReaderService readers;
    private final LoanService loans;

    public ReaderController(ReaderService readers, LoanService loans) {
        this.readers = readers;
        this.loans = loans;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) Integer jump,
                       Model model) {
        int requested = jump != null ? jump - 1 : page;
        model.addAttribute("page", Paging.fetch(p -> readers.search(q, p, PAGE_SIZE), requested));
        model.addAttribute("q", q == null ? "" : q);
        return "readers/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("reader", new Reader());
        model.addAttribute("statuses", ReaderStatus.values());
        return "readers/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        Reader reader = readers.get(id);
        var activeLoans = loans.activeByReader(id);
        model.addAttribute("reader", reader);
        model.addAttribute("activeLoans", activeLoans);
        model.addAttribute("activeCount", activeLoans.size());
        model.addAttribute("remaining", Math.max(0, reader.getMaxBorrow() - activeLoans.size()));
        model.addAttribute("history", loans.historyByReader(id, page, PAGE_SIZE));
        return "readers/detail";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("reader") Reader reader,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("statuses", ReaderStatus.values());
            return "readers/form";
        }
        readers.create(reader);
        redirectAttributes.addFlashAttribute("msg", "已添加读者「" + reader.getName() + "」");
        return "redirect:/readers";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("reader", readers.get(id));
        model.addAttribute("statuses", ReaderStatus.values());
        return "readers/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("reader") Reader reader,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            reader.setId(id);
            model.addAttribute("statuses", ReaderStatus.values());
            return "readers/form";
        }
        readers.update(id, reader);
        redirectAttributes.addFlashAttribute("msg", "已保存读者「" + reader.getName() + "」");
        return "redirect:/readers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        readers.delete(id);
        redirectAttributes.addFlashAttribute("msg", "已删除读者");
        return "redirect:/readers";
    }
}
