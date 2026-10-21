package com.example.javalinky;

import com.example.javalinky.model.Permission;
import com.example.javalinky.model.Role;
import com.example.javalinky.model.User;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    @Test
    void 位标志能正确往返转换() {
        var set = EnumSet.of(Permission.FOLLOW, Permission.WRITE);
        int bits = Permission.toBits(set);

        assertThat(bits).isEqualTo(1 + 4);
        assertThat(Permission.fromBits(bits)).isEqualTo(set);
    }

    @Test
    void 普通用户不能协管评论() {
        var role = new Role("普通用户", EnumSet.of(
            Permission.FOLLOW, Permission.COMMENT, Permission.WRITE));
        var user = new User("u@example.com", "u");
        user.setRole(role);

        assertThat(user.can(Permission.WRITE)).isTrue();
        assertThat(user.can(Permission.MODERATE)).isFalse();
        assertThat(user.isAdministrator()).isFalse();
    }

    @Test
    void 管理员拥有全部权限() {
        var role = new Role("管理员", EnumSet.allOf(Permission.class));
        var user = new User("a@example.com", "a");
        user.setRole(role);

        for (Permission p : Permission.values()) {
            assertThat(user.can(p)).as("管理员应该有 %s", p).isTrue();
        }
    }

    @Test
    void 没有角色的用户什么都不能干() {
        var user = new User("x@example.com", "x");    // role 为 null
        for (Permission p : Permission.values()) {
            assertThat(user.can(p)).isFalse();
        }
    }

    @Test
    void 增删权限() {
        var role = new Role("测试", Set.of());
        assertThat(role.has(Permission.WRITE)).isFalse();

        role.add(Permission.WRITE);
        role.add(Permission.WRITE);       // 加两次应该等于加一次
        assertThat(role.has(Permission.WRITE)).isTrue();
        assertThat(role.getPermissionBits()).isEqualTo(Permission.WRITE.bit());

        role.remove(Permission.WRITE);
        assertThat(role.has(Permission.WRITE)).isFalse();
    }
}
