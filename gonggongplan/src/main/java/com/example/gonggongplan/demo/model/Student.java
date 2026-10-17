package com.example.gonggongplan.demo.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * 데이터베이스 STUDENT 표의 한 줄에 해당하는 클래스.
 *
 * @Entity 를 붙이면 이 클래스가 곧 데이터베이스 표가 된다.
 * 로그인할 때마다 한 줄이 쌓여서, 몇 명이 수업에 참여했는지 남는다.
 */
@Entity
@Table(name = "STUDENT")
@Getter                                             // getSchoolName() 같은 조회 메서드를 자동으로 만들어 준다
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 가 내부적으로 쓰는 빈 생성자 (밖에서는 못 쓰게 막음)
@AllArgsConstructor
@Builder                                            // Student.builder().schoolName(...).build() 형태로 만들 수 있게 해 준다
public class Student {

    /** 번호는 데이터베이스가 1, 2, 3... 순서로 자동으로 매긴다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String schoolName;
    private String grade;
    private String gender;
}
