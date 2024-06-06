package com.qriz.sqld.controller;

import com.qriz.sqld.config.auth.LoginUser;
import com.qriz.sqld.dto.ResponseDto;
import com.qriz.sqld.service.NoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api")
public class NoticeController {

    @Autowired
    NoticeService noticeService;

    @GetMapping("/v1/notice/list")
    public ResponseEntity<?> getNoticeList(@AuthenticationPrincipal @Valid LoginUser loginUser){
        return new ResponseEntity<>(new ResponseDto<>(1, "공지사항 목록 불러오기 성공", noticeService.getNoticeList(loginUser.getUser().getId())), HttpStatus.OK);
    }
}
