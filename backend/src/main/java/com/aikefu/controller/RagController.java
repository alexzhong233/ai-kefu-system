package com.aikefu.controller;

import com.aikefu.dto.DocumentUploadResponse;
import com.aikefu.dto.QueryRequest;
import com.aikefu.entity.RagChunk;
import com.aikefu.entity.RagDocument;
import com.aikefu.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {
    
    private final RagService ragService;
    
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new DocumentUploadResponse(null, null, "failed", 0, "File is empty"));
            }
            

            DocumentUploadResponse response = ragService.uploadDocument(file);

            return ResponseEntity.ok(response);
        } catch (Exception e) {

            return ResponseEntity.internalServerError()
                .body(new DocumentUploadResponse(null, file != null ? file.getOriginalFilename() : null, "failed", 0, "Error: " + e.getMessage()));
        }
    }
    
    @PostMapping("/query")
    public ResponseEntity<List<Map<String, Object>>> queryRag(@RequestBody QueryRequest request) {
        try {
            List<Map<String, Object>> results = ragService.queryRag(request);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/documents")
    public ResponseEntity<List<RagDocument>> getAllDocuments() {
        try {
            List<RagDocument> documents = ragService.getAllDocuments();
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/documents/{documentId}")
    public ResponseEntity<RagDocument> getDocument(@PathVariable String documentId) {
        try {
            RagDocument document = ragService.getDocumentById(documentId);
            if (document != null) {
                return ResponseEntity.ok(document);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/documents/{documentId}/chunks")
    public ResponseEntity<List<RagChunk>> getDocumentChunks(@PathVariable String documentId) {
        try {
            List<RagChunk> chunks = ragService.getChunksByDocumentId(documentId);
            return ResponseEntity.ok(chunks);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String documentId) {
        try {
            boolean success = ragService.deleteDocument(documentId);
            Map<String, Object> response = Map.of("success", success, "documentId", documentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = ragService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
