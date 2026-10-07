package com.example.kai.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.kai.config.UserConfigService;
import com.example.kai.orchestrator.Finding;
import com.example.kai.orchestrator.Orchestrator;
import com.example.kai.orchestrator.Progress;
import com.example.kai.orchestrator.Proposal;
import com.example.kai.writer.ChangeWriter;

@Controller
public class ChatController {

    public record Message(String sender, String text, Finding.Report report, boolean applied, String link) {
        Message(String sender, String text, Finding.Report report) {
            this(sender, text, report, false, null);
        }
    }

    record Job(Progress progress, CompletableFuture<Finding.Report> result) {}

    private final Orchestrator orchestrator;
    private final UserConfigService config;
    private final ExecutorService jobs = Executors.newVirtualThreadPerTaskExecutor();

    public ChatController(Orchestrator orchestrator, UserConfigService config) {
        this.orchestrator = orchestrator;
        this.config = config;
    }

    @GetMapping({"/impact", "/chat"})
    public String impact(HttpSession session, Model model) {
        if (!config.setupComplete()) return "redirect:/setup";
        collect(session);
        Job job = (Job) session.getAttribute("job");
        model.addAttribute("active", "impact");
        model.addAttribute("messages", history(session));
        model.addAttribute("log", job == null ? null : job.progress().since(0));
        model.addAttribute("targets", orchestrator.targets());
        model.addAttribute("backupDir", config.backupDir().toString());
        model.addAttribute("aiReady", config.ai().configured());
        model.addAttribute("sourceReady", !orchestrator.targets().isEmpty());
        return "impact";
    }

    @PostMapping({"/impact/scan", "/chat"})
    public String send(@RequestParam String message, HttpSession session) {
        collect(session);
        if (session.getAttribute("job") != null) return "redirect:/impact";

        if (!config.ai().configured()) {
            history(session).add(new Message("error", "AI connection is not configured. Open Settings and add an endpoint, API key and model.", null));
            return "redirect:/impact";
        }
        if (orchestrator.targets().isEmpty()) {
            history(session).add(new Message("error", "No usable knowledge source is connected. Open Knowledge Sources and connect one.", null));
            return "redirect:/impact";
        }

        history(session).add(new Message("user", message, null));
        Progress progress = new Progress();
        progress.add("Started");
        session.setAttribute("job", new Job(progress, CompletableFuture.supplyAsync(() -> {
            try {
                return orchestrator.scan(message, progress);
            }
            catch (IOException e) {
                throw new CompletionException(e);
            }
        }, jobs)));
        return "redirect:/impact";
    }

    @GetMapping({"/impact/progress", "/progress"})
    @ResponseBody
    public Map<String, Object> progress(@RequestParam int since, HttpSession session) {
        Job job = (Job) session.getAttribute("job");
        return job == null ? Map.of("lines", List.of(), "done", true)
                : Map.of("lines", job.progress().since(since), "done", job.result().isDone());
    }

    private synchronized void collect(HttpSession session) {
        Job job = (Job) session.getAttribute("job");
        if (job == null || !job.result().isDone()) return;

        session.removeAttribute("job");
        try {
            Finding.Report report = job.result().join();
            String summary = report.affectedCount() + " of " + report.findings().size() + " files affected"
                    + (report.readyCount() > 0 ? ". " + report.readyCount() + " proposed edits ready for review" : "")
                    + (report.unappliedCount() > 0 ? ". " + report.unappliedCount() + " could not be edited automatically" : "")
                    + (report.manualCount() > 0 ? ". " + report.manualCount() + " need a manual update" : "")
                    + (report.errorCount() > 0 ? ". " + report.errorCount() + " could not be checked" : "");
            history(session).add(new Message("bot", summary, report));
        }
        catch (CompletionException e) {
            Throwable c = e.getCause() == null ? e : e.getCause();
            history(session).add(new Message("error", c.getClass().getSimpleName() + ": " + c.getMessage(), null));
        }
    }

    @PostMapping({"/impact/save", "/save"})
    public String save(@RequestParam int report, @RequestParam Map<String, String> form, HttpSession session) {
        Message m = reportMessage(history(session), report);
        if (m != null && !m.applied()) save(m.report(), form);
        return "redirect:/impact#report-" + report;
    }

    @PostMapping({"/impact/finalize", "/finalize"})
    public String finalizeReport(@RequestParam int report, @RequestParam Map<String, String> form, HttpSession session) {
        List<Message> history = history(session);
        Message m = reportMessage(history, report);
        if (m == null || m.applied()) return "redirect:/impact";

        save(m.report(), form);
        ChangeWriter.Outcome outcome = orchestrator.apply(m.report());
        if (outcome.result() == ChangeWriter.Result.APPLIED) {
            history.set(report, new Message(m.sender(), m.text(), m.report(), true, null));
        }
        history.add(new Message(outcome.result() == ChangeWriter.Result.APPLIED ? "bot" : "error",
                outcome.message(), null, false,
                outcome.run() == null ? null : "/history/report/" + outcome.run()));
        return "redirect:/impact";
    }

    @PostMapping("/impact/clear")
    public String clear(HttpSession session) {
        if (session.getAttribute("job") == null) session.removeAttribute("history");
        return "redirect:/impact";
    }

    private static void save(Finding.Report report, Map<String, String> form) {
        List<Finding> findings = report.findings();
        for (int i = 0; i < findings.size(); i++) {
            Proposal p = findings.get(i).proposal();
            String text = form.get("text" + i);
            if (p != null && p.ready() && text != null) {
                p.save(text, form.containsKey("include" + i));
            }
        }
    }

    private static Message reportMessage(List<Message> history, int index) {
        return index >= 0 && index < history.size() && history.get(index).report() != null ? history.get(index) : null;
    }

    @SuppressWarnings("unchecked")
    private List<Message> history(HttpSession session) {
        List<Message> history = (List<Message>) session.getAttribute("history");
        if (history == null) {
            history = new ArrayList<>();
            session.setAttribute("history", history);
        }
        return history;
    }
}
