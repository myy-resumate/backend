package dev.resumate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.resumate.apiPayload.exception.BusinessBaseException;
import dev.resumate.apiPayload.exception.ErrorCode;
import dev.resumate.common.embedding.ResumeEmbeddingService;
import dev.resumate.common.rabbitmq.EmbeddingMessageDto;
import dev.resumate.common.redis.RedisUtil;
import dev.resumate.common.redis.domain.RecentResume;
import dev.resumate.common.s3.S3Util;
import dev.resumate.converter.AttachmentConverter;
import dev.resumate.common.redis.repository.RecentResumeRepository;
import dev.resumate.converter.CoverLetterConverter;
import dev.resumate.converter.ResumeConverter;
import dev.resumate.converter.ResumeSearchConverter;
import dev.resumate.domain.*;
import dev.resumate.domain.enums.AggregateType;
import dev.resumate.domain.enums.EventStatus;
import dev.resumate.domain.rabbitmq.OutboxEvent;
import dev.resumate.dto.ResumeRequestDTO;
import dev.resumate.dto.ResumeResponseDTO;
import dev.resumate.repository.*;
import dev.resumate.repository.dto.AttachmentDTO;
import dev.resumate.repository.dto.CoverLetterDTO;
import dev.resumate.repository.dto.TagDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResumeService {

    private static final String S3_FOLDER = "attachment/";
    private static final int MAX_RECENT_RESUME = 5;  //최대 5개까지 조회

    private final ResumeRepository resumeRepository;
    private final TaggingRepository taggingRepository;
    private final TagRepository tagRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final VectorStore vectorStore;
    private final RedisUtil redisUtil;
    private final RecentResumeRepository recentResumeRepository;
    private final S3Util s3Util;
    private final AttachmentRepository attachmentRepository;
    private final ResumeEmbeddingService embeddingService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    //private final OutboxAfterCommitPublisher outboxAfterCommitPublisher;

    /**
     * 지원서 저장
     * @param member
     * @param request
     * @return
     * @throws IOException
     */
    @Transactional //하나라도 실패하면 전체 롤백
    public ResumeResponseDTO.CreateResultDTO saveResume(Member member, ResumeRequestDTO.CreateDTO request) throws IOException {

        StringBuilder questions = new StringBuilder();
        StringBuilder answers = new StringBuilder();
        List<ResumeResponseDTO.FileDTO> presignedUrlList = new ArrayList<>();
        Resume resume = ResumeConverter.toResume(request, member);

        //자소서 추가
        for (ResumeRequestDTO.CoverLetterDTO coverLetterDTO : request.getCoverLetterDTOS()) {
            resume.addCoverLetter(CoverLetterConverter.toCoverLetter(coverLetterDTO));
            questions.append(coverLetterDTO.getQuestion()).append(" ");
            answers.append(coverLetterDTO.getAnswer()).append(" ");
        }

        //첨부파일 추가
        for (ResumeRequestDTO.FileDTO file : request.getFileDTOS()) {
            String uploadKey = S3_FOLDER + resume.getTitle() + UUID.randomUUID();  //고유한 키 생성
            String presignedUrl = getPresignedUrlForS3Upload(file.getFileName(), file.getContentType(), uploadKey);
            presignedUrlList.add(buildFileDTO(file, presignedUrl));
            resume.addAttachment(buildAttachment(file, uploadKey));
        }

        //ResumeSearch 저장
        ResumeSearch resumeSearch = ResumeSearchConverter.toResumeSearch(resume, questions.toString(), answers.toString());
        resume.setResumeSearch(resumeSearch);
        Resume newResume = resumeRepository.save(resume);  //cascade로 저장

        //태그 저장은 따로
        saveTagAndTagging(request.getTags(), member, newResume);

        //redis에 최근 본 지원서로 저장
        addRecentResume(toTagDTOList(request.getTags()), newResume, member);

        //아웃박스 이벤트 저장 - 임베딩, 벡터db 저장
        outboxEventRepository.save(buildOutboxEvent(member, newResume));

        return ResumeResponseDTO.CreateResultDTO.builder()
                .resumeId(newResume.getId())
                .fileDTOS(presignedUrlList)
                .build();
    }

    private OutboxEvent buildOutboxEvent(Member member, Resume resume) throws JsonProcessingException {
        List<EmbeddingMessageDto.CoverLetterDto> coverLetterDtoList = resume.getCoverLetters().stream()
                .map(coverLetter -> EmbeddingMessageDto.CoverLetterDto.builder()
                        .coverLetterId(coverLetter.getId())
                        .question(coverLetter.getQuestion())
                        .build())
                .toList();

        return OutboxEvent.builder()
                .aggregateId(resume.getId())
                .aggregateType(AggregateType.RESUME)
                .status(EventStatus.PENDING)
                .payload(objectMapper.writeValueAsString(EmbeddingMessageDto.builder()
                        .memberId(member.getId())
                        .resumeId(resume.getId())
                        .coverLetterDtoList(coverLetterDtoList)
                        .build()))
                .build();
    }

    //최근 본 지원서 redis에 저장
    private void addRecentResume(List<TagDTO> tags, Resume resume, Member member) throws JsonProcessingException {

        ResumeResponseDTO.ReadThumbnailDTO thumbnailDTO = ResumeConverter.toReadThumbnailDTO(resume, tags);

        String jsonMember = redisUtil.toJson(thumbnailDTO);  //dto를 json으로 직렬화
        Set<String> oldestSet = redisUtil.addSortedSet(member.getId().toString(), System.currentTimeMillis() / 1000.0, resume.getId().toString(), MAX_RECENT_RESUME);

        oldestSet.forEach(oldestId -> recentResumeRepository.deleteById(Long.valueOf(oldestId)));  //5개 넘는 오래된 데이터 삭제
        recentResumeRepository.save(RecentResume.builder()
                .resumeId(resume.getId())
                .thumbnail(jsonMember)
                .build());
    }

    //태그를 dto로 변환 -> converter로 옮기기
    public List<TagDTO> toTagDTOList(List<String> tags) {
        return tags.stream().map(tag -> TagDTO.builder()
                .tagName(tag)
                .build()).toList();
    }

    //태그, 태깅 저장
    private void saveTagAndTagging(List<String> tags, Member member, Resume resume) {

        for (String tagName : tags) {
            Tag tag = saveTag(tagName, member);
            saveTagging(resume, tag);
        }
    }

    //이미 있으면 태그 반환, 없으면 저장
    private Tag saveTag(String tagName, Member member) {

        return tagRepository.findByNameAndMember(tagName, member)
                .orElseGet(() -> tagRepository.save(Tag.builder()
                        .name(tagName)
                        .member(member)
                        .build()));
    }

    private void saveTagging(Resume resume, Tag tag) {

        Tagging tagging = Tagging.builder()
                .tag(tag)
                .build();
        resume.addTagging(tagging);  //양방향 편의 메소드
        taggingRepository.save(tagging);
    }

    //첨부파일 빌더
    private static Attachment buildAttachment(ResumeRequestDTO.FileDTO file, String uploadKey) {
        return Attachment.builder()
                .fileName(file.getFileName())
                .uploadKey(uploadKey)
                .build();
    }

    //FileDTO 빌더
    private static ResumeResponseDTO.FileDTO buildFileDTO(ResumeRequestDTO.FileDTO file, String presignedUrl) {
        return ResumeResponseDTO.FileDTO.builder()
                .fileName(file.getFileName())
                .presignedUrl(presignedUrl)
                .build();
    }

    //presigned url 발급
    private String getPresignedUrlForS3Upload(String fileName, String contentType, String uploadKey) {
        if (fileName == null) {
            throw new BusinessBaseException(ErrorCode.FILE_NAME_IS_NULL);
        }
        if (contentType == null) {
            throw new BusinessBaseException(ErrorCode.CONTENT_TYPE_IS_NULL);
        }
        return s3Util.getPresignedUrl(uploadKey, contentType);
    }

    /**
     * 지원서 수정
     * @param member
     * @param resumeId
     * @param request
     * @return
     * @throws IOException
     */
    @Transactional
    public ResumeResponseDTO.UpdateResultDTO updateResume(Member member, Long resumeId, ResumeRequestDTO.UpdateDTO request) throws IOException {

        List<ResumeResponseDTO.FileDTO> presignedUrlList = new ArrayList<>();
        Resume resume = resumeRepository.findById(resumeId).orElseThrow(() -> new BusinessBaseException(ErrorCode.RESUME_NOT_FOUND));

        //벡터db에서 기존 자소서 질문 벡터 삭제
        //deleteQuestionVector(resume);

        updateCoverLetters(request.getCoverLetterDTOS(), resume);  //자소서 수정
        presignedUrlList = updateFilesWithPresignedUrl(resume, request.getFileDTOS());  //첨부 파일 수정
        resume.getResumeSearch().setResumeSearch(request);  //ResumeSearch 수정
        resume.setResume(request);  //지원서 수정
        updateTagging(request.getTags(), member, resume);  //태깅 수정

        //redis에 최근 본 지원서로 저장
        addRecentResume(request.getTags(), resume, member);

        //벡터db에 다시 저장
        //saveQuestionVector(member, resume);

        return ResumeResponseDTO.UpdateResultDTO.builder()
                .resumeId(resume.getId())
                .fileDTOS(presignedUrlList)
                .build();
    }

    //자소서 수정
    private void updateCoverLetters(List<ResumeRequestDTO.CoverLetterDTO> coverLetterDTOS, Resume resume) {
        List<CoverLetter> oldCoverLetters = coverLetterRepository.findAllByResume(resume);

        Map<Long, CoverLetter> oldCoverLettersMap = oldCoverLetters.stream().collect(Collectors.toMap(CoverLetter::getId, Function.identity()));

        //삭제 대상을 구하기 위한 set
        Set<Long> coverLetterIdsToDelete = new HashSet<>(oldCoverLettersMap.keySet());

        //기존 꺼는 수정하고, 새로운 건 추가
        for (ResumeRequestDTO.CoverLetterDTO coverLetterDTO : coverLetterDTOS) {

            if (oldCoverLettersMap.containsKey(coverLetterDTO.getCoverLetterId())) {
                CoverLetter coverLetter = oldCoverLettersMap.get(coverLetterDTO.getCoverLetterId());
                coverLetter.setQuestionAndAnswer(coverLetterDTO.getQuestion(), coverLetterDTO.getAnswer());
                coverLetterIdsToDelete.remove(coverLetterDTO.getCoverLetterId());  //삭제 대상 set에서 제거
            } else {
                addCoverLetter(resume, coverLetterDTO);
            }
        }

        //set에 남은 자소서들 삭제
        //cascade, orphanRemoval 적용한 경우엔 리스트에서 제거해줘야 한다.
        resume.getCoverLetters().removeIf(coverLetter -> coverLetterIdsToDelete.contains(coverLetter.getId()));
    }

    //자소서 수정 시 자소서 추가
    private void addCoverLetter(Resume resume, ResumeRequestDTO.CoverLetterDTO coverLetterDTO) {

        CoverLetter newCoverLetter = CoverLetter.builder()
                .question(coverLetterDTO.getQuestion())
                .answer(coverLetterDTO.getAnswer())
                .build();

        resume.addCoverLetter(newCoverLetter);
    }

    //태깅 수정
    public void updateTagging(List<TagDTO> tags, Member member, Resume resume) {

        List<Tagging> oldTaggings = taggingRepository.findAllByResume(resume);

        //Map으로 변환 - key=taggingId, value=tagging객체
        Map<Long, Tagging> oldTaggingMap = oldTaggings.stream().collect(Collectors.toMap(Tagging::getId, Function.identity()));

        //taggingId를 Set에 저장 - 삭제 대상 tagging
        Set<Long> taggingIdsToDelete = new HashSet<>(oldTaggingMap.keySet());

        for (TagDTO tagDTO : tags) {
            Tag tag = saveTag(tagDTO.getTagName(), member);

            if (oldTaggingMap.containsKey(tagDTO.getTaggingId())) {
                Tagging tagging = oldTaggingMap.get(tagDTO.getTaggingId());
                tagging.setTag(tag);  //변경감지
                taggingIdsToDelete.remove(tagDTO.getTaggingId());
            } else {
                saveTagging(resume, tag);
            }
        }
        //양방향 매핑된 resume의 tagging리스트에서도 요소 삭제
        resume.getTaggings().removeIf(tagging -> taggingIdsToDelete.contains(tagging.getId()));
        //set에 남아있는 tagging들 삭제
        taggingRepository.deleteAllById(taggingIdsToDelete);
    }

    //첨부파일 수정하고, presigned url 발급
    private List<ResumeResponseDTO.FileDTO> updateFilesWithPresignedUrl(Resume resume, List<ResumeRequestDTO.FileDTO> newAttachList) {
        List<ResumeResponseDTO.FileDTO> fileDTOS = new ArrayList<>();
        List<Attachment> oldAttachList = attachmentRepository.findAllByResume(resume);

        if (newAttachList == null) { //수정 파일이 null인 경우 기존 파일 삭제
            oldAttachList.forEach(oldAttachment -> s3Util.deleteObject(oldAttachment.getUploadKey()));
            resume.getAttachments().removeIf(oldAttachList::contains);
        } else {
            Iterator<ResumeRequestDTO.FileDTO> fileIterator = newAttachList.iterator();

            for (Attachment oldAttach : oldAttachList) {
                if (fileIterator.hasNext()) {
                    ResumeRequestDTO.FileDTO newAttach = fileIterator.next();
                    oldAttach.setFileName(newAttach.getFileName());
                    fileDTOS.add(buildFileDTO(newAttach, s3Util.getPresignedUrl(oldAttach.getUploadKey(), newAttach.getContentType())));//presigned url 발급
                } else {  //더 이상 바꿀 file이 없으면 남은 기존 파일은 삭제
                    s3Util.deleteObject(oldAttach.getUploadKey());
                    resume.getAttachments().remove(oldAttach);
                }
            }

            //기존보다 수정 파일이 많은 경우
            while (fileIterator.hasNext()) {
                ResumeRequestDTO.FileDTO file = fileIterator.next();
                String uploadKey = S3_FOLDER + resume.getTitle() + UUID.randomUUID();  //고유한 키 생성
                fileDTOS.add(buildFileDTO(file, s3Util.getPresignedUrl(uploadKey, file.getContentType())));  //presigned url 발급
                resume.addAttachment(AttachmentConverter.toAttachment(uploadKey, file.getFileName()));
            }
        }
        return fileDTOS;
    }

    /**
     * 지원서 삭제
     * @param member
     * @param resumeId
     */
    @Transactional
    public void deleteResume(Member member, Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow(() -> new BusinessBaseException(ErrorCode.RESUME_NOT_FOUND));
        //태깅은 cascade 안했으므로 따로 삭제
        taggingRepository.deleteAllByResume(resume);
        //첨부파일 s3에서 삭제
        deleteFromS3(resume);

        //벡터db에서 자소서 질문 벡터 삭제
        //deleteQuestionVector(resume);

        //redis에서 최근 지원서 삭제
        deleteRecentResume(member, resume);
        resumeRepository.deleteById(resume.getId());
    }

    //첨부파일 s3에서 삭제
    private void deleteFromS3(Resume resume) {
        //s3에서 삭제
        List<Attachment> attachments = attachmentRepository.findAllByResume(resume);
        for (Attachment attachment : attachments) {
            s3Util.deleteObject(attachment.getUploadKey());
        }
    }

    //최근 지원서 삭제
    private void deleteRecentResume(Member member, Resume resume) {
        redisUtil.deleteSortedSetMember(member.getId().toString(), resume.getId());
        recentResumeRepository.deleteById(resume.getId());
    }

    //벡터 삭제
    private void deleteQuestionVector(Resume resume) {
        List<String> Ids = resume.getCoverLetters().stream().map(coverLetter -> coverLetter.getId().toString()).toList();
        for (String id : Ids) {
            System.out.println(id);
        }
        vectorStore.delete(Ids);
    }

    /**
     * 지원서 상세조회
     * @param member
     * @param resumeId
     * @return
     * @throws JsonProcessingException
     */
    public ResumeResponseDTO.ReadResultDTO readResume(Member member, Long resumeId) throws JsonProcessingException {

        Resume resume = resumeRepository.findById(resumeId).orElseThrow(() -> new BusinessBaseException(ErrorCode.RESUME_NOT_FOUND));
        List<CoverLetterDTO> coverLetterDTOS = resumeRepository.findCoverLetter(resumeId);
        List<AttachmentDTO> attachmentDTOS = resumeRepository.findAttachment(resumeId);
        List<TagDTO> tagDTOS = resumeRepository.findTag(resumeId);

        //redis에 최근 본 지원서로 저장
        addRecentResume(tagDTOS, resume, member);

        return ResumeConverter.toReadResultDTO(resume, coverLetterDTOS, attachmentDTOS, tagDTOS);
    }

    /**
     * 지원서 목록조회
     * @param member
     * @param pageable
     * @return
     */
    public Slice<ResumeResponseDTO.ReadThumbnailDTO> readResumeList(Member member, Pageable pageable) {

        Slice<Resume> resumes = resumeRepository.findAllResume(member, pageable);
        return ResumeConverter.mapReadThumbnailDTO(resumes);
    }

    /**
     * 태그 기반 검색
     * @param member
     * @param tags
     * @param pageable
     * @return
     */
    public Slice<ResumeResponseDTO.ReadThumbnailDTO> getResumesByTags(Member member, List<String> tags, Pageable pageable) {

        Slice<Resume> resumes = resumeRepository.findByTag(member, tags, pageable);
        return ResumeConverter.mapReadThumbnailDTO(resumes);
    }

    /**
     * 지원서 검색
     * @param member
     * @param keyword
     * @param pageable
     * @return
     */
    public Slice<ResumeResponseDTO.ReadThumbnailDTO> getResumesByKeyword(Member member, String keyword, Pageable pageable) {

        Slice<Resume> resumes = resumeRepository.findByKeyword(member.getId(), keyword, pageable);
        return ResumeConverter.mapReadThumbnailDTO(resumes);
    }
}
