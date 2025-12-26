package dev.resumate.common.embedding;

import dev.resumate.common.rabbitmq.EmbeddingMessageDto;
import dev.resumate.common.rabbitmq.MessageSender;
import dev.resumate.domain.Member;
import dev.resumate.domain.Resume;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Thread.sleep;

@Component
@RequiredArgsConstructor
public class ResumeEmbeddingService {
    private final VectorStore vectorStore;
    private final MessageSender messageSender;

    //자소서 질문을 벡터db에 저장
    @Async("embeddingExecutor")
    @Retryable(  //실패 시 재시도
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void saveQuestionVector(Member member, Resume resume) {
        if (resume.getCoverLetters().isEmpty()) {
            return;
        }
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("member_id", member.getId());
        metaData.put("resume_id", resume.getId());
        List<Document> documentList = resume.getCoverLetters().stream()
                .filter(coverLetter -> !coverLetter.getQuestion().isEmpty())  //빈 질문은 거르기
                .map(coverLetter -> {
                    metaData.put("cover_letter_id", coverLetter.getId());
                    return new Document(coverLetter.getId().toString(), coverLetter.getQuestion(), metaData);  //자소서의 id로 벡터 id 지정
                }).toList();
        vectorStore.add(documentList);
    }

    @Async("embeddingExecutor")
    @Retryable(  //실패 시 재시도
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void saveQuestionVectorTest(Member member, Resume resume) {
        if (resume.getCoverLetters().isEmpty()) {
            return;
        }
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("member_id", member.getId());
        metaData.put("resume_id", resume.getId());
        List<Document> documentList = resume.getCoverLetters().stream()
                .filter(coverLetter -> !coverLetter.getQuestion().isEmpty())  //빈 질문은 거르기
                .map(coverLetter -> {
                    metaData.put("cover_letter_id", coverLetter.getId());
                    return new Document(coverLetter.getId().toString(), coverLetter.getQuestion(), metaData);  //자소서의 id로 벡터 id 지정
                }).toList();
        //vectorStore.add(documentList);
        try {
            Thread.sleep(700);  //외부 api호출 대체 700ms 지연
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    //rabbitmq 메시지 발행
    public void sendEmbeddingMessage(Member member, Resume resume) {
        List<EmbeddingMessageDto.CoverLetterDto> coverLetterDtoList = resume.getCoverLetters().stream()
                .map(coverLetter -> EmbeddingMessageDto.CoverLetterDto.builder()
                        .coverLetterId(coverLetter.getId())
                        .question(coverLetter.getQuestion())
                        .build())
                .toList();

        messageSender.sendEmbeddingMessage(EmbeddingMessageDto.builder()
                        .memberId(member.getId())
                        .resumeId(resume.getId())
                        .coverLetterDtoList(coverLetterDtoList)
                        .build());
    }
}
