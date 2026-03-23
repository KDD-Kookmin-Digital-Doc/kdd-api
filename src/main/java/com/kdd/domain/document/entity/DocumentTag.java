package com.kdd.domain.document.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "document_tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentTag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Builder
    public DocumentTag(String name, String code) {
        this.name = name;
        this.code = code;
    }
}
