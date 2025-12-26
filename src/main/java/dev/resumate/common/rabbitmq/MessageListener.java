package dev.resumate.common.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@RabbitListener(queues = "${rabbitmq.queue}")  //지정한 큐를 구독
public class MessageListener {
    private final VectorStore vectorStore;

    //수신한 메시지에 대해 처리
    @RabbitHandler
    public void handleMessageAndSaveVector(EmbeddingMessageDto message) {
        log.info("Received message: {}", message);
        if (message.getCoverLetterDtoList().isEmpty()) {
            return;
        }
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("member_id", message.getMemberId());
        metaData.put("resume_id", message.getResumeId());
        List<Document> documentList = message.getCoverLetterDtoList().stream()
                .filter(coverLetter -> !coverLetter.getQuestion().isEmpty())  //빈 질문은 거르기
                .map(coverLetter -> {
                    metaData.put("cover_letter_id", coverLetter.getCoverLetterId());
                    return new Document(coverLetter.getCoverLetterId().toString(), coverLetter.getQuestion(), metaData);  //자소서의 id로 벡터 id 지정
                }).toList();
        vectorStore.add(documentList);
    }
}
