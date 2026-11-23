package com.example.library.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.library.service.DashboardService;

@Controller
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("categoryCount", dashboard.categoryCount());
        model.addAttribute("totalCopies", dashboard.totalCopies());
        model.addAttribute("readerCount", dashboard.readerCount());
        model.addAttribute("activeLoans", dashboard.activeLoanCount());
        model.addAttribute("overdueCount", dashboard.overdueCount());
        model.addAttribute("overdueLoans", dashboard.latestOverdue());
        return "index";
    }
}
