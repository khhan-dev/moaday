package com.couponwith.audit;

import com.couponwith.common.ApiException;
import com.couponwith.identity.UserRepository;
import com.couponwith.space.SpaceMemberRepository;
import com.couponwith.space.SpaceRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository logs;
    private final SpaceMemberRepository members;
    private final UserRepository users;
    public AuditService(AuditLogRepository logs, SpaceMemberRepository members, UserRepository users){this.logs=logs;this.members=members;this.users=users;}

    @Transactional
    public void record(UUID spaceId, UUID actorId, String action, String resourceType, UUID resourceId, String summary, String reason){
        recordAt(spaceId,actorId,action,resourceType,resourceId,summary,reason,Instant.now());
    }

    @Transactional
    public void recordAt(UUID spaceId, UUID actorId, String action, String resourceType, UUID resourceId, String summary, String reason, Instant createdAt){
        logs.save(new AuditLog(UUID.randomUUID(),spaceId,actorId,action,resourceType,resourceId,limit(summary),clean(reason),createdAt));
    }

    @Transactional(readOnly=true)
    public List<AuditView> listSpace(UUID actorId, UUID spaceId){
        var member=requireMembership(spaceId,actorId);
        if(member.getRole()!=SpaceRole.OWNER&&member.getRole()!=SpaceRole.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"AUDIT_NOT_ALLOWED","관리자만 감사 기록을 볼 수 있습니다.");
        return logs.findTop100BySpaceIdOrderByCreatedAtDesc(spaceId).stream().map(this::view).toList();
    }

    @Transactional(readOnly=true)
    public List<AuditView> listResource(UUID actorId, UUID spaceId, String resourceType, UUID resourceId){
        requireMembership(spaceId,actorId);
        return logs.findTop100ByResourceTypeAndResourceIdOrderByCreatedAtDesc(resourceType,resourceId).stream()
                .filter(item->item.getSpaceId().equals(spaceId)).map(this::view).toList();
    }

    private com.couponwith.space.SpaceMember requireMembership(UUID spaceId,UUID actorId){return members.findBySpaceIdAndUserIdAndStatus(spaceId,actorId,"ACTIVE").orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SPACE_NOT_FOUND","공간을 찾을 수 없습니다."));}
    private AuditView view(AuditLog log){var actor=log.getActorId()==null?"시스템":users.findById(log.getActorId()).map(user->user.getDisplayName()).orElse("알 수 없음");return new AuditView(log.getId(),log.getSpaceId(),log.getActorId(),actor,log.getAction(),log.getResourceType(),log.getResourceId(),log.getSummary(),log.getReason(),log.getCreatedAt());}
    private String limit(String value){var clean=value==null?"작업 기록":value.trim();return clean.substring(0,Math.min(clean.length(),500));}
    private String clean(String value){if(value==null||value.isBlank())return null;var clean=value.trim();return clean.substring(0,Math.min(clean.length(),500));}
    public record AuditView(UUID id,UUID spaceId,UUID actorId,String actorName,String action,String resourceType,UUID resourceId,String summary,String reason,Instant createdAt){}
}
