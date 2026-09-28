package com.relayra.role.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "member_roles")
public class MemberRole {

  @EmbeddedId private MemberRoleId id;

  protected MemberRole() {}

  public MemberRole(MemberRoleId id) {
    this.id = id;
  }

  public MemberRoleId getId() {
    return id;
  }
}
