package com.example.gonggongplan.demo.dto;

import lombok.Builder;

/**
 * 로그인 화면에서 서버로 보내는 학생 정보.
 *
 * DTO(Data Transfer Object)는 "주고받기 위한 그릇"이다.
 * 데이터베이스에 저장되는 Student 와 따로 두는 이유는,
 * 화면이 보내는 내용과 실제로 저장하는 내용이 항상 같지 않기 때문이다.
 *
 * record 로 만들면 필드만 적어도 생성자와 조회 메서드가 자동으로 생긴다.
 */
@Builder
public record StudentRequest(
        String schoolName,   // 학교 이름 (예: 서울초등학교)
        String grade,        // 학년 (예: 5학년)
        String gender        // 성별 (예: 남학생)
) {}
