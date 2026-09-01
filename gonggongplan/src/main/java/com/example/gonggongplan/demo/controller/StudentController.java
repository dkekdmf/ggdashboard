package com.example.gonggongplan.demo.controller;

import com.example.gonggongplan.demo.dto.StudentRequest;
import com.example.gonggongplan.demo.model.Student;
import com.example.gonggongplan.demo.repository.StudentRepository;
import com.example.gonggongplan.demo.service.GoogleSheetService; // 1. GoogleSheetService 임포트
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
@CrossOrigin(origins = "*")
public class StudentController {

    private final StudentRepository studentRepository;
    private final GoogleSheetService googleSheetService; // 2. 서비스 변수 선언

    // 3. 생성자 주입 방식으로 두 클래스를 모두 가져옵니다.
    public StudentController(StudentRepository studentRepository, GoogleSheetService googleSheetService) {
        this.studentRepository = studentRepository;
        this.googleSheetService = googleSheetService;
    }

    @PostMapping("/login")
    public ResponseEntity<Student> login(@RequestBody StudentRequest request) {

        // 예: "5학년 3반" -> "5학년"만 추출 (첫 번째 띄어쓰기 전 텍스트만 가져오기)
        String rawGrade = request.grade(); // DTO 필드
        String gradeOnly = (rawGrade != null && rawGrade.contains(" "))
                ? rawGrade.split(" ")[0]
                : rawGrade;

        Student student = Student.builder()
                .schoolName(request.schoolName())
                .grade(gradeOnly) // 👈 "5학년"만 깔끔하게 저장!
                .gender(request.gender())
                .build();

        // 1) 데이터베이스(MySQL)에 저장
        Student saved = studentRepository.save(student);

        // 2) 📊 구글 스프레드시트로 로그인 정보 실시간 전송!
        // (이름/아이디 항목이 따로 없다면 "-" 또는 학생 ID를 넣어 전송합니다)
        googleSheetService.sendLoginInfoToGoogleSheet(
                "학생_" + saved.getId(), // 식별용 이름 (또는 "-")
                saved.getSchoolName(),
                saved.getGrade(),
                saved.getGender()
        );

        return ResponseEntity.ok(saved);
    }
}