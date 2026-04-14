package com.civicworks.api;

import com.civicworks.billing.domain.Account;
import com.civicworks.billing.domain.Bill;
import com.civicworks.billing.domain.BillLineItem;
import com.civicworks.billing.domain.FeeItem;
import com.civicworks.billing.infra.AccountRepository;
import com.civicworks.billing.infra.BillLineItemRepository;
import com.civicworks.billing.infra.BillRepository;
import com.civicworks.billing.infra.FeeItemRepository;
import com.civicworks.content.domain.ContentItem;
import com.civicworks.content.domain.ContentItem.ContentState;
import com.civicworks.content.domain.ContentItem.ContentType;
import com.civicworks.content.infra.ContentItemRepository;
import com.civicworks.dispatch.domain.Driver;
import com.civicworks.dispatch.domain.DriverDailyPresence;
import com.civicworks.dispatch.domain.Zone;
import com.civicworks.dispatch.domain.ZoneCapacityRule;
import com.civicworks.dispatch.infra.DriverDailyPresenceRepository;
import com.civicworks.dispatch.infra.DriverRepository;
import com.civicworks.dispatch.infra.ZoneCapacityRuleRepository;
import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import com.civicworks.platform.security.UserEntity.UserStatus;
import com.civicworks.platform.security.UserRepository;
import com.civicworks.searchanalytics.domain.AnomalyFlag;
import com.civicworks.searchanalytics.domain.KpiSnapshot;
import com.civicworks.searchanalytics.domain.SearchDocument;
import com.civicworks.searchanalytics.domain.SearchHistory;
import com.civicworks.searchanalytics.infra.AnomalyFlagRepository;
import com.civicworks.searchanalytics.infra.KpiSnapshotRepository;
import com.civicworks.searchanalytics.infra.SearchDocumentRepository;
import com.civicworks.searchanalytics.infra.SearchHistoryRepository;
import com.civicworks.settlement.domain.CashShift;
import com.civicworks.settlement.infra.CashShiftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.HttpRetryException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseApiIT {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected TestRestTemplate rest;
    @LocalServerPort protected int port;

    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected AccountRepository accountRepository;
    @Autowired protected BillRepository billRepository;
    @Autowired protected BillLineItemRepository billLineItemRepository;
    @Autowired protected FeeItemRepository feeItemRepository;
    @Autowired protected ContentItemRepository contentItemRepository;
    @Autowired protected SearchDocumentRepository searchDocumentRepository;
    @Autowired protected SearchHistoryRepository searchHistoryRepository;
    @Autowired protected KpiSnapshotRepository kpiSnapshotRepository;
    @Autowired protected AnomalyFlagRepository anomalyFlagRepository;
    @Autowired protected DriverRepository driverRepository;
    @Autowired protected DriverDailyPresenceRepository driverDailyPresenceRepository;
    @Autowired protected ZoneCapacityRuleRepository zoneCapacityRuleRepository;
    @Autowired protected CashShiftRepository cashShiftRepository;
    @Autowired protected JdbcTemplate jdbc;

    protected static String unique(String prefix) {
        return prefix + "_" + SEQ.incrementAndGet() + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    protected UserEntity createUser(String username, String password, Role... roles) {
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setDisplayName(username);
        u.setStatus(UserStatus.ACTIVE);
        u.setRoles(Set.of(roles));
        return userRepository.saveAndFlush(u);
    }

    @SuppressWarnings("unchecked")
    protected String login(String username, String password) {
        Map<String, Object> body = Map.of("username", username, "password", password);
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login", body, Map.class);
        if (resp.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Login failed for " + username + ": " + resp.getStatusCode());
        }
        return (String) resp.getBody().get("accessToken");
    }

    protected String createUserAndLogin(String prefix, String password, Role... roles) {
        String username = unique(prefix);
        createUser(username, password, roles);
        return login(username, password);
    }

    protected HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    protected HttpHeaders idempotentHeaders(String token) {
        HttpHeaders h = bearerHeaders(token);
        h.set("Idempotency-Key", UUID.randomUUID().toString());
        return h;
    }

    protected HttpHeaders idempotentHeaders(String token, String key) {
        HttpHeaders h = bearerHeaders(token);
        h.set("Idempotency-Key", key);
        return h;
    }

    protected <T> ResponseEntity<T> get(String url, String token, Class<T> type) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), type);
    }

    protected <T> ResponseEntity<T> get(String url, String token, ParameterizedTypeReference<T> type) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), type);
    }

    protected ResponseEntity<Map> post(String url, String token, Object body) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, bearerHeaders(token)), Map.class);
    }

    protected ResponseEntity<Map> put(String url, String token, Object body) {
        return rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, bearerHeaders(token)), Map.class);
    }

    protected ResponseEntity<Void> delete(String url, String token) {
        return rest.exchange(url, HttpMethod.DELETE, new HttpEntity<>(bearerHeaders(token)), Void.class);
    }

    protected ResponseEntity<Map> getMap(String url, String token) {
        return get(url, token, Map.class);
    }

    /**
     * POST without auth. Handles Java's HttpURLConnection 401 retry behavior
     * by catching HttpRetryException and returning the status code.
     */
    @SuppressWarnings("unchecked")
    protected ResponseEntity<Map> postNoAuth(String url, Object body) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof HttpRetryException hre) {
                return ResponseEntity.status(hre.responseCode()).build();
            }
            throw e;
        }
    }

    /**
     * GET without auth. Same HttpRetryException handling.
     */
    @SuppressWarnings("unchecked")
    protected ResponseEntity<Map> getNoAuth(String url) {
        try {
            return rest.getForEntity(url, Map.class);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof HttpRetryException hre) {
                return ResponseEntity.status(hre.responseCode()).build();
            }
            throw e;
        }
    }

    /**
     * Exchange without auth (for non-Map response types).
     */
    protected <T> ResponseEntity<T> exchangeNoAuth(String url, HttpMethod method, Object body, Class<T> type) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            return rest.exchange(url, method, new HttpEntity<>(body, h), type);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof HttpRetryException hre) {
                return ResponseEntity.status(hre.responseCode()).build();
            }
            throw e;
        }
    }

    protected Account createAccount(Long userId, String name) {
        Account a = new Account();
        a.setUserId(userId);
        a.setName(name);
        a.setStatus("ACTIVE");
        return accountRepository.saveAndFlush(a);
    }

    protected FeeItem createFeeItem(String code, String name, FeeItem.CalculationType type, BigDecimal rate) {
        FeeItem f = new FeeItem();
        f.setCode(code);
        f.setName(name);
        f.setCalculationType(type);
        f.setRate(rate);
        f.setActive(true);
        return feeItemRepository.saveAndFlush(f);
    }

    protected Bill createBill(Long accountId, BigDecimal amount) {
        Bill b = new Bill();
        b.setAccountId(accountId);
        b.setCycleDate(LocalDate.now());
        b.setCycleType("MONTHLY");
        b.setDueDate(LocalDate.now().plusDays(15));
        b.setPolicyDueInDays(15);
        b.setOriginalAmount(amount);
        b.setBalance(amount);
        return billRepository.saveAndFlush(b);
    }

    protected Bill createBillWithLineItems(Long accountId, BigDecimal amount) {
        Bill b = createBill(accountId, amount);
        // Ensure a fee item exists for line items
        FeeItem feeItem = feeItemRepository.findAll().stream().findFirst()
                .orElseGet(() -> createFeeItem(unique("LINE_FEE"), "Line Fee",
                        FeeItem.CalculationType.FLAT, amount));
        BillLineItem line = new BillLineItem();
        line.setBillId(b.getId());
        line.setFeeItemId(feeItem.getId());
        line.setDescription("Service charge");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitRate(amount);
        line.setAmount(amount);
        line.setLineOrder(0);
        billLineItemRepository.saveAndFlush(line);
        return b;
    }

    protected ContentItem createPublishedContent(Long createdBy) {
        ContentItem c = new ContentItem();
        c.setTitle("Test Content " + SEQ.incrementAndGet());
        c.setBody("<p>Test body</p>");
        c.setSanitizedBody("<p>Test body</p>");
        c.setContentType(ContentType.NEWS);
        c.setState(ContentState.PUBLISHED);
        c.setPublishedAt(Instant.now());
        c.setCreatedBy(createdBy);
        return contentItemRepository.saveAndFlush(c);
    }

    protected SearchDocument createSearchDocument(String title, String recordType, Long recordId) {
        SearchDocument d = new SearchDocument();
        d.setTitle(title);
        d.setRecordType(recordType);
        d.setRecordId(recordId);
        d.setBody("search body content");
        d.setCategory("test");
        d.setState("PUBLISHED");
        return searchDocumentRepository.saveAndFlush(d);
    }

    protected Zone createZone(String name) {
        jdbc.update("INSERT INTO zones (name, active) VALUES (?, true)", name);
        Long id = jdbc.queryForObject("SELECT id FROM zones WHERE name = ?", Long.class, name);
        Zone z = new Zone();
        z.setId(id);
        z.setName(name);
        z.setActive(true);
        return z;
    }

    protected Driver createDriver(Long userId, BigDecimal rating, BigDecimal lat, BigDecimal lng) {
        Driver d = new Driver();
        d.setUserId(userId);
        d.setRating(rating);
        d.setLatitude(lat);
        d.setLongitude(lng);
        d.setActive(true);
        return driverRepository.saveAndFlush(d);
    }

    protected DriverDailyPresence createPresence(Long driverId, int minutesOnline) {
        DriverDailyPresence p = new DriverDailyPresence();
        p.setDriverId(driverId);
        p.setPresenceDate(LocalDate.now());
        p.setMinutesOnline(minutesOnline);
        return driverDailyPresenceRepository.saveAndFlush(p);
    }

    protected ZoneCapacityRule createCapacityRule(Long zoneId, int maxAssignments) {
        ZoneCapacityRule r = new ZoneCapacityRule();
        r.setZoneId(zoneId);
        r.setMaxConcurrentAssignments(maxAssignments);
        return zoneCapacityRuleRepository.saveAndFlush(r);
    }

    protected CashShift createOpenShift(Long operatorId) {
        CashShift s = new CashShift();
        s.setOperatorId(operatorId);
        s.setStartedAt(Instant.now());
        s.setStatus("OPEN");
        return cashShiftRepository.saveAndFlush(s);
    }
}
