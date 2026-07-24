# Báo cáo kiểm thử Inbox branch — 2026-07-22

## Kết luận

- Phạm vi: toàn bộ checklist phần 7–20, đúng **138 test case**.
- Kết quả: **135 PASS, 1 FAIL, 2 PARTIAL, 0 NOT RUN**.
- Lỗi yêu cầu chắc chắn: `OBS-08` không phân biệt retry do admin với retry thủ công của user trong metric.
- Chưa xác minh đủ end-to-end: `DUR-06` chưa gửi SIGTERM thật ở mức process; `NAV-03` chưa hoàn tất callback bằng tài khoản Google thật.
- Có một lỗi test frontend ngoài checklist: fixture idempotency dùng ngày cố định 2026-07-17 nên sau TTL 24 giờ assertion fail; runtime idempotency và các test controlled mới đều pass.

## Môi trường và bằng chứng chính

- Backend full suite: **257/257 pass**; targeted bổ sung retention, migration, durability, telemetry, concurrency và auth đều pass.
- Frontend full suite tại checkpoint: **118/119 pass**; failure duy nhất là test phụ thuộc thời gian nói trên. Targeted bổ sung IDEM-04/05: **9/9 pass**; auth/navigation: **16/16 pass**; production build sạch pass.
- Hạ tầng thật/cô lập: PostgreSQL 16/18 Testcontainers, Redis 7 Testcontainers, Flyway V1→V17, ứng dụng local 8080/8081 và frontend 3000.
- AI: đã gọi DeepSeek thật cho các flow ngắn về capture, pagination, retry và durability; controlled provider chỉ dùng ở các case cần tạo lỗi chính xác.
- Các test Java/TypeScript tạm dùng để thu bằng chứng đã được xóa sau khi chạy.

## Kết quả từng test case

### 7. Validation và giới hạn input

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| VAL-01 | PASS | UI có `maxlength=10000`; API nhận đúng 10.000 ký tự, trả 202 và workflow đi tới terminal bằng DeepSeek thật. | Không. |
| VAL-02 | PASS | API với 10.001 ký tự trả 422 `VALIDATION_ERROR`; UI chặn ở 10.000 ký tự. | Không. |
| VAL-03 | PASS | `text` rỗng, null hoặc missing đều trả 422; không phát sinh capture/job. | Không. |
| VAL-04 | PASS | Thiếu `Idempotency-Key` trả 400 `IDEMPOTENCY_KEY_REQUIRED`. | Không. |
| VAL-05 | PASS | Key không phải UUID trả 400 `IDEMPOTENCY_KEY_INVALID`. | Không. |
| VAL-06 | PASS | `GET /captures?status=completed` trả 422, không silently ignore. | Không. |
| VAL-07 | PASS | Inbox `limit=0` và `limit=51` đều trả 422. | Không. |
| VAL-08 | PASS | Cursor Inbox sửa một ký tự trả 422. | Không. |

### 8. Idempotency và retry request mơ hồ

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| IDEM-01 | PASS | Cùng text + cùng UUID key trả cùng capture; DB chỉ có 1 capture, 1 job và 1 item. | Không. |
| IDEM-02 | PASS | Cùng key nhưng text khác trả 409 `IDEMPOTENCY_KEY_REUSED`. | Không. |
| IDEM-03 | PASS | Text có whitespace ngoài và replay bằng text đã trim trả cùng capture. | Không. |
| IDEM-04 | PASS | Controlled UI test: server model tạo capture rồi làm mất response; submit lại dùng đúng key cũ và tổng số capture vẫn là 1. | Không. |
| IDEM-05 | PASS | Controlled UI test: request đầu offline không tới server; reconnect dùng đúng key cũ và tạo workflow đúng 1 lần. | Không. |
| IDEM-06 | PASS | Replay workflow đã tồn tại vẫn thành công khi active-job limit đầy; không tạo job mới. | Không. |
| IDEM-07 | PASS | Logout xóa private tracking; Back vẫn bị giữ ở login và user khác không thấy card/capture của user trước. | Không. |

