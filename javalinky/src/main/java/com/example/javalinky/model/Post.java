package com.example.javalinky.model;

import com.example.javalinky.util.Markdown;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户写的原文（Markdown） */
    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** 渲染并清洗之后的 HTML，见本章"富文本"一节 */
    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Comment> comments = new java.util.ArrayList<>();

    protected Post() {}

    public Post(String body, User author) {
        this.body = body;
        this.author = author;
    }

    /**
     * JPA 生命周期回调：在 INSERT 和 UPDATE 之前自动渲染 Markdown。
     * 相当于原书用 SQLAlchemy 的 db.event.listen(Post.body, 'set', ...) 做的事。
     */
    @PrePersist
    @PreUpdate
    private void renderBody() {
        this.bodyHtml = Markdown.toSafeHtml(body);
    }

    public Long getId() { return id; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getBodyHtml() { return bodyHtml; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public User getAuthor() { return author; }
    public java.util.List<Comment> getComments() { return comments; }
}
