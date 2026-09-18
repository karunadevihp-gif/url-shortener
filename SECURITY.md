# Security Considerations & Fixes

## Overview

This document outlines the security measures implemented in the URL Shortener service and addresses potential vulnerabilities.

## Fixed Vulnerabilities

### 1. Open Redirect Prevention
**Issue**: Without validation, the service could redirect users to arbitrary URLs (e.g., `javascript:alert(1)` or `file://`), enabling phishing or malicious redirects.

**Fix**: 
- URL input is validated to accept only `https://` protocols
- Redirect handler validates the stored URL protocol before issuing the HTTP 302 response
- Invalid protocols result in a 400 Bad Request response

**Test**:
```bash
# This fails validation
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"longURL":"javascript:alert(1)"}'

# Returns: 400 {"status":400,"error":"invalid format for longURL"}
```

### 2. Information Disclosure via Error Messages
**Issue**: Raw exception details exposed in error responses leak sensitive information (e.g., stack traces, internal class names) to attackers.

**Fix**:
- GlobalExceptionHandler sanitizes error responses
- Client-facing 500 errors return a generic "Internal server error" message
- Full exception details are only logged server-side for debugging

**Test**:
```bash
curl http://localhost:8080/api/v1/nonexistent
# Returns: 404 {"status":404,"error":"couldn't find a matching long URL to redirect"}
```

### 3. Credential Exposure in Source Control
**Issue**: Database and service credentials hardcoded in `docker-compose.yml` are checked into version control, making them accessible to anyone with repo access.

**Fix**:
- Credentials moved to `.env` file (not committed)
- `.env.example` template provided for setup reference
- Docker Compose uses environment variables with `env_file` directive
- `.gitignore` updated to exclude `.env` and `.env.local`

**Usage**:
```bash
cp .env.example .env
# Edit .env with actual credentials
docker compose up --build
```

### 4. Distributed Rate Limiting Bypass
**Issue**: In multi-instance deployments, in-memory rate limiting allows 30 requests/min (10 per instance × 3 instances) instead of a true global cap of 10.

**Fix**:
- Replaced in-memory Bucket4j with Redis-backed distributed rate limiting
- Rate-limit state stored in Redis under keys like `rate_limit:<IP>`
- All instances query Redis, ensuring consistent enforcement across load balancer
- Graceful fallback to in-memory bucket if Redis is unavailable (with logging)

**Verification**:
```bash
# Rate limit enforces globally, not per instance
for i in {1..11}; do
  curl -X POST http://localhost:8080/api/v1/shorten \
    -H "Content-Type: application/json" \
    -d "{\"longURL\":\"https://example.com/page$i\"}"
done
# Requests 1-10 succeed, request 11 returns 429
```

### 5. Silent Failure in Redis Integration
**Issue**: If Redis connection fails during initialization, the exception is silently caught with no logging, making it invisible to operators that the rate limiter has degraded to in-memory mode.

**Fix**:
- Added explicit logging when Redis connection fails
- Exception details logged at WARN level with Redis host/port
- Operators can detect and respond to connectivity issues

**Log Example**:
```
WARN  c.t.u.c.IpRateLimitingFilter - Failed to connect to Redis at redis:6379. Falling back to in-memory rate limiting.
```

## Remaining Considerations

### Authentication & Authorization
**Current**: No authentication implemented. The service is intended for prototype/internal testing.

**For production**: Add API key or OAuth 2.0 authentication to control who can create short URLs.

### URL Content Validation
**Current**: Only protocol validation (HTTPS only).

**Best practices**:
- Optionally validate that the URL is reachable (HEAD request)
- Maintain a blacklist of malicious domains (e.g., phishing sites)
- Log all shortened URLs for auditing

### Rate Limiting Granularity
**Current**: 10 requests/min per IP.

**Considerations**:
- IP can be spoofed in proxied environments (use `X-Forwarded-For` header)
- Legitimate users behind corporate proxy may appear as single IP
- Consider user-based rate limiting with API keys for production

### Data Retention & Privacy
**Current**: URLs stored indefinitely.

**Best practices**:
- Implement TTL/expiration for short URLs
- Provide admin delete endpoints
- Log data access for compliance (GDPR, SOC 2)
- Encrypt URLs at rest and in transit

### Redis Security
**Current**: Redis runs without authentication in Docker.

**For production**:
- Enable Redis AUTH with strong passwords
- Use Redis SSL/TLS for encrypted communication
- Restrict Redis to internal network only
- Enable Redis ACLs for fine-grained access control

### Database Security
**Current**: PostgreSQL uses basic password authentication.

**For production**:
- Use strong, randomly generated passwords (handled by `.env`)
- Enable SSL for database connections
- Restrict database access to app network only
- Enable audit logging for sensitive operations
- Implement row-level security (RLS) if needed

## Testing Security

Run the security test suite:

```bash
# Valid HTTPS URL (succeeds)
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"longURL":"https://example.com"}'

# Invalid HTTP (fails)
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"longURL":"http://example.com"}'
# Returns: 400 {"status":400,"error":"invalid format for longURL"}

# Invalid FTP (fails)
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"longURL":"ftp://example.com"}'
# Returns: 400 {"status":400,"error":"invalid format for longURL"}

# Rate limit test (11th request fails)
for i in {1..11}; do curl -X POST http://localhost:8080/api/v1/shorten ...; done
```

## Deployment Checklist

- [ ] Use `.env` for all credentials (never commit secrets)
- [ ] Enable HTTPS for all external traffic (use reverse proxy/load balancer)
- [ ] Configure Redis with authentication and network isolation
- [ ] Configure PostgreSQL with strong password and network isolation
- [ ] Enable audit logging for API access and data modifications
- [ ] Set up monitoring and alerting for rate limit violations
- [ ] Implement automated backups for PostgreSQL
- [ ] Configure CORS headers if serving from different domain
- [ ] Review and test all error paths for information disclosure
- [ ] Perform penetration testing before production deployment

## References

- OWASP Top 10: https://owasp.org/www-project-top-ten/
- Spring Security Best Practices: https://spring.io/projects/spring-security
- Redis Security: https://redis.io/topics/security
- PostgreSQL Security: https://www.postgresql.org/docs/current/sql-syntax.html
