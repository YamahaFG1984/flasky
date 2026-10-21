package com.example.javalinky.model;

import com.example.javalinky.util.Markdown;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    /** 被协管员隐藏了。注意是"隐藏"而不是"删除"——见本章最后的讨论 */
    @Column(nullable = false)
    private boolean disabled = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    protected Comment() {}

    public Comment(String body, Post post, User author) {
        this.body = body;
        this.post = post;
        this.author = author;
    }

    /** 评论用更严格的标签白名单（第 11 章的 COMMENT_POLICY） */
    @PrePersist
    @PreUpdate
    private void renderBody() {
        this.bodyHtml = Markdown.toSafeCommentHtml(body);
    }

    public Long getId() { return id; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getBodyHtml() { return bodyHtml; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }
    public Post getPost() { return post; }
    public User getAuthor() { return author; }
}
