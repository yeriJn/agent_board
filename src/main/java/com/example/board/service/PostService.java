package com.example.board.service;

import com.example.board.entity.Post;
import com.example.board.repository.PostRepository;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class PostService {

    private static final int PAGE_SIZE = 10;

    private final PostRepository repository;
    private final Validator validator;

    public PostService(PostRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public Page<Post> list(int page) {
        return repository.findAll(PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));
    }

    public Post get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("게시글이 없습니다."));
    }

    public void create(Post post) {
        repository.save(post);
    }

    public void update(Long id, Post form) {
        Post post = get(id);
        post.setTitle(form.getTitle());
        post.setName(form.getName());
        post.setContent(form.getContent());
        repository.save(post);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** title 또는 content에 word가 포함된 글 id 목록 */
    public List<Long> searchIdsByWord(String word) {
        return repository.findIdsByWord(word);
    }

    /**
     * 지정한 글들의 title/content에서 before를 after로 변경. 없는 id는 건너뛰고, 변경된 글 수를 반환.
     * 하나라도 Post 검증(길이/공백)에 걸리면 아무 것도 저장하지 않고 IllegalArgumentException을 던진다.
     */
    @Transactional
    public int replaceWord(List<Long> ids, String before, String after) {
        Map<Long, Post> changes = new LinkedHashMap<>();
        for (Long id : ids) {
            Optional<Post> found = repository.findById(id);
            if (found.isEmpty()) {
                continue;
            }
            Post post = found.get();
            Post form = new Post();
            form.setTitle(post.getTitle().replace(before, after));
            form.setName(post.getName());
            form.setContent(post.getContent().replace(before, after));
            validator.validate(form).stream().findFirst().ifPresent(v -> {
                throw new IllegalArgumentException("글 #" + id + ": " + v.getMessage());
            });
            changes.put(id, form);
        }
        changes.forEach(this::update);
        return changes.size();
    }

    public Optional<Post> findLatest() {
        return repository.findFirstByOrderByIdDesc();
    }

    /** 이미 삭제된 글이면 false */
    public boolean deleteIfExists(Long id) {
        return repository.deleteByPostId(id) > 0;
    }

    /** 최신 글 삭제. 글이 없거나 이미 삭제되었으면 false */
    public boolean deleteLatest() {
        return findLatest().map(post -> deleteIfExists(post.getId())).orElse(false);
    }
}
