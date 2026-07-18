package com.blaie.blaie_be.audit.infrastructure.web;

import com.blaie.blaie_be.audit.application.AuditTrail;
import com.blaie.blaie_be.audit.domain.AuditAccess;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuditAccessFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuditAccessFilter.class);

    private final AuditRequestClassifier classifier;
    private final AuditTrail auditTrail;

    public AuditAccessFilter(AuditRequestClassifier classifier, AuditTrail auditTrail) {
        this.classifier = classifier;
        this.auditTrail = auditTrail;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<AuditAccess> access = classifier.classify(request);
        try {
            filterChain.doFilter(request, response);
        } finally {
            access.ifPresent(value -> recordSafely(value, response.getStatus()));
        }
    }

    private void recordSafely(AuditAccess access, int status) {
        try {
            auditTrail.record(access, status);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to persist access audit event: action={}, resourceType={}, status={}",
                    access.action(), access.resourceType(), status, exception
            );
        }
    }
}
