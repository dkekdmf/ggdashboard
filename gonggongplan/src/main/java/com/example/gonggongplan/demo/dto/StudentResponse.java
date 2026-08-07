package com.example.gonggongplan.demo.dto;

public record StudentResponse(
        Long studentId,
        String schoolName,
        String gradeClass,
        String name,
        String message
) {}