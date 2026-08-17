package dev.resumate.common.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingMessageDto {

    private Long memberId;
    private Long resumeId;
    private List<CoverLetterDto> coverLetterDtoList;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoverLetterDto {
        private Long coverLetterId;
        private String question;
    }
}
