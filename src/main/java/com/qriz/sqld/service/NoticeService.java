package com.qriz.sqld.service;

import com.qriz.sqld.domain.notice.Notice;
import com.qriz.sqld.domain.notice.NoticeRepository;
import com.qriz.sqld.dto.notice.noticeRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class NoticeService {
    private final NoticeRepository noticeRepository;

    public List<noticeRespDTO.noticeListRespDTO> getNoticeList(Long userId){
        List<Notice> noticeList = noticeRepository.findByUserId(userId);
        List<noticeRespDTO.noticeListRespDTO> resultList = new ArrayList<>();
        for(Notice notice : noticeList){
            noticeRespDTO.noticeListRespDTO dto = new noticeRespDTO.noticeListRespDTO();
            dto.setId(notice.getId());
            dto.setTitle(notice.getTitle());
            dto.setContent(notice.getContent());
            dto.setDate(notice.getDate());
            resultList.add(dto);
        }
        return resultList;
    }
}
