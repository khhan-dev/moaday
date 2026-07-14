package com.couponwith.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController @RequestMapping("/api/v1")
public class PostController {
    private final PostService service; public PostController(PostService service){this.service=service;}
    @GetMapping("/spaces/{spaceId}/posts") List<PostService.PostSummaryView> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID spaceId,@RequestParam(required=false) String query,@RequestParam(required=false) String tag,@RequestParam(defaultValue="false") boolean pinned){return service.list(userId(jwt),spaceId,query,tag,pinned);}
    @PostMapping("/spaces/{spaceId}/posts") @ResponseStatus(HttpStatus.CREATED) PostService.PostDetailView create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID spaceId,@Valid @RequestBody PostRequest request){return service.create(userId(jwt),spaceId,request.title(),request.content(),request.tags());}
    @GetMapping("/posts/{postId}") PostService.PostDetailView get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID postId){return service.get(userId(jwt),postId);}
    @PatchMapping("/posts/{postId}") PostService.PostDetailView update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID postId,@Valid @RequestBody PostRequest request){return service.update(userId(jwt),postId,request.title(),request.content(),request.tags());}
    @DeleteMapping("/posts/{postId}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID postId){service.delete(userId(jwt),postId);}
    @PostMapping("/posts/{postId}/pin") PostService.PostSummaryView pin(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID postId,@RequestBody PinRequest request){return service.pin(userId(jwt),postId,request.pinned());}
    @PostMapping(value="/posts/{postId}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) PostService.AttachmentView upload(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID postId,@RequestPart("file") MultipartFile file){return service.upload(userId(jwt),postId,file);}
    @GetMapping("/attachments/{attachmentId}/download") ResponseEntity<Resource> download(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID attachmentId){var file=service.download(userId(jwt),attachmentId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString()).body(file.resource());}
    @DeleteMapping("/attachments/{attachmentId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteAttachment(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID attachmentId){service.deleteAttachment(userId(jwt),attachmentId);}
    @PostMapping("/posts/{postId}/comments") @ResponseStatus(HttpStatus.CREATED) PostService.CommentView addComment(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID postId,@Valid @RequestBody CommentRequest request){return service.addComment(userId(jwt),postId,request.content());}
    @PatchMapping("/comments/{commentId}") PostService.CommentView updateComment(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID commentId,@Valid @RequestBody CommentRequest request){return service.updateComment(userId(jwt),commentId,request.content());}
    @DeleteMapping("/comments/{commentId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteComment(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID commentId){service.deleteComment(userId(jwt),commentId);}
    private UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    record PostRequest(@NotBlank @Size(max=160) String title,@NotBlank @Size(max=10000) String content,@Size(max=10) Set<@Size(max=40) String> tags){}
    record CommentRequest(@NotBlank @Size(max=2000) String content){}
    record PinRequest(boolean pinned){}
}