### 9. Privacy và dữ liệu nhạy cảm

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| PRI-01 | PASS | GitLab token bị chặn 422 `CAPTURE_SENSITIVE_CONTENT` trước persistence. | Không. |
| PRI-02 | PASS | Slack token bị chặn 422 trước persistence. | Không. |
| PRI-03 | PASS | Database URI chứa credential bị chặn 422 trước persistence. | Không. |
| PRI-04 | PASS | Test card `4111…` bị chặn 422 trước persistence. | Không. |
| PRI-05 | PASS | SSN test bị chặn 422 trước persistence. | Không. |
| PRI-06 | PASS | Reserved placeholder marker bị chặn 422 trước persistence. | Không. |
| PRI-07 | PASS | Fake-provider integration xác nhận email/phone/IP được mask outbound và restore nguyên văn trong Inbox. | Không. |
| PRI-08 | PASS | Date/time/room bình thường được DeepSeek nhận và không bị mask nhầm. | Không. |
| PRI-09 | PASS | UI disclosure nói rõ common email/phone/IP được mask, tên và địa chỉ tự do vẫn có thể tới provider. | Không. |
| PRI-10 | PASS | Marker benign không xuất hiện trong admin/audit/metrics; test log riêng cũng xác nhận raw marker không nằm trong message/arguments. | Không. |
| PRI-11 | PASS | Sau completed nhưng chưa Delete, owner vẫn đọc được original text như thiết kế. | Không. |
| PRI-12 | PASS | Sau Delete, cả capture và item đều trả 404 khi đọc lại. | Không. |

### 10. Provider retry và manual retry

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| RET-01 | PASS | Controlled provider timeout/429/5xx một lần rồi thành công; job backoff và hoàn tất. | Không. |
| RET-02 | PASS | Lỗi retryable liên tục đi qua bounded attempts rồi thành `dead`; không loop vô hạn. | Không. |
| RET-03 | PASS | Provider chưa cấu hình được map sang failure code/class an toàn, không lộ secret. | Không. |
| RET-04 | PASS | Structured output sai được map `ai_invalid_response` và tuân theo retry policy. | Không. |
| RET-05 | PASS | Failed retryable card có Retry/Delete/Dismiss và safe message. | Không. |
| RET-06 | PASS | Sau sửa provider, Retry requeue cùng capture/job generation và cuối cùng completed. | Không. |
| RET-07 | PASS | Hai retry đồng thời: đúng một 202, một 409; retryGeneration tăng một lần và chỉ một publication. | Không. |
| RET-08 | PASS | Retry capture processing/completed trả 409 `CAPTURE_NOT_RETRYABLE`. | Không. |
| RET-09 | PASS | Content-terminal có `canRetry=false`, UI không hiện Retry và API trả 409. | Không. |
| RET-10 | PASS | Manual retry khi capacity đầy trả 429/503 + `Retry-After`; trạng thái không đổi. | Không. |
| RET-11 | PASS | Khi slot được giải phóng và qua thời gian retry, request được nhận và hoàn tất. | Không. |

### 11. Security và ownership

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| SEC-01 | PASS | Anonymous GET trả 401 JSON; anonymous mutation có CSRF hợp lệ cũng trả 401. | Mutation thiếu CSRF trả 403 trước auth do filter ordering; không có data leak. |
| SEC-02 | PASS | Unverified submit trả 403 `EMAIL_NOT_VERIFIED`; không tạo workflow. | Không. |
| SEC-03 | PASS | User B đọc capture của A nhận 404 `CAPTURE_NOT_FOUND`. | Không. |
| SEC-04 | PASS | User B retry/delete capture của A đều nhận 404; dữ liệu A không đổi. | Không. |
| SEC-05 | PASS | User B đọc item của A nhận 404 `CAPTURE_ITEM_NOT_FOUND`. | Không. |
| SEC-06 | PASS | Admin dùng user endpoint vẫn không bypass ownership và nhận 404. | Không. |
| SEC-07 | PASS | Cookie-only POST/DELETE không có `X-XSRF-TOKEN` đều bị 403. | Không. |
| SEC-08 | PASS | Bearer-only mutation hợp lệ không cần cookie CSRF; DELETE trả 204. | Không. |
| SEC-09 | PASS | `MANUAL-SEC-09` được giữ qua response, DB `origin_request_id`, admin `correlationId` và worker context. | Không. |
| SEC-10 | PASS | Request ID có space hoặc dài 129 ký tự được thay bằng UUID an toàn. | Không. |

### 12. Pagination và refresh

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| PAGE-01 | PASS | Tạo 25 items bằng DeepSeek thật; page 1 trả 20 và `hasMore=true`. | Không. |
| PAGE-02 | PASS | UI Load more tăng danh sách từ 20 lên 27 item unique. | Không. |
| PAGE-03 | PASS | Tạo item giữa hai page không làm lặp boundary; refresh đưa item mới lên đầu. | Không. |
| PAGE-04 | PASS | Cursor của user A dùng cho user B trả 422. | Không. |
| PAGE-05 | PASS | Cursor dùng chéo Inbox/admin/audit đều trả 422. | Không. |
| PAGE-06 | PASS | Refresh ba lần vẫn 27 item unique, không duplicate. | Không. |

