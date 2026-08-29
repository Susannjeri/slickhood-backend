package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.helpdesk.HelpDeskModels;
import org.pms.silverocean.service.helpdesk.HelpDeskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/helpdesk")
@RequiredArgsConstructor
public class HelpDeskController {
    private final HelpDeskService service;
    private final I18NService i18n;
    @PostMapping("/conversations") public ResponseEntity<ResponseDTO> start(@Valid @RequestBody HelpDeskModels.StartConversation r){return ok(service.start(r));}
    @GetMapping("/conversations") public ResponseEntity<ResponseDTO> mine(Pageable p){return page(service.mine(p));}
    @GetMapping("/conversations/{id}") public ResponseEntity<ResponseDTO> get(@PathVariable long id){return ok(service.get(id));}
    @PostMapping("/conversations/{id}/messages") public ResponseEntity<ResponseDTO> send(@PathVariable long id,@Valid @RequestBody HelpDeskModels.SendMessage r){return ok(service.send(id,r));}
    @PostMapping("/conversations/{id}/escalate") public ResponseEntity<ResponseDTO> escalate(@PathVariable long id,@RequestBody HelpDeskModels.Escalate r){return ok(service.escalate(id,r));}
    @GetMapping("/articles") public ResponseEntity<ResponseDTO> articles(){return ok(service.publicArticles());}
    @GetMapping("/admin/conversations") @PreAuthorize("hasAuthority('list_users')") public ResponseEntity<ResponseDTO> queue(Pageable p){return page(service.queue(p));}
    @PostMapping("/admin/conversations/{id}/reply") @PreAuthorize("hasAuthority('list_users')") public ResponseEntity<ResponseDTO> reply(@PathVariable long id,@Valid @RequestBody HelpDeskModels.AgentReply r){return ok(service.agentReply(id,r));}
    @PostMapping("/admin/conversations/{id}/resolve") @PreAuthorize("hasAuthority('list_users')") public ResponseEntity<ResponseDTO> resolve(@PathVariable long id){return ok(service.resolve(id));}
    @GetMapping("/admin/articles") @PreAuthorize("hasAuthority('list_users')") public ResponseEntity<ResponseDTO> adminArticles(){return ok(service.adminArticles());}
    @PostMapping("/admin/articles") @PreAuthorize("hasAuthority('list_users')") public ResponseEntity<ResponseDTO> createArticle(@Valid @RequestBody HelpDeskModels.ArticleUpsert r){return ok(service.saveArticle(null,r));}
    @PutMapping("/admin/articles/{id}") @PreAuthorize("hasAuthority('list_users')") public ResponseEntity<ResponseDTO> updateArticle(@PathVariable long id,@Valid @RequestBody HelpDeskModels.ArticleUpsert r){return ok(service.saveArticle(id,r));}
    private ResponseEntity<ResponseDTO> ok(Object data){return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),data));}
    private ResponseEntity<ResponseDTO> page(Page<?> p){return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),p.getContent(),p.getTotalPages(),p.getTotalElements(),p.getSize()));}
}
