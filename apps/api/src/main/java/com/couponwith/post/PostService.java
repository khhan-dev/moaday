package com.couponwith.post;

import com.couponwith.audit.AuditService;
import com.couponwith.common.ApiException;
import com.couponwith.identity.UserRepository;
import com.couponwith.notification.NotificationService;
import com.couponwith.space.SpaceMember;
import com.couponwith.space.SpaceMemberRepository;
import com.couponwith.space.SpaceRole;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PostService {
    private final SharedPostRepository posts; private final PostTagRepository tags; private final PostCommentRepository comments;
    private final PostAttachmentRepository attachments; private final SpaceMemberRepository members; private final UserRepository users;
    private final AttachmentStorage storage;
    private final NotificationService notifications;
    private final AuditService audits;
    public PostService(SharedPostRepository posts, PostTagRepository tags, PostCommentRepository comments,
                       PostAttachmentRepository attachments, SpaceMemberRepository members, UserRepository users, AttachmentStorage storage, NotificationService notifications, AuditService audits) {
        this.posts=posts;this.tags=tags;this.comments=comments;this.attachments=attachments;this.members=members;this.users=users;this.storage=storage;this.notifications=notifications;this.audits=audits;
    }

    @Transactional(readOnly = true)
    public List<PostSummaryView> list(UUID actorId, UUID spaceId, String query, String tag, boolean pinnedOnly) {
        requireMembership(spaceId, actorId);
        var normalizedQuery = clean(query); var normalizedTag = normalizeTag(tag);
        return posts.findBySpaceIdAndStatusOrderByPinnedDescUpdatedAtDesc(spaceId,"ACTIVE").stream()
                .filter(post -> !pinnedOnly || post.isPinned())
                .filter(post -> normalizedQuery == null || post.getTitle().toLowerCase(Locale.ROOT).contains(normalizedQuery.toLowerCase(Locale.ROOT)) || post.getContent().toLowerCase(Locale.ROOT).contains(normalizedQuery.toLowerCase(Locale.ROOT)))
                .filter(post -> normalizedTag == null || tagValues(post.getId()).contains(normalizedTag))
                .map(post -> summary(post, actorId)).toList();
    }

    @Transactional(readOnly = true)
    public PostDetailView get(UUID actorId, UUID postId) {
        var post=requirePost(postId); requireMembership(post.getSpaceId(),actorId);
        return detail(post,actorId);
    }

    @Transactional
    public PostDetailView create(UUID actorId, UUID spaceId, String title, String content, Set<String> inputTags) {
        requireWriter(spaceId,actorId);
        var post=posts.save(new SharedPost(UUID.randomUUID(),spaceId,actorId,title.trim(),content.trim()));
        replaceTags(post.getId(),inputTags); notifications.notifySpace(spaceId,actorId,"POST_CREATED","새 공유글",post.getTitle(),"/posts/"+post.getId()); return detail(post,actorId);
    }

    @Transactional
    public PostDetailView update(UUID actorId, UUID postId, String title, String content, Set<String> inputTags) {
        var post=requirePost(postId); requireMutation(post,actorId); post.update(title.trim(),content.trim()); replaceTags(postId,inputTags); return detail(post,actorId);
    }

    @Transactional
    public PostSummaryView pin(UUID actorId, UUID postId, boolean pinned) {
        var post=requirePost(postId); requireManager(post.getSpaceId(),actorId); post.setPinned(pinned); return summary(post,actorId);
    }

    @Transactional
    public void delete(UUID actorId, UUID postId) {
        var post=requirePost(postId); requireMutation(post,actorId);
        attachments.findByPostIdOrderByCreatedAt(postId).forEach(item->{storage.delete(item.getStorageKey());attachments.delete(item);});
        post.delete();
    }

    @Transactional
    public AttachmentView upload(UUID actorId, UUID postId, MultipartFile file) {
        var post=requirePost(postId); requireMutation(post,actorId);
        if (attachments.countByPostId(postId) >= 20) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "TOO_MANY_ATTACHMENTS", "게시글 하나에 첨부파일은 최대 20개까지 올릴 수 있습니다.");
        var stored=storage.store(file);
        var attachment=attachments.save(new PostAttachment(UUID.randomUUID(),postId,stored.originalName(),stored.contentType(),stored.sizeBytes(),stored.storageKey(),actorId));
        return AttachmentView.from(attachment);
    }

    @Transactional
    public Download download(UUID actorId, UUID attachmentId) {
        var attachment=requireAttachment(attachmentId); var post=requirePost(attachment.getPostId()); requireMembership(post.getSpaceId(),actorId);
        audits.record(post.getSpaceId(),actorId,"FILE_DOWNLOADED","ATTACHMENT",attachmentId,attachment.getOriginalName()+" 파일 열람",null);
        return new Download(attachment.getOriginalName(),attachment.getContentType(),storage.load(attachment.getStorageKey()));
    }

    @Transactional
    public void deleteAttachment(UUID actorId, UUID attachmentId) {
        var attachment=requireAttachment(attachmentId); var post=requirePost(attachment.getPostId()); requireMutation(post,actorId);
        storage.delete(attachment.getStorageKey()); attachments.delete(attachment);
    }

    @Transactional
    public CommentView addComment(UUID actorId, UUID postId, String content) {
        var post=requirePost(postId); requireWriter(post.getSpaceId(),actorId);
        var comment=comments.save(new PostComment(UUID.randomUUID(),postId,actorId,content.trim()));if(!post.getAuthorId().equals(actorId))notifications.notifyUser(post.getAuthorId(),post.getSpaceId(),"POST_COMMENT","새 댓글",post.getTitle()+"에 댓글이 달렸습니다.","/posts/"+postId);return commentView(comment,actorId,post.getSpaceId());
    }

    @Transactional
    public CommentView updateComment(UUID actorId, UUID commentId, String content) {
        var comment=requireComment(commentId); var post=requirePost(comment.getPostId()); requireCommentMutation(comment,post.getSpaceId(),actorId);
        comment.update(content.trim()); return commentView(comment,actorId,post.getSpaceId());
    }

    @Transactional
    public void deleteComment(UUID actorId, UUID commentId) {
        var comment=requireComment(commentId); var post=requirePost(comment.getPostId()); requireCommentMutation(comment,post.getSpaceId(),actorId); comment.delete();
    }

    private PostSummaryView summary(SharedPost post,UUID actorId){var author=users.findById(post.getAuthorId()).orElseThrow();return new PostSummaryView(post.getId(),post.getSpaceId(),post.getTitle(),post.getContent(),post.isPinned(),author.getId(),author.getDisplayName(),tagValues(post.getId()),attachments.findByPostIdOrderByCreatedAt(post.getId()).stream().map(AttachmentView::from).toList(),comments.countByPostIdAndStatus(post.getId(),"ACTIVE"),post.getCreatedAt(),post.getUpdatedAt(),canMutate(post,actorId));}
    private PostDetailView detail(SharedPost post,UUID actorId){var base=summary(post,actorId);return new PostDetailView(base,comments.findByPostIdAndStatusOrderByCreatedAt(post.getId(),"ACTIVE").stream().map(item->commentView(item,actorId,post.getSpaceId())).toList());}
    private CommentView commentView(PostComment comment,UUID actorId,UUID spaceId){var author=users.findById(comment.getAuthorId()).orElseThrow();var membership=requireMembership(spaceId,actorId);boolean canEdit=comment.getAuthorId().equals(actorId)||membership.getRole()==SpaceRole.OWNER||membership.getRole()==SpaceRole.ADMIN;return new CommentView(comment.getId(),author.getId(),author.getDisplayName(),comment.getContent(),comment.getCreatedAt(),comment.getUpdatedAt(),canEdit);}
    private void replaceTags(UUID postId,Set<String> input){tags.deleteByPostId(postId);normalizeTags(input).forEach(tag->tags.save(new PostTag(postId,tag)));}
    private Set<String> tagValues(UUID postId){var result=new LinkedHashSet<String>();tags.findByPostIdOrderByTag(postId).forEach(item->result.add(item.getTag()));return result;}
    private Set<String> normalizeTags(Set<String> input){var result=new LinkedHashSet<String>();if(input!=null)input.stream().map(this::normalizeTag).filter(value->value!=null&&!value.isBlank()).limit(10).forEach(result::add);return result;}
    private String normalizeTag(String value){if(value==null)return null;var tag=value.trim().replaceFirst("^#+","").toLowerCase(Locale.ROOT);return tag.isBlank()?null:tag.substring(0,Math.min(tag.length(),40));}
    private String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private void requireMutation(SharedPost post,UUID actorId){requireMembership(post.getSpaceId(),actorId);if(!canMutate(post,actorId))throw new ApiException(HttpStatus.FORBIDDEN,"POST_MUTATION_NOT_ALLOWED","게시글을 수정하거나 삭제할 권한이 없습니다.");}
    private boolean canMutate(SharedPost post,UUID actorId){var member=requireMembership(post.getSpaceId(),actorId);return post.getAuthorId().equals(actorId)||member.getRole()==SpaceRole.OWNER||member.getRole()==SpaceRole.ADMIN;}
    private void requireCommentMutation(PostComment comment,UUID spaceId,UUID actorId){var member=requireMembership(spaceId,actorId);if(!comment.getAuthorId().equals(actorId)&&member.getRole()!=SpaceRole.OWNER&&member.getRole()!=SpaceRole.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"COMMENT_MUTATION_NOT_ALLOWED","댓글을 수정하거나 삭제할 권한이 없습니다.");}
    private SpaceMember requireWriter(UUID spaceId,UUID actorId){var member=requireMembership(spaceId,actorId);if(member.getRole()==SpaceRole.VIEWER)throw new ApiException(HttpStatus.FORBIDDEN,"POST_WRITE_NOT_ALLOWED","열람자는 글이나 댓글을 작성할 수 없습니다.");return member;}
    private SpaceMember requireManager(UUID spaceId,UUID actorId){var member=requireMembership(spaceId,actorId);if(member.getRole()!=SpaceRole.OWNER&&member.getRole()!=SpaceRole.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"POST_PIN_NOT_ALLOWED","게시글을 고정할 권한이 없습니다.");return member;}
    private SpaceMember requireMembership(UUID spaceId,UUID actorId){return members.findBySpaceIdAndUserIdAndStatus(spaceId,actorId,"ACTIVE").orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SPACE_NOT_FOUND","공간을 찾을 수 없습니다."));}
    private SharedPost requirePost(UUID id){return posts.findById(id).filter(item->"ACTIVE".equals(item.getStatus())).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"POST_NOT_FOUND","게시글을 찾을 수 없습니다."));}
    private PostComment requireComment(UUID id){return comments.findById(id).filter(item->"ACTIVE".equals(item.getStatus())).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"COMMENT_NOT_FOUND","댓글을 찾을 수 없습니다."));}
    private PostAttachment requireAttachment(UUID id){return attachments.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ATTACHMENT_NOT_FOUND","첨부파일을 찾을 수 없습니다."));}

    public record AttachmentView(UUID id,String originalName,String contentType,long sizeBytes){static AttachmentView from(PostAttachment item){return new AttachmentView(item.getId(),item.getOriginalName(),item.getContentType(),item.getSizeBytes());}}
    public record CommentView(UUID id,UUID authorId,String authorName,String content,Instant createdAt,Instant updatedAt,boolean canEdit){}
    public record PostSummaryView(UUID id,UUID spaceId,String title,String content,boolean pinned,UUID authorId,String authorName,Set<String> tags,List<AttachmentView> attachments,long commentCount,Instant createdAt,Instant updatedAt,boolean canEdit){}
    public record PostDetailView(PostSummaryView post,List<CommentView> comments){}
    public record Download(String filename,String contentType,Resource resource){}
}