### 13. Rate limit và admission control

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| LIM-01 | PASS | Request vượt HTTP limit trả 429 `RATE_LIMITED`, `Retry-After: 60`; invalid requests không tạo workflow. | Không. |
| LIM-02 | PASS | Per-user active-job limit trả 429 và giữ đúng số active jobs. | Không. |
| LIM-03 | PASS | Global active limit trả 503/backpressure; không vượt limit. | Không. |
| LIM-04 | PASS | Oldest queued age vượt ngưỡng chặn acceptance với retry timing an toàn. | Không. |
| LIM-05 | PASS | Redis rate-limit backend down thì fail-closed; không silently bypass limiter. | Không. |
| LIM-06 | PASS | Idempotent replay đã tồn tại vẫn được trả lại khi limit đầy, không tạo workflow thứ hai. | Không. |
| LIM-07 | PASS | Concurrent admission integration giữ số active jobs không vượt giới hạn. | Không. |
| LIM-08 | PASS | Sau cửa sổ 60 giây request mới trả 202 và hoàn tất bằng DeepSeek. | Không. |

### 14. Pause, restart và durability

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| DUR-01 | PASS | POST 202 khi worker off; kill/restart API vẫn giữ capture/job trong PostgreSQL; worker mới hoàn tất old capture. | Không. |
| DUR-02 | PASS | Worker off giữ capture processing + DB job queued + Redis record; bật lại thì queue drain. | Không. |
| DUR-03 | PASS | Redis/publisher unavailable làm outbox backlog tăng; restore làm attempts tăng, backlog về 0 và capture completed. | Không. |
| DUR-04 | PASS | Exact integration xóa Redis wake-up rồi reconciliation tăng dispatch generation, xử lý đúng một lần. | Không. |
| DUR-05 | PASS | Lease-expired job được recovery; stale worker bị fencing và không commit đè. | Không. |
| DUR-06 | PARTIAL | `shutdownStopsAdmissionAndWaitsForRunningJobWithinConfiguredBound` pass; default await là 30s, crash/lease recovery cũng pass. | Chưa gửi SIGTERM thật tới process đang ở provider call trên Windows. |
| DUR-07 | PASS | Duplicate Redis delivery bị dispatch-generation/claim fencing; không duplicate item/provider execution hợp lệ. | Không. |
| DUR-08 | PASS | Redis restart không mất durable DB job; outbox recovery republish và hoàn tất. | Không. |
| DUR-09 | PASS | Acceptance disabled: submit/retry trả 503 trước write; GET vẫn 200 và capture count không đổi. | Không. |
| DUR-10 | PASS | Worker disabled không chạy provider và không tự đánh failed durable queued work. | Không. |
| DUR-11 | PASS | Đã quan sát dependency restore → outbox recovery/drain → worker completion; acceptance switch fail-closed dùng để mở nhận việc sau cùng. | Trình tự là operational control, không phải auto-orchestrator. |

