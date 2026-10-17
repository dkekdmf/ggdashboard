package com.example.gonggongplan.demo.repository;

import com.example.gonggongplan.demo.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 학생 정보를 데이터베이스에 넣고 꺼내는 창구.
 *
 * JpaRepository 를 물려받기만 하면 save(), findAll(), findById() 같은 기능이
 * 저절로 생긴다. 직접 SQL 을 쓰지 않아도 된다.
 */
public interface StudentRepository extends JpaRepository<Student, Long> {
}
