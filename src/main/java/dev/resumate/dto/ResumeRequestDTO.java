package dev.resumate.dto;

import dev.resumate.repository.dto.TagDTO;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ResumeRequestDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDTO {

        private String title;
        private List<String> tags = new ArrayList<>();  //null 가능
        private String organization;
        private String orgURl;
        private LocalDate applyStart;
        private LocalDate applyEnd;
        private List<CoverLetterDTO> coverLetterDTOS = new ArrayList<>();  //null 가능
        private List<FileDTO> fileDTOS = new ArrayList<>();
    }

    @Getter
    public static class FileDTO {
        private String fileName;
        private String contentType;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateDTO {

        private String title;
        private List<TagDTO> tags = new ArrayList<>();
        private String organization;
        private String orgURl;
        private LocalDate applyStart;
        private LocalDate applyEnd;
        private List<CoverLetterDTO> coverLetterDTOS = new ArrayList<>();
        private List<Long> remainFileIds = new ArrayList<>();   //유지할 기존 파일 id
        private List<NewFileDTO> newFiles = new ArrayList<>();  //추가될 파일
    }

    @Getter
    public static class NewFileDTO {  //새 파일
        private String fileName;
        private String contentType;
    }

    @Getter
    public static class CoverLetterDTO{

        private Long coverLetterId;
        private String question;
        private String answer;
    }

}
