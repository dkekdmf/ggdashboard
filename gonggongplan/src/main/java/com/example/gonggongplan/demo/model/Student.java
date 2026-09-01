package com.example.gonggongplan.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "STUDENT")
@Getter // 👈 필드명과 일치하는 getSchoolName(), getGrade(), getGender() 자동 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String schoolName;
    private String grade;
    private String gender;
}