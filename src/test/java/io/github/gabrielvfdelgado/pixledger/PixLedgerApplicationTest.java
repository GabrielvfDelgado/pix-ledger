package io.github.gabrielvfdelgado.pixledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PixLedgerApplicationTest extends PostgresIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void exposesHealthEndpoint() {
        ResponseEntity<String> response = http.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void servesDemoPage() {
        ResponseEntity<String> response = http.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("<title>PixLedger</title>");
    }

    @Test
    void publishesOpenApiDocument() {
        ResponseEntity<String> response = http.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("/api/v1/transfers", "/api/v1/accounts/{id}/statement");
    }
}
