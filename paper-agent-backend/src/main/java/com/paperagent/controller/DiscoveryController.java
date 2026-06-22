package com.paperagent.controller;

import com.paperagent.dto.DiscoveryResult;
import com.paperagent.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @GetMapping("/search")
    public List<DiscoveryResult> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "all") String source,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return discoveryService.search(query, source, limit);
    }
}
