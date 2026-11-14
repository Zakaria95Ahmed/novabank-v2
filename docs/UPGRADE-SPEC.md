# NovaBank v2: بيان الترقية الشامل (Upgrade Specification)

| البند | القيمة |
|---|---|
| الإصدار الحالي | **v1** (Monorepo `novabank-MS`، يفضل كما هو للرجوع) |
| الإصدار المستهدف | **v2.0.0** في مستودع مستقل جديد `novabank-v2` |
| تاريخ البيان | 2026-11-11 |
| الستاك | Java 21، Spring Boot 4.0.3، Spring Cloud 2025.1.0، Maven، Docker، PostgreSQL، Kafka |
| الهدف | تحويل تطبيق تعليمي سليم إلى أساس **جاهز للإنتاج** من حيث الأمان وسلامة الفلوس والتشغيل والاختبار |

---

## 0. كيف تستخدم هذا البيان

1. كل **مرحلة (P0 إلى P8)** = Milestone على GitHub. كل **مهمة** (لها ID مثل `SEC-01`) = Issue. كل Issue = Branch + Pull Request واحد.
2. **لا تبدأ مرحلة قبل ما معايير القبول للي قبلها تتحقق.**
3. لو بتنفّذ بمساعد برمجة (Claude Code أو غيره): أعطه **مرحلة واحدة** مع الأقسام 1 و2 و4، وقوله ينفّذ مهمة مهمة ويشغّل الاختبارات بعد كل واحدة.
4. ⚠️ **تنبيه تحقق:** أسماء بعض الـ dependencies والـ properties الخاصة بـ Spring Boot 4 وSpring Cloud 2025.1 كُتبت من المعرفة العامة **من غير تشغيل فعلي**. اللي عليه علامة ⚠️ تأكد منه من start.spring.io أو الوثائق الرسمية قبل ما تعتمد عليه.
5. هذا البيان **تطوير للمعمارية الحالية، مش إعادة كتابة**: Eureka وKafka وGateway وداتابيز لكل Service كلها تفضل.

---

## 1. الغرض والنطاق والمبادئ

### 1.1 داخل النطاق
أمان الهوية والصلاحيات، سلامة العمليات المالية، إدارة تغييرات الداتابيز، العقود المشتركة وتوحيد الـ API، المرونة، المراقبة، الاختبارات، حاويات Docker وCI/CD، وتنظيم المستودع.

### 1.2 خارج النطاق (Backlog لإصدارات لاحقة)
Refresh tokens، Kubernetes، Config Server، Debezium (CDC)، تعدد العملات، KYC، واجهة أمامية، service mesh/mTLS، فصل Auth في Service مستقلة، API keys.

### 1.3 المبادئ الحاكمة
| المبدأ | المعنى العملي |
|---|---|
| **لا أسرار في Git** | كل سر في `.env` أو secrets، و`.env.example` فقط يترفع |
| **الحقيقة في مكان واحد** | رصيد الحساب مصدره Account Service فقط |
| **افتراض التكرار والفشل** | أي رسالة ممكن تتكرر، وأي نداء ممكن يفشل |
| **تغييرات صغيرة** | PR واحد = مهمة واحدة، ويمر بـ CI أخضر |
| **التغيير الكاسر موثّق** | أي تغيير في الـ API يتسجّل في `CHANGELOG.md` |
| **الإعداد قابل للتشغيل بأمر واحد** | `docker compose up` من نظيف يشغّل كل حاجة |

---

## 2. القرارات المعمارية (ADR مختصرة)

| # | القرار | السبب | البديل المستبعد |
|---|---|---|---|
| ADR-1 | مستودع جديد مستقل `novabank-v2` (Monorepo) | تاريخ نضيف وإصدار مستقل، مع إبقاء v1 للرجوع | ترقية v1 في مكانه |
| ADR-2 | وحدة `novabank-contracts` مشتركة (أحداث وEnums فقط) | إزالة التكرار بين الـ Services بدون مشاركة منطق | نسخ الأصناف في كل Service |
| ADR-3 | **Saga** بالأحداث لعمليات الفلوس مع تعويض | عملية موزعة على Service-ين، ولا توجد transaction واحدة | 2PC (غير عملي مع Kafka) |
| ADR-4 | **Outbox** + **Idempotent Consumer** | حفظ الداتابيز ونشر Kafka ليسوا ذرّيين، وKafka at-least-once | النشر المباشر داخل الـ transaction |
| ADR-5 | JWT موقّع بـ **RS256** (مفتاح خاص عند Account فقط) | أي Service تتخترق ما تقدرش تزوّر توكنات | HS512 بسر مشترك |
| ADR-6 | **Flyway** + `ddl-auto=validate` | تغييرات الجداول موثّقة ومتكررة، وبذور الـ roles تلقائية | `ddl-auto=update` |
| ADR-7 | أخطاء بصيغة **ProblemDetail** (RFC 9457) | معيار موحّد، أسهل للعملاء | شكل مخصص |
| ADR-8 | **Resource Server** من Spring Security للتحقق من JWT | حذف فلاتر JWT المكتوبة يدوياً | الإبقاء على `JwtAuthFilter` المخصص |
| ADR-9 | صور الـ Services تُنشر على **GHCR** | السيرفر يسحب الصور بدل ما يبنيها | البناء على السيرفر |
| ADR-10 | المراقبة في Docker Compose **profile** اختياري | جهازك رامه محدودة (حوالي 7.4 جيجا لـ Docker) | تشغيلها دايماً |

---

## 3. الهيكل المستهدف للمستودع

```text
novabank-v2/
├── pom.xml                              # Parent: BOMs، الإصدارات، إعدادات الـ plugins
├── libs/
│   └── novabank-contracts/              # أحداث وEnums وثوابت Topics (بدون منطق أعمال)
├── services/
│   ├── discovery-service/
│   ├── api-gateway/
│   ├── user-account-service/
│   ├── transaction-service/
│   └── notification-service/
├── deploy/
│   ├── docker-compose.yml               # الأساس: شبكة داخلية، Gateway فقط منشور
│   ├── docker-compose.dev.yml           # override للتطوير: نشر بورتات إضافية
│   ├── .env.example
│   ├── postgres/init-db.sh              # يوزر وداتابيز لكل Service
│   ├── observability/
│   │   ├── prometheus.yml
│   │   └── grafana/provisioning/        # datasources وdashboards
│   └── scripts/smoke-test.sh
├── docs/
│   ├── UPGRADE-SPEC.md                  # هذا الملف
│   ├── architecture.md
│   ├── events.md
│   ├── adr/0001-monorepo.md ...
│   └── api/novabank.postman_collection.json
├── .github/
│   ├── workflows/ci.yml
│   ├── workflows/release.yml
│   ├── dependabot.yml
│   ├── CODEOWNERS
│   └── pull_request_template.md
├── Dockerfile                           # واحد لكل الـ Services (ARG SERVICE)
├── .dockerignore  .gitignore  .gitattributes  .editorconfig
├── README.md  LICENSE  CHANGELOG.md
```

**الهيكل الداخلي لكل Service** (بعد إعادة تسمية الـ package إلى `com.novabank.<service>`):

