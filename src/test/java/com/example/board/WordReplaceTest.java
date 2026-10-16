package com.example.board;

import com.example.board.entity.Post;
import com.example.board.repository.PostRepository;
import com.example.board.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:wordtestdb;DB_CLOSE_DELAY=-1")
class WordReplaceTest {

    @LocalServerPort
    int port;

    @Autowired
    PostService service;

    @Autowired
    PostRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private Long save(String title, String content) {
        Post p = new Post();
        p.setTitle(title);
        p.setName("kim");
        p.setContent(content);
        return repository.save(p).getId();
    }

    @Test
    void 정상_치환은_모든_글에_반영된다() {
        Long a = save("kfc 후기", "kfc 맛집");
        Long b = save("야식", "오늘도 kfc");

        int changed = service.replaceWord(List.of(a, b), "kfc", "bhc");

        assertThat(changed).isEqualTo(2);
        assertThat(repository.findById(a).orElseThrow().getTitle()).isEqualTo("bhc 후기");
        assertThat(repository.findById(b).orElseThrow().getContent()).isEqualTo("오늘도 bhc");
    }

    @Test
    void 없는_id는_건너뛴다() {
        Long a = save("kfc", "내용");

        int changed = service.replaceWord(List.of(a, 9999L), "kfc", "bhc");

        assertThat(changed).isEqualTo(1);
    }

    @Test
    void 뒤쪽_글이_제목_100자를_넘으면_앞쪽_글도_바뀌지_않는다() {
        Long ok = save("kfc 후기", "kfc 맛집");
        Long tooLong = save("kfc " + "가".repeat(95), "내용");

        assertThatThrownBy(() -> service.replaceWord(List.of(ok, tooLong), "kfc", "케이에프씨치킨프랜차이즈"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("#" + tooLong);

        assertThat(repository.findById(ok).orElseThrow().getTitle()).isEqualTo("kfc 후기");
        assertThat(repository.findById(ok).orElseThrow().getContent()).isEqualTo("kfc 맛집");
        assertThat(repository.findById(tooLong).orElseThrow().getTitle()).startsWith("kfc ");
    }

    @Test
    void 바꿀_단어를_비워_제목이_공백이_되면_전체가_취소된다() {
        Long ok = save("kfc 후기", "kfc 맛집");
        Long blank = save("kfc", "내용");

        assertThatThrownBy(() -> service.replaceWord(List.of(ok, blank), "kfc", ""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(repository.findById(ok).orElseThrow().getTitle()).isEqualTo("kfc 후기");
        assertThat(repository.findById(blank).orElseThrow().getTitle()).isEqualTo("kfc");
    }

    @Test
    void API는_검증_실패를_400과_메시지로_돌려주고_아무것도_바꾸지_않는다() {
        Long ok = save("kfc 후기", "kfc 맛집");
        Long tooLong = save("kfc " + "가".repeat(95), "내용");
        RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        client.post().uri("/testReplace")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("ids=" + ok + "," + tooLong + "&before=kfc&after=" + "x".repeat(30))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(repository.findById(ok).orElseThrow().getTitle()).isEqualTo("kfc 후기");
    }
}
