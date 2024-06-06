package com.qriz.sqld.dto.notice;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class noticeRespDTO {

    @Getter
    @Setter
    public static class noticeListRespDTO{
        private Long id;
        private Long userId;
        private String title;
        private String content;
        private LocalDateTime date;
    }
}