### 15. Admin operations API

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| ADM-01 | PASS | Admin API không auth trả 401 JSON. | Không. |
| ADM-02 | PASS | Regular verified user nhận 403 `FORBIDDEN`. | Không. |
| ADM-03 | PASS | Unverified admin nhận 403 `EMAIL_NOT_VERIFIED`. | Không. |
| ADM-04 | PASS | Verified admin đọc và mutation operational endpoints thành công bằng server-side admin flag. | Không. |
| ADM-05 | PASS | Jobs newest-first, chỉ có safe metadata; không originalText/payload/stack. | Không. |
| ADM-06 | PASS | Filter queued/processing/retry_wait/completed/dead chỉ trả đúng status. | Không. |
| ADM-07 | PASS | `stuck=true` chỉ trả active fixture đã quá control timestamp. | Không. |
| ADM-08 | PASS | `status=processing&stuck=true` áp dụng đồng thời cả hai filter. | Không. |
| ADM-09 | PASS | Pagination limit 2 qua hai page ổn định, không duplicate. | Không. |
| ADM-10 | PASS | Bad status/stuck/cursor và limit 0/101/non-number đều trả 422. | Admin max hợp lệ là 100; `limit=51` hợp lệ. |
| ADM-11 | PASS | GET job tồn tại trả đúng safe metadata/correlation, không raw content. | Không. |
| ADM-12 | PASS | UUID job không tồn tại trả 404 `PROCESSING_JOB_NOT_FOUND`. | Không. |
| ADM-13 | PASS | Malformed job ID với non-admin trả 403 trước validation. | Không. |
| ADM-14 | PASS | Malformed job ID với admin trả 422. | Không. |
| ADM-15 | PASS | Mark-dead queued/processing/retry_wait chuyển job dead, capture failed, clear lease và set đúng failure metadata. | Không. |
| ADM-16 | PASS | Mark-dead completed/dead trả 409 `PROCESSING_JOB_MARK_DEAD_NOT_ALLOWED`, không đổi dữ liệu. | Không. |
| ADM-17 | PASS | Operator mark-dead thắng; provider có thể xong network nhưng stale worker không commit. | Không. |
| ADM-18 | PASS | Requeue dead retryable trả 202, giữ IDs, reset attempt, tăng generation và clear failure. | Không. |
| ADM-19 | PASS | Requeue content-terminal trả 409, không bypass privacy/content policy. | Không. |
| ADM-20 | PASS | Requeue queued/processing/completed trả 409 và không tạo publication mới. | Không. |
| ADM-21 | PASS | Hai admin requeue đồng thời: một 202, một 409, đúng một outbox publication. | Không. |
| ADM-22 | PASS | Capacity đầy trả 429/503 + `Retry-After`; job vẫn dead và generation không đổi. | Không. |
| ADM-23 | PASS | Healthy summary backlog/age = 0, timestamp nullable hợp lý và không có payload. | Không. |
| ADM-24 | PASS | Publisher outage: backlog 1 và age tăng; restore làm backlog 0 và cập nhật resubmission metadata. | Không. |
| ADM-25 | PASS | Không tồn tại generic outbox purge và POST/DELETE thử nghiệm không xóa dữ liệu. | Route/method không tồn tại đang bị map 500 thay vì 404/405; là defect phụ cần sửa. |

### 16. Audit events

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| AUD-01 | PASS | Audit list có actor/admin/action/resource/outcome/requestId/time đúng; không raw content. | Không. |
| AUD-02 | PASS | Resource không tồn tại tạo audit outcome `not_found`. | Không. |
| AUD-03 | PASS | Non-admin gọi admin endpoint tạo outcome `denied` với đúng actor. | Không. |
| AUD-04 | PASS | Mutation 409/422/429 được ghi outcome `rejected`. | Không. |
| AUD-05 | PASS | Controlled HTTP 503 được audit thành `failed`. | Không. |
| AUD-06 | PASS | Hai successful read cùng bucket chỉ tăng đúng một audit row. | Không. |
| AUD-07 | PASS | Hai mutation/denied lặp lại tạo đủ hai audit rows, không deduplicate. | Không. |
| AUD-08 | PASS | Signed cursor page 2+2 không duplicate; cursor sửa trả 422. | Không. |
| AUD-09 | PASS | Raw marker không có trong audit response; không provider payload/stack. | Không. |
| AUD-10 | PASS | Audit store exception fail-open: business response vẫn 200; log chỉ có safe metadata. | Không. |

### 17. Prometheus, health và vận hành

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| OBS-01 | PASS | Liveness/readiness/prometheus trên 8081 trả 200, không cần app auth. | Không. |
| OBS-02 | PASS | Actuator trên app port 8080 không expose; anonymous nhận API auth JSON, không actuator payload. | Không. |
| OBS-03 | PASS | Management listener bind `127.0.0.1:8081`; app listener riêng 8080. | Không. |
| OBS-04 | PASS | Healthy idle: DB/Redis source_up=1; leases/outbox/pending=0; usage 0 ≤ limit 1. | Không. |
| OBS-05 | PASS | Real capture làm job/provider timer count 70→71 và queue cuối cùng drain về 0. | Không. |
| OBS-06 | PASS | Router + processor + Micrometer tests xác nhận provider error và `retry{source=automatic}` tăng với bounded tags, không resource ID/text. | Không. |
| OBS-07 | PASS | Mark-dead làm `capture_dead_total` tăng với safe failure_class/source. | Không. |
| OBS-08 | FAIL | Admin requeue hiện tăng `RetrySource.MANUAL`; metric chỉ có `source=manual`. | Không phân biệt `admin` với user/manual như checklist yêu cầu. |
| OBS-09 | PASS | Lease recovery làm stale-recovered counter tăng. | Không. |
| OBS-10 | PASS | Lost Redis record/reconciliation làm queued-redispatched counter tăng. | Không. |
| OBS-11 | PASS | Redis concurrency integration giữ limit=1 qua renewal/TTL; waiter bị block và wait timer được ghi. | Không. |
| OBS-12 | PASS | DB down làm source_up(db)=0; last-success và last-good gauges không reset zero giả. | Không. |
| OBS-13 | PASS | Redis down làm last-success ngừng; source_up(redis)=0 sau command timeout; restore thành công. | Detection latency khoảng 60s cần ghi trong runbook. |
| OBS-14 | PASS | Exact log test có requestId/eventId/jobId/captureId/dispatchGeneration/attempt/provider/providerAttemptId; raw marker không nằm trong log. | Không. |

