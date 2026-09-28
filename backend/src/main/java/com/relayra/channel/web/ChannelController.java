package com.relayra.channel.web;

import com.relayra.channel.ChannelService;
import com.relayra.channel.dto.ChannelResponse;
import com.relayra.channel.dto.CreateChannelRequest;
import com.relayra.channel.dto.ReorderChannelsRequest;
import com.relayra.channel.dto.UpdateChannelRequest;
import com.relayra.common.error.DomainException;
import com.relayra.common.error.ErrorCodes;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChannelController {

  private final ChannelService channels;

  public ChannelController(ChannelService channels) {
    this.channels = channels;
  }

  @PostMapping("/communities/{communityId}/channels")
  public ResponseEntity<ChannelResponse> create(
      @PathVariable UUID communityId,
      @Valid @RequestBody CreateChannelRequest request,
      Authentication authentication) {
    ChannelResponse created = channels.create(requireCaller(authentication), communityId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/communities/{communityId}/channels")
  public ResponseEntity<List<ChannelResponse>> list(
      @PathVariable UUID communityId, Authentication authentication) {
    return ResponseEntity.ok(channels.list(requireCaller(authentication), communityId));
  }

  @PatchMapping("/channels/{channelId}")
  public ResponseEntity<ChannelResponse> update(
      @PathVariable UUID channelId,
      @Valid @RequestBody UpdateChannelRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(channels.update(requireCaller(authentication), channelId, request));
  }

  @DeleteMapping("/channels/{channelId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID channelId, Authentication authentication) {
    channels.delete(requireCaller(authentication), channelId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/communities/{communityId}/channels/reorder")
  public ResponseEntity<List<ChannelResponse>> reorder(
      @PathVariable UUID communityId,
      @Valid @RequestBody ReorderChannelsRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        channels.reorder(requireCaller(authentication), communityId, request));
  }

  private UUID requireCaller(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
      throw new DomainException(
          401, ErrorCodes.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
    return userId;
  }
}
