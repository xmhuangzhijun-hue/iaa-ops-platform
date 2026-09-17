package com.iaaops.iam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "user_roles")
@IdClass(UserRoleEntity.Key.class)
public class UserRoleEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Column(name = "role")
    private String role;

    protected UserRoleEntity() {
    }

    public UserRoleEntity(String userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public static class Key implements Serializable {

        private String userId;
        private String role;

        public Key() {
        }

        public Key(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key && Objects.equals(userId, key.userId) && Objects.equals(role, key.role);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, role);
        }
    }
}
