package com.yyliucha.pixabay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Tests for {@link PixabayDownloadService}: settings validation, global dedupe
 * and history recording (same scenarios as the Python end-to-end test).
 */
@ExtendWith(MockitoExtension.class)
class PixabayDownloadServiceTest {

    @Mock
    private PixabayClient pixabayClient;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private ReactiveExtensionClient extensionClient;
    @Mock
    private ReactiveSettingFetcher settingFetcher;

    private PixabayDownloadService service;

    private static final String POLICY = "default-policy";

    @BeforeEach
    void setUp() {
        service = new PixabayDownloadService(pixabayClient, attachmentService, extensionClient,
            settingFetcher);
    }

    private PixabaySetting setting(String keywords, int count) {
        return new PixabaySetting("test-key", keywords, count, "original", "photo", "", "", true,
            "0 12 27 * *", false, List.of());
    }

    private PixabayImage image(long id, boolean withOriginal) {
        return new PixabayImage(id, "mock", "https://pixabay.com/photos/" + id, "photo",
            "http://cdn.example.com/" + id + "_150.jpg",
            "http://cdn.example.com/" + id + "_640.jpg",
            "http://cdn.example.com/" + id + "_1280.jpg",
            withOriginal ? "http://cdn.example.com/" + id + ".jpg" : null, "tester");
    }

