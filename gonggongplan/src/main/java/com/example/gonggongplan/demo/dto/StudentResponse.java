package com.example.gonggongplan.demo.dto;

/**
 * 로그인 처리 결과를 화면으로 돌려줄 때 쓰는 그릇.
 *
 * 지금은 StudentController 가 Student 를 그대로 돌려주고 있어서 쓰이지 않는다.
 * 앞으로 "환영합니다" 같은 안내 문구를 함께 내려주고 싶을 때 쓰면 된다.
 */
public record StudentResponse(
        Long studentId,      // 저장된 학생 번호
        String schoolName,   // 학교 이름
        String gradeClass,   // 학년·반
        String name,         // 이름
        String message       // 화면에 보여 줄 안내 문구
) {}
