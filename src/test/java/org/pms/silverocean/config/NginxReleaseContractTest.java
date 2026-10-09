package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NginxReleaseContractTest {

    private static final String ASSET_NAME = "00-slickhood-client-headers.conf";
    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void deploymentWorkflowRemainsValidYaml() throws IOException {
        try (InputStream source = Files.newInputStream(PROJECT_ROOT.resolve(".github/workflows/deploy.yml"))) {
            Object workflow = new Yaml().load(source);
            assertThat(workflow).isInstanceOf(Map.class);
        }
    }

    @Test
    void nginxAssetKeepsTheHeaderAllowanceExplicitAndBounded() throws IOException {
        List<String> directives = Files.readAllLines(
                        PROJECT_ROOT.resolve("config/nginx").resolve(ASSET_NAME))
                .stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();

        assertThat(directives).containsExactly(
                "client_header_buffer_size 1k;",
                "large_client_header_buffers 4 32k;");
    }

    @Test
    void deploymentPackagesChecksumsCopiesAndVerifiesTheNginxAsset() throws IOException {
        String workflow = deployWorkflow();

        assertThat(workflow)
                .contains("install -m 0644 config/nginx/" + ASSET_NAME + " target/" + ASSET_NAME)
                .contains("sha256sum -- *.jar production-preflight.py production-backup.py "
                        + ASSET_NAME + " > release.sha256")
                .contains("target/" + ASSET_NAME + "\"")
                .contains("sha256sum -c release.sha256")
                .contains("nginx_candidate=/tmp/slickhood-backend-release/" + ASSET_NAME)
                .contains("test -f \"$nginx_candidate\"");
    }

    @Test
    void deploymentUsesProtectedBackupsAndRejectsUnsafeManagedTargets() throws IOException {
        String workflow = deployWorkflow();

        assertThat(workflow)
                .contains("backup_dir=/var/backups/slickhood")
                .contains("if sudo test -L \"$backup_dir\"; then")
                .contains("sudo install -d -o root -g root -m 0700 \"$backup_dir\"")
                .contains("sudo stat -c '%u:%g:%a' \"$backup_dir\"")
                .contains("if [ \"$backup_dir_state\" != \"0:0:700\" ]; then")
                .contains("sudo mktemp -p \"$backup_dir\" \"backend-$stamp.XXXXXXXX.jar\"")
                .contains("sudo mktemp -p \"$backup_dir\" \"nginx-client-headers-$stamp.XXXXXXXX.conf\"")
                .contains("if sudo test -L \"$nginx_live\"; then")
                .contains("if sudo test -e \"$nginx_live\" && ! sudo test -f \"$nginx_live\"; then")
                .doesNotContain("/home/silverocean/backups/nginx-client-headers-")
                .doesNotContain("/home/silverocean/backups/backend-");
    }

    @Test
    void deploymentValidatesBaselineAndInstallsTheNginxAssetAtomically() throws IOException {
        String workflow = deployWorkflow();
        String deployment = workflow.substring(workflow.indexOf("trap cleanup EXIT"));

        assertInOrder(deployment,
                "if ! sudo nginx -t; then",
                "Existing Nginx configuration is invalid; refusing to change it",
                "if ! sudo systemctl is-active --quiet nginx; then",
                "Nginx is not active; refusing to change its configuration",
                "sudo mktemp -p \"$nginx_dir\" '.slickhood-client-headers.XXXXXXXX'",
                "sudo install -o root -g root -m 0644 \"$nginx_candidate\" \"$nginx_staged\"",
                "sudo mv -- \"$nginx_staged\" \"$nginx_live\"",
                "nginx_staged=\"\"",
                "nginx_installed=true",
                "if ! sudo nginx -t; then",
                "if ! sudo systemctl reload nginx; then",
                "if ! sudo systemctl is-active --quiet nginx; then",
                "installed=true",
                "sudo install -o silverocean -g silverocean -m 0644 \"$candidate\" \"$live\"");
        assertThat(deployment)
                .doesNotContain("${nginx_live}.new-")
                .doesNotContain("$nginx_dir/00-slickhood-client-headers.conf.new");
    }

    @Test
    void failedDeploymentRestoresNginxOutsideConfDAndVerifiesTheService() throws IOException {
        String workflow = deployWorkflow();
        String rollback = section(workflow, "restore_previous_nginx() {", "cleanup_nginx_stage() {");

        assertThat(rollback)
                .contains("sudo mktemp -p \"$backup_dir\" \"nginx-client-headers-$stamp.failed.XXXXXXXX\"")
                .doesNotContain("failed_config=\"$nginx_dir")
                .doesNotContain("failed_config=\"${nginx_live}")
                .contains("if ! sudo mv -- \"$nginx_live\" \"$failed_config\"; then")
                .contains("if ! sudo rm -f -- \"$nginx_live\"; then")
                .contains("sudo mktemp -p \"$nginx_dir\" '.slickhood-client-headers-rollback.XXXXXXXX'")
                .contains("if ! sudo install -o root -g root -m 0644 \"$nginx_rollback\" \"$nginx_staged\"; then")
                .contains("if ! sudo mv -- \"$nginx_staged\" \"$nginx_live\"; then");
        assertInOrder(rollback,
                "sudo mktemp -p \"$nginx_dir\" '.slickhood-client-headers-rollback.XXXXXXXX'",
                "sudo install -o root -g root -m 0644 \"$nginx_rollback\" \"$nginx_staged\"",
                "sudo mv -- \"$nginx_staged\" \"$nginx_live\"",
                "if ! sudo nginx -t; then",
                "if ! sudo systemctl reload nginx; then",
                "if ! sudo systemctl is-active --quiet nginx; then",
                "nginx_installed=false");
    }

    @Test
    void cleanupExplicitlyChecksBothRollbackFunctionsAndThenRemovesAnyStage() throws IOException {
        String workflow = deployWorkflow();
        String cleanup = section(workflow, "cleanup() {", "run_preflight() {");
        String backendRollback = section(workflow, "restore_previous_release() {", "restore_previous_nginx() {");

        assertInOrder(cleanup,
                "if ! restore_previous_nginx; then",
                "if ! restore_previous_release; then",
                "if ! cleanup_nginx_stage; then");
        assertThat(backendRollback)
                .contains("if [ -z \"$rollback\" ] || sudo test -L \"$rollback\" || ! sudo test -f \"$rollback\"; then")
                .contains("if ! sudo install -o silverocean -g silverocean -m 0644 \"$rollback\" \"$live\"; then")
                .contains("if ! sudo systemctl restart pms.service; then")
                .contains("if ! wait_for_health; then");
    }

    @Test
    void deploymentProbesTheLargeHeaderThroughTheBackendContract() throws IOException {
        String workflow = deployWorkflow();

        assertThat(workflow)
                .contains("\"Authorization\": \"Bearer \" + (\"A\" * 14_000)")
                .contains("if status != 401:")
                .contains("payload.get(\"code\") != \"S0020\"")
                .contains("headers.get(\"X-Correlation-ID\") != correlation_id");
        assertInOrder(workflow,
                "nginx_installed=true",
                "sudo systemctl reload nginx",
                "sudo systemctl restart pms.service",
                "run_preflight 121 enabled require-insurance",
                "verify_large_authorization_header",
                "nginx_installed=false",
                "installed=false");
    }

    private String deployWorkflow() throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(".github/workflows/deploy.yml"));
    }

    private String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start of section %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end of section %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    private void assertInOrder(String source, String... fragments) {
        int previous = -1;
        for (String fragment : fragments) {
            int current = source.indexOf(fragment, previous + 1);
            assertThat(current)
                    .as("position of deployment fragment %s", fragment)
                    .isGreaterThan(previous);
            previous = current;
        }
    }
}
