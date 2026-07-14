package com.couponwith.calendar;

import com.couponwith.audit.AuditService;
import com.couponwith.common.ApiException;
import com.couponwith.coupon.Coupon;
import com.couponwith.coupon.CouponRepository;
import com.couponwith.post.PostAttachment;
import com.couponwith.post.PostAttachmentRepository;
import com.couponwith.post.SharedPost;
import com.couponwith.post.SharedPostRepository;
import com.couponwith.space.SpaceMember;
import com.couponwith.space.SpaceMemberRepository;
import com.couponwith.space.SpaceRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class EventResourceService {
    private static final int MAX_LINKS = 50;

    private final CalendarEventRepository events;
    private final EventResourceLinkRepository links;
    private final SharedPostRepository posts;
    private final PostAttachmentRepository attachments;
    private final CouponRepository coupons;
    private final SpaceMemberRepository members;
    private final AuditService audits;

    public EventResourceService(CalendarEventRepository events, EventResourceLinkRepository links,
                                SharedPostRepository posts, PostAttachmentRepository attachments,
                                CouponRepository coupons, SpaceMemberRepository members, AuditService audits) {
        this.events = events;
        this.links = links;
        this.posts = posts;
        this.attachments = attachments;
        this.coupons = coupons;
        this.members = members;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public List<ResourceView> listLinkable(UUID actorId, UUID spaceId) {
        requireMembership(spaceId, actorId);
        var result = new ArrayList<ResourceView>();
        for (var post : posts.findBySpaceIdAndStatusOrderByPinnedDescUpdatedAtDesc(spaceId, "ACTIVE")) {
            result.add(postView(post));
            attachments.findByPostIdOrderByCreatedAt(post.getId()).stream().map(item -> attachmentView(item, post))
                    .forEach(result::add);
        }
        coupons.findBySpaceIdOrderByExpiresAt(spaceId).stream().map(this::couponView).forEach(result::add);
        result.sort(Comparator.comparing(ResourceView::type).thenComparing(ResourceView::title, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional(readOnly = true)
    public List<ResourceView> listEventResources(UUID actorId, UUID eventId) {
        var event = requireEvent(eventId);
        requireMembership(event.getSpaceId(), actorId);
        return links.findByEventIdOrderByCreatedAt(eventId).stream()
                .map(link -> resolve(link, event.getSpaceId()))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    @Transactional
    public List<ResourceView> replace(UUID actorId, UUID eventId, List<ResourceReference> requested) {
        var event = requireEvent(eventId);
        requireMutation(event, actorId);
        var unique = new LinkedHashSet<>(requested == null ? List.<ResourceReference>of() : requested);
        if (unique.size() > MAX_LINKS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_EVENT_RESOURCES", "일정에는 자료를 최대 50개까지 연결할 수 있습니다.");
        }
        unique.forEach(reference -> validateReference(event.getSpaceId(), reference));
        links.deleteByEventId(eventId);
        unique.forEach(reference -> links.save(new EventResourceLink(UUID.randomUUID(), eventId, reference.type(), reference.resourceId(), actorId)));
        audits.record(event.getSpaceId(), actorId, "EVENT_RESOURCES_UPDATED", "EVENT", eventId,
                event.getTitle() + " 일정 연결 자료 " + unique.size() + "개 저장", null);
        return listEventResources(actorId, eventId);
    }

    private void validateReference(UUID spaceId, ResourceReference reference) {
        if (reference == null || reference.type() == null || reference.resourceId() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_EVENT_RESOURCE", "연결할 자료 정보를 확인해 주세요.");
        }
        switch (reference.type()) {
            case POST -> requireActivePost(reference.resourceId(), spaceId);
            case ATTACHMENT -> {
                var attachment = attachments.findById(reference.resourceId()).orElseThrow(() -> notFound("첨부파일"));
                requireActivePost(attachment.getPostId(), spaceId);
            }
            case COUPON -> {
                var coupon = coupons.findById(reference.resourceId()).orElseThrow(() -> notFound("쿠폰"));
                requireSameSpace(coupon.getSpaceId(), spaceId);
            }
        }
    }

    private java.util.Optional<ResourceView> resolve(EventResourceLink link, UUID spaceId) {
        return switch (link.getType()) {
            case POST -> posts.findById(link.getPostId()).filter(post -> post.getStatus().equals("ACTIVE") && post.getSpaceId().equals(spaceId)).map(this::postView);
            case ATTACHMENT -> attachments.findById(link.getAttachmentId()).flatMap(attachment -> posts.findById(attachment.getPostId())
                    .filter(post -> post.getStatus().equals("ACTIVE") && post.getSpaceId().equals(spaceId))
                    .map(post -> attachmentView(attachment, post)));
            case COUPON -> coupons.findById(link.getCouponId()).filter(coupon -> coupon.getSpaceId().equals(spaceId)).map(this::couponView);
        };
    }

    private ResourceView postView(SharedPost post) {
        return new ResourceView(EventResourceType.POST, post.getId(), post.getTitle(), "공유글", null, null, null, null, null);
    }

    private ResourceView attachmentView(PostAttachment attachment, SharedPost post) {
        return new ResourceView(EventResourceType.ATTACHMENT, attachment.getId(), attachment.getOriginalName(),
                post.getTitle(), null, null, post.getId(), attachment.getContentType(), attachment.getSizeBytes());
    }

    private ResourceView couponView(Coupon coupon) {
        return new ResourceView(EventResourceType.COUPON, coupon.getId(), coupon.getTitle(), coupon.getBrand(),
                coupon.getStatus().name(), coupon.getExpiresAt(), null, null, null);
    }

    private SharedPost requireActivePost(UUID postId, UUID spaceId) {
        var post = posts.findById(postId).orElseThrow(() -> notFound("공유글"));
        requireSameSpace(post.getSpaceId(), spaceId);
        if (!post.getStatus().equals("ACTIVE")) throw notFound("공유글");
        return post;
    }

    private void requireSameSpace(UUID actual, UUID expected) {
        if (!actual.equals(expected)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVENT_RESOURCE_SPACE_MISMATCH", "같은 공간의 자료만 일정에 연결할 수 있습니다.");
        }
    }

    private void requireMutation(CalendarEvent event, UUID actorId) {
        var member = requireMembership(event.getSpaceId(), actorId);
        if (!event.getCreatedBy().equals(actorId) && member.getRole() != SpaceRole.OWNER && member.getRole() != SpaceRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EVENT_MUTATION_NOT_ALLOWED", "이 일정의 연결 자료를 변경할 권한이 없습니다.");
        }
    }

    private SpaceMember requireMembership(UUID spaceId, UUID actorId) {
        return members.findBySpaceIdAndUserIdAndStatus(spaceId, actorId, "ACTIVE")
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "공간을 찾을 수 없습니다."));
    }

    private CalendarEvent requireEvent(UUID eventId) {
        return events.findById(eventId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "일정을 찾을 수 없습니다."));
    }

    private ApiException notFound(String label) {
        return new ApiException(HttpStatus.NOT_FOUND, "EVENT_RESOURCE_NOT_FOUND", label + "을(를) 찾을 수 없습니다.");
    }

    public record ResourceReference(EventResourceType type, UUID resourceId) {}
    public record ResourceView(EventResourceType type, UUID resourceId, String title, String subtitle,
                               String status, Instant expiresAt, UUID postId, String contentType, Long sizeBytes) {}
}
