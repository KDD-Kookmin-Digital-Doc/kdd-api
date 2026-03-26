package com.kdd.domain.document.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "document_categories", indexes = {
        @Index(columnList = "parent_id"),
        @Index(columnList = "parent_id, sort_order")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private DocumentCategory parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<DocumentCategory> children = new ArrayList<>();

    @Column(nullable = false)
    private int depth;

    @Column(nullable = false)
    private int sortOrder;

    @Builder
    public DocumentCategory(String name, DocumentCategory parent, int depth, int sortOrder) {
        this.name = name;
        this.parent = parent;
        this.depth = depth;
        this.sortOrder = sortOrder;
    }
}
