package com.kdd.document.repository;

import java.time.LocalDateTime;

public interface PopularDocumentProjection {
    Long getId();
    String getTitle();
    String getCategoryName();
    int getViewCount();
    int getReferenceCount();
    int getPopularityScore();
    LocalDateTime getUpdatedAt();
}
