package com.example.jira.invitations;

import com.example.jira.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ProjectInvitationController {
    private static final String PROJECT = "/projects/{projectId}/invitations";
    private static final String ACCOUNT = "/account/project-invitations";
    private static final String PROOF = "X-Jira-Invitation-Proof";
    private final ProjectInvitationService service;

    @PostMapping(PROJECT)
    public ResponseEntity<?> create(@PathVariable int projectId, @AuthenticationPrincipal UserPrincipal actor,
                                    @RequestBody JsonNode body) {
        if (body.size() != 2 || !body.path("email").isTextual() || !positive(body.path("roleId"))) return invalid();
        return ok(201, service.create(projectId, actor, body.path("email").asText(), null, body.path("roleId").intValue()));
    }

    /** Giữ contract body của client cũ, trả lời mời thay cho ghi project_members. */
    @PostMapping("/projects/{projectId}/members")
    public ResponseEntity<?> compatibility(@PathVariable int projectId, @AuthenticationPrincipal UserPrincipal actor,
                                           @RequestBody JsonNode body) {
        if (body.size() != 2 || !positive(body.path("userId")) || !positive(body.path("roleId"))) return invalid();
        return ok(202, service.create(projectId, actor, null, body.path("userId").intValue(), body.path("roleId").intValue()));
    }

    @GetMapping(PROJECT)
    public ResponseEntity<?> projectList(@PathVariable int projectId, @AuthenticationPrincipal UserPrincipal actor,
                                         @RequestParam(required = false) InvitationStore.State state,
                                         @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int size) {
        if (!validPage(offset, size)) return invalid();
        return ok(200, service.projectList(projectId, actor, state, offset, size));
    }

    @PostMapping(PROJECT + "/{id}/resend")
    public ResponseEntity<?> resend(@PathVariable int projectId, @PathVariable UUID id,
                                    @AuthenticationPrincipal UserPrincipal actor, @RequestBody(required = false) JsonNode body) {
        return empty(body) ? result(service.resend(projectId, id, actor)) : invalid();
    }

    @PostMapping(PROJECT + "/{id}/revoke")
    public ResponseEntity<?> revoke(@PathVariable int projectId, @PathVariable UUID id,
                                    @AuthenticationPrincipal UserPrincipal actor, @RequestBody(required = false) JsonNode body) {
        return empty(body) ? result(service.revoke(projectId, id, actor)) : invalid();
    }

    @GetMapping(ACCOUNT)
    public ResponseEntity<?> inbox(@AuthenticationPrincipal UserPrincipal actor, @RequestParam(defaultValue = "0") int offset,
                                   @RequestParam(defaultValue = "20") int size) {
        if (!validPage(offset, size)) return invalid();
        return ok(200, service.inbox(actor, offset, size));
    }

    @GetMapping(ACCOUNT + "/{id}")
    public ResponseEntity<?> preview(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal actor,
                                     @RequestParam(defaultValue = "false") boolean link,
                                     @RequestHeader(value = PROOF, required = false) String proof) {
        return ok(200, service.preview(id, actor, link || proof != null, proof));
    }

    @PostMapping(ACCOUNT + "/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal actor,
                                     @RequestParam(defaultValue = "false") boolean link,
                                     @RequestHeader(value = PROOF, required = false) String proof,
                                     @RequestBody(required = false) JsonNode body) {
        if (!empty(body)) return invalid();
        return result(service.respond(id, actor, link || proof != null, proof, true));
    }

    @PostMapping(ACCOUNT + "/{id}/decline")
    public ResponseEntity<?> decline(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal actor,
                                     @RequestParam(defaultValue = "false") boolean link,
                                     @RequestHeader(value = PROOF, required = false) String proof,
                                     @RequestBody(required = false) JsonNode body) {
        if (!empty(body)) return invalid();
        return result(service.respond(id, actor, link || proof != null, proof, false));
    }

    @ExceptionHandler(ProjectInvitationService.Rejected.class)
    ResponseEntity<?> rejected(ProjectInvitationService.Rejected error) { return result(error.result); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() { return result(new ProjectInvitationService.Result(409, "STATE_CONFLICT", null, null)); }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    ResponseEntity<?> failed() { return result(new ProjectInvitationService.Result(500, "INVITATION_UNCERTAIN", null, null)); }

    private static boolean positive(JsonNode value) { return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() > 0; }
    private static boolean empty(JsonNode body) { return body == null || body.isObject() && body.isEmpty(); }
    private static boolean validPage(int offset, int size) { return offset >= 0 && offset <= 10000 && size >= 1 && size <= 50; }
    private static ResponseEntity<?> invalid() { return result(new ProjectInvitationService.Result(400, "INVALID_INPUT", null, null)); }

    private static ResponseEntity<?> ok(int status, Object value) {
        return result(new ProjectInvitationService.Result(status, null, null, value));
    }

    private static ResponseEntity<?> result(ProjectInvitationService.Result result) {
        var response = ResponseEntity.status(result.status()).header("Cache-Control", "private, no-store");
        if (result.retryAfter() != null) response.header("Retry-After", result.retryAfter().toString());
        return result.code() != null ? response.body(Map.of("code", result.code()))
                : result.value() == null ? response.build() : response.body(result.value());
    }
}
