package com.kdd.document.storage;

import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 업로드된 PDF 파일을 디스크에 저장하고 다시 스트리밍으로 서빙한다.
 * storageKey는 디렉터리 경로와 분리된 파일 식별자 (경로 traversal 방지를 위해 Path.resolve 결과가 루트 아래인지 검증).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentFileStorage {

    private final DocumentStorageProperties properties;
    private Path rootDir;

    @PostConstruct
    void init() {
        this.rootDir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉터리 생성 실패: " + rootDir, e);
        }
        log.info("Document storage root: {}", rootDir);
    }

    /**
     * 파일을 저장하고 storageKey(루트 기준 상대 파일명)를 반환한다.
     * 최종 경로에 직접 쓰지 않고 임시 파일에 먼저 쓴 뒤 ATOMIC_MOVE 로 이동한다 —
     * JVM 크래시·디스크 풀 등으로 부분 쓰기가 발생해도 storageKey 와 매핑된 파일이
     * 반쪽 상태로 남지 않도록 보장 (#59).
     */
    public String store(MultipartFile file) {
        String storageKey = UUID.randomUUID() + ".pdf";
        Path target = resolveSafe(storageKey);
        Path tmp = null;
        try {
            tmp = Files.createTempFile(rootDir, ".upload-", ".pdf.part");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            return storageKey;
        } catch (IOException e) {
            // 디스크 풀 / 권한 / ATOMIC_MOVE 미지원 FS 등 원인 구분이 운영 디버깅에 필수.
            // BusinessException 으로 변환되면서 cause 가 사라지므로 여기서 원본 스택 보존.
            // 절대 경로 노출 방지를 위해 파일명만 로깅한다.
            String tmpName = tmp == null ? null : tmp.getFileName().toString();
            log.error("PDF 저장 실패: storageKey={}, tmpName={}", storageKey, tmpName, e);
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException cleanup) {
                    log.warn("업로드 임시 파일 정리 실패: tmpName={}", tmpName, cleanup);
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * DB 저장 실패 등으로 발생할 수 있는 고아 파일을 정리한다.
     * 이미 없는 파일이거나 IO 에러가 발생해도 호출자 흐름을 막지 않도록 swallow + WARN.
     */
    public void deleteIfExists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveSafe(storageKey));
        } catch (IOException e) {
            log.warn("저장 파일 정리 실패: storageKey={}", storageKey, e);
        }
    }

    /**
     * 저장된 파일의 절대 경로를 반환한다.
     * 파일 기반 스트리밍 파싱(PDFBox 등)에서 메모리 전체 로드를 피하기 위해 내부적으로만 사용.
     * <p>
     * 반환되는 절대 경로는 서버 디스크 레이아웃을 그대로 노출하므로 API 응답·로그·외부 전송에 포함하면 안 된다.
     * 경로 traversal 방어는 {@link #resolveSafe(String)}에 위임한다.
     */
    public Path getPath(String storageKey) {
        return resolveSafe(storageKey);
    }

    public Resource loadAsResource(String storageKey) {
        Path target = resolveSafe(storageKey);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.isReadable()) {
                throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private Path resolveSafe(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        Path resolved = rootDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return resolved;
    }
}
