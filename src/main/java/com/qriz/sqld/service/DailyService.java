package com.qriz.sqld.service;

import com.qriz.sqld.domain.question.Question;
import com.qriz.sqld.domain.question.QuestionRepository;
import com.qriz.sqld.domain.skill.Skill;
import com.qriz.sqld.domain.user.User;
import com.qriz.sqld.domain.user.UserRepository;
import com.qriz.sqld.domain.userActivity.UserActivity;
import com.qriz.sqld.domain.userActivity.UserActivityRepository;
import com.qriz.sqld.domain.daily.UserDaily;
import com.qriz.sqld.domain.daily.UserDailyRepository;
import com.qriz.sqld.dto.daily.DailyResultDetailDto;
import com.qriz.sqld.dto.test.TestReqDto;
import com.qriz.sqld.dto.test.TestRespDto;
import com.qriz.sqld.handler.ex.CustomApiException;
import com.qriz.sqld.util.WeekendPlanUtil;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class DailyService {

    private final QuestionRepository questionRepository;
    private final UserActivityRepository userActivityRepository;
    private final UserRepository userRepository;
    private final UserDailyRepository userDailyRepository;
    private final DailyPlanService dailyPlanService;
    private final DKTService dktService;

    private final Logger log = LoggerFactory.getLogger(DailyService.class);

    @Lazy
    @Autowired
    private final WeekendPlanUtil weekendPlanUtil;

    /**
     * 오늘의 데일리 테스트 문제를 가져오기
     * 
     * @param user 현재 사용자
     * @return 데일리 테스트 문제 목록
     */
    public List<TestRespDto.DailyRespDto> getDailyTestQuestionsByDay(Long userId, String dayNumber) {
        UserDaily userDaily = userDailyRepository.findByUserIdAndDayNumberWithPlannedSkills(userId, dayNumber)
                .orElseThrow(() -> new CustomApiException("해당 일자의 데일리 플랜을 찾을 수 없습니다."));

        // 이전 Day 완료 여부 및 당일 접근 가능 여부 확인
        if (!dailyPlanService.canAccessDay(userId, userDaily.getDayNumber())) {
            throw new CustomApiException("이 테스트에 아직 접근할 수 없습니다.");
        }

        List<Question> questions;
        if (userDaily.getPlannedSkills() == null) {
            // 4주차: DKT 기반 문제 추천
            questions = getWeekFourQuestions(userId, userDaily);
        } else if (userDaily.isReviewDay()) {
            // 주말: 복습 문제
            questions = weekendPlanUtil.getWeekendQuestions(userId, userDaily);
        } else {
            // 평일: 일반 문제
            questions = getRegularDayQuestions(userDaily);
        }

        return questions.stream()
                .map(TestRespDto.DailyRespDto::new)
                .collect(Collectors.toList());
    }

    private List<Question> getWeekFourQuestions(Long userId, UserDaily todayPlan) {
        LocalDateTime startDateTime = todayPlan.getPlanDate().minusWeeks(3).atStartOfDay();
        LocalDateTime endDateTime = todayPlan.getPlanDate().atTime(23, 59, 59);

        List<UserActivity> activities = userActivityRepository.findByUserIdAndDateBetween(
                userId, startDateTime, endDateTime);
        List<Double> predictions = dktService.getPredictions(userId, activities);
        return getQuestionsBasedOnPredictions(predictions);
    }

    private List<Question> getRegularDayQuestions(UserDaily todayPlan) {
        return questionRepository.findRandomQuestionsBySkillsAndCategory(
                todayPlan.getPlannedSkills(),
                2, // 데일리 카테고리 값
                10 // 데일리 테스트 문제 수
        );
    }

    private List<Question> getQuestionsBasedOnPredictions(List<Double> predictions) {
        List<Long> sortedSkillIds = IntStream.range(0, predictions.size())
                .boxed()
                .sorted(Comparator.comparingDouble(predictions::get))
                .map(Long::valueOf)
                .limit(5)
                .collect(Collectors.toList());

        return questionRepository.findRandomQuestionsBySkillIdsAndCategory(sortedSkillIds, 2, 10); // 데일리 카테고리, 10문제
    }

    /**
     * 데일리 테스트 제출 처리
     * 
     * @param user             현재 사용자
     * @param testSubmitReqDto 테스트 제출 데이터
     * @return 테스트 제출 결과 목록
     */
    @Transactional
    public List<TestRespDto.TestSubmitRespDto> processDailyTestSubmission(Long userId, String dayNumber,
            TestReqDto testSubmitReqDto) {
        UserDaily userDaily = userDailyRepository.findByUserIdAndDayNumber(userId, dayNumber)
                .orElseThrow(() -> new CustomApiException("해당 일자의 데일리 플랜을 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomApiException("사용자를 찾을 수 없습니다."));

        List<TestRespDto.TestSubmitRespDto> results = new ArrayList<>();

        for (TestReqDto.TestSubmitReqDto activity : testSubmitReqDto.getActivities()) {
            Question question = questionRepository.findById(activity.getQuestion().getQuestionId())
                    .orElseThrow(() -> new CustomApiException("문제를 찾을 수 없습니다."));

            UserActivity userActivity = new UserActivity();
            userActivity.setUser(user);
            userActivity.setQuestion(question);
            userActivity.setTestInfo(dayNumber);
            userActivity.setQuestionNum(activity.getQuestionNum());
            userActivity.setChecked(activity.getChecked());
            userActivity.setTimeSpent(activity.getTimeSpent());
            userActivity.setCorrection(question.getAnswer().equals(activity.getChecked()));
            userActivity.setDate(LocalDateTime.now());

            userActivityRepository.save(userActivity);

            TestRespDto.TestSubmitRespDto result = new TestRespDto.TestSubmitRespDto(
                    userActivity.getId(),
                    userId,
                    new TestRespDto.TestSubmitRespDto.QuestionRespDto(
                            question.getId(),
                            getCategoryName(question.getCategory())),
                    activity.getQuestionNum(),
                    activity.getChecked(),
                    activity.getTimeSpent(),
                    userActivity.isCorrection());

            results.add(result);
        }

        userDaily.setCompleted(true);
        userDaily.setCompletionDate(LocalDate.now());
        userDailyRepository.save(userDaily);

        int day = Integer.parseInt(dayNumber.replace("Day", ""));
        if (day % 7 == 5 && day <= 19) { // Day5, Day12, Day19 완료 시
            log.info("Day {} completed. Updating weekend plan.", day);
            dailyPlanService.updateWeekendPlan(userId, day);
        }

        return results;
    }

    /**
     * 카테고리 번호에 해당하는 카테고리 이름 반환
     * 
     * @param category 카테고리 번호
     * @return 카테고리 이름
     */
    private String getCategoryName(int category) {
        switch (category) {
            case 1:
                return "진단";
            case 2:
                return "데일리";
            case 3:
                return "모의고사";
            default:
                return "알 수 없음";
        }
    }

    /**
     * 오늘의 데일리 테스트 결과 - 문제 상세보기
     * 
     * @param userId     로그인 사용자 아이디
     * @param dayNumber  데일리 정보
     * @param questionId 문제 아이디
     * @return
     */
    public DailyResultDetailDto getDailyResultDetail(Long userId, String dayNumber, Long questionId) {
        UserActivity userActivity = userActivityRepository
                .findByUserIdAndTestInfoAndQuestionId(userId, "데일리 테스트 - " + dayNumber, questionId)
                .orElseThrow(() -> new CustomApiException("해당 문제의 풀이 결과를 찾을 수 없습니다."));

        Question question = userActivity.getQuestion();
        Skill skill = question.getSkill();

        return DailyResultDetailDto.builder()
                .skillName(skill.getKeyConcepts())
                .question(question.getQuestion())
                .option1(question.getOption1())
                .option2(question.getOption2())
                .option3(question.getOption3())
                .option4(question.getOption4())
                .answer(question.getAnswer())
                .solution(question.getSolution())
                .checked(userActivity.getChecked())
                .correction(userActivity.isCorrection())
                .build();
    }

    // 테스트용
    @Transactional
    public void completeDailyTest(Long userId, String dayNumber) {
        log.info("Completing daily test for user {} and day {}", userId, dayNumber);
        dailyPlanService.completeDailyPlan(userId, dayNumber);

        if (dayNumber.equals("Day21")) {
            log.info("Day21 completed. Updating week four plan.");
            dailyPlanService.updateWeekFourPlan(userId);
        }
        log.info("Completed daily test for user {} and day {}", userId, dayNumber);
    }
}