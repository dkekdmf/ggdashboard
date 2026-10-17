package com.example.gonggongplan.demo.controller;

import com.example.gonggongplan.demo.dto.StudentRequest;
import com.example.gonggongplan.demo.model.Student;
import com.example.gonggongplan.demo.repository.StudentRepository;
import com.example.gonggongplan.demo.service.GoogleSheetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 로그인 화면의 요청을 받는 곳.
 *
 * 컨트롤러는 "손님을 맞는 창구" 역할이다.
 * 화면에서 온 요청을 받아, 실제 일은 Repository·Service 에 넘기고 결과만 돌려준다.
 *
 * 여기서 로그인은 비밀번호를 확인하는 것이 아니라,
 * "누가 수업에 참여했는지 기록을 남기는 것" 에 가깝다.
 */
@RestController
@RequestMapping("/api/students")   // 이 클래스의 주소는 모두 /api/students 로 시작한다
@CrossOrigin(origins = "*")        // 다른 주소에서 온 요청도 허용 (교실에서 여러 기기로 접속하기 위함)
public class StudentController {

    private final StudentRepository studentRepository;    // 데이터베이스에 저장하는 담당
    private final GoogleSheetService googleSheetService;   // 구글 시트로 보내는 담당

    /**
     * 생성자로 필요한 부품을 받는다(생성자 주입).
     * 스프링이 알아서 알맞은 것을 넣어 주므로 직접 new 로 만들지 않는다.
     */
    public StudentController(StudentRepository studentRepository, GoogleSheetService googleSheetService) {
        this.studentRepository = studentRepository;
        this.googleSheetService = googleSheetService;
    }

    /** POST /api/students/login — 학교·학년·성별을 받아 기록을 남긴다. */
    @PostMapping("/login")
    public ResponseEntity<Student> login(@RequestBody StudentRequest request) {

        // 화면에서 "5학년 3반" 처럼 반까지 올 수 있는데 통계는 학년 단위로 보면 되므로,
        // 첫 띄어쓰기 앞부분만 남긴다. ("5학년 3반" → "5학년")
        String rawGrade = request.grade();
        String gradeOnly = (rawGrade != null && rawGrade.contains(" "))
                ? rawGrade.split(" ")[0]
                : rawGrade;

        Student student = Student.builder()
                .schoolName(request.schoolName())
                .grade(gradeOnly)
                .gender(request.gender())
                .build();

        // 1) 데이터베이스에 저장하면 번호(id)가 매겨진 학생이 돌아온다
        Student saved = studentRepository.save(student);

        // 2) 구글 시트에도 함께 남긴다.
        //    이름 칸이 따로 없어서 "학생_3" 처럼 번호를 붙여 구분한다.
        googleSheetService.sendLoginInfoToGoogleSheet(
                "학생_" + saved.getId(),
                saved.getSchoolName(),
                saved.getGrade(),
                saved.getGender()
        );

        return ResponseEntity.ok(saved);
    }
}
