package dev.resumate.dto;

import dev.resumate.domain.Attachment;
import dev.resumate.domain.CoverLetter;
import dev.resumate.repository.dto.AttachmentDTO;
import dev.resumate.repository.dto.CoverLetterDTO;
import dev.resumate.repository.dto.TagDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ResumeResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateResultDTO {
        private Long resumeId;
        List<FileDTO> fileDTOS;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileDTO {
        private String fileName;
        private String contentType;
        private String presignedUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateResultDTO {

        private Long resumeId;
        List<FileDTO> fileDTOS;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ReadResultDTO {

        //지원서
        private String title;
        private LocalDateTime createdAt;
        private String org;
        private String orgUrl;
        private LocalDate applyStart;
        private LocalDate applyEnd;

        //태그
        private List<TagDTO> tags;

        //첨부파일
        private List<AttachmentDTO> attachments;

        //자소서
        private List<CoverLetterDTO> coverLetters;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ReadThumbnailDTO {

        private Long resumeId;
        private String title;
        private LocalDate createDate;
        private String organization;
        private LocalDate applyStart;
        private LocalDate applyEnd;
        private List<TagDTO> tags;

        public void setTags(List<TagDTO> tags) {
            this.tags = tags;
        }
    }


}
