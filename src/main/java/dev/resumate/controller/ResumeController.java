package dev.resumate.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.resumate.apiPayload.response.ApiResponseDTO;
import dev.resumate.common.auth.AuthUser;
import dev.resumate.domain.Member;
import dev.resumate.dto.ResumeRequestDTO;
import dev.resumate.dto.ResumeResponseDTO;
import dev.resumate.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /**
     * 지원서 저장
     * @param member
     * @param request
     * @return
     */
    @PostMapping
    public ApiResponseDTO<ResumeResponseDTO.CreateResultDTO> createResume(@AuthUser Member member,
                                                                          @RequestBody ResumeRequestDTO.CreateDTO request) throws IOException {
        return ApiResponseDTO.onSuccess(resumeService.saveResume(member, request));
    }

    /**
     * 지원서 수정
     *
     * @param resumeId
     * @param request
     * @return
     */
    @PatchMapping("/{resumeId}")
    public ApiResponseDTO<ResumeResponseDTO.UpdateResultDTO> updateResume(@AuthUser Member member,
                                                                          @PathVariable Long resumeId,
                                                                          @Valid @RequestBody ResumeRequestDTO.UpdateDTO request) throws IOException {
        return ApiResponseDTO.onSuccess(resumeService.updateResume(member, resumeId, request));
    }

    /**
     * 지원서 삭제
     *
     * @param resumeId
     * @return
     */
    @DeleteMapping("/{resumeId}")
    public ApiResponseDTO<String> deleteResume(@AuthUser Member member, @PathVariable Long resumeId) {
        resumeService.deleteResume(member, resumeId);
        return ApiResponseDTO.onSuccess("지원서 삭제 성공");
    }

    /**
     * 지원서 상세 조회
     * @param resumeId
     * @return
     */
    @GetMapping("/{resumeId}")
    public ApiResponseDTO<ResumeResponseDTO.ReadResultDTO> readResume(@AuthUser Member member, @PathVariable Long resumeId) throws JsonProcessingException {
        return ApiResponseDTO.onSuccess(resumeService.readResume(member, resumeId));
    }

    /**
     * 지원서 목록 조회
     * @param member
     * @param pageable
     * @return
     */
    @GetMapping
    public ApiResponseDTO<Slice<ResumeResponseDTO.ReadThumbnailDTO>> readResumeList(@AuthUser Member member, Pageable pageable) {  //Pageable 구현체를 생성할 필요 없이 그냥 파라미터로 받을 수 있다. spring data jpa
        return ApiResponseDTO.onSuccess(resumeService.readResumeList(member, pageable));
    }

    /**
     * 태그로 검색
     * @param tags
     * @return
     */
    @GetMapping("/tags")
    public ApiResponseDTO<Slice<ResumeResponseDTO.ReadThumbnailDTO>> searchResumesByTags(@AuthUser Member member,
                                                                                         @RequestParam(name = "tags") List<String> tags,
                                                                                         Pageable pageable) {
        return ApiResponseDTO.onSuccess(resumeService.getResumesByTags(member, tags, pageable));
    }

    /**
     * 키워드로 지원서 검색
     * @param member
     * @param keyword
     * @param pageable
     * @return
     */
    @GetMapping("/search")
    public ApiResponseDTO<Slice<ResumeResponseDTO.ReadThumbnailDTO>> searchResumesByKeyword(@AuthUser Member member,
                                                                                            @RequestParam(name = "keyword") String keyword,
                                                                                            Pageable pageable) {
        return ApiResponseDTO.onSuccess(resumeService.getResumesByKeyword(member, keyword, pageable));
    }
}