```text
src/main/java/com/novabank/<service>/
├── web/        # controllers, request/response DTOs, exception handler
├── domain/     # entities, domain services, business rules
├── infra/      # repositories, kafka (producers/consumers/outbox), feign, security, config
└── <Service>Application.java
src/main/resources/
├── application.yml
├── application-local.yml      # localhost
├── application-docker.yml     # أسماء containers
└── db/migration/V1__baseline.sql ...
```

---

## 4. التصميم المستهدف

### 4.1 مسؤوليات الـ Services

| Service | المسؤولية | الداتابيز | ما لا تفعله |
|---|---|---|---|
| **discovery-service** | سجل الخدمات (Eureka) | لا | |
| **api-gateway** | نقطة دخول وحيدة، التحقق من JWT، توجيه، rate limiting، `X-Request-Id` | لا (Redis للـ rate limit) | لا تحتوي منطق أعمال |
| **user-account-service** | المستخدمون، الصلاحيات، إصدار JWT، **الحسابات والأرصدة (مصدر الحقيقة)**، تنفيذ أوامر Debit/Credit | `nova_bank_account_db` | لا تعرف تفاصيل المعاملة |
| **transaction-service** | استقبال طلبات الإيداع والتحويل، سجل المعاملات، **منسّق الـ Saga**، idempotency | `nova_bank_transaction_db` | لا تعدّل الرصيد مباشرة |
| **notification-service** | إرسال الإيميلات من الأحداث، سجل الإشعارات | `nova_bank_notification_db` | لا تكلّم Services أخرى |

### 4.2 كتالوج الأحداث والـ Topics

**مظروف مشترك لكل حدث:** `eventId` (UUID)، `occurredAt` (Instant)، `schemaVersion` (int)، `correlationId` (string).

| Topic | المُنتِج | المستهلكون | Key | الأحداث |
|---|---|---|---|---|
| `novabank.account.commands.v1` | Transaction | Account | `accountNumber` | `DebitAccount`، `CreditAccount` |
| `novabank.account.events.v1` | Account | Transaction، Notification | `accountNumber` (أو `email` لـ UserRegistered) | `UserRegistered`، `AccountDebited`، `AccountCredited`، `AccountOperationRejected` |
| `novabank.transaction.events.v1` | Transaction | (Notification اختياري) | `reference` | `TransactionCompleted`، `TransactionFailed` |

- لكل Topic **DLT** تلقائي باسم `<topic>.DLT`.
- الـ Key = رقم الحساب يضمن **ترتيب العمليات على نفس الحساب** (نفس الـ partition).
- Partitions = 3، Replication = 1 (للتطوير فقط).

**حقول الأحداث:**

| الحدث | الحقول (إضافة للمظروف) |
|---|---|
| `UserRegistered` | `email`، `firstName`، `lastName`، `accountNumber`، `bankName` |
| `DebitAccount` / `CreditAccount` | `transactionReference`، `accountNumber`، `amount`، `currency`، `reason` (`DEPOSIT` \| `TRANSFER_DEBIT` \| `TRANSFER_CREDIT` \| `REFUND`)، `description` |
| `AccountDebited` / `AccountCredited` | `transactionReference`، `accountNumber`، `amount`، `currency`، `balanceAfter`، `ownerEmail`، `ownerName`، `description`، `counterpartyAccount` (اختياري) |
| `AccountOperationRejected` | `transactionReference`، `accountNumber`، `operation`، `reasonCode` (`INSUFFICIENT_FUNDS` \| `ACCOUNT_FROZEN` \| `ACCOUNT_CLOSED` \| `ACCOUNT_NOT_FOUND` \| `CURRENCY_MISMATCH`) |
| `TransactionCompleted` / `TransactionFailed` | `reference`، `type`، `status`، `amount`، `currency`، `fromAccount`، `toAccount`، `failureReason` |

**قواعد تطوير العقود:** إضافة حقول اختيارية فقط، لا حذف ولا إعادة تسمية. أي تغيير كاسر = Topic جديد `.v2`.

### 4.3 الـ Saga: دورة حياة المعاملة

**حالات المعاملة:** `PENDING`، `DEBITED`، `COMPLETED`، `COMPENSATING`، `FAILED`، `COMPENSATED`، `NEEDS_REVIEW`.

**التحويل (Transfer):**

| # | الحدث/الخطوة | المسؤول | النتيجة |
|---|---|---|---|
| 1 | `POST /api/v1/transactions/transfers` مع `Idempotency-Key` | Transaction | تحقق (الحسابات موجودة، نفس العملة، المُرسِل ملك المستخدم)، حفظ المعاملة `PENDING` + صف Outbox `DebitAccount`، رد **202** |
| 2 | استلام `DebitAccount` | Account | لو الحساب `ACTIVE` والرصيد كافٍ: خصم + `AccountDebited`. غير كده: `AccountOperationRejected` |
| 3a | استلام `AccountOperationRejected` | Transaction | الحالة `FAILED` مع السبب |
| 3b | استلام `AccountDebited` | Transaction | الحالة `DEBITED` + Outbox `CreditAccount` للمستقبِل |
| 4 | استلام `CreditAccount` | Account | إضافة الرصيد + `AccountCredited` (أو `Rejected` لو الحساب `CLOSED`) |
| 5a | استلام `AccountCredited` | Transaction | الحالة `COMPLETED` + `TransactionCompleted` |
| 5b | استلام `Rejected` للإضافة | Transaction | الحالة `COMPENSATING` + `CreditAccount` بسبب `REFUND` للمُرسِل |
| 6 | استلام `AccountCredited` للـ `REFUND` | Transaction | الحالة `COMPENSATED` (فشل التحويل واتردّت الفلوس) |

**الإيداع (Deposit):** الخطوات 1 ثم 4 ثم 5a فقط (إضافة بدون خصم).

**قواعد الحساب (في Account):**
- `ACTIVE`: يقبل الخصم والإضافة.
- `FROZEN`: **يرفض الخصم** ويقبل الإضافة.
- `CLOSED`: يرفض الاتنين.
- العملة لازم تطابق عملة الحساب. الرصيد لا يقل عن صفر (قيد في الداتابيز).

**المعاملات العالقة:** مهمة مجدولة كل دقيقة تحوّل أي معاملة في `PENDING` أو `DEBITED` أو `COMPENSATING` لأكثر من 5 دقايق إلى `NEEDS_REVIEW`، وتسجّل خطأ وترفع metric.

### 4.4 معايير الـ API (v1 للـ API نفسه)

| البند | القرار |
|---|---|
| المسارات | `/api/v1/auth/**`، `/api/v1/users/**`، `/api/v1/accounts/**`، `/api/v1/transactions/**`، وللأدمن `/api/v1/admin/**` |
| الأخطاء | `ProblemDetail` مع حقل `code` ثابت (مثل `INSUFFICIENT_FUNDS`) و`traceId` |
| النجاح | رجوع الـ DTO مباشرة (بدون غلاف `ApiResponse`) |
| القوائم | pagination: `?page=0&size=20&sort=createdAt,desc` والرد `PageResponse<T>` |
| العمليات المالية | تتطلب `Idempotency-Key`، وترجع **202 Accepted** و`Location` لـ `GET /transactions/{reference}` |
| التوثيق | OpenAPI عبر springdoc ⚠️ (تأكد من توافق الإصدار مع Boot 4) |
| داخلي | مسارات `/internal/**` للتواصل بين الـ Services، **الـ Gateway لا يوجّهها** |

