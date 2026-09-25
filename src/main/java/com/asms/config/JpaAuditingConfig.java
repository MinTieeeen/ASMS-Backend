package com.asms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so that {@code created_at} and {@code updated_at} of
 * {@link com.asms.entity.base.BaseEntity} are filled automatically.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
