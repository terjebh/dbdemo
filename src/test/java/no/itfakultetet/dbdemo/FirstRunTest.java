package no.itfakultetet.dbdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tester first-run-flyten: uten konfigurasjon skal alle sider redirecte til
 * /setup, og /setup skal vises.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "dbdemo.config.path=/tmp/dbdemo-test-ingen-config/dbconfig.json"
})
class FirstRunTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void utenConfigRedirectesAltTilSetup() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup"));
    }

    @Test
    void selectRedirectesTilSetup() throws Exception {
        mockMvc.perform(get("/select/postgres"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup"));
    }

    @Test
    void setupSidenVises() throws Exception {
        mockMvc.perform(get("/setup"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("første gangs oppsett")));
    }
}