### 18. Retention

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| RTN-01 | PASS | Old completed processing job bị xóa theo cutoff nhưng capture vẫn còn. | Không. |
| RTN-02 | PASS | Old completed outbox bị xóa; fresh hoặc incomplete publication được giữ. | Không. |
| RTN-03 | PASS | Old audit bị xóa; fresh audit được giữ. | Không. |
| RTN-04 | PASS | Dead/queued fixtures được giữ; cleanup predicate không chọn processing/retry_wait. | Không. |
| RTN-05 | PASS | Test tạm làm một category throw: scheduler không văng, category khác vẫn chạy và category lỗi retry ở lượt sau. | Không. |
| RTN-06 | PASS | Batch size 1/2 và max batches được tôn trọng; scheduler drain theo bounded batches và cutoff độc lập. | Không. |

### 19. Migration và deployment

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| MIG-01 | PASS | Fresh PostgreSQL chạy Flyway V1→V17 và app/worker khởi động thành công. | Không. |
| MIG-02 | PASS | Dedicated V6→V17 giữ nguyên user, username/email/status, email_verified và password_hash. | Không. |
| MIG-03 | PASS | V9 pending capture được chuyển thành failed `legacy_pending_not_processed`; incomplete item bị loại, không còn invalid state. | Không. |
| MIG-04 | PASS | Restart app nhiều lần trên schema V17 không rerun/checksum failure. | Không. |
| MIG-05 | PASS | Legacy/current durable queued/retry/processing/dead records vẫn đọc và recovery/fencing tiếp tục xử lý. | Không. |
| MIG-06 | PASS | Dataset 5.000 jobs dùng admin/cleanup/audit indexes trong EXPLAIN ANALYZE; không full scan rõ ràng. | Không. |

### 20. Navigation và phạm vi đã loại bỏ

| ID | Trạng thái | Kết quả thực tế / bằng chứng | Sai lệch |
|---|---|---|---|
| NAV-01 | PASS | Sidebar chỉ có Inbox, Tasks, Calendar, Reminders, Information; không Notes/Search. | Không. |
| NAV-02 | PASS | `/notes`, `/search`, `/items/<id>` trả Next 404, không runtime crash. | Không. |
| NAV-03 | PARTIAL | Browser: local login về `/inbox`; Google start link có `next=%2Finbox`; email verification, PKCE/state và Google profile service tests pass. | Chưa chạy callback OAuth ngoài bằng tài khoản Google thật. |
| NAV-04 | PASS | Logout về `/login`; Back vẫn bị middleware giữ ở login; login lại về Inbox và không có console error. | Không. |

## Defect và follow-up đề xuất

1. **P1 — OBS-08:** thêm `RetrySource.ADMIN` và dùng source này trong admin requeue; giữ `MANUAL` cho user retry. Thêm regression test Prometheus tag.
2. **P1/P2 — unknown admin route:** map `NoResourceFoundException`/unsupported method thành 404/405 an toàn thay vì 500, vẫn không thêm purge endpoint.
3. **Test reliability:** thay timestamp cố định trong `inbox-tracking.test.tsx` bằng fake timer/clock hoặc truyền `now` vào assertion.
4. **DUR-06:** bổ sung Docker process test gửi SIGTERM trong lúc fake provider đang block; assert executor chờ trong bound hoặc lease được recovery.
5. **NAV-03:** chỉ cần một smoke test staging bằng tài khoản Google test để chuyển PARTIAL thành PASS.
6. **Operations:** ghi rõ Redis observability có thể mất khoảng 60 giây mới đổi `source_up=0` do command timeout.

## Trạng thái cleanup

- Frontend/backend local dùng để test đã dừng; không còn listener 3000/8080/8081.
- Thư mục log runtime tạm đã xóa; không còn file test tạm trong source tree.
- DB dev vẫn giữ 9 test users vì thao tác xóa destructive chưa được user phê duyệt rõ ở thời điểm cleanup. Snapshot trước cleanup: 9 users, 23 captures, 34 items, 23 jobs và 95 audit rows theo actor. Exact UUID nằm trong checkpoint ngày 2026-07-21.
