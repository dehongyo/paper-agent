package com.paperagent.service;

import com.paperagent.dto.ReviewStartEvent;
import com.paperagent.dto.ReviewStartRequest;
import com.paperagent.entity.Paper;
import com.paperagent.entity.PaperChunk;
import com.paperagent.entity.ReviewSession;
import com.paperagent.repository.PaperChunkRepository;
import com.paperagent.repository.PaperRepository;
import com.paperagent.repository.ReviewSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    @Mock
    ReviewSessionRepository sessionRepository;

    @Mock
    PaperRepository paperRepository;

    @Mock
    PaperChunkRepository chunkRepository;

    @Mock
    TemplateService templateService;

    @InjectMocks
    ReviewService reviewService;

    @Test
    void startReviewReviewsPaperSectionBySectionWithProgressAndFinalSummary() {
        Paper paper = Paper.builder()
                .id(7L)
                .title("A paper")
                .filename("paper.pdf")
                .filePath("uploads/paper.pdf")
                .status(Paper.PaperStatus.READY)
                .build();
        PaperChunk chunk = PaperChunk.builder()
                .paperId(7L)
                .chunkIndex(0)
                .content("""
                        摘要
                        本文提出一种面向知识库问答的检索增强方法。

                        引言
                        现有方法在证据可追溯性方面仍然不足。

                        系统建模
                        系统由检索器、生成器和引用校验模块组成。
                        """)
                .build();
        ReviewSession session = ReviewSession.builder()
                .id(11L)
                .paperId(7L)
                .templateId(2L)
                .status("in_progress")
                .build();

        when(paperRepository.findById(7L)).thenReturn(Optional.of(paper));
        when(chunkRepository.findByPaperIdOrderByChunkIndex(7L)).thenReturn(List.of(chunk));
        when(templateService.getTemplateContent(2L)).thenReturn("review template");
        when(sessionRepository.save(any(ReviewSession.class))).thenReturn(session);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("摘要评审：证据充分。", "引言评审：问题陈述具体。", "系统建模评审：结构清楚。", "总结性评审：整体可接受。");

        List<ReviewStartEvent> events = reviewService
                .startReview(new ReviewStartRequest(7L, 2L))
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ReviewStartEvent::type)
                .containsExactly(
                        "status",
                        "status", "status", "text",
                        "status", "text",
                        "status", "text",
                        "status", "text",
                        "done"
                );
        assertThat(events).filteredOn(e -> "status".equals(e.type()))
                .extracting(ReviewStartEvent::message)
                .contains(
                        "识别到 3 个论文章节，准备逐节评审。",
                        "正在评审：摘要 (1/3)",
                        "正在评审：引言 (2/3)",
                        "正在评审：系统建模 (3/3)",
                        "正在生成总结性评审。"
                );
        assertThat(events).filteredOn(e -> "text".equals(e.type()))
                .extracting(ReviewStartEvent::content)
                .anySatisfy(content -> assertThat(content).contains("摘要评审"))
                .anySatisfy(content -> assertThat(content).contains("总结性评审"));

        ArgumentCaptor<ReviewSession> sessionCaptor = ArgumentCaptor.forClass(ReviewSession.class);
        verify(sessionRepository, times(2)).save(sessionCaptor.capture());
        ReviewSession completedSession = sessionCaptor.getAllValues().get(1);
        assertThat(completedSession.getStatus()).isEqualTo("completed");
        assertThat(completedSession.getResultText()).contains("摘要评审", "引言评审", "系统建模评审", "总结性评审");
    }

    @Test
    void startReviewPassesPreviousSectionSummaryIntoNextSectionPrompt() {
        Paper paper = Paper.builder()
                .id(7L)
                .title("A paper")
                .filename("paper.pdf")
                .filePath("uploads/paper.pdf")
                .status(Paper.PaperStatus.READY)
                .build();
        PaperChunk chunk = PaperChunk.builder()
                .paperId(7L)
                .chunkIndex(0)
                .content("""
                        摘要
                        摘要内容。

                        引言
                        引言内容。
                        """)
                .build();
        ReviewSession session = ReviewSession.builder()
                .id(11L)
                .paperId(7L)
                .templateId(2L)
                .status("in_progress")
                .build();

        when(paperRepository.findById(7L)).thenReturn(Optional.of(paper));
        when(chunkRepository.findByPaperIdOrderByChunkIndex(7L)).thenReturn(List.of(chunk));
        when(templateService.getTemplateContent(2L)).thenReturn("review template");
        when(sessionRepository.save(any(ReviewSession.class))).thenReturn(session);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("摘要评审：摘要缺少实验对象。", "引言评审：引言回应了摘要问题。", "总评。");
        clearInvocations(chatClient.prompt());

        reviewService.startReview(new ReviewStartRequest(7L, 2L)).collectList().block();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient.prompt(), times(3)).user(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1)).contains("前面章节评审摘要", "摘要缺少实验对象");
    }
    @Test
    void startReviewRemovesOverlapWhenRebuildingPaperTextFromChunks() {
        Paper paper = Paper.builder()
                .id(7L)
                .title("A paper")
                .filename("paper.pdf")
                .filePath("uploads/paper.pdf")
                .status(Paper.PaperStatus.READY)
                .build();
        String overlap = "重复桥接内容用于跨块重叠。";
        PaperChunk firstChunk = PaperChunk.builder()
                .paperId(7L)
                .chunkIndex(0)
                .content("""
                        摘要
                        本文提出一种检索增强方法。
                        重复桥接内容用于跨块重叠。
                        """)
                .build();
        PaperChunk secondChunk = PaperChunk.builder()
                .paperId(7L)
                .chunkIndex(1)
                .content("""
                        重复桥接内容用于跨块重叠。
                        引言
                        现有方法缺少可追溯证据。
                        """)
                .build();
        ReviewSession session = ReviewSession.builder()
                .id(11L)
                .paperId(7L)
                .templateId(2L)
                .status("in_progress")
                .build();

        when(paperRepository.findById(7L)).thenReturn(Optional.of(paper));
        when(chunkRepository.findByPaperIdOrderByChunkIndex(7L)).thenReturn(List.of(firstChunk, secondChunk));
        when(templateService.getTemplateContent(2L)).thenReturn("review template");
        when(sessionRepository.save(any(ReviewSession.class))).thenReturn(session);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("摘要评审。", "引言评审。", "总结。");
        clearInvocations(chatClient.prompt());

        reviewService.startReview(new ReviewStartRequest(7L, 2L)).collectList().block();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient.prompt(), times(3)).user(promptCaptor.capture());
        assertThat(countOccurrences(promptCaptor.getAllValues().get(0), overlap)).isEqualTo(1);
    }
    @Test
    void startReviewReturnsErrorEventWhenModelReviewFails() {
        Paper paper = Paper.builder()
                .id(7L)
                .title("A paper")
                .filename("paper.pdf")
                .filePath("uploads/paper.pdf")
                .status(Paper.PaperStatus.READY)
                .build();
        PaperChunk chunk = PaperChunk.builder()
                .paperId(7L)
                .chunkIndex(0)
                .content("paper content")
                .build();
        ReviewSession session = ReviewSession.builder()
                .id(11L)
                .paperId(7L)
                .templateId(2L)
                .status("in_progress")
                .build();

        when(paperRepository.findById(7L)).thenReturn(Optional.of(paper));
        when(chunkRepository.findByPaperIdOrderByChunkIndex(7L)).thenReturn(List.of(chunk));
        when(templateService.getTemplateContent(2L)).thenReturn("review template");
        when(sessionRepository.save(any(ReviewSession.class))).thenReturn(session);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("upstream timeout"));

        List<ReviewStartEvent> events = reviewService
                .startReview(new ReviewStartRequest(7L, 2L))
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ReviewStartEvent::type).contains("status", "error");
        assertThat(events.get(events.size() - 1).message()).contains("upstream timeout");

        ArgumentCaptor<ReviewSession> sessionCaptor = ArgumentCaptor.forClass(ReviewSession.class);
        verify(sessionRepository, times(2)).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getAllValues().get(1).getStatus()).isEqualTo("error");
    }


    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
