package com.example.gonggongplan.demo.dto;

import lombok.Builder;

@Builder
public record StudentRequest(
        String schoolName,
        String grade,
        String gender
) {}