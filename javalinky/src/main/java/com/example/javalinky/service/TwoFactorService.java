package com.example.javalinky.service;

import com.example.javalinky.model.RecoveryCode;
import com.example.javalinky.model.User;
import com.example.javalinky.util.Passwords;
import com.example.javalinky.util.Totp;
import jakarta.persistence.EntityManager;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 两步验证的业务逻辑（第 22 章）。
 *
 * 校验这件事有两个容易漏的点，都在 verify() 里：
 *   1. 防重放 —— 一个码在 30 秒窗口里本来可以用很多次
 *   2. 恢复码只能用一次，而且要和 TOTP 走同一个入口
 */
public final class TwoFactorService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECOVERY_CODE_COUNT = 10;

    /** 恢复码的字母表：去掉了容易看错的 0/O、1/I/l */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final EntityManager em;

    public TwoFactorService(EntityManager em) {
        this.em = em;
    }

    /** 校验结果。分开是为了让调用方能给出不同的提示，也便于记日志 */
    public enum Result { OK, WRONG_CODE, REPLAYED, NOT_ENABLED }

    /**
     * 校验一个码。可以是 6 位 TOTP，也可以是一个恢复码。
     * 成功时会把状态写回（更新 lastSlot / 作废恢复码）。
     */
    public Result verify(User user, String input, Instant now) {
        if (!user.isTotpEnabled() || user.getTotpSecret() == null) return Result.NOT_ENABLED;
        if (input == null || input.isBlank()) return Result.WRONG_CODE;

        String cleaned = input.replaceAll("\\s", "").toUpperCase();

        // 先试 TOTP
        long slot = Totp.verify(user.getTotpSecret(), cleaned, now.getEpochSecond());
        if (slot != -1) {
            Long last = user.getTotpLastSlot();
            // 同一个时间片只认一次。不做这一步的话，攻击者在用户登录后的
            // 30 秒内拿到同一个码（肩窥、中间人、剪贴板）就能再登一次。
            if (last != null && slot <= last) return Result.REPLAYED;
            user.setTotpLastSlot(slot);
            return Result.OK;
        }

        // 再试恢复码
        return useRecoveryCode(user, cleaned) ? Result.OK : Result.WRONG_CODE;
    }

    /** 生成一批新的恢复码。返回<b>明文</b> —— 只有这一次机会给用户看 */
    public List<String> regenerateRecoveryCodes(User user) {
        em.createQuery("delete from RecoveryCode c where c.user = :u")
          .setParameter("u", user)
          .executeUpdate();

        var plain = new ArrayList<String>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomCode();
            plain.add(code);
            em.persist(new RecoveryCode(user, Passwords.hash(code)));
        }
        return plain;
    }

    public long unusedRecoveryCodeCount(User user) {
        return em.createQuery("""
                select count(c) from RecoveryCode c
                 where c.user = :u and c.usedAt is null
                """, Long.class)
            .setParameter("u", user)
            .getSingleResult();
    }

    /** 关掉两步验证：密钥和所有恢复码一起清掉 */
    public void disable(User user) {
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpLastSlot(null);
        em.createQuery("delete from RecoveryCode c where c.user = :u")
          .setParameter("u", user)
          .executeUpdate();
    }

    private boolean useRecoveryCode(User user, String input) {
        // 恢复码是 bcrypt 哈希存的，没法按值查 —— 只能逐个比。
        // 一个用户最多 10 条，可以接受。
        List<RecoveryCode> codes = em.createQuery("""
                select c from RecoveryCode c
                 where c.user = :u and c.usedAt is null
                """, RecoveryCode.class)
            .setParameter("u", user)
            .getResultList();

        for (RecoveryCode code : codes) {
            if (Passwords.verify(input, code.getCodeHash())) {
                code.markUsed();
                return true;
            }
        }
        return false;
    }

    /** 形如 ABCD-EFGH-JKMN */
    private static String randomCode() {
        var sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
