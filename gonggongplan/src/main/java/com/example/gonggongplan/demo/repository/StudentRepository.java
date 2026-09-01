package com.example.gonggongplan.demo.repository;

import com.example.gonggongplan.demo.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    // 기본적인 save(), findAll(), findById() 기능이 자동으로 제공됩니다.
}