⚠️ **هذا تغيير كاسر عن v1** (المسارات والردود). يتحدّث ملف Postman ويتسجّل في `CHANGELOG.md`.

---

## 5. مصفوفة التحسينات (تتبّع كامل)

| ID | التحسين | الأولوية | المرحلة |
|---|---|---|---|
| SEC-01 | منع اختيار `ADMIN` عند التسجيل + إنشاء الأدمن بطريقة آمنة | 1 | P1 |
| SEC-02 | تقليل كشف Actuator + منفذ إدارة داخلي | 1 | P1 |
| SEC-03 | نشر بورت الـ Gateway فقط، وعزل الباقي داخل الشبكة | 1 | P1 |
| SEC-06 | Rate limiting على `/auth/login` | 2 | P1 |
| DB-01 | Flyway + `ddl-auto=validate` + بذور الـ roles | 1 | P2 |
| SEC-05 | يوزر داتابيز مستقل لكل Service (أقل صلاحيات) | 1 | P2 |
| FIN-01 | Idempotency (مفتاح للعميل + معالج رسائل مكرر) | 1 | P3 |
| FIN-02 | تزامن الرصيد (`@Version` + قيد `balance >= 0`) | 1 | P3 |
| FIN-03 | Outbox pattern | 1 | P3 |
| FIN-04 | Saga + مصدر حقيقة للرصيد + تعويض | 1 | P3 |
| FIN-05 | تفويض: العميل يتعامل مع حسابه فقط (منع IDOR) *(مضاف)* | 1 | P3 |
| QA-01 | اختبارات وحدة + Testcontainers + تفعيلها في CI | 2 | P3 وما بعدها |
| ARCH-01 | وحدة العقود المشتركة | 3 | P4 |
| API-01 | `/api/v1` + ProblemDetail + pagination + OpenAPI | 3 | P4 |
| ARCH-02 | Profiles + `@ConfigurationProperties` | 3 | P4 |
| ARCH-04 | إعادة تسمية الـ package إلى `com.novabank` | 3 | P0 (اختياري) |
| SEC-04 | JWT بـ RS256 + Resource Server | 1 | P5 |
| RES-01 | Timeouts + Circuit Breaker على Feign | 2 | P6 |
| RES-02 | Retry + Dead Letter Topic لمستهلكي Kafka | 2 | P6 |
| OBS-01 | SLF4J وتنظيف اللوجات | 2 | P7 |
| OBS-02 | Tracing + Correlation ID | 2 | P7 |
| OBS-03 | Prometheus + Grafana | 2 | P7 |
| OPS-01 | تقوية Docker (إصدارات ثابتة، healthchecks، حدود ذاكرة، JRE، non-root) | 2 | P8 |
| OPS-02 | Dependabot + CI/CD + نشر الصور على GHCR | 3 | P8 |
| ARCH-03 | MapStruct بدل ModelMapper (اختياري) | 4 | بعد v2.0 |

---

## 6. خطة التنفيذ (المراحل)

> الأوقات تقريبية لشخص واحد بدوام جزئي، وتتغير حسب خبرتك. المجموع التقريبي لكل المراحل: **حوالي 17 يوم عمل**.

### P0: تأسيس المستودع (حوالي 0.5 يوم)

| ID | المهمة | معيار القبول |
|---|---|---|
| P0-1 | إنشاء repo `novabank-v2` (Private في البداية). حماية فرع `main`: PR مطلوب، CI أخضر مطلوب، منع force-push | الإعدادات مفعّلة |
| P0-2 | استيراد v1 في commit واحد `chore: import v1 baseline` (بدون `.git` و`.env` و`target` و`.idea`) + tag `v1-baseline` | التاج موجود، و`git status` نظيف |
| P0-3 | إعادة الهيكل: نقل الـ Services تحت `services/` بأسماء مختصرة، وإنشاء `libs/` و`deploy/` | الشجرة مطابقة للقسم 3 |
| P0-4 | تحديث `pom.xml` الجذري: `<modules>` والمسارات، و`relativePath` في كل Service يصير `../../pom.xml` | `mvn -B verify` ينجح |
| P0-5 | ملفات المستودع: `.gitignore` و`.gitattributes` و`.editorconfig` و`CODEOWNERS` و`pull_request_template.md` و`dependabot.yml` | موجودة |
| P0-6 | CI أساسي (بناء فقط) | الـ workflow أخضر على `main` |
| P0-7 | *(اختياري، commit منفصل)* إعادة تسمية `com.example.*` إلى `com.novabank.*` من IntelliJ (Refactor ثم Rename package) | البناء ينجح بعد التغيير |

**تعديلات الـ Parent POM (`pom.xml`):**
- `<maven.compiler.release>21</maven.compiler.release>` و`<java.version>21</java.version>`.
- إضافة `maven-enforcer-plugin` بقاعدة `requireJavaVersion` لـ `[21,)`.
- في `dependencyManagement`: إضافة `com.novabank:novabank-contracts:${project.version}`.

**الـ Dockerfile الموحّد (بعد الهيكل الجديد):**