    private void mockBasics(PixabaySetting settings) {
        when(settingFetcher.fetch(eq("basic"), eq(PixabaySetting.class)))
            .thenReturn(Mono.just(settings));
        Policy policy = new Policy();
        policy.setMetadata(new Metadata());
        policy.getMetadata().setName(POLICY);
        when(extensionClient.list(eq(Policy.class), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(Mono.just(
                new run.halo.app.extension.ListResult<Policy>(0, 10, 1, List.of(policy))));
        // no record yet -> created at the end
        when(extensionClient.fetch(eq(PixabayDownloadRecord.class), anyString()))
            .thenReturn(Mono.empty());
        when(extensionClient.create(any(PixabayDownloadRecord.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    private void mockDownloadAndUpload() {
        when(pixabayClient.download(anyString())).thenReturn(Mono.just(new byte[] {1, 2, 3}));
        when(attachmentService.upload(eq(POLICY), any(), anyString(), any(Flux.class), any()))
            .thenAnswer(invocation -> {
                Attachment attachment = new Attachment();
                attachment.setMetadata(new Metadata());
                attachment.getMetadata().setName("fake-attachment");
                return Mono.just(attachment);
            });
    }

    @Test
    void firstRunDownloadsNewImagesAndRecordsHistory() {
        mockBasics(setting("mountain", 3));
        // page 1 has 3 hits, all new
        when(pixabayClient.search(eq("test-key"), eq("mountain"), eq(1), eq(3), eq("photo"),
            eq(true))).thenReturn(Mono.just(
            new PixabaySearchResponse(3, 3, List.of(image(1001, true), image(1002, false),
                image(1003, true)))));
        mockDownloadAndUpload();

        StepVerifier.create(service.runOnce(true))
            .assertNext(summary -> {
                assertEquals(3, summary.added());
                assertEquals(0, summary.failed());
            })
            .verifyComplete();

        // record saved with 3 ids
        var recordCaptor = org.mockito.ArgumentCaptor.forClass(PixabayDownloadRecord.class);
        verify(extensionClient).create(recordCaptor.capture());
        assertEquals(3, recordCaptor.getValue().getSpec().getDownloadedIds().size());
        assertTrue(recordCaptor.getValue().getSpec().getDownloadedIds()
            .containsAll(List.of("1001", "1002", "1003")));
    }

    @Test
    void secondRunSkipsAlreadyDownloadedImages() {
        // existing record with 2 downloaded ids
        PixabayDownloadRecord existing = new PixabayDownloadRecord();
        existing.setMetadata(new Metadata());
        existing.getMetadata().setName(PixabayDownloadRecord.RECORD_NAME);
        existing.getMetadata().setCreationTimestamp(java.time.Instant.parse("2026-08-27T04:00:00Z"));
        existing.getSpec().getDownloadedIds().addAll(List.of("1001", "1002"));
        when(settingFetcher.fetch(eq("basic"), eq(PixabaySetting.class)))
            .thenReturn(Mono.just(setting("mountain", 3)));
        Policy policy = new Policy();
        policy.setMetadata(new Metadata());
        policy.getMetadata().setName(POLICY);
        when(extensionClient.list(eq(Policy.class), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(Mono.just(
                new run.halo.app.extension.ListResult<Policy>(0, 10, 1, List.of(policy))));
        when(extensionClient.fetch(eq(PixabayDownloadRecord.class), anyString()))
            .thenReturn(Mono.just(existing));
        when(extensionClient.update(any(PixabayDownloadRecord.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        // page 1: 3 hits, 2 already downloaded -> only 1003 is new
        when(pixabayClient.search(eq("test-key"), eq("mountain"), eq(1), eq(3), eq("photo"),
            eq(true))).thenReturn(Mono.just(
            new PixabaySearchResponse(3, 3, List.of(image(1001, true), image(1002, true),
                image(1003, true)))));
        // still short of the target count -> the service keeps paging; page 2 exhausts
        when(pixabayClient.search(eq("test-key"), eq("mountain"), eq(2), eq(3), eq("photo"),
            eq(true))).thenReturn(Mono.just(
            new PixabaySearchResponse(0, 3, List.of())));
        mockDownloadAndUpload();

        StepVerifier.create(service.runOnce(true))
            .assertNext(summary -> assertEquals(1, summary.added()))
            .verifyComplete();

        var captor = org.mockito.ArgumentCaptor.forClass(PixabayDownloadRecord.class);
        verify(extensionClient).update(captor.capture());
        assertTrue(captor.getValue().getSpec().getDownloadedIds().contains("1003"));
    }

    @Test
    void fallsBackToNextUrlTierWhenDownloadFails() {
        mockBasics(setting("mountain", 1));
        when(pixabayClient.search(eq("test-key"), eq("mountain"), eq(1), eq(3), eq("photo"),
            eq(true))).thenReturn(Mono.just(
            new PixabaySearchResponse(1, 1, List.of(image(2001, true)))));
        // derived CDN original URL fails (403), the derived CDN large URL succeeds
        when(pixabayClient.download(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url != null && url.endsWith("2001.jpg")) {
                return Mono.error(new RuntimeException("HTTP 403 FORBIDDEN"));
            }
            return Mono.just(new byte[] {1, 2, 3});
        });
        when(attachmentService.upload(eq(POLICY), any(), anyString(), any(Flux.class), any()))
            .thenAnswer(invocation -> {
                Attachment attachment = new Attachment();
                attachment.setMetadata(new Metadata());
                attachment.getMetadata().setName("fake-attachment");
                return Mono.just(attachment);
            });

        StepVerifier.create(service.runOnce(true))
            .assertNext(summary -> assertEquals(1, summary.added()))
            .verifyComplete();

        // 2 attempts on the failing URL (retry once) + 1 success on the fallback
        verify(pixabayClient, times(3)).download(anyString());
        verify(attachmentService, times(1)).upload(eq(POLICY), any(), anyString(),
            any(Flux.class), any());
    }

    @Test
    void mappedKeywordUsesDedicatedPolicyAndGroup() {
        var settings = new PixabaySetting("test-key", "mountain,flower", 1, "original", "photo",
            "", "", true, "0 12 27 * *", false,
            List.of(new PixabaySetting.KeywordMapping("mountain", "map-policy", "map-group")));
        mockBasics(settings);
        when(extensionClient.fetch(eq(run.halo.app.core.extension.attachment.Group.class),
            anyString())).thenReturn(Mono.empty());
        when(extensionClient.create(any(run.halo.app.core.extension.attachment.Group.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(pixabayClient.search(eq("test-key"), eq("mountain"), eq(1), eq(3), eq("photo"),
            eq(true))).thenReturn(Mono.just(
            new PixabaySearchResponse(1, 1, List.of(image(3001, true)))));
        when(pixabayClient.search(eq("test-key"), eq("flower"), eq(1), eq(3), eq("photo"),
            eq(true))).thenReturn(Mono.just(
            new PixabaySearchResponse(1, 1, List.of(image(3002, true)))));
        when(pixabayClient.download(anyString())).thenReturn(Mono.just(new byte[] {1, 2, 3}));
        when(attachmentService.upload(eq("map-policy"), eq("map-group"), anyString(),
            any(Flux.class), any())).thenAnswer(invocation -> {
                Attachment attachment = new Attachment();
                attachment.setMetadata(new Metadata());
                attachment.getMetadata().setName("fake-attachment");
                return Mono.just(attachment);
            });
        when(attachmentService.upload(eq(POLICY), isNull(), anyString(), any(Flux.class),
            any())).thenAnswer(invocation -> {
                Attachment attachment = new Attachment();
                attachment.setMetadata(new Metadata());
                attachment.getMetadata().setName("fake-attachment");
                return Mono.just(attachment);
            });

        StepVerifier.create(service.runOnce(true))
            .assertNext(summary -> assertEquals(2, summary.added()))
            .verifyComplete();

        // mapped keyword uses its own policy/group; unmapped falls back to global
        verify(attachmentService).upload(eq("map-policy"), eq("map-group"), anyString(),
            any(Flux.class), any());
        verify(attachmentService).upload(eq(POLICY), isNull(), anyString(), any(Flux.class),
            any());
    }

    @Test
    void missingApiKeyFailsGracefully() {
        when(settingFetcher.fetch(eq("basic"), eq(PixabaySetting.class)))
            .thenReturn(Mono.just(new PixabaySetting("", "mountain", 3, "original", "photo", "",
                "", true, "0 12 27 * *", false, List.of())));
        StepVerifier.create(service.runOnce(true))
            .assertNext(summary -> assertTrue(summary.message().contains("API key")))
            .verifyComplete();
        verify(pixabayClient, never()).search(anyString(), anyString(), anyInt(), anyInt(),
            anyString(), anyBoolean());
    }

    @Test
    void concurrentRunIsRejected() {
        // minimal stubs: the first run hangs at search, so create() is never reached
        when(settingFetcher.fetch(eq("basic"), eq(PixabaySetting.class)))
            .thenReturn(Mono.just(setting("mountain", 3)));
        Policy policy = new Policy();
        policy.setMetadata(new Metadata());
        policy.getMetadata().setName(POLICY);
        when(extensionClient.list(eq(Policy.class), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(Mono.just(
                new run.halo.app.extension.ListResult<Policy>(0, 10, 1, List.of(policy))));
        when(extensionClient.fetch(eq(PixabayDownloadRecord.class), anyString()))
            .thenReturn(Mono.empty());
        when(pixabayClient.search(anyString(), anyString(), anyInt(), anyInt(), anyString(),
            anyBoolean())).thenReturn(Mono.never()); // first run hangs, keeping the running flag
        var first = service.runOnce(true).subscribe();
        StepVerifier.create(service.runOnce(true))
            .assertNext(summary -> assertTrue(summary.message().contains("already running")))
            .verifyComplete();
        first.dispose();
    }
}
