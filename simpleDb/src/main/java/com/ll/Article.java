package com.ll;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Article {
    private final Long id;
    private final String title;
    private final String body;
    private final LocalDateTime createdDate;
    private final LocalDateTime modifiedDate;
    private final boolean isBlind;

    public Article(
            Long id,
            String title,
            String body,
            LocalDateTime createdDate,
            LocalDateTime modifiedDate,
            boolean isBlind
    ) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.createdDate = createdDate;
        this.modifiedDate = modifiedDate;
        this.isBlind = isBlind;
    }
}