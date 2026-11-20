package com.example.fraud.controller;

import com.example.fraud.dto.Decision;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.service.ScoringService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScoreController {

    private final ScoringService scoringService;

    public ScoreController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @PostMapping("/score")
    public Decision score(@Valid @RequestBody ScoringRequest request) {
        return scoringService.score(request);
    }
}