```dockerfile
# syntax=docker/dockerfile:1
FROM maven:3.9.12-amazoncorretto-21 AS build
ARG SERVICE
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/${SERVICE} -am clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
ARG SERVICE
ARG PORT=8080
ENV PORT=${PORT}
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --no-create-home appuser
WORKDIR /app
COPY --from=build --chown=appuser /build/services/${SERVICE}/target/*.jar app.jar
USER appuser
EXPOSE ${PORT}
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:9090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> الـ healthcheck بيضرب منفذ الإدارة `9090` (انظر SEC-02). لو غيّرت المنفذ حدّثه هنا.

**معايير قبول P0:** `mvn -B verify` أخضر محلياً وعلى GitHub، و`docker build --build-arg SERVICE=api-gateway --build-arg PORT=8080 .` ينجح.

---

### P1: أمان سريع (حوالي 1 يوم)

#### SEC-01: منع التسجيل كـ ADMIN
1. حذف حقل `role` من `RegistrationRequest`.
2. `AuthServiceImpl.register` ينشئ دايماً `CUSTOMER`.
3. إنشاء أول أدمن عن طريق `ApplicationRunner` في Account:
   - يقرأ `BOOTSTRAP_ADMIN_EMAIL` و`BOOTSTRAP_ADMIN_PASSWORD` من البيئة.
   - ينشئ الأدمن **فقط لو** المتغيران موجودان **ولا يوجد أدمن** في الداتابيز.
   - يسجّل في اللوج بدون طباعة الباسورد.
4. إنشاء أدمن إضافي عبر `POST /api/v1/admin/users` وعليه `hasAuthority('ADMIN')`.

**الاختبارات المطلوبة:**
- تسجيل مع `role: "ADMIN"` في الجسم ينتج مستخدم صلاحياته `[CUSTOMER]` بس.
- Customer يستدعي `/api/v1/admin/**` ← **403**.
- `ApplicationRunner` لا ينشئ أدمن ثاني لو فيه واحد.

#### SEC-02: Actuator
```properties
management.server.port=9090
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.probes.enabled=true
management.endpoint.health.show-details=never
```
- منفذ `9090` **داخلي** (غير منشور في Compose).
- في كل `SecurityConfig` أضف `SecurityFilterChain` بأولوية عالية يسمح بـ `EndpointRequest.toAnyEndpoint()` ⚠️ (حزمة `EndpointRequest` اتغيّرت في Boot 4، تأكد من الـ import).
- Eureka يسجّل منفذ الإدارة تلقائياً لفحص الصحة ⚠️ (تحقق إن الـ Services تظهر UP في Eureka بعد التغيير).

#### SEC-03: عزل الشبكة
- في `deploy/docker-compose.yml`: **`ports` للـ Gateway فقط** (`8080:8080`)، والباقي بـ `expose`.
- `docker-compose.dev.yml` ينشر 8761 و5433 و9092 وغيرها لراحة التطوير:
  `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`.
- الـ Gateway لا يوجّه `/internal/**` ولا `/actuator/**`.

#### SEC-06: Rate limiting على الدخول
- إضافة Redis إلى Compose (`redis:7-alpine`، شبكة داخلية).
- Gateway: dependency `spring-boot-starter-data-redis-reactive` و`RequestRateLimiter` على مسار `/api/v1/auth/login`، والمفتاح = عنوان IP للعميل (مع احترام `X-Forwarded-For` لو وراء proxy).
- القيم المبدئية: `replenishRate=1`، `burstCapacity=5`.
- الأفضل نقل تعريف الـ routes من Java إلى `application.yml`. ⚠️ في Spring Cloud Gateway 2025.x البادئة صارت `spring.cloud.gateway.server.webflux.*`:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: auth-login
              uri: lb://user-account-service
              predicates:
                - Path=/api/v1/auth/login
              filters:
                - name: RequestRateLimiter
                  args:
                    redis-rate-limiter.replenishRate: 1
                    redis-rate-limiter.burstCapacity: 5
                    key-resolver: '#{@clientIpKeyResolver}'
            - id: user-account
              uri: lb://user-account-service
              predicates:
                - Path=/api/v1/auth/**,/api/v1/users/**,/api/v1/accounts/**,/api/v1/admin/users/**,/api/v1/admin/accounts/**
            - id: transaction
              uri: lb://transaction-service
              predicates:
                - Path=/api/v1/transactions/**,/api/v1/admin/transactions/**
```

**معايير قبول P1:**
- الاختبارات الثلاثة في SEC-01 تنجح.
- `curl http://localhost:8081/actuator/env` (من الجهاز) **لا يرد** في الوضع الافتراضي.
- المحاولة السادسة لـ login خلال دقيقة تُرجع **429**.
- Eureka يعرض كل الـ Services بحالة UP.

---

### P2: قاعدة البيانات (حوالي 1.5 يوم)

#### DB-01: Flyway
1. إضافة dependencies في Services الثلاثة التي لها داتابيز: `spring-boot-starter-flyway` ⚠️ (اسم الـ starter في Boot 4) و`flyway-database-postgresql`.
2. توليد `V1__baseline.sql` من الـ schema الحالي:
   - شغّل الـ Service مرة على داتابيز فاضية بـ `ddl-auto=create`.
   - `pg_dump --schema-only --no-owner --no-privileges -d <db> > V1__baseline.sql`
   - نظّف الملف وأزل ما لا لزوم له، وضعه في `src/main/resources/db/migration/`.
3. `spring.jpa.hibernate.ddl-auto=validate` و`spring.flyway.enabled=true`.
4. `V2__seed_roles.sql` في Account:
   ```sql
   INSERT INTO roles (name) VALUES ('ADMIN'), ('CUSTOMER')
   ON CONFLICT (name) DO NOTHING;
   ```
5. **قواعد:** ممنوع تعديل migration اتطبّقت. أي تغيير = ملف جديد `V<n>__وصف.sql`.

#### SEC-05: يوزر لكل Service
`deploy/postgres/init-db.sh` (يشتغل مرة واحدة عند أول إنشاء للـ volume):

```bash
#!/bin/bash
set -euo pipefail

create_db() {
  local db="$1" user="$2" pass="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
    CREATE USER ${user} WITH PASSWORD '${pass}';
    CREATE DATABASE ${db} OWNER ${user};
    REVOKE ALL ON DATABASE ${db} FROM PUBLIC;
SQL
}

create_db nova_bank_account_db      "$ACCOUNT_DB_USER"      "$ACCOUNT_DB_PASSWORD"
create_db nova_bank_transaction_db  "$TRANSACTION_DB_USER"  "$TRANSACTION_DB_PASSWORD"
create_db nova_bank_notification_db "$NOTIFICATION_DB_USER" "$NOTIFICATION_DB_PASSWORD"
```
- الـ postgres container يستلم متغيرات الأرقام السرية من `.env`.
- **تجنّب علامة `'` في الباسوردات** (بتكسر الـ SQL).
- كل Service تستخدم **يوزرها** فقط، و`postgres` (superuser) لا يستخدمه أي Service.

**معايير قبول P2:**
- `docker compose down -v && docker compose up` يعمل الجداول والـ roles من غير أي تدخل يدوي.
- `ddl-auto=validate` ينجح (الـ entities متطابقة مع الـ migrations).
- الاتصال بداتابيز Transaction بيوزر Account **يفشل**.

---

### P3: سلامة الفلوس (حوالي 5 إلى 6 أيام، أهم مرحلة)

#### 3.1 الجداول (Flyway migrations)

**Transaction Service:**
```sql
-- المعاملات (عدّل على حسب الكيان الحالي، المطلوب إضافته: reference + status + version)
ALTER TABLE transactions ADD COLUMN reference VARCHAR(40);
ALTER TABLE transactions ADD COLUMN failure_reason VARCHAR(255);
ALTER TABLE transactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- بعد تعبئة القيم القديمة:
ALTER TABLE transactions ALTER COLUMN reference SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT uq_transactions_reference UNIQUE (reference);
ALTER TABLE transactions ADD CONSTRAINT chk_amount_positive CHECK (amount > 0);

CREATE TABLE idempotency_keys (
  idem_key              VARCHAR(100) NOT NULL,
  user_email            VARCHAR(255) NOT NULL,
  request_hash          CHAR(64)     NOT NULL,
  transaction_reference VARCHAR(40),
  created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
  PRIMARY KEY (idem_key, user_email)
);

CREATE TABLE outbox_events (
  id          UUID PRIMARY KEY,
  topic       VARCHAR(200) NOT NULL,
  message_key VARCHAR(100) NOT NULL,
  event_type  VARCHAR(100) NOT NULL,
  payload     JSONB        NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_messages (
  message_id   UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Account Service:**
```sql
ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD CONSTRAINT chk_balance_non_negative CHECK (balance >= 0);
-- + نفس جدولي outbox_events وprocessed_messages
```

**Notification Service:** جدول `processed_messages` (علشان الإيميل ما يتبعتش مرتين).

#### 3.2 Outbox (FIN-03)
- أي تغيير في الداتابيز **وصف الحدث** بيتحفظوا في **نفس الـ transaction**.
- مهمة مجدولة (`@Scheduled(fixedDelay=500)`) و`@Transactional`:
  1. تجلب دفعة غير منشورة بـ `SELECT ... WHERE published_at IS NULL ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED` (native query).
  2. لكل صف: تحوّل `payload` إلى كائن الحدث عن طريق **سجل `event_type` ← الكلاس** (علشان `JsonSerializer` يضيف type headers صح)، ثم `kafkaTemplate.send(topic, key, event).join()`.
  3. تحدّث `published_at`.
- **الضمان:** at-least-once، فكل المستهلكين لازم يكونوا idempotent.
- **الترتيب:** شغّل منشور واحد (instance واحدة) أو اضمن ترتيب `created_at` لكل Key.
- مهمة تنظيف تحذف المنشور الأقدم من 7 أيام.
- Metrics: `novabank.outbox.pending` (gauge).
- Producer: `acks=all` و`enable.idempotence=true`.

#### 3.3 المستهلك المعالج للتكرار (FIN-01)
في كل مستهلك، **داخل transaction واحدة**:
1. `INSERT INTO processed_messages(message_id) VALUES (:id) ON CONFLICT DO NOTHING`.
2. لو عدد الصفوف المتأثرة = 0 ← **تجاهل الرسالة** (مكررة).
3. غير كده ← نفّذ التغيير (تحديث الرصيد أو إرسال الإيميل).

**Idempotency-Key للعميل (في Transaction):**
- Header إجباري على `POST` للإيداع والتحويل.
- نفس المفتاح + نفس الجسم ← يرجّع نفس الرد الأصلي.
- نفس المفتاح + جسم مختلف (مقارنة `request_hash` SHA-256) ← **422** بكود `IDEMPOTENCY_KEY_REUSED`.
- غياب الـ Header ← **400**.

#### 3.4 التزامن والقواعد (FIN-02، FIN-04)
- كيان `Account`: حقل `@Version Long version`.
- دوال `debit(amount)` و`credit(amount)` **داخل الكيان**، وبتطبّق قواعد القسم 4.3، وبترجع نتيجة أو ترمي استثناء دومين واضح.
- أوامر الحساب الواحد بتتعالج بالترتيب (نفس الـ partition)، و`@Version` شبكة أمان للعمليات الأخرى (الأدمن مثلاً). عند `OptimisticLockingFailureException` أعد المحاولة 3 مرات.
- المبالغ: `BigDecimal`، و`@Digits(integer=15, fraction=2)` على الـ DTO، والتقريب `HALF_EVEN`.

#### 3.5 التفويض (FIN-05)
- التحويل: `fromAccount` لازم يكون مملوكاً للمستخدم في الـ JWT (التحقق عبر `GET /internal/accounts/{no}` الذي يرجع `ownerEmail`).
- `GET /transactions/{reference}`: صاحب العملية أو الأدمن فقط.
- الإيداع: للأدمن فقط (زي ما هو في v1) أو لقناة `BANK_BRANCH`.

#### 3.6 المعاملات العالقة
- مهمة مجدولة كل دقيقة (انظر 4.3)، و`GET /api/v1/admin/transactions?status=NEEDS_REVIEW` للأدمن.
- Metric: `novabank.transactions.needs_review`.

#### 3.7 الاختبارات (QA-01)
- **وحدة:** قواعد `Account.debit/credit` (كل الحالات: ACTIVE/FROZEN/CLOSED، رصيد غير كافٍ، عملة مختلفة).
- **تكامل (Testcontainers: PostgreSQL + Kafka):**
  1. نفس أمر `DebitAccount` يوصل مرتين ← يتنفذ **مرة واحدة**.
  2. 50 عملية إضافة متزامنة ← الرصيد النهائي **مضبوط بالظبط**.
  3. تحويل برصيد غير كافٍ ← `FAILED` والأرصدة لم تتغير.
  4. فشل الإضافة للمستقبِل (حساب `CLOSED`) ← `COMPENSATED` وفلوس المُرسِل رجعت.
  5. إيقاف النشر بعد حفظ الـ Outbox (محاكاة crash) ثم إعادة التشغيل ← الحدث يتنشر.
  6. نفس `Idempotency-Key` مرتين ← معاملة واحدة.

**معايير قبول P3:**
- الاختبارات الستة تنجح في CI.
- مجموع أرصدة كل الحسابات **ثابت** قبل وبعد اختبار تحويلات عشوائية (invariant).
- رد التحويل **202** مع `reference`، والحالة النهائية تصل `COMPLETED` خلال ثواني.

---

### P4: العقود والـ API والإعدادات (حوالي 2 يوم)

#### ARCH-01: وحدة `novabank-contracts`
- المسار: `libs/novabank-contracts`، الحزمة `com.novabank.contracts`.
- المحتوى: `Topics` (ثوابت)، `events/*` كـ **records**، `enums/*` (`Currency`، `TransactionType`، `TransactionStatus`، `TransactionDirection`، `AccountStatus`، `AccountType`)، `ErrorCodes`.
- **ممنوع** فيها: منطق أعمال، Spring، JPA. الاعتماد الوحيد: annotations بتاعة Jackson.
- حذف النسخ المكررة من الـ Services (`BalanceUpdateEvent` كانت في 3 أماكن، و`Currency` وغيرها).
- حذف `spring.json.type.mapping` من كل الإعدادات، واستبداله بـ:
  ```properties
  spring.kafka.consumer.properties.spring.json.trusted.packages=com.novabank.contracts.*
  ```
- اختبار تسلسل/عكس (round-trip JSON) لكل حدث.

#### API-01: توحيد الـ API
- كل الـ controllers تحت `/api/v1/...`، والـ Gateway يتحدّث (القسم P1 فيه routes).
- معالج أخطاء موحّد بـ `ProblemDetail` (`@RestControllerAdvice`)، مع `code` و`traceId`.
- حذف `ApiResponse` من ردود النجاح، وإضافة `PageResponse<T>` للقوائم.
- OpenAPI عبر springdoc ⚠️، و`/swagger-ui` متاح في `local` فقط.
- تحديث `docs/api/novabank.postman_collection.json` وتسجيل التغيير الكاسر في `CHANGELOG.md`.

#### ARCH-02: Profiles
- `application.yml` للقيم العامة، و`application-local.yml` (`localhost`)، و`application-docker.yml` (أسماء containers).
- Compose يضبط فقط `SPRING_PROFILES_ACTIVE=docker`، فيتحذف الـ `environment` الطويل (Eureka وKafka وDatasource).
- IntelliJ: profile `local` في الـ Run Configurations.
- الإعدادات المرتبطة (JWT، outbox، إلخ) في `@ConfigurationProperties` records بدل `@Value` المتناثرة.

**معايير قبول P4:**
- مفيش أي Enum أو حدث مكرر بين الـ Services.
- أي خطأ يرجع بصيغة ProblemDetail مع `code`.
- نفس الـ jar يشتغل بـ `local` و`docker` من غير تغيير كود.

---

### P5: JWT بـ RS256 (حوالي 1.5 يوم)

**الفكرة:** Account Service **فقط** يملك المفتاح الخاص ويوقّع. الباقي (Gateway وTransaction) يتحقق بالمفتاح **العام**، فلو أي Service اتخترقت ما تقدرش تزوّر توكنات.

| ID | المهمة |
|---|---|
| SEC-04a | توليد زوج مفاتيح RSA 2048 (الأوامر في الملحق) ووضعهما في `deploy/secrets/` (**خارج Git**) |
| SEC-04b | Account: `JwtService` يوقّع بالمفتاح الخاص (`RS256`)، وعمر التوكن **15 دقيقة** (`JWT_EXPIRATION_MINUTES`) |
| SEC-04c | الـ claims: `sub` (الإيميل)، `roles` (قايمة)، `iss`، `iat`، `exp`، `jti` |
| SEC-04d | Transaction: استبدال `JwtAuthFilter` و`JwtService` المخصصين بـ **Resource Server** من Spring Security ⚠️ (`spring-boot-starter-oauth2-resource-server`) |
| SEC-04e | Gateway: استبدال `AuthenticationFilter` و`JwtValidationUtils` بـ Resource Server (النسخة reactive) |
| SEC-04f | تحويل claim `roles` إلى authorities **بدون بادئة** `ROLE_` علشان `hasAuthority('ADMIN')` تفضل شغالة |
| SEC-04g | حذف `jwt.secret` و`JWT_SECRET` من كل مكان |

**الإعداد في Transaction والـ Gateway:**
```properties
spring.security.oauth2.resourceserver.jwt.public-key-location=file:${JWT_PUBLIC_KEY_PATH}
spring.security.oauth2.resourceserver.jwt.issuer-uri=
```
> اترك `issuer-uri` فاضياً (مفيش Authorization Server خارجي)، وتحقق من `iss` عن طريق `JwtValidators` مخصص ⚠️.

**الاختبارات:**
- توكن موقّع بمفتاح مختلف ← **401**.
- توكن منتهي ← **401**.
- توكن سليم بـ `roles=[CUSTOMER]` على endpoint أدمن ← **403**.
- Transaction والـ Gateway **لا يحتويان** أي مفتاح خاص (بحث نصي في الـ repo والصور).

**Backlog:** JWKS endpoint + `kid` لتدوير المفاتيح، وRefresh tokens.

**معايير قبول P5:** تسجيل الدخول والإيداع والتحويل تشتغل end-to-end بتوكنات RS256، وكل اختبارات الأمان تنجح.

---

### P6: المرونة (حوالي 1.5 يوم)

#### RES-01: النداءات بين الـ Services (Feign)
- Timeouts: `connect=2s`، `read=5s` ⚠️ (أسماء الـ properties في OpenFeign 5: `spring.cloud.openfeign.client.config.default.connect-timeout` و`read-timeout`).
- **Circuit Breaker** عبر Resilience4j ⚠️ (`spring-cloud-starter-circuitbreaker-resilience4j`) و`spring.cloud.openfeign.circuitbreaker.enabled=true`.
- الفشل يُترجَم إلى **503** بصيغة ProblemDetail بكود `UPSTREAM_UNAVAILABLE` (مش خطأ 500 غامض).
- Gateway: `httpclient.connect-timeout=2000` و`response-timeout=10s`.

#### RES-02: مستهلكو Kafka
```java
@Bean
DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template);   // يرسل إلى <topic>.DLT
    var backOff = new ExponentialBackOff(1_000L, 2.0);
    backOff.setMaxElapsedTime(30_000L);
    var handler = new DefaultErrorHandler(recoverer, backOff);
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    return handler;
}
```
- بعد استنفاد المحاولات الرسالة تروح الـ DLT، ويرتفع Metric `novabank.kafka.dlt` (تنبيه).
- في Notification: فشل إرسال الإيميل ← retry ثم DLT، ويتسجل الإشعار `FAILED` (موجود أصلاً).
- المستهلكون لازم يكونوا **idempotent** (P3) لأن إعادة المحاولة بتكرر المعالجة.

#### إعدادات عامة
- `server.shutdown=graceful` و`spring.lifecycle.timeout-per-shutdown-phase=30s`.
- Hikari: `maximum-pool-size=10` لكل Service.
- Producer: `acks=all`.

**معايير قبول P6:**
- إيقاف Account أثناء طلب تحويل ← رد **503** واضح من Transaction (مش 500)، وبعد رجوعه المعاملة تكمل.
- رسالة فاسدة تنتهي في `<topic>.DLT` من غير ما توقف باقي الرسايل.

---

### P7: المراقبة (حوالي 2 يوم)

#### OBS-01: اللوجات
- حذف كل `System.out.println` (Gateway)، واستخدام SLF4J.
- `logging.level.org.springframework.cloud.gateway` ← `INFO` (مش `TRACE`).
- **ممنوع** تسجيل: كلمات المرور، التوكنات، أرقام الحسابات كاملة، أو محتوى الإيميلات.
- في profile `docker`: `logging.structured.format.console=logstash` (لوجات JSON).

#### OBS-02: Tracing وCorrelation
```properties
# اللون التطويري 1.0، وفي الإنتاج 0.1 مثلاً
management.tracing.sampling.probability=1.0
logging.pattern.correlation=[%X{traceId:-},%X{spanId:-}]
spring.kafka.template.observation-enabled=true
spring.kafka.listener.observation-enabled=true
```
- Dependencies ⚠️: `micrometer-tracing-bridge-otel` و`opentelemetry-exporter-otlp` (أو starter الجاهز لو متوفر في Boot 4).
- الـ Gateway يولّد `X-Request-Id` لو غائب ويمرره، و`traceId` يظهر في رد الأخطاء (ProblemDetail).

#### OBS-03: Metrics وDashboards
- `micrometer-registry-prometheus` في كل Service.
- Compose **profile** اسمه `observability` فيه: Prometheus وGrafana وJaeger (يستقبل OTLP). تشغيله: `docker compose --profile observability up -d`.
- **Metrics مخصصة:**

| Metric | النوع |
|---|---|
| `novabank.transactions{type,status}` | counter |
| `novabank.transactions.needs_review` | gauge |
| `novabank.outbox.pending` | gauge |
| `novabank.kafka.dlt` | counter |

- Dashboards مجهّزة (provisioning): JVM، HTTP (معدل وأخطاء وlatency)، Kafka consumer lag، المعاملات حسب الحالة، الـ Outbox.
- تنبيهات مقترحة: `needs_review > 0`، `dlt > 0`، نسبة 5xx أكبر من 2%.

**معايير قبول P7:**
- طلب واحد (Gateway ← Transaction ← Kafka ← Account ← Notification) يظهر في Jaeger كـ **trace واحد**.
- لوح Grafana يعرض كل المقاييس أعلاه.
- مفيش أي `println` في الكود (`grep` نظيف).

---

### P8: تقوية Docker وCI/CD (حوالي 1.5 يوم)

#### OPS-01: Docker وCompose
- **تثبيت الإصدارات:** `postgres:17.<patch>` و`apache/kafka:<tag>` و`redis:7-alpine` وغيرها بـ tag محدد (بدل `latest`) ⚠️ اختر أحدث patch مستقر وقت التنفيذ.
- الـ Dockerfile النهائي (JRE، non-root، healthcheck، `MaxRAMPercentage`) كما في P0.
- حدود الذاكرة: `mem_limit` لكل Service (ابدأ بـ 640m وعدّل بعد القياس).
- `restart: unless-stopped` للجميع.
- الأسرار عبر Docker secrets (ملفات المفاتيح)، و`deploy/secrets/` في `.gitignore`.
- مثال لـ Service:

```yaml
x-service: &service
  restart: unless-stopped
  mem_limit: 640m
  networks: [novabank-net]

services:
  user-account-service:
    <<: *service
    build:
      context: ..
      dockerfile: Dockerfile
      args:
        SERVICE: user-account-service
        PORT: 8081
    image: ghcr.io/OWNER/novabank-user-account-service:${TAG:-dev}
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_URL: jdbc:postgresql://postgres:5432/nova_bank_account_db
      DB_USERNAME: ${ACCOUNT_DB_USER}
      DB_PASSWORD: ${ACCOUNT_DB_PASSWORD}
      JWT_PRIVATE_KEY_PATH: /run/secrets/jwt_private
    secrets: [jwt_private]
    depends_on:
      postgres: { condition: service_healthy }
      kafka: { condition: service_healthy }
      discovery-service: { condition: service_healthy }

secrets:
  jwt_private:
    file: ./secrets/jwt-private.pem
  jwt_public:
    file: ./secrets/jwt-public.pem
```
> الذاكرة: Docker Desktop عندك حوالي 7.4 جيجا. 5 Services (~0.6 جيجا) + Kafka (~1 جيجا) + Postgres + Redis ≈ 4.5 جيجا. شغّل `observability` لما تحتاجه بس.

#### OPS-02: CI/CD
انظر القسم 9 (الـ workflows)، بالإضافة لـ `dependabot.yml`.

**معايير قبول P8:**
- `docker compose up -d --wait` من داتابيز ومفاتيح جديدة ينجح، و`smoke-test.sh` يمر.
- `docker image ls` يوضح الصور أصغر من v1 (JRE بدل JDK).
- صورة منشورة على GHCR عند إنشاء tag `v*`.
- `docker inspect` يوضح أن الـ containers تعمل بمستخدم غير root.

---

## 6.1 ترتيب التنفيذ والتبعيات

```text
P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8
```
- **P3 تعتمد على P2** (الجداول بتتعمل بـ Flyway).
- **تعديل مهم:** ابدأ **ARCH-01 (وحدة contracts)** في **أول P3** بدل P4، لأن أحداث الـ Saga الجديدة بتتعرّف فيها من اليوم الأول (علشان ما نكتبها في Service ثم ننقلها). باقي P4 (API وprofiles) بعد P3.
- **P5** يتحسن بعد P4 (لأن الـ Resource Server بيستفيد من مسارات `/api/v1` الموحّدة).
- **P6 وP7** مستقلتان، وتقدر تبدّل ترتيبهما.

---

## 7. الإعدادات والمتغيرات (`deploy/.env.example`)

```properties
# ---- الصور ----
TAG=dev

# ---- PostgreSQL (superuser للـ container فقط، لا تستخدمه أي Service) ----
POSTGRES_USER=postgres
POSTGRES_PASSWORD=change_me

# ---- يوزر لكل Service (بدون علامة ' في الباسوردات) ----
ACCOUNT_DB_USER=account_svc
ACCOUNT_DB_PASSWORD=change_me
TRANSACTION_DB_USER=transaction_svc
TRANSACTION_DB_PASSWORD=change_me
NOTIFICATION_DB_USER=notification_svc
NOTIFICATION_DB_PASSWORD=change_me

# ---- JWT (RS256) ----
JWT_ISSUER=novabank-account
JWT_EXPIRATION_MINUTES=15
# للتشغيل المحلي من IntelliJ (في Docker بتتحط عبر secrets)
JWT_PRIVATE_KEY_PATH=./deploy/secrets/jwt-private.pem
JWT_PUBLIC_KEY_PATH=./deploy/secrets/jwt-public.pem

# ---- أول أدمن (اختياري، يُنشأ مرة لو لا يوجد أدمن) ----
BOOTSTRAP_ADMIN_EMAIL=
BOOTSTRAP_ADMIN_PASSWORD=

# ---- Gmail SMTP (App Password مش باسورد الإيميل) ----
EMAIL_USERNAME=your_email@gmail.com
EMAIL_APP_PASSWORD=your_app_password

# ---- Observability (profile اختياري) ----
GRAFANA_ADMIN_PASSWORD=change_me
```

- التشغيل المحلي من IntelliJ: `application-local.yml` يستورد `optional:file:./deploy/.env[.properties]` و**Working directory = جذر المشروع**.
- لا يُرفع إلا `.env.example`. وفيه **قيم وهمية** فقط.

---

## 8. استراتيجية الاختبار

| الطبقة | الأدوات | تشتغل | ما تغطيه |
|---|---|---|---|
| وحدة | JUnit 5، AssertJ، Mockito | `mvn test` (ثواني) | قواعد الدومين: `Account.debit/credit`، حساب الـ hash، تحويل claims |
| تكامل | Spring Boot Test + **Testcontainers** (PostgreSQL، Kafka) | `mvn verify` | الـ repositories، Flyway، الـ Outbox، المستهلكين، الأمان |
| عقود | round-trip JSON لكل حدث | `mvn test` | تسلسل/عكس أحداث `novabank-contracts` |
| شامل (Smoke) | `smoke-test.sh` على Compose | في CI | تسجيل ← دخول ← إيداع ← تحويل ← الرصيد |

**تسمية:** `*Test` للوحدة (surefire) و`*IT` للتكامل (failsafe).
**التغطية:** JaCoCo للتقرير، وحد أدنى **70%** على طبقتي `domain` و`service`.
**قاعدة:** أي إصلاح خطأ يبدأ باختبار يفشل ثم يُصلَح.

**سكربت `smoke-test.sh` (الخطوات):**
1. `POST /api/v1/auth/register` ← 201.
2. `POST /api/v1/auth/login` ← توكن.
3. أدمن (من `BOOTSTRAP_ADMIN_*`) يودع في حساب العميل ← 202 ثم ينتظر `COMPLETED`.
4. تحويل بين حسابين ← `COMPLETED`، ومجموع الأرصدة ثابت.
5. إعادة نفس `Idempotency-Key` ← نفس الرد.
6. محاولة تسجيل بـ `role: ADMIN` ← تنتج `CUSTOMER`.

---

## 9. CI/CD

**`.github/workflows/ci.yml`:**
```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:

jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: corretto
          java-version: '21'
          cache: maven
      - name: Build and test (unit + integration)
        run: mvn -B verify

  smoke:
    needs: build-test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Prepare temp env and keys
        run: |
          mkdir -p deploy/secrets
          openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out deploy/secrets/jwt-private.pem
          openssl rsa -pubout -in deploy/secrets/jwt-private.pem -out deploy/secrets/jwt-public.pem
          cp deploy/.env.example deploy/.env
      - name: Start stack
        run: docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build --wait
      - name: Smoke test
        run: ./deploy/scripts/smoke-test.sh
      - name: Logs on failure
        if: failure()
        run: docker compose -f deploy/docker-compose.yml logs --no-color
      - name: Cleanup
        if: always()
        run: docker compose -f deploy/docker-compose.yml down -v
```

**`.github/workflows/release.yml`** (عند tag `v*`):
```yaml
name: Release
on:
  push:
    tags: ['v*']

permissions:
  contents: read
  packages: write

jobs:
  images:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        include:
          - { service: discovery-service,    port: 8761 }
          - { service: api-gateway,          port: 8080 }
          - { service: user-account-service, port: 8081 }
          - { service: transaction-service,  port: 8082 }
          - { service: notification-service, port: 8083 }
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          build-args: |
            SERVICE=${{ matrix.service }}
            PORT=${{ matrix.port }}
          tags: |
            ghcr.io/${{ github.repository_owner }}/novabank-${{ matrix.service }}:${{ github.ref_name }}
            ghcr.io/${{ github.repository_owner }}/novabank-${{ matrix.service }}:latest
```
> أسماء الصور في GHCR **حروف صغيرة** فقط، فلو اسم حسابك فيه حروف كبيرة استخدم نسخة صغيرة ثابتة. وأضف خطوة فحص ثغرات (Trivy) بإصدار مثبّت ⚠️.

**`.github/dependabot.yml`:** تحديثات أسبوعية لـ `maven` و`docker` و`github-actions` (كلها `directory: /`).

**النشر (يكمل مع محاضرات الـ CI/CD):** لما يتوفر سيرفر، يضاف job يدخل بـ SSH ويشغّل `docker compose pull && docker compose up -d` اعتماداً على صور GHCR (مش بناء على السيرفر). الأسرار (مفاتيح SSH والـ `.env`) تتخزن في **GitHub Secrets/Environments** مع موافقة يدوية على بيئة الإنتاج. سيرفر النشر لا يهم نوعه (AWS أو Azure أو VPS)، والـ workflow نفسه.

---

## 10. معايير القبول النهائية لـ v2.0.0 (Definition of Done)

**الأمان**
- [ ] تسجيل مستخدم بـ `role: ADMIN` ينتج `CUSTOMER`.
- [ ] مفيش مفتاح خاص خارج Account، ومفيش `JWT_SECRET` مشترك.
- [ ] Gateway فقط منشور، ومسارات `/internal/**` و`/actuator/**` لا تُوجَّه.
- [ ] كل Service بيوزر داتابيز مستقل، ولا Service تستخدم superuser.
- [ ] rate limit يعمل على الدخول.
- [ ] مفيش أسرار في Git (`git log -p` + فحص أسرار نظيف).

**سلامة الفلوس**
- [ ] كل العمليات المالية idempotent (عميل ومستهلك).
- [ ] اختبار التزامن والتكرار والتعويض كلها خضراء.
- [ ] مجموع الأرصدة ثابت في اختبار التحويلات العشوائية.
- [ ] العميل لا يستطيع التعامل مع حساب غيره.

**التشغيل**
- [ ] `docker compose up -d --wait` من نظيف ينجح، والـ smoke test يمر.
- [ ] Flyway يبني الـ schema، و`ddl-auto=validate`.
- [ ] trace واحد يتتبع طلب عبر كل الـ Services.
- [ ] الصور على GHCR ومعها tag `v2.0.0`.

**الجودة**
- [ ] CI أخضر (`mvn verify` + smoke).
- [ ] تغطية ≥ 70% على الدومين والـ services.
- [ ] مفيش تكرار للأحداث والـ Enums بين الـ Services.
- [ ] `README.md` و`CHANGELOG.md` و`docs/events.md` محدّثة، وملف Postman بالـ `/api/v1`.

---

## 11. المخاطر والتقدير

| الخطر | الاحتمال | الأثر | التخفيف |
|---|---|---|---|
| أسماء dependencies/properties في Boot 4 وCloud 2025.1 تختلف عمّا هو مكتوب (⚠️) | عالٍ | متوسط | تحقق من الوثائق في بداية كل مرحلة، ولا تنسخ حرفياً |
| تعقيد الـ Saga والتعويض | عالٍ | عالٍ | ابدأ بالإيداع، وبعدين التحويل. اكتب اختبارات الفشل **قبل** الكود |
| تغيير الـ API يكسر Postman والعملاء | مؤكد | منخفض | `CHANGELOG.md` وتحديث الـ collection |
| الرام المحدودة (Docker ~7.4 جيجا) | متوسط | متوسط | profiles، `mem_limit`، تشغيل جزء من الستاك |
| بطء Testcontainers في CI | متوسط | منخفض | cache لـ Maven، وفصل `*IT` عند الحاجة |
| نسيان أسرار في الـ repo أثناء الاستيراد | متوسط | عالٍ | استيراد من نسخة نظيفة، وفحص بـ `git log -p`، وتدوير أي سر ظهر |
| توسّع النطاق | عالٍ | عالٍ | أي شيء خارج القسم 1.2 يروح للـ Backlog |

| المرحلة | الأيام التقريبية |
|---|---|
| P0 تأسيس | 0.5 |
| P1 أمان سريع | 1 |
| P2 قاعدة البيانات | 1.5 |
| P3 سلامة الفلوس | 5.5 |
| P4 العقود والـ API | 2 |
| P5 JWT RS256 | 1.5 |
| P6 المرونة | 1.5 |
| P7 المراقبة | 2 |
| P8 Docker وCI/CD | 1.5 |
| **المجموع** | **17** |

---

## 12. ملحق

### 12.1 أوامر مفيدة
```bash
# توليد مفاتيح RS256
mkdir -p deploy/secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out deploy/secrets/jwt-private.pem
openssl rsa -pubout -in deploy/secrets/jwt-private.pem -out deploy/secrets/jwt-public.pem

# بناء وتشغيل
mvn -B verify
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build --wait

# تشغيل التطوير مع بورتات إضافية
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.dev.yml up -d

# المراقبة
docker compose -f deploy/docker-compose.yml --profile observability up -d

# إعادة تهيئة كاملة (تمسح بيانات الداتابيز)
docker compose -f deploy/docker-compose.yml down -v

# توليد V1 من schema موجود
pg_dump --schema-only --no-owner --no-privileges -h localhost -p 5433 -U postgres nova_bank_account_db > V1__baseline.sql
```

### 12.2 اصطلاحات GitHub
- **Branches:** `feat/sec-01-registration-role`، `fix/...`، `chore/...`.
- **Commits (Conventional Commits):** `feat(account): force CUSTOMER role on public registration`.
- **عنوان الـ Issue:** `[SEC-01] منع التسجيل كـ ADMIN`، وفيه: الوصف، معايير القبول (من هذا الملف)، الـ Milestone (P1).
- **Labels:** `type:security`، `type:finance`، `type:infra`، `type:quality`، `breaking-change`، `needs-verify` (لعلامة ⚠️).
- **PR template:** وصف، رابط الـ Issue، قايمة (اختبارات أُضيفت، `CHANGELOG` محدّث، لا أسرار).
- **CODEOWNERS:** `* @USERNAME`.

### 12.3 ما بعد v2.0.0 (Backlog مرتّب)
1. Refresh tokens وJWKS وتدوير المفاتيح.
2. Debezium (CDC) بدل Outbox polling.
3. Config Server أو إدارة إعدادات مركزية.
4. فصل Auth في Service مستقلة.
5. Kubernetes (Helm) ونشر Blue/Green.
6. تعدد العملات وأسعار الصرف.
7. MapStruct بدل ModelMapper.

### 12.4 ملاحظات توافق وتحقق قبل البدء
- **Jackson في Boot 4:** الـ `ObjectMapper` الافتراضي ممكن يكون Jackson 3 (حزمة `tools.jackson`) بدل `com.fasterxml.jackson` ⚠️. عدّل الـ imports وقت ما تكتب الـ Outbox وأي كود تحويل JSON.
- **Spring Framework 7** فيه `@Retryable` مدمج كبديل لـ Spring Retry ⚠️.
- **Spring Cloud Gateway 2025.x:** بادئة الإعدادات `spring.cloud.gateway.server.webflux.*`.
- لو أي مكتبة اتعاقبت أو اتغير اسمها: افتح Issue بعلامة `needs-verify` ووثّق البديل في ADR.
