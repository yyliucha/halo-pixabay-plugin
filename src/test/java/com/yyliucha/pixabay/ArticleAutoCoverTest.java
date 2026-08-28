package com.yyliucha.pixabay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.content.Post;
import run.halo.app.event.post.PostPublishedEvent;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@ExtendWith(MockitoExtension.class)
class ArticleAutoCoverTest {

    @Mock
    private PixabayDownloadService downloadService;
    @Mock
    private ReactiveExtensionClient extensionClient;
    @Mock
    private ReactiveSettingFetcher settingFetcher;

    private ArticleAutoCover autoCover;

    @BeforeEach
    void setUp() {
        autoCover = new ArticleAutoCover(downloadService, extensionClient, settingFetcher);
    }

    private Post post(String name, String title, List<String> tags, String cover) {
        Post post = new Post();
        post.setMetadata(new Metadata());
        post.getMetadata().setName(name);
        post.setSpec(new Post.PostSpec());
        post.getSpec().setTitle(title);
        post.getSpec().setTags(tags);
        post.getSpec().setCover(cover);
        return post;
    }

    @Test
    void appliesCoverFromTagsWhenEnabled() {
        when(settingFetcher.fetch(eq("basic"), eq(PixabaySetting.class)))
            .thenReturn(Mono.just(new PixabaySetting("k", "mountain", 5, "original", "photo",
                "", "", true, "0 12 27 * *", true, List.of())));
        Post post = post("p1", "My post", List.of("mountain", "sunset"), null);
        when(extensionClient.fetch(eq(Post.class), eq("p1"))).thenReturn(Mono.just(post));
        when(downloadService.downloadForCover(eq("mountain sunset")))
            .thenReturn(Mono.just("https://example.com/cover.jpg"));
        when(extensionClient.update(any(Post.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(autoCover.handle(new PostPublishedEvent(this, "p1")))
            .verifyComplete();

        var captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(extensionClient).update(captor.capture());
        assertEquals("https://example.com/cover.jpg", captor.getValue().getSpec().getCover());
    }

    @Test
    void skipsWhenFeatureDisabled() {
        when(settingFetcher.fetch(eq("basic"), eq(PixabaySetting.class)))
            .thenReturn(Mono.just(new PixabaySetting("k", "mountain", 5, "original", "photo",
                "", "", true, "0 12 27 * *", false, List.of())));

        StepVerifier.create(autoCover.handle(new PostPublishedEvent(this, "p2")))
            .verifyComplete();

        verify(downloadService, never()).downloadForCover(anyString());
        verify(extensionClient, never()).fetch(eq(Post.class), anyString());
        verify(extensionClient, never()).update(any(Post.class));
    }

    @Test
    void skipsWhenPostAlreadyHasCover() {
        when(settingFetcher.fetch(eq("basic"), eq(PixabaySetting.class)))
            .thenReturn(Mono.just(new PixabaySetting("k", "mountain", 5, "original", "photo",
                "", "", true, "0 12 27 * *", true, List.of())));
        Post post = post("p3", "My post", List.of("mountain"), "https://example.com/old.jpg");
        when(extensionClient.fetch(eq(Post.class), eq("p3"))).thenReturn(Mono.just(post));

        StepVerifier.create(autoCover.handle(new PostPublishedEvent(this, "p3")))
            .verifyComplete();

        verify(downloadService, never()).downloadForCover(anyString());
        verify(extensionClient, never()).update(any(Post.class));
    }
}